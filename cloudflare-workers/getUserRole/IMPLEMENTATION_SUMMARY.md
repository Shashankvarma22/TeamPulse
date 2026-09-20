# Cloudflare Worker Implementation Summary

## Problem Solved
TeamPulse_Users_Registry sheet (email→role authorization) was publicly accessible. Cloud Functions solution required billing account approval (blocked).

## Solution: Cloudflare Workers
Zero-cost, no-credit-card-required alternative to Cloud Functions.

**Why Cloudflare Workers:**
- **100,000 requests/day free** (vs Cloud Functions requiring billing account)
- No credit card required for signup
- Same security model (ID token verification + service account auth)
- Better performance (edge network vs single region)

## Implementation Details

### ID Token Verification
**Issuer:** `accounts.google.com` (NOT Firebase - verified this session)

**Public keys endpoint:** `https://www.googleapis.com/oauth2/v3/certs`

**Verification flow:**
1. Parse JWT (header.payload.signature)
2. Fetch Google's JWK public keys (cached for 1 hour)
3. Validate claims:
   - `iss`: must be `accounts.google.com` or `https://accounts.google.com`
   - `aud`: must match `973824891796-i5gddskacq0gfsntsotn2pk41er301k4.apps.googleusercontent.com`
   - `exp`: not expired
   - `iat`: not in future (allow 60s clock skew)
4. Verify RS256 signature using Web Crypto API
5. Return payload if valid, null otherwise

**Web Crypto API usage:**
```javascript
const key = await crypto.subtle.importKey('jwk', jwk, {
  name: 'RSASSA-PKCS1-v1_5',
  hash: { name: 'SHA-256' },
}, false, ['verify']);

const valid = await crypto.subtle.verify(
  'RSASSA-PKCS1-v1_5',
  key,
  signature,
  data
);
```

### Service Account Authentication
**Pattern:** JWT signing → OAuth token exchange → Sheets API call

**Flow:**
1. Load service account JSON from Cloudflare Secret (base64-encoded)
2. Parse PEM private key → import as PKCS#8 CryptoKey
3. Create JWT with claims:
   ```json
   {
     "iss": "teampulse-sheets@teampulse-505107.iam.gserviceaccount.com",
     "sub": "teampulse-sheets@teampulse-505107.iam.gserviceaccount.com",
     "aud": "https://oauth2.googleapis.com/token",
     "iat": <now>,
     "exp": <now + 3600>,
     "scope": "https://www.googleapis.com/auth/spreadsheets.readonly"
   }
   ```
4. Sign JWT with RS256 using Web Crypto API
5. Exchange signed JWT for access token via POST to `oauth2.googleapis.com/token`
6. Cache access token for ~1 hour (reuse across requests)
7. Use access token in `Authorization: Bearer` header for Sheets API calls

**Web Crypto API usage:**
```javascript
// Import PEM private key
const key = await crypto.subtle.importKey('pkcs8', derBuffer, {
  name: 'RSASSA-PKCS1-v1_5',
  hash: { name: 'SHA-256' },
}, false, ['sign']);

// Sign JWT
const signature = await crypto.subtle.sign(
  'RSASSA-PKCS1-v1_5',
  key,
  dataBuffer
);
```

### Sheets API Access
**Method:** Direct REST API calls via `fetch()`

**Endpoint:**
```
GET https://sheets.googleapis.com/v4/spreadsheets/{spreadsheetId}/values/{range}
```

**Headers:**
```
Authorization: Bearer {access_token}
```

**Response:** Standard Sheets API JSON with `values` array.

### Email Matching Enforcement
Server-side check at line 86-88:
```javascript
if (tokenEmail.toLowerCase() !== email.toLowerCase()) {
  return jsonResponse({ error: 'Token email does not match requested email' }, 403);
}
```

Occurs **before** Sheets API call. Cannot be bypassed by client.

## Client ID Verification

### Android App Configuration
**File:** `app/src/main/res/values/strings.xml`
```xml
<string name="default_web_client_id">973824891796-i5gddskacq0gfsntsotn2pk41er301k4.apps.googleusercontent.com</string>
```

**Usage:** `GoogleAuthClientImpl.kt` line 45
```kotlin
val serverClientId = context.getString(R.string.default_web_client_id)
val signInWithGoogleOption = GetSignInWithGoogleOption.Builder(
    serverClientId = serverClientId,
)
```

**Worker Configuration:** `src/index.js` line 13
```javascript
EXPECTED_AUDIENCE: '973824891796-i5gddskacq0gfsntsotn2pk41er301k4.apps.googleusercontent.com',
```

**Verification Result:** ✅ **EXACT MATCH**

This is the OAuth 2.0 **Web application** client ID (NOT the Android client ID). Google Sign-In uses the Web client ID as `serverClientId` to issue ID tokens, and those tokens have this value in their `aud` claim.

## Security Model

### No Credentials in Code
- Service account JSON stored as **Cloudflare Secret** (encrypted at rest)
- Injected at runtime as environment variable
- Never in source code or git
- Worker code contains only public configuration (client IDs, sheet IDs, API URLs)

