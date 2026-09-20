# Testing getUserRole Worker Without Android App

## Quick Answer: YES

You can get a real Google ID token for your OAuth client ID without building/installing the Android app.

## Method: HTML Test Page

I've created `test-token.html` which uses Google's Sign in with Google JavaScript library to obtain a real ID token for the same OAuth client ID (`973824891796-...`) used by your Android app.

### How It Works

1. **Sign in with Google button** - Uses the official Google Identity Services library
2. **Same OAuth client ID** - Configured to use your Web application client ID
3. **Real ID token** - Gets an actual JWT with all correct claims (iss, aud, email, etc.)
4. **Test directly** - Can send request to Worker from the page or copy token for curl/PowerShell

## Usage

### Step 0: Prerequisites

**IMPORTANT:** Before using the test page, check OAuth client configuration.

See `CHECK_OAUTH_CONFIG.md` for detailed instructions.

**Quick check:**
1. Go to: https://console.cloud.google.com/apis/credentials?project=teampulse-505107
2. Find OAuth client: `973824891796-...`
3. Check "Authorized JavaScript origins" includes `http://localhost` or `http://localhost:8000`
4. If not, add it and save (wait 5 minutes for propagation)

### Step 1: Serve the Test Page

**DO NOT open with `file://` protocol** - Google's Sign-In won't work.

**Option A: Python (simplest)**
```powershell
cd "d:\4th year\TeamPulse\cloudflare-workers\getUserRole"
python -m http.server 8000
```

**Option B: Node.js**
```powershell
cd "d:\4th year\TeamPulse\cloudflare-workers\getUserRole"
npx http-server -p 8000
```

**Option C: VS Code Live Server**
- Right-click `test-token.html` → "Open with Live Server"

Then open in browser: **http://localhost:8000/test-token.html** (or whatever port you used)

### Step 2: Sign In

1. Click the "Sign in with Google" button
2. Sign in with a user from your TeamPulse_Users_Registry sheet
3. The page will display your ID token

**Expected result:**
- ID token appears (long JWT string)
- Decoded payload shows your email, name, etc.
- Claims should show:
  ```json
  {
    "iss": "accounts.google.com",
    "aud": "973824891796-i5gddskacq0gfsntsotn2pk41er301k4.apps.googleusercontent.com",
    "email": "your@email.com",
    ...
  }
  ```

### Step 3: Test the Worker

**Option A: Test from the HTML page**
1. Scroll to "Step 3: Test Worker"
2. Enter your Worker URL: `https://teampulse-getuserrole.<YOUR_SUBDOMAIN>.workers.dev`
3. Click "Send Request"
4. View response (should show your role if everything works)

**Option B: Copy and use in PowerShell**
1. Click "Copy PowerShell Command"
2. Paste into PowerShell
3. Replace `YOUR_WORKER_URL_HERE` with your actual Worker URL
4. Run the command

**Option C: Copy token and use manually**
```powershell
$token = "PASTE_ID_TOKEN_HERE"
$email = "your@email.com"
$workerUrl = "https://teampulse-getuserrole.YOUR_SUBDOMAIN.workers.dev"

curl -X POST $workerUrl `
  -H "Authorization: Bearer $token" `
  -H "Content-Type: application/json" `
  -d "{`"email`":`"$email`"}"
```

## Expected Responses

### ✅ Success (Worker deployed, sheet access OK)
```json
{
  "role": "TEACHER",
  "displayName": "John Doe",
  "enrolledAt": "1704067200000",
  "status": "ACTIVE"
}
```

### ❌ Token Verification Failed
```json
{
  "error": "Invalid or expired ID token"
}
```

**Causes:**
- Token expired (they're valid for ~1 hour - get a fresh one)
- Worker code has wrong OAuth client ID (check line 13 in `src/index.js`)
- Token issuer mismatch (should be `accounts.google.com`)

### ❌ Service Account Issues
```json
{
  "error": "Failed to read Users Registry sheet"
}
```

**Causes:**
- Service account doesn't have Viewer access to the sheet
- `GOOGLE_SERVICE_ACCOUNT_JSON` secret not configured
- Service account JSON is malformed/invalid

**Debug:** Check Worker logs with `wrangler tail`

### ❌ User Not Found
```json
{
  "error": "User not found in Users Registry. Please contact your teacher to be enrolled."
}
```

**Causes:**
- Email not in the TeamPulse_Users_Registry sheet
- Email case mismatch (should work case-insensitive, but verify)
- Sheet range is wrong (should be `Users!A:F`)

## Why This Works

The `test-token.html` page uses Google's official **Sign in with Google** JavaScript library, configured with your Web application OAuth client ID. When you sign in:

1. Google issues an ID token for your Web client (same as Android app uses)
2. Token has correct `aud` claim: `973824891796-i5gddskacq0gfsntsotn2pk41er301k4.apps.googleusercontent.com`
3. Token has correct `iss` claim: `accounts.google.com`
4. Token is exactly what your Android app would send

**This is NOT a mock** - it's a real Google ID token that will work with your Worker.

## Token Lifetime

ID tokens expire after ~1 hour. If you get "Invalid or expired ID token":
1. Go back to `test-token.html`
2. Sign in again (may auto-sign-in if session still active)
3. Get fresh token

## Troubleshooting

### "Sign in with Google" button doesn't appear
- Check browser console for errors
- Make sure you have internet connectivity
- Try a different browser (Chrome, Edge, Firefox)

### "Token verification failed" but token looks valid
- Decode the token (page shows this automatically)
- Check `aud` claim matches Worker's `EXPECTED_AUDIENCE`
- Check `iss` claim is `accounts.google.com`
- Check `exp` hasn't passed (compare to current Unix time)

### Worker returns 500 error
- Check Worker logs: `wrangler tail`
- Look for service account auth errors
- Verify secret is set: `wrangler secret list`

### CORS error in browser
- This is normal for the HTML page test
- Worker includes CORS headers, but some browsers may still block
- Use PowerShell/curl instead, or check browser console for actual error

## Next Steps

Once you've verified the Worker works with a real ID token:

1. ✅ Worker is deployed and accessible
2. ✅ ID token verification works
3. ✅ Service account auth works
4. ✅ Sheets API read works
5. ✅ Role is returned correctly

Then update the Android app to use the Worker URL and test the full integration.

## Comparison to Android App Test

| Aspect | HTML Test Page | Android App |
|--------|---------------|-------------|
| **ID Token** | Real Google token | Real Google token |
| **OAuth Client ID** | Same (`973824891796-...`) | Same (`973824891796-...`) |
| **Token Claims** | Identical (iss, aud, email) | Identical |
| **Network Path** | Browser → Worker | Android → Worker |
| **Isolation** | Tests Worker only | Tests Worker + Android integration |
| **Speed** | Instant (seconds) | Slow (build/install/logcat) |

**Recommendation:** Test with HTML page first to verify Worker works, then test Android app to verify full integration.
