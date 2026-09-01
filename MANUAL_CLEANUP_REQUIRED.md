# Manual Cleanup Required - Action Needed

## Critical Bug Fixed (Commit 96d6034)

The cleanup code I added in commit 6a0f366 was **actively broken**:
- Fired on EVERY lifecycle event (not once)
- Debug seed logic recreated data after every cleanup
- Created infinite delete/recreate loop (7+ executions in 20 seconds)
- **Caused student "No Active Project" bug** - real project/tasks wiped by loop

All debug seed/cleanup logic has been **completely removed**.

## Action Required: Clear App Data

To remove orphaned test projects ("Blaa", "debug-project-1"), you must manually clear app data:

### Option 1: Via Device Settings (Recommended)
```
1. Open Settings on device
2. Navigate to: Settings → Apps → TeamPulse
3. Tap "Storage"
4. Tap "Clear Data" or "Clear Storage"
5. Confirm
```

### Option 2: Via ADB
```powershell
adb shell pm clear com.cutm.TeamPulse
```

## After Clearing Data

1. **Install updated APK:** `app\build\outputs\apk\debug\app-debug.apk`
2. **Sign in as teacher** - create real project/teams
3. **Sign in as student** - verify they see correct project/tasks
4. **Check logcat** - confirm NO "Debug seed data created" or "Cleaned up" messages across multiple screen resumes

## Verification Steps

### 1. Confirm Seeding Loop Stopped
```powershell
# Monitor logcat while navigating app
adb logcat -s TeacherHome:D ProjectRepository:D

# Should see ZERO of these lines:
# - "Cleaned up orphaned 'Blaa' project"
# - "Cleaned up debug-project-1"
# - "Debug seed data created for..."
```

### 2. Check DB State
- Teacher should see ONLY projects they actually created
- No "Debug Test Project", no "Blaa"
- Student should see their actual assigned project

### 3. Student Screen Test
- Sign in as student (231801371093@cutmap.ac.in)
- **Expected:** "No Active Project" empty state (if not assigned to any team)
- OR: Correct project card with correct tasks
- **NOT expected:** Orphaned "Hi"/"Hi" tasks, wrong project name

## Why This Happened

**My mistake:** I added automated cleanup in `viewLifecycleOwner.lifecycleScope.launch`, which runs on every lifecycle callback (onCreate, onResume, etc.), not once.

**Should have done:**
- Manual cleanup via adb/settings from the start
- OR one-time migration with SharedPreferences flag
- NOT lifecycle-triggered logic without guards

**Lesson learned:** Never add auto-cleanup to lifecycle methods without proper "already executed" guards.

## Current Status

✅ All debug seed/cleanup logic removed (commit 96d6034)  
⏳ Manual cleanup pending (user action required)  
⏳ Student screen verification pending (after cleanup)

Once you've cleared data and verified student screen, update this doc with results.
