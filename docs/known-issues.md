# Known Issues & Future Work

## Authorization Gap: Repository Layer (LOW PRIORITY)

**Issue:** `deleteProject()` lacks explicit ownership check at repository layer

**Location:** `app/src/main/java/com/cutm/TeamPulse/data/repository/ProjectRepositoryImpl.kt` lines 160-193

**Current Behavior:**
```kotlin
override suspend fun deleteProject(projectId: String): ApiResult<Unit> {
    // Only checks session exists, NOT if session.email == project.teacherEmail
    val session = sessionDao.getActive()
    if (session == null) {
        return ApiResult.Error("Session expired")
    }
    
    // Proceeds with delete - no ownership verification
    database.withTransaction { /* ... */ }
}
```

**Current Protection:**
- `TeacherHomeViewModel.projectsWithProgress` filters: `projects.filter { it.teacherEmail == session.email }`
- Teachers only see/delete their own projects via UI
- **Implicit authorization** (UI-level) not **explicit authorization** (repo-level)

**Risk:**
- Current app: **LOW** - all paths go through filtered ViewModel
- Future expansion: **MEDIUM** - direct repo calls (REST API, admin tools, deep links) would bypass check

**Impact:**
- If deleteProject() called directly with any projectId, it will delete regardless of ownership
- No audit trail of who deleted what (session logged but not ownership verified)

**Proposed Fix (Phase 6.4 - Sheets Migration):**
```kotlin
override suspend fun deleteProject(projectId: String): ApiResult<Unit> {
    val session = sessionDao.getActive() ?: return ApiResult.Error("Session expired")
    val project = projectDao.getById(projectId) ?: return ApiResult.Error("Project not found")
    
    // EXPLICIT AUTHORIZATION CHECK
    if (project.teacherEmail != session.email && session.role != SessionRole.ADMIN) {
        Log.w("ProjectRepository", "Authorization denied: ${session.email} attempted to delete project owned by ${project.teacherEmail}")
        return ApiResult.Error("Not authorized to delete this project")
    }
    
    // Proceed with delete...
}
```

**Benefits of fixing during Sheets migration:**
- Add `SessionRole.ADMIN` support for cleanup/support tasks
- Enable audit logging (who deleted what, when)
- Enable transfer ownership feature
- Consistent with other admin features being added

**Related Issues:**
- `createProject()`, `updateProject()`, `deleteTeam()` may have similar gaps
- Should audit all write operations for explicit authorization checks

**Status:** DEFERRED to Phase 6.4  
**Priority:** LOW (no current exploit path)  
**Workaround:** UI filter prevents unauthorized access in practice

---

## Sheets Migration Open Questions (Phase 6.4)

### 1. findTaskRowIndex() Design

**Context:** Need to locate task row in Sheet for updates

**Options:**
- A: Query remoteRowIndex (stored in DB) - fast but fragile if rows reordered
- B: Linear search by taskId - slow but robust against Sheet changes
- C: Hybrid: Try remoteRowIndex first, fallback to search - best of both

**Decision:** TBD during Phase 6.4 implementation

### 2. Conflict Resolution vs. hasEverBeenCompleted Guard

**Context:** Two competing constraints:

**Constraint 1 - Teacher-wins conflict resolution:**
- If teacher and offline student both update same task
- Teacher edit in Sheets should overwrite student's pending local change

**Constraint 2 - hasEverBeenCompleted guard (XP farming prevention):**
- Once student completes task → sets hasEverBeenCompleted = true
- This flag must NEVER reset to false (prevents re-completion XP exploit)
- Even if Sheets shows DONE → TODO → DONE, only award XP once

**Conflict Scenario:**
1. Student completes task offline (hasEverBeenCompleted = true, XP awarded)
2. Teacher changes status in Sheets to TODO (before student syncs)
3. Student syncs: teacher-wins → overwrites to TODO
4. **Question:** Should we preserve hasEverBeenCompleted = true despite status change?

**Options:**
- A: hasEverBeenCompleted overrides teacher edit (keep true) → guard preserved, teacher edit partially ignored
- B: Teacher edit overwrites hasEverBeenCompleted → guard broken, XP exploit possible
- C: Detect conflict, reject sync, require manual resolution → safest but poorest UX
- D: Track XP awards separately (xp_awards table with taskId+studentEmail FK) → guard becomes audit log

**Recommendation:** Option D (separate XP audit table) - cleanest separation of concerns

**Status:** TBD during Phase 6.4 design

---

## Test Data Artifacts (Benign)

**Orphaned Projects in Dev DB:**
1. "Blaa" (ID: `93ab134c-fe14-4101-9a02-9956c4c7c7cd`) - teacherEmail = student account
2. "Debug Test Project" (ID: `debug-project-1`) - auto-seeded on first launch

**Impact:** None - filtered from UI by teacherEmail ownership check

**Cleanup:** Not needed (harmless local artifacts, won't exist in production installs)

**See:** `CLEAN_TEST_DATA.md` for manual cleanup options if desired

---

## Stale Session Bug (FIXED - Commit 1d62c01)

**Issue:** Multiple session rows accumulated, `observeSession()` returned oldest instead of newest

**Fix:** Added `ORDER BY lastSignInAt DESC LIMIT 1` to UserSessionDao.getActive()

**Status:** ✅ RESOLVED

---

## Layout Overlap (FIXED - Commit 6b6d33f)

**Issue:** Task cards overlapped headers/adjacent views in student home screen

**Fix:** Added ConstraintLayout Barriers between mutually-exclusive views

**Status:** ✅ RESOLVED
