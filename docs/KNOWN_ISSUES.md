# Known Issues

## Splash Screen During Session Check (UX Gap)

**Status:** UX gap identified, deferred (not a bug)

**Current Behavior:**
After process death (Recents-swipe, low-memory kill, reboot), returning users briefly see the sign-in screen (~1-2 seconds) before being auto-redirected to their home screen. The sign-in UI (Google Sign-In button, branding) is fully visible during this pause.

**Root Cause:**
- Navigation graph starts at `signInFragment` by default
- Session check runs asynchronously (Room database query)
- Cold database open takes ~1-2 seconds
- UI shows interactive sign-in screen while waiting for session query result

**Expected "Properly Crafted" Behavior:**
- Show neutral splash screen (logo only, no interactive UI) while session check runs
- Only show sign-in UI if session query returns null (no saved session)
- Auto-navigate to home if session exists, without ever showing sign-in UI

**Why Deferred:**
This is a **separate, larger change** from the session restoration fix:
- Requires new splash screen/Activity or Fragment
- Changes navigation graph start destination
- Needs splash → sign-in and splash → home transitions
- Proper implementation involves Activity splash (shows before app process fully starts)

Current behavior is **functionally correct** (session restores, no re-authentication required), just not optimal UX.

**Scope for Future Task:**
1. Create SplashFragment or use Android 12+ Splash Screen API
2. Set nav graph start destination to splash
3. Run session check in splash
4. Navigate to sign-in (if null) or home (if session exists)
5. Ensure smooth transitions (no flash/flicker)

---

## Cold Start Frame Drops (~195-218 Frames)

**Status:** Root cause confirmed, fix not yet applied

**Symptom:**
Choreographer reports ~195-218 dropped frames during cold start, causing brief UI freeze/stutter.

**Root Cause (Evidence-Based):**
- Frame drops occur during onStart() → database-warmup-completion window (~1578ms measured)
- Database open takes ~1-2 seconds on cold start
- Sign-in flow waits for database warmup to complete before proceeding
- **Toolbar is NOT the cause:** `setSupportActionBar` measured at only 1-5ms (negligible)

**Current Mitigation:**
- Database warmup runs in background coroutine (Dispatchers.IO)
- Warmup starts in Application.onCreate(), as early as possible
- Sign-in still waits for completion before showing home screen

**Potential Fixes (Not Implemented):**
1. Show loading indicator during database warmup (honest about wait)
2. Defer database-dependent UI until warmup completes (don't block navigation)
3. Optimize database open time (add indices, reduce schema complexity)
4. Use Room's `setJournalMode(TRUNCATE)` for faster cold opens

**Decision:**
Deferred - functional but slow. Prioritize after critical bugs resolved.

---

## Worker getUserRole Call Slow (~1.7s)

**Status:** Root cause confirmed, fix not yet applied

**Symptom:**
Sign-in feels slow. Worker network call takes ~1.7 seconds (measured: 12:58:56.219 → 12:58:57.964).

**Root Cause (Evidence-Based):**
**Breakdown hypothesis:**
- Cloudflare cold-start: ~50-200ms
- **JWK fetch: ~200-500ms** ← PRIMARY TARGET
- OAuth exchange: ~300-600ms
- Sheets API: ~400-800ms

**Current Implementation:**
```javascript
// In-memory JWK cache (cloudflare-workers/getUserRole/src/index.js lines 26-30)
let jwkCache = null;
let jwkCacheExpiry = 0;
```

**Problem:**
- In-memory cache is process-local
- Cloudflare Workers cold-start clears cache
- Each cold start = JWK fetch (~200-500ms)

**Proposed Fix (Not Implemented):**
Use Cloudflare Workers KV for persistent JWK caching:
- Survives cold starts (shared across all Worker instances)
- Free tier: 100K reads/day, 1K writes/day (sufficient)
- Latency: <10ms for reads (vs 200-500ms for fetch)
- Keep 1-hour TTL (Google's keys rotate periodically)

**Expected Improvement:**
- Cold start with KV hit: 1.7s → 1.2-1.5s (save 200-500ms JWK fetch)
- Warm start: No change (already fast with in-memory cache)

**Decision:**
Deferred - bounded, well-scoped change. Can implement independently after critical bugs resolved.

---

Last updated: 2026-09-20
