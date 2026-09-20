# Check OAuth Client Configuration Before Testing

## Problem
Google's Sign-In JS library requires registered "Authorized JavaScript origins" for the OAuth client. If `http://localhost` isn't registered, the sign-in button won't work.

## Step 1: Check Current Configuration

### Go to Google Cloud Console
1. Open: https://console.cloud.google.com/apis/credentials?project=teampulse-505107
2. Find the OAuth 2.0 Client ID: `973824891796-i5gddskacq0gfsntsotn2pk41er301k4.apps.googleusercontent.com`
   - Look for the name (likely "Web client" or similar)
   - Click on it to view details

### Check "Authorized JavaScript origins"
Look at the "Authorized JavaScript origins" section.

**Currently configured:** (Unknown - you need to check this)

**Required for `test-token.html`:**
- `http://localhost`
- `http://localhost:8000` (or whatever port you'll use)

## Step 2: Add `http://localhost` if Missing

If localhost is NOT in the list:

1. Click **"Edit"** or **"Add URI"** under "Authorized JavaScript origins"
2. Add:
   ```
   http://localhost
   http://localhost:8000
   ```
3. Click **"Save"**
4. Wait ~5 minutes for changes to propagate

**Note:** Adding localhost doesn't affect your Android app - it will continue to work normally.

## Step 3: Serve the Test Page

**DO NOT open `test-token.html` with `file://` protocol** - it won't work.

### Option A: Python HTTP Server (Simplest)
```powershell
cd "d:\4th year\TeamPulse\cloudflare-workers\getUserRole"
python -m http.server 8000
```

Then open: http://localhost:8000/test-token.html

### Option B: Node.js HTTP Server
```powershell
cd "d:\4th year\TeamPulse\cloudflare-workers\getUserRole"
npx http-server -p 8000
```

Then open: http://localhost:8000/test-token.html

### Option C: PHP Built-in Server
```powershell
cd "d:\4th year\TeamPulse\cloudflare-workers\getUserRole"
php -S localhost:8000
```

Then open: http://localhost:8000/test-token.html

### Option D: VS Code Live Server Extension
1. Install "Live Server" extension in VS Code
2. Right-click `test-token.html`
3. Click "Open with Live Server"

## Step 4: Test Sign-In

1. Open http://localhost:8000/test-token.html (or whatever port you used)
2. Open browser DevTools (F12)
3. Go to Console tab
4. Click "Sign in with Google"

### Expected: Success
- Google sign-in popup appears
- You sign in
- ID token appears on the page

### Expected: Failure if localhost not configured
**Console error:**
```
[GSI_LOGGER]: The given origin is not allowed for the given client ID.
```

**OR:**

```
Cookies required for sign-in
```

**Solution:** Add `http://localhost:8000` to Authorized JavaScript origins (see Step 2).

## Alternative: No Configuration Changes Needed

If you don't want to modify the OAuth client configuration, you can use a **different approach**:

### Use `gcloud auth print-identity-token` (if gcloud installed)

This doesn't work for your use case because it generates an ID token for a **service account**, not a user account, and the issuer will be wrong.

### Use a Temporary Web Deployment

Deploy `test-token.html` to a real HTTPS domain:
1. GitHub Pages (free, HTTPS)
2. Netlify (free, HTTPS)
3. Cloudflare Pages (free, HTTPS)

Add that HTTPS origin to the OAuth client's Authorized JavaScript origins.

**This is overkill for testing** - just add localhost instead.

## Verification Checklist

- [ ] Checked Google Cloud Console for OAuth client `973824891796-...`
- [ ] Verified "Authorized JavaScript origins" includes `http://localhost` or `http://localhost:8000`
- [ ] If not, added localhost origins and saved
- [ ] Waited 5 minutes for changes to propagate
- [ ] Started local HTTP server (Python/Node/PHP/VS Code)
- [ ] Opened http://localhost:8000/test-token.html (NOT file://)
- [ ] Clicked "Sign in with Google"
- [ ] Sign-in popup appeared (if not, check Console for errors)

## If You Can't Modify OAuth Client

**Reason:** Maybe the OAuth client is managed by someone else, or you don't have permissions.

**Workaround:** Skip the HTML test page and go straight to Android app testing. The Android app doesn't need "Authorized JavaScript origins" - it works differently (package name + SHA-1 fingerprint).

The HTML test page is a convenience for faster iteration, but it's not required if OAuth client config can't be changed.

## Summary

**Before running `test-token.html`:**
1. ✅ Check OAuth client has `http://localhost` in Authorized JavaScript origins
2. ✅ Add it if missing
3. ✅ Serve file via HTTP server (NOT `file://`)
4. ✅ Open http://localhost:PORT/test-token.html

**If OAuth client can't be modified:** Skip HTML test, go straight to Android app integration.
