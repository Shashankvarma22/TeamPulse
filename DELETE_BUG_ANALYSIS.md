# Delete Bug - Root Cause Analysis

## Summary

Project "Blaa" (ID: `93ab134c-fe14-4101-9a02-9956c4c7c7cd`) still exists in database after delete attempt. Root cause identified.

## Critical Finding: NO Authorization Check

**File:** `app/src/main/java/com/cutm/TeamPulse/data/repository/ProjectRepositoryImpl.kt`  
**Lines:** 160-193

The `deleteProject()` function **ONLY checks if a session exists**, but does **NOT verify ownership**:

```kotlin
override suspend fun deleteProject(projectId: String): ApiResult<Unit> = withContext(dispatchers.io) {
    try {
        android.util.Log.d("ProjectRepository", "=== DELETE PROJECT CALLED ===")
        android.util.Log.d("ProjectRepository", "Project ID: $projectId")

        // Verify session
        val session = sessionDao.getActive()
        if (session == null) {
            android.util.Log.e("ProjectRepository", "Delete failed: Session expired")
            return@withContext ApiResult.Error("Session expired")
        }
        
        android.util.Log.d("ProjectRepository", "Session verified: ${session.email}")
        // ^^^ NO CHECK: session.email == project.teacherEmail ^^^

        // Cascade delete in ATOMIC TRANSACTION
        database.withTransaction {
            // ... deletes tasks, teams, project ...
        }
```

## The Smoking Gun

Your DB dump revealed:
- **Project "Blaa"** has `teacherEmail = 231801371093@cutmap.ac.in` (STUDENT email)
- **Teacher account** trying to delete: `scs982627@gmail.com`

**This is a data creation bug, not a delete bug.**

## Two Possible Scenarios

### Scenario A: Delete Succeeded (But Wrong Owner)
- Teacher deleted "Blaa" 
- Code allows any logged-in teacher to delete any project (no ownership check)
- Delete should have succeeded
- But "Blaa" still exists → **transaction rolled back silently**

### Scenario B: Silent Authorization Rejection
- Some code path (not visible in deleteProject()) rejects the delete
- UI doesn't show any error
- User thinks delete worked, but it was silently rejected

## What We Need From Logcat

Search for this exact sequence in your logcat:

```
ProjectRepository: === DELETE PROJECT CALLED ===
ProjectRepository: Project ID: 93ab134c-fe14-4101-9a02-9956c4c7c7cd
```

This will be followed by EITHER:

**Success path:**
```
ProjectRepository: Session verified: scs982627@gmail.com
ProjectRepository: Starting transaction...
ProjectRepository: Found X teams to delete
ProjectRepository: Deleting X teams...
ProjectRepository: Deleting project 93ab134c-fe14-4101-9a02-9956c4c7c7cd...
ProjectRepository: Transaction completed successfully
ProjectRepository: === DELETE PROJECT SUCCEEDED ===
```

**Failure path:**
```
ProjectRepository: Delete failed: Session expired
```

OR:
```
ProjectRepository: === DELETE PROJECT FAILED ===
[Exception details here]
```

## If Log Shows Success But Row Exists

This means:
1. The transaction claimed to succeed
2. But the row still exists in DB
3. Possible causes:
   - Room transaction rollback without throwing exception (unlikely)
   - Multiple projects with same ID (DB constraint violation)
   - Delete was on wrong database instance
   - Compiler/build cache issue (old code running)

## Fixed: DB Dump Tool

**Changes made:**
- Added `@Inject lateinit var taskDao: TaskAssignmentDao` to TeacherHomeFragment
- Fixed `dumpDatabaseState()` to properly access TaskAssignmentDao
- Now dumps both projects AND tasks with full details

**How to use:**
1. Install updated APK: `app\build\outputs\apk\debug\app-debug.apk`
2. Long-press FAB (+ button) in teacher home screen
3. Check Logcat (tag: `TeacherHome`) for full dump

The task dump will now show:
- Task ID
- Task Title (to verify "hi" vs "hello" vs "Hi")
- Team ID (to verify which project they belong to)
- Assigned To (specific student or "WHOLE TEAM")
- Status

## Next Steps

1. **IMMEDIATE:** Search logcat for delete attempt log (instructions above)
2. **OR:** Delete "Blaa" again RIGHT NOW and capture the fresh log
3. Run updated DB dump tool to see all tasks
4. Report back with both logs

Once we see the actual delete log, we'll know definitively:
- Did delete succeed or fail?
- If failed: what was the error?
- If succeeded: why does row still exist?

## Build Status

✅ **Build successful** - Updated APK ready at:
```
app\build\outputs\apk\debug\app-debug.apk
```

Install this build to get:
- Complete DB dump (projects + tasks)
- All existing diagnostic logging for delete operations
