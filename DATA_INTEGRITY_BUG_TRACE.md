# Data Integrity Bug - Trace Instructions

## 🔴 CRITICAL FINDING - Authorization Bug Identified

**Root Cause Located:**
- Project "Blaa" has `teacherEmail = 231801371093@cutmap.ac.in` (STUDENT email, not teacher)
- Teacher trying to delete: `scs982627@gmail.com` (different account)
- **`deleteProject()` has NO authorization check** - only verifies session exists
- Any logged-in teacher can delete any project (security issue)

**Files:** `ProjectRepositoryImpl.kt` lines 160-193

### Immediate Action Required

**Option 1: Check if delete was attempted**
Search your existing logcat for:
```
ProjectRepository: === DELETE PROJECT CALLED ===
```

This will tell us if:
- Delete was never called (UI issue)
- Delete was called and claimed success (transaction rollback)
- Delete was called and failed (exception)

See `LOGCAT_SEARCH_INSTRUCTIONS.md` for detailed search steps.

**Option 2: Delete "Blaa" again RIGHT NOW**
1. Install updated APK: `app\build\outputs\apk\debug\app-debug.apk`
2. Sign in as teacher
3. Delete "Blaa" project
4. Capture logcat immediately
5. Long-press FAB to dump DB state
6. Check if "Blaa" still exists in dump

### Updated Build Available
✅ **DB dump tool fixed** - now dumps tasks correctly  
✅ All diagnostic logging in place  
✅ APK: `app\build\outputs\apk\debug\app-debug.apk`

---

## Critical Bug Report

**Observed Behavior:**
1. Teacher deleted project "Blaa"
2. Teacher created project "Hi" with 2 tasks ("hi", "hello")
3. Student force-stopped app and signed in fresh
4. **Student screen shows:** Project "Blaa" (deleted), 2 tasks both titled "Hi" (wrong)

**This indicates serious data integrity issues:**
- Delete may not be working (orphaned data)
- OR Create may not be persisting correctly
- OR Read path is showing stale/cached data

---

## Diagnostic Build Ready

**Comprehensive logging added to trace:**
1. ✅ Actual database state (what rows exist)
2. ✅ Delete operations (did they execute and commit?)
3. ✅ Create operations (did they persist?)
4. ✅ Read operations (what data is being retrieved?)

---

## Test Instructions

### Step 1: Build and Install Diagnostic APK

```powershell
# Clean build to ensure all logging is included
.\gradlew clean assembleDebug

# Install
adb install -r app\build\outputs\apk\debug\app-debug.apk
```

### Step 2: Reproduce the Bug with Logging

**As Teacher:**

1. Sign in as teacher
2. **Delete project "Blaa"** (if it exists)
   - Note the project ID from UI or check logcat for "DELETE PROJECT CALLED"
   - Watch for: `ProjectRepository: === DELETE PROJECT SUCCEEDED ===`
   
3. **Create new project "Hi"**
   - Watch for: `ProjectRepository: === CREATE PROJECT SUCCEEDED ===`
   - Note the project ID

4. **Add 2 tasks:**
   - Task 1: title "hi", assigned to student `231801371093@cutmap.ac.in`, due in ~9 days
   - Task 2: title "hello", unassigned (whole team)
   - Watch for: `TaskRepository: === CREATE TASK SUCCEEDED ===` (twice)

### Step 3: Capture Complete Logcat

```powershell
# Clear old logs
adb logcat -c

# Start capturing (leave running)
adb logcat -s ProjectRepository:D TaskRepository:D StudentHome:D TeacherHome:D > bug_trace_$(Get-Date -Format "yyyyMMdd_HHmmss").log
```

### Step 4: Student Sign-In (Fresh)

1. **Force-stop the app:**
   - Settings → Apps → TeamPulse → Force Stop

2. **Launch app and sign in as student:**
   - Email: `231801371093@cutmap.ac.in`

3. **Wait for home screen to load**
   - Let it sit for 10 seconds to capture all Flow emissions

### Step 5: Verify What Student Sees

**Observe and note:**
- Project card: What name is displayed? ("Blaa" or "Hi"?)
- Task list: How many tasks? What are the titles?
- Due dates: What do they show?

### Step 6: Check Actual Database State

**As Teacher (still logged in on device):**

1. Long-press the FAB (+ button) on teacher home screen
2. This dumps actual database state to logcat
3. Watch for: `TeacherHome: ========== DATABASE STATE DUMP ==========`

---

## What to Look For in Logcat

### 1. Delete Operation Check

**Search for:** `ProjectRepository: === DELETE PROJECT CALLED ===`

**Expected sequence:**
```
ProjectRepository: === DELETE PROJECT CALLED ===
ProjectRepository: Project ID: [some-id]
ProjectRepository: Session verified: [teacher-email]
ProjectRepository: Starting transaction...
ProjectRepository: Found X teams to delete
ProjectRepository:   Deleting tasks for team: [team-id]
ProjectRepository: Deleting X teams...
ProjectRepository: Deleting project [project-id]...
ProjectRepository: Transaction completed successfully
ProjectRepository: === DELETE PROJECT SUCCEEDED ===
```

**Red flags:**
- ❌ "DELETE PROJECT FAILED" - operation threw exception
- ❌ "Session expired" - delete aborted, data not deleted
- ❌ No "Transaction completed successfully" - rollback occurred

### 2. Create Project Check

**Search for:** `ProjectRepository: === CREATE PROJECT CALLED ===`

**Expected:**
```
ProjectRepository: === CREATE PROJECT CALLED ===
ProjectRepository: Project ID: [new-id]
ProjectRepository: Project Name: Hi
ProjectRepository: Teacher: [teacher-email]
ProjectRepository: Due Date: [timestamp] ([date])
ProjectRepository: === CREATE PROJECT SUCCEEDED ===
```

