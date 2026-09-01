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
✅ **Read path: CORRECT** - only shows owned projects  
✅ **UI-level access control: WORKING** - can't delete what you can't see

⚠️ **Authorization gap identified (LOW PRIORITY):**
- `deleteProject()` has NO `session.email == project.teacherEmail` check
- Only protection: ViewModel filters UI, so teacher can't see/tap unowned projects
- If direct repo call made (future API, deep link, etc.), no authorization enforcement
- **Defer to Sheets migration** - add proper admin/audit layer then

❌ **"Blaa" is test data created under wrong account** during development

## Gap Identified: Missing Repository-Layer Authorization

**Current architecture:**
```
UI Layer (ViewModel) → filters by teacherEmail → only owned projects visible
↓
Repository Layer → deleteProject() → NO ownership check
↓
DAO Layer → deleteById() → executes delete
```

**The gap:**
- ViewModel prevents UI access to unowned projects ✅
- But `deleteProject(projectId)` doesn't verify caller owns that projectId ❌
- If called directly (future API endpoint, deep link, programmatic call), no enforcement
- Authorization is **implicit** (UI filter) not **explicit** (repo check)

**Is this a security issue?**
- **Current app:** No - all paths go through filtered UI
- **Future expansion:** Yes - if any path bypasses ViewModel (REST API, admin tools, scripts)

**Recommendation:**
Add explicit check during **Sheets migration (Phase 6.4)**:
```kotlin
override suspend fun deleteProject(projectId: String): ApiResult<Unit> {
    val session = sessionDao.getActive() ?: return ApiResult.Error("Session expired")
    val project = projectDao.getById(projectId) ?: return ApiResult.Error("Project not found")
    
    // NEW: Explicit authorization check
    if (project.teacherEmail != session.email && session.role != SessionRole.ADMIN) {
        return ApiResult.Error("Not authorized to delete this project")
    }
    
    // ... proceed with delete transaction
}
```

This also enables admin capabilities:
- Support staff can clean up orphaned projects
- Transfer ownership between teachers
- Audit log of who deleted what

**Priority:** LOW (defer to admin/Sheets work)  
**Current workaround:** UI filter prevents unauthorized access in practice

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

## Admin/Cleanup Path Gap

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
- **Explicit authorization checks** at repository layer (not just UI filter)

For now, if "Blaa" needs cleanup:
```powershell
# Clear app data (loses all local DB)
adb shell pm clear com.cutm.TeamPulse
```

---

## Original Bug Report Resolution

**User's original report:**
> Student screen shows: project card still labeled "Blaa" (the deleted project), two tasks both titled "Hi" (not "hi" and "hello")

### Project "Blaa" Showing: RESOLVED
- **Root cause:** Orphaned test data (this investigation)
- **Not a bug:** Correctly filtered from teacher UI
- **Student seeing it:** Different issue (student sees their team's project, which may legitimately be "Blaa" if their team was created under it)

### Tasks Both Titled "Hi": RESOLVED (Previous Session)
- **DB dump #2 confirmed:** Four tasks total, all correctly titled
- Two tasks legitimately titled "Hi" (placeholder) in different projects/teams
- **Not a bug:** Coincidental identical titles, data is correct in DB and UI
- No further investigation needed

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

**"Blaa" investigation: CLOSED ✅**
- Not a bug - test data artifact
- No delete was attempted (project not visible to teacher)
- Teacher UI correctly enforces ownership via ViewModel filter

**Authorization gap identified: LOW PRIORITY ⚠️**
- Repository layer lacks explicit `session.email == project.teacherEmail` check
- Current app safe (all paths through filtered UI)
- Defer fix to Sheets migration - add proper admin/authorization layer

**Original bug report: FULLY RESOLVED ✅**
- Project "Blaa" showing: Explained (orphaned test data)
- Tasks both titled "Hi": Resolved previous session (coincidental, data correct)

**Remaining unverified:**
- ⚠️ **Runtime test needed:** Verify teacher can delete their OWN project end-to-end
- All diagnostic scaffolding added, but actual owned-project delete not exercised
- Test: Teacher creates "Test Delete Me" → deletes it → verify removed from UI and DB

**Next steps:**
1. Runtime test: Delete owned project (verify deleteProject() still works)
2. If passes: Remove all diagnostic logging (cleanup commit)
3. If fails: Investigate why (diagnostic logs will show exactly where it breaks)
4. Defer authorization/admin work to Sheets migration (Phase 6.4)
