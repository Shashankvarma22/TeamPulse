# "Blaa" Investigation - CLOSED (Test Data Artifact)

## Final Diagnosis: NOT A BUG

### What Happened

**Project "Blaa" (ID: `93ab134c-fe14-4101-9a02-9956c4c7c7cd`)**
- `teacherEmail = 231801371093@cutmap.ac.in` (STUDENT account)
- Created during earlier testing under wrong account
- Exists in raw database but **not visible in any UI**

### Why It's Invisible

**Teacher UI correctly filters by ownership:**

```kotlin
// TeacherHomeViewModel.kt lines 53-57
projectRepository.observeProjects()
    .combine(userSession) { projects, session ->
        if (session == null) return@combine emptyList()
        projects.filter { it.teacherEmail == session.email }  // ✅ Ownership filter
    }
```

**DAO query returns all projects:**
```kotlin
// ProjectDao.kt line 14
@Query("SELECT * FROM projects ORDER BY lastModifiedLocal DESC")
fun observeAll(): Flow<List<ProjectEntity>>
```

But ViewModel filters it, so:
- Teacher (`scs982627@gmail.com`) **correctly** doesn't see "Blaa" (not theirs)
- Student (`231801371093@cutmap.ac.in`) has **no teacher UI** to manage projects
- Result: Orphaned row with no UI surface to view/delete it

### Why Teacher Never Deleted It

**User reported:** "Deleted project 'Blaa'"

**Reality:** "Blaa" was never visible in teacher's project list to begin with. Teacher couldn't tap delete because it doesn't appear on their screen. No delete was attempted, no delete failed. The row just exists orphaned in the DB.

### Conclusion

✅ **Teacher home filtering: CORRECT** - filters by teacherEmail  
✅ **Authorization: CORRECT** - can't delete what you can't see  
✅ **Read path: CORRECT** - only shows owned projects  
✅ **Delete logic: CORRECT** - works when actually called (see commit 1d62c01 diagnostic logging)

❌ **"Blaa" is test data created under wrong account** during development

## Gap Identified: No Admin/Cleanup Path

**Current situation:**
- Project created under wrong account → **no UI to fix it**
- Student account owns project but has no teacher/admin UI
- Teacher can't see/manage projects they don't own
- Only cleanup: raw DB delete (adb shell, or reinstall app)

### Is This a Priority to Fix?

**Arguments for "No" (defer to Sheets migration):**
- This is a test-data artifact, not user-facing production scenario
- Real deployments will onboard teachers → create projects → assign students
- Creating project under student email is dev-time mistake, not runtime bug
- Sheets migration will add proper admin/audit capabilities anyway

**Arguments for "Yes" (fix now):**
- Edge case: Teacher signs in with student email by mistake, creates project
- No way to transfer ownership or delete orphaned projects
- Data integrity: orphaned rows accumulate with no cleanup path

### Recommendation

**Defer to Sheets migration** - add admin capabilities as part of that work:
- Audit log: who created what, when
- Transfer ownership: reassign project to correct teacher
- Archive/delete: admin can clean up orphaned data
- History preservation: archived projects visible in Sheets even after app delete

For now, if "Blaa" needs cleanup:
```powershell
# Option 1: Clear app data (loses all local DB)
adb shell pm clear com.cutm.TeamPulse

# Option 2: Direct DB delete (requires rooted device or debug build)
# Not practical for this use case
```

## Remaining Real Bug: Student Sees Wrong Data

**Still unresolved from original report:**
> Student screen shows: project card still labeled "Blaa" (the deleted project), two tasks both titled "Hi" (not "hi" and "hello")

**This is separate from "Blaa" investigation.** Need to:
1. Verify what project student is actually assigned to (check team.projectId)
2. Check what tasks exist in DB for that team (use updated dump tool)
3. Verify task titles in DB vs UI
4. May be a different project with similar issue, or UI rendering bug

**Next step:** User should run updated DB dump tool (long-press FAB) to see:
- What projects exist in DB
- What teams exist and their projectId references
- What tasks exist and their actual titles
- Whether student's team points to "Blaa", "Hi", or something else

## Files Reference

**Teacher home ownership filter:**
- `TeacherHomeViewModel.kt` lines 53-57: `projects.filter { it.teacherEmail == session.email }`

**DAO query (no filter):**
- `ProjectDao.kt` line 14: `SELECT * FROM projects`

**Delete logic (with authorization check would be here):**
- `ProjectRepositoryImpl.kt` lines 160-193: Only checks session.exists(), not ownership
- But can't be called if project not visible in UI (pre-filtered by ViewModel)

**Diagnostic tools:**
- DB dump: Long-press FAB in teacher home → dumps projects + tasks to Logcat
- Logging: All delete/create operations have diagnostic logs (commit c4bfa58)

## Conclusion

**"Blaa" investigation: CLOSED**
- Not a bug
- Test data artifact
- No delete was attempted (project not visible to teacher)
- No fix needed for this specific issue

**Remaining work:**
- Investigate student's wrong data (separate issue)
- Consider admin/cleanup UI during Sheets migration (Phase 6.4)