**Red flags:**
- ❌ "CREATE PROJECT FAILED" - not persisted
- ❌ Wrong project name in log

### 3. Create Tasks Check

**Search for:** `TaskRepository: === CREATE TASK CALLED ===` (should appear twice)

**Expected (twice):**
```
TaskRepository: === CREATE TASK CALLED ===
TaskRepository: Task ID: [task-id]
TaskRepository: Title: hi  (or "hello")
TaskRepository: Assignee: 231801371093@cutmap.ac.in  (or empty for team task)
TaskRepository: === CREATE TASK SUCCEEDED ===
```

**Red flags:**
- ❌ Only one CREATE TASK log (missing second task)
- ❌ Wrong titles in logs

### 4. Student Read Path Check

**Search for:** `StudentHome: === DIAGNOSTIC: Looking for student project ===`

**Expected:**
```
StudentHome: === DIAGNOSTIC: Looking for student project ===
StudentHome: Student email: 231801371093@cutmap.ac.in
StudentHome: All teams in DB: X
StudentHome:   Team: [team-id], project: [project-id], name: [team-name]
StudentHome: Found student team: [team-id], projectId: [project-id]
StudentHome: === PROJECT DATA ===
StudentHome: Project ID: [project-id]
StudentHome: Project Name: Hi  <-- SHOULD BE "Hi", not "Blaa"
StudentHome: Due Date: [timestamp] ([date])
StudentHome: Status: ACTIVE
StudentHome: Tasks for team: X total, Y completed
```

**Red flags:**
- ❌ Project Name: Blaa (wrong project loaded)
- ❌ Wrong project ID (loading deleted project)

**Search for:** `StudentHome: === TASKS RECEIVED ===`

**Expected:**
```
StudentHome: === TASKS RECEIVED ===
StudentHome: Total tasks: 2  <-- SHOULD BE 2
StudentHome:   Task: [task-id-1]
StudentHome:     Title: hi  <-- SHOULD BE "hi", not "Hi"
StudentHome:   Task: [task-id-2]
StudentHome:     Title: hello  <-- SHOULD BE "hello", not "Hi"
```

**Red flags:**
- ❌ Both tasks have title "Hi" (data corruption)
- ❌ Wrong number of tasks

### 5. Database Dump Check

**Search for:** `TeacherHome: === PROJECTS`

**Expected:**
```
TeacherHome: === PROJECTS (X) ===
TeacherHome:   ID: [project-id]
TeacherHome:   Name: Hi  <-- "Hi" should exist
TeacherHome:   Teacher: [teacher-email]
TeacherHome:   Status: ACTIVE
TeacherHome:   ---
(Blaa should NOT appear here if delete succeeded)
```

**Red flags:**
- ❌ Project "Blaa" still in DB (delete failed silently)
- ❌ Project "Hi" NOT in DB (create failed silently)
- ❌ No projects at all (complete data loss)

---

## Diagnosis Decision Tree

### Scenario A: Delete Failed (Blaa still in DB)
**Evidence:**
- Database dump shows "Blaa" project
- DELETE log shows "FAILED" or "Session expired"

**Root Cause:** `deleteProject()` not executing or rolling back

**Next Steps:**
- Check why transaction failed
- Check session expiry timing
- Check if exception thrown

### Scenario B: Delete Succeeded BUT Wrong Project Loaded
**Evidence:**
- Database dump shows ONLY "Hi" (Blaa gone)
- Student screen shows "Blaa"
- Flow is loading wrong data

**Root Cause:** Stale Flow/cache, wrong query join

**Next Steps:**
- Check if observeProject returns cached data
- Check if team still references old projectId
- Check Flow SharingStarted behavior

### Scenario C: Create Failed (Hi not in DB)
**Evidence:**
- Database dump shows NO "Hi" project
- CREATE log shows "FAILED"

**Root Cause:** `createProject()` not persisting

**Next Steps:**
- Check if transaction rolled back
- Check if upsert throws exception

### Scenario D: Tasks Wrong Data
**Evidence:**
- Database dump shows tasks with correct titles
- Student screen shows wrong titles
- Data exists but displayed incorrectly

**Root Cause:** UI rendering bug, not data bug

**Next Steps:**
- Check TaskItemView setTaskData()
- Check if wrong field being displayed

---

## Report Back With

1. **Full logcat output** (the entire .log file)
2. **Screenshot of student screen** showing wrong data
3. **Database dump section** from logcat (PROJECTS list)
4. **Which scenario matches** (A, B, C, or D)
5. **First anomaly found** (which step failed first)

---

## Critical Questions to Answer

1. **Does project "Blaa" still exist in the database after delete?**
   - YES → Delete is broken (Scenario A)
   - NO → Delete worked, read path is broken (Scenario B)

2. **Does project "Hi" exist in the database after create?**
   - YES → Create worked, data is there
   - NO → Create is broken (Scenario C)

3. **Do tasks have correct titles in database ("hi", "hello")?**
   - YES → Data is correct, UI is wrong (Scenario D)
   - NO → Data corruption at create time

4. **What project ID is student's team referencing?**
   - Old "Blaa" ID → Team not updated after delete/create
   - New "Hi" ID → Team correct, project query wrong

---

## Files Modified

**Added diagnostic logging:**
- `ProjectDao.kt` - debugGetAllProjects() query
- `TaskAssignmentDao.kt` - debugGetAllTasks() query
- `ProjectRepositoryImpl.kt` - DELETE/CREATE logging
- `TaskRepositoryImpl.kt` - CREATE logging
- `StudentHomeViewModel.kt` - Read path logging with full data dump
- `TeacherHomeFragment.kt` - Long-press FAB dumps DB state

**No functional changes** - only logging added.
