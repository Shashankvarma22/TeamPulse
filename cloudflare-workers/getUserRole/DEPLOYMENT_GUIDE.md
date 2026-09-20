# Cloudflare Worker Deployment Guide

## Quick Start (5 Steps)

### 1. Install Wrangler
```powershell
npm install -g wrangler
```

Verify:
```powershell
wrangler --version
```

### 2. Login to Cloudflare
```powershell
wrangler login
```

This opens a browser. Sign up for a free Cloudflare account (no credit card required) or log in if you already have one.

**Free tier includes:**
- 100,000 Worker requests per day
- Unlimited deployments
- Built-in monitoring

### 3. Deploy the Worker
```powershell
cd "d:\4th year\TeamPulse\cloudflare-workers\getUserRole"
npm install
wrangler deploy
```

**Save the Worker URL from the output!**

Example: `https://teampulse-getuserrole.your-subdomain.workers.dev`

### 4. Store Service Account Key

**a. Base64-encode your service account JSON:**
```powershell
$json = Get-Content "path\to\teampulse-sheets-service-account-key.json" -Raw
$base64 = [Convert]::ToBase64String([Text.Encoding]::UTF8.GetBytes($json))
$base64 | Set-Clipboard
```

**b. Store as Cloudflare Secret:**
```powershell
wrangler secret put GOOGLE_SERVICE_ACCOUNT_JSON
```

Paste the base64 string when prompted (it's in your clipboard).

### 5. Share Sheet with Service Account
1. Open: https://docs.google.com/spreadsheets/d/1MzysETdhkqVYxPvlehkTxEXd1qG8y85cqmB6g2qmzK0
2. Click **Share**
3. Add: `teampulse-sheets@teampulse-505107.iam.gserviceaccount.com` with **Viewer** access
4. Uncheck "Notify people"
5. Click **Done**

---

## Testing the Deployment

### Test 1: Worker Responds
```powershell
$workerUrl = "https://teampulse-getuserrole.YOUR_SUBDOMAIN.workers.dev"
curl -X POST $workerUrl -H "Content-Type: application/json" -d '{"email":"test@example.com"}'
```

**Expected:** `{"error":"Missing or invalid Authorization header"}`

✅ This confirms the Worker is live and enforcing authentication.

### Test 2: Real End-to-End Test

**Prerequisites:**
- Updated Android APK with Worker URL configured
- Test user in TeamPulse_Users_Registry sheet
- Device with ADB access

**Steps:**
1. Update `SheetsConfig.kt` with your Worker URL:
   ```kotlin
   const val CLOUD_FUNCTIONS_BASE_URL = "https://teampulse-getuserrole.YOUR_SUBDOMAIN.workers.dev/"
   ```

2. Rebuild APK:
   ```powershell
   .\gradlew.bat assembleDebug
   ```

3. Install and test:
   ```powershell
   adb install "app\build\outputs\apk\debug\app-debug.apk"
   adb logcat -s SignInViewModel:D
   ```

4. Sign in on the device

**Expected logcat output:**
```
D SignInViewModel: Session created: email=user@example.com, role=TEACHER
```

✅ This confirms end-to-end: Android app → Worker → ID token verification → Sheets API → role returned

---

## Remove Public Access (ONLY After Verification)

**DO NOT do this until you've verified the Worker is working end-to-end.**

1. Open the TeamPulse_Users_Registry sheet
2. Click **Share**
3. Change "Anyone with the link" to **Restricted**
4. Verify only these accounts have access:
   - Your Google account (Owner)
   - `teampulse-sheets@teampulse-505107.iam.gserviceaccount.com` (Viewer)
5. Click **Done**

**Test immediately:** Sign out of the Android app, sign in again, verify role lookup still works.

If it fails, the service account doesn't have access. Re-share the sheet with the correct service account email.

---

## Configuration Verification Checklist

### ✅ Client ID Match
The Worker expects this client ID in ID token `aud` claim:
```
973824891796-i5gddskacq0gfsntsotn2pk41er301k4.apps.googleusercontent.com
```

**Android app configuration:**
- File: `app/src/main/res/values/strings.xml`
- Key: `default_web_client_id`
- Value: `973824891796-i5gddskacq0gfsntsotn2pk41er301k4.apps.googleusercontent.com`
- Used in: `GoogleAuthClientImpl.kt` line 45 as `serverClientId`

**Verified:** ✅ Match confirmed (checked in codebase)

### ✅ Sheet ID Match
Worker uses:
```
1MzysETdhkqVYxPvlehkTxEXd1qG8y85cqmB6g2qmzK0
```

**Verify this is your actual TeamPulse_Users_Registry sheet ID** (from the sheet URL).

### ✅ Service Account Email
Worker expects Sheets access via:
```
teampulse-sheets@teampulse-505107.iam.gserviceaccount.com
```

**Verify:**
- This service account exists in your Google Cloud project
- You have its JSON key file
- The sheet is shared with this exact email address

---

## Monitoring

### Real-Time Logs
```powershell
wrangler tail
```

Shows Worker execution logs, errors, and request details.

### Deployment History
```powershell
wrangler deployments list
```

### Worker Metrics
View in Cloudflare dashboard:
1. Go to https://dash.cloudflare.com
2. Navigate to **Workers & Pages**
3. Click your Worker name
4. View request count, errors, CPU time

---

## Updating the Worker

After code changes:
```powershell
wrangler deploy
```

Secrets persist - no need to re-set `GOOGLE_SERVICE_ACCOUNT_JSON`.

---

## Troubleshooting

### "Project not found" during deploy
You need to create a Cloudflare account first:
```powershell
wrangler login
```

### "Secret not found" errors in Worker logs
Set the secret:
```powershell
wrangler secret put GOOGLE_SERVICE_ACCOUNT_JSON
```

### "Failed to read Users Registry sheet"
Check:
1. Service account has Viewer access to the sheet
2. Sheet ID in Worker code matches your actual sheet
3. Service account email is exactly `teampulse-sheets@teampulse-505107.iam.gserviceaccount.com`

### "Invalid or expired ID token"
Check:
1. Token is from Google Sign-In, not Firebase (issuer should be `accounts.google.com`)
2. Token `aud` claim matches expected client ID
3. Token hasn't expired (they're valid for ~1 hour)