### Least-Privilege Service Account
```
teampulse-sheets@teampulse-505107.iam.gserviceaccount.com
```

- **No project-level IAM roles**
- Access scoped to resources explicitly shared with it
- Viewer-only access to TeamPulse_Users_Registry sheet
- Cannot modify sheet data

### Token Verification
- Signature verified against Google's public keys (fetched from googleapis.com)
- All standard JWT claims validated
- Issuer checked against expected value
- Audience checked against known client ID
- Expiration enforced

### Email Matching
- Server-side enforcement
- Token email must match requested email
- Users cannot lookup other users' roles
- 403 Forbidden if mismatch

## Deployment Requirements

### Prerequisites
1. Cloudflare account (free signup, no credit card)
2. Wrangler CLI: `npm install -g wrangler`
3. Service account JSON key (base64-encoded for Cloudflare Secret)
4. Sheet shared with service account (Viewer access)

### Deployment Steps
```powershell
# 1. Login
wrangler login

# 2. Deploy
cd cloudflare-workers/getUserRole
npm install
wrangler deploy

# 3. Store secret
wrangler secret put GOOGLE_SERVICE_ACCOUNT_JSON
# Paste base64-encoded service account JSON

# 4. Share sheet
# Add teampulse-sheets@teampulse-505107.iam.gserviceaccount.com as Viewer
```

### Worker URL Format
```
https://teampulse-getuserrole.<YOUR_SUBDOMAIN>.workers.dev
```

Subdomain is auto-assigned by Cloudflare based on your account.

## Testing Checklist

### ✅ Pre-Deployment
- [x] Code compiles (no build step for Workers)
- [x] Client ID match verified in codebase
- [x] Service account email confirmed
- [x] Sheet ID matches actual sheet

### □ Post-Deployment
- [ ] Worker deployed successfully (URL obtained)
- [ ] Secret stored (GOOGLE_SERVICE_ACCOUNT_JSON)
- [ ] Sheet shared with service account
- [ ] Test 1: Worker responds to unauthenticated request
- [ ] Test 2: Android app → Worker → role lookup succeeds
- [ ] Test 3: Public access removed from sheet
- [ ] Test 4: Sign-in still works after removing public access

## Cost

**Cloudflare Workers Free Tier:**
- 100,000 requests/day
- 10ms CPU time per request
- No egress charges
- No credit card required

**Expected Usage:**
- 100 users × 5 sign-ins/day = 500 requests/day
- 0.5% of free tier
- **Cost: $0.00/month**

## Comparison: Cloud Functions vs Cloudflare Workers

| Feature | Cloud Functions | Cloudflare Workers |
|---------|----------------|-------------------|
| **Cost (free tier)** | 2M invocations/month | 100K requests/day (3M/month) |
| **Billing account** | **Required** (blocked) | Not required ✅ |
| **Credit card** | Required for billing | Not required ✅ |
| **Runtime** | Node.js (googleapis SDK) | V8 isolates (Web Crypto API) |
| **Service account auth** | Runtime identity (no key) | Secret (base64 key) |
| **Cold start** | ~300ms | <10ms |
| **Regions** | Single region | Global edge network |
| **Deployment** | gcloud CLI | wrangler CLI |

**Decision:** Cloudflare Workers chosen due to billing account blocker.

## Files

### Worker Code
- `src/index.js` - Main Worker (ID token verification + service account auth + Sheets API)

### Configuration
- `wrangler.toml` - Worker name, compatibility date
- `package.json` - wrangler dependency for deployment

### Documentation
- `README.md` - API specification, configuration verification
- `DEPLOYMENT_GUIDE.md` - Step-by-step deployment with troubleshooting
- `IMPLEMENTATION_SUMMARY.md` - This file (technical details)

### Secrets (not in code)
- `GOOGLE_SERVICE_ACCOUNT_JSON` - Base64-encoded service account key (set via `wrangler secret put`)

## Rollback Plan

### If Worker Fails
**Option 1:** Deploy Cloud Functions instead (code preserved in `cloud-functions/getUserRole/`)

**Option 2:** Revert to direct Sheets API access:
```powershell
Copy-Item "app\src\main\java\com\cutm\TeamPulse\data\repository\UserRegistryRepositoryImpl.kt.backup_before_cloud_function" `
          "app\src\main\java\com\cutm\TeamPulse\data\repository\UserRegistryRepositoryImpl.kt" -Force
```

Re-enable public access to sheet, rebuild APK.

## Next Steps

1. **Deploy Worker** following `DEPLOYMENT_GUIDE.md`
2. **Test end-to-end** with real Android device + real sign-in
3. **Report back** with:
   - Worker URL
   - Real logcat output showing successful role lookup
   - Confirmation of ID token verification working
4. **Remove public access** from sheet (only after verification)
5. **Push to origin** (Worker code + updated Android app)

---

**Status:** Implementation complete, ready for deployment and testing.

**Awaiting:** User deployment + end-to-end verification before calling this done.
