# Runtime Test: Verify Delete Still Works

## Why This Test Is Needed

After adding extensive diagnostic logging to `deleteProject()`, we need to confirm the delete flow still works end-to-end for the happy path (teacher deleting their own project).

**What was changed:**
- Added logging at every step of delete transaction
- Added DB dump tool with DAO injection
- No functional logic changed, but need runtime verification

**What could have broken:**
- Transaction rollback from logging overhead
- DAO dependency injection issues
- Build/compilation cache issues

## Test Procedure

### Prerequisites
- APK installed: `app\build\outputs\apk\debug\app-debug.apk` (from commit c4bfa58 or later)
- Logcat monitoring: `adb logcat -s ProjectRepository:D TeacherHome:D`

### Step 1: Create Test Project

**As teacher (scs982627@gmail.com):**
1. Open app, sign in as teacher
2. Tap FAB (+) to create project
3. Fill in:
   - Name: `DELETE_ME_TEST`
   - Due date: Any future date
   - Google Sheet ID: `test-sheet-123` (placeholder)
   - Drive Folder ID: `test-folder-456` (placeholder)
4. Tap "Create"

**Expected in logcat:**
```
ProjectRepository: === CREATE PROJECT CALLED ===
ProjectRepository: Project Name: DELETE_ME_TEST
ProjectRepository: Teacher: scs982627@gmail.com
ProjectRepository: === CREATE PROJECT SUCCEEDED ===
```

**Expected in UI:**
- "DELETE_ME_TEST" appears in teacher's project list
- Card shows project name, due date, 0% progress

### Step 2: Verify Ownership

**Before attempting delete, confirm it's your project:**
1. Long-press FAB to dump DB state
2. Check logcat for:
```
TeacherHome: === PROJECTS (X) ===
TeacherHome:   Name: DELETE_ME_TEST
TeacherHome:   Teacher: scs982627@gmail.com  <-- MUST match signed-in user
```

If teacher email doesn't match, abort test and investigate create bug.

### Step 3: Delete Project

**In teacher UI:**
1. Tap "DELETE_ME_TEST" card → opens project detail
2. Tap 3-dot menu → "Delete Project"
3. Confirm deletion in dialog

**Expected in logcat:**
```
ProjectRepository: === DELETE PROJECT CALLED ===
ProjectRepository: Project ID: [some-uuid]
ProjectRepository: Session verified: scs982627@gmail.com
ProjectRepository: Starting transaction...
ProjectRepository: Found 0 teams to delete  (new project, no teams yet)
ProjectRepository: Deleting 0 teams...
ProjectRepository: Deleting project [uuid]...
ProjectRepository: Transaction completed successfully
ProjectRepository: === DELETE PROJECT SUCCEEDED ===
```

**Expected in UI:**
- Navigation back to teacher home
- Toast: "Project deleted"
- "DELETE_ME_TEST" **no longer appears** in project list

### Step 4: Verify Removal from DB

**Immediately after delete:**
1. Long-press FAB to dump DB state again
2. Check logcat:
```
TeacherHome: === PROJECTS (X) ===
(DELETE_ME_TEST should NOT appear in this list)
```

**If project still appears:**
- ❌ Delete transaction rolled back
- Check logcat for exception between "Starting transaction" and "Transaction completed"

**If project doesn't appear:**
- ✅ Delete succeeded

## Test Results

### ✅ PASS Criteria
1. CREATE log shows "SUCCEEDED"
2. Project appears in UI and DB dump
3. DELETE log shows "Transaction completed successfully" and "SUCCEEDED"
4. Project disappears from UI
5. Project does NOT appear in post-delete DB dump

### ❌ FAIL Criteria
- CREATE succeeds but project not in DB dump (create bug)
- DELETE log shows "FAILED" or exception
- DELETE log shows "SUCCEEDED" but project still in DB dump (transaction rollback)
- UI crash or navigation fails

### ⚠️ PARTIAL FAIL
- Delete succeeds in DB but UI still shows project (stale Flow/cache issue)
- Delete log incomplete (logging itself broke transaction)

## If Test Fails

**Capture full context:**
1. Complete logcat from create through delete
2. Screenshot of UI state before/after delete
3. Both DB dumps (before and after delete)
4. Note exact error message or exception in logs

**Likely causes if fail:**
1. **Transaction rollback from logging:**
   - Remove some Log.d() calls and rebuild
   - Logging in transaction may cause issues

2. **Flow not updating:**
   - Delete succeeded in DB but ViewModel Flow didn't re-emit
   - Check SharingStarted policy

3. **DAO injection issue:**
   - DB dump tool broke Room's dependency graph
   - Remove taskDao injection temporarily

## Current Status

✅ **PASS** (Tested: August 28, 2026)

**Test Results:**
- Created project: `DELETE_ME_TEST` (ID: `e471a08c-7d98-4ae1-9a93-45cab7402e28`)
- Deleted through normal teacher UI flow
- **Verified on-device:** Project removed from UI
- **Verified in DB:** DB dump showed count 4→3, project ID gone, no orphaned tasks
- Transaction cascade worked correctly

**Conclusion:** Delete functionality works end-to-end post-scaffolding

## After Test Passes

**Cleanup work:**
1. Remove all diagnostic logging from:
   - ProjectRepositoryImpl.kt (deleteProject, createProject)
   - TaskRepositoryImpl.kt (createTask)
   - StudentHomeViewModel.kt (Flow logging)
   - TeacherHomeFragment.kt (DB dump tool, unless keeping for dev)
2. Remove debug DAO queries:
   - ProjectDao.debugGetAllProjects()
   - TaskAssignmentDao.debugGetAllTasks()
3. Commit as "chore: Remove diagnostic scaffolding"
4. Keep ORDER BY fix in UserSessionDao (actual bug fix from session investigation)

## Related Files

**Delete implementation:**
- `ProjectRepositoryImpl.kt` lines 160-193

**Teacher UI:**
- `TeacherHomeViewModel.kt` lines 53-57 (ownership filter)
- `ProjectDetailFragment.kt` lines 475-503 (delete UI flow)

**Diagnostic tools:**
- `TeacherHomeFragment.kt` lines 87-93, 415-442 (DB dump)
- `ProjectDao.kt` lines 33-42 (debug query)
- `TaskAssignmentDao.kt` lines 37-52 (debug query)