View logs to see exact error:
```powershell
wrangler tail
```

### Android app can't reach Worker
1. Verify Worker URL in `SheetsConfig.kt` is correct
2. Check device has internet connectivity
3. View Android logcat for HTTP errors

---

## Rollback Plan

If the Worker doesn't work:

### Option 1: Revert to Cloud Functions
The Cloud Functions code is still in `cloud-functions/getUserRole/` - you can deploy that instead.

### Option 2: Revert to Direct Sheets Access
1. Restore: `UserRegistryRepositoryImpl.kt.backup_before_cloud_function`
2. Re-enable public access to the sheet
3. Rebuild APK

---

## Cost Breakdown

**Cloudflare Workers Free Tier:**
- 100,000 requests/day
- 10ms CPU time per invocation
- No data transfer fees
- No credit card required

**Expected Usage:**
- 100 users
- 5 sign-ins per user per day
- = 500 requests/day
- **0.5% of free tier limit**

**Cost:** $0.00/month

---

## Security Notes

1. **Service account key stored as Secret:**
   - Encrypted at rest by Cloudflare
   - Never exposed in logs or dashboard
   - Injected at runtime only

2. **ID token verification:**
   - Signature verified against Google's public keys
   - Claims validated (issuer, audience, expiration)
   - Email matching enforced server-side

3. **No credentials in code:**
   - Worker code contains no secrets
   - Safe to commit to git
   - Public GitHub repos OK

4. **Least-privilege service account:**
   - Only Viewer access to one sheet
   - No project-level IAM roles
   - Can't modify sheet data

---

## Next Steps After Successful Deployment

1. ✅ Verify Worker works end-to-end
2. ✅ Remove public access from TeamPulse_Users_Registry sheet
3. ✅ Test sign-in still works after removing public access
4. ✅ Commit Worker code to git
5. ✅ Push to origin
6. ✅ Document Worker URL for team reference
