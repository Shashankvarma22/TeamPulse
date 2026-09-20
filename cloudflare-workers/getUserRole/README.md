# getUserRole Cloudflare Worker

## Purpose
Securely lookup user role from TeamPulse_Users_Registry sheet without exposing the sheet publicly.

## Security Model
- **Authentication**: Verifies Google Sign-In ID tokens from `accounts.google.com`
- **Authorization**: Users can only lookup their own role (token email must match requested email)
- **Credentials**: Service account JSON stored as Cloudflare Secret (encrypted, never in code)

## No Billing Account Required
Cloudflare Workers free tier:
- **100,000 requests per day**
- **No credit card needed**
- More than enough for once-per-sign-in role lookups

## Prerequisites

### 1. Install Wrangler CLI (Cloudflare's deployment tool)
```powershell
npm install -g wrangler
```

### 2. Login to Cloudflare
```powershell
wrangler login
```

This opens a browser for OAuth authentication. No billing account setup required.

### 3. Prepare Service Account Key
Your service account JSON key needs to be base64-encoded for storage as a Cloudflare Secret.

**PowerShell:**
```powershell
$serviceAccountJson = Get-Content "path\to\service-account-key.json" -Raw
$base64 = [Convert]::ToBase64String([Text.Encoding]::UTF8.GetBytes($serviceAccountJson))
$base64 | Set-Clipboard
# Now the base64 string is in your clipboard
```

## Deployment

### Step 1: Deploy the Worker
```powershell
cd "d:\4th year\TeamPulse\cloudflare-workers\getUserRole"
npm install
wrangler deploy
```

**Expected output:**
```
Total Upload: XX.XX KiB / gzip: XX.XX KiB
Uploaded teampulse-getuserrole (X.XX sec)
Published teampulse-getuserrole (X.XX sec)
  https://teampulse-getuserrole.<YOUR_SUBDOMAIN>.workers.dev
```

**Copy the Worker URL** - you'll need it for testing and Android app configuration.

### Step 2: Store Service Account Key as Secret
```powershell
wrangler secret put GOOGLE_SERVICE_ACCOUNT_JSON
```

When prompted, paste the base64-encoded service account JSON (from your clipboard if you followed Step 3).

**Security:** Cloudflare Secrets are encrypted at rest and never logged or exposed in the dashboard.

### Step 3: Share Sheet with Service Account
1. Open: https://docs.google.com/spreadsheets/d/1MzysETdhkqVYxPvlehkTxEXd1qG8y85cqmB6g2qmzK0
2. Click **Share**
3. Add: `teampulse-sheets@teampulse-505107.iam.gserviceaccount.com` (Viewer)
4. Uncheck "Notify people"
5. Click **Done**

## API Specification

### Endpoint
`POST https://teampulse-getuserrole.<YOUR_SUBDOMAIN>.workers.dev`

### Request
**Headers:**
- `Authorization: Bearer <google_id_token>` (from Android CredentialManager)
- `Content-Type: application/json`

**Body:**
```json
{
  "email": "user@example.com"
}
```

### Response

**Success (200):**
```json
{
  "role": "TEACHER",
  "displayName": "John Doe",
  "enrolledAt": "1704067200000",
  "status": "ACTIVE"
}
```

**Errors:**
- `401 Unauthorized` - Missing/invalid/expired ID token
- `403 Forbidden` - Token email doesn't match requested email
- `404 Not Found` - User not found in registry
- `400 Bad Request` - Invalid role data in sheet
- `500 Internal Server Error` - Unexpected error

## Configuration Verification

### Client ID Match
The Worker expects ID tokens with `aud` claim:
```
973824891796-i5gddskacq0gfsntsotn2pk41er301k4.apps.googleusercontent.com
```

This **MUST** match the `serverClientId` in the Android app's sign-in configuration.

**Verified in Android code:**
- File: `GoogleAuthClientImpl.kt` line 45
- Reads from: `R.string.default_web_client_id`
- Value in `strings.xml`: `973824891796-i5gddskacq0gfsntsotn2pk41er301k4.apps.googleusercontent.com`
- ✅ **Confirmed match**

### Expected Token Claims
Your Google Sign-In ID tokens should have:
```json
{
  "iss": "accounts.google.com",
  "aud": "973824891796-i5gddskacq0gfsntsotn2pk41er301k4.apps.googleusercontent.com",
  "sub": "10769150350006150715113082367",
  "email": "user@example.com",
  "email_verified": true,
  "name": "John Doe",
  "iat": 1234567890,
  "exp": 1234571490
}
```

**NOT Firebase tokens** - this Worker verifies against `accounts.google.com`, not `securetoken.google.com`.

## Testing

### Test 1: Worker is Live
```powershell
curl -X POST https://teampulse-getuserrole.<YOUR_SUBDOMAIN>.workers.dev `
  -H "Content-Type: application/json" `
  -d '{"email":"test@example.com"}'
```

**Expected response:**
```json
{"error":"Missing or invalid Authorization header"}
```

This confirms the Worker is running and enforcing authentication.

### Test 2: Real Token Verification
1. Install updated Android APK
2. Sign in with a real user from the registry
3. Check logcat for role lookup result:
   ```
   adb logcat -s SignInViewModel:D
   ```

**Expected log:**
```
D SignInViewModel: Session created: email=user@example.com, role=TEACHER
```

If this appears, the Worker is working end-to-end.

## Monitoring

### View Worker Logs
```powershell
wrangler tail
```

This streams real-time logs from your Worker.

### View Deployment Info
```powershell
wrangler deployments list
```

## Updating the Worker

After code changes:
```powershell
wrangler deploy
```

Secrets persist across deployments - no need to re-set them.

## Cost

**Free tier limits:**
- 100,000 requests/day
- 10ms CPU time per request
- No egress fees

**Expected usage:**
- Each sign-in = 1 request
- 100 users × 5 sign-ins/day = 500 requests/day
- **Well within free tier**

## Troubleshooting

### "Invalid or expired ID token"
- Check that token is from Google Sign-In, not Firebase
- Verify `aud` claim matches expected client ID
- Check token hasn't expired (max 1 hour lifetime)

### "Failed to read Users Registry sheet"
- Verify service account has Viewer access to the sheet
- Check sheet ID in Worker code matches your actual sheet
- View Worker logs: `wrangler tail`

### "GOOGLE_SERVICE_ACCOUNT_JSON secret not configured"
- Set secret: `wrangler secret put GOOGLE_SERVICE_ACCOUNT_JSON`
- Paste base64-encoded service account JSON

## Files

**Worker code:**
- `src/index.js` - Main Worker logic (ID token verification + Sheets API)

**Configuration:**
- `wrangler.toml` - Worker name and compatibility settings
- `package.json` - Dependencies (only wrangler for deployment)

**Secrets (not in code):**
- `GOOGLE_SERVICE_ACCOUNT_JSON` - Base64-encoded service account key (set via `wrangler secret put`)
