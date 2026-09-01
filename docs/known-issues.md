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

---

## Orphaned Data from Deleted Projects (FIXED - Commit 600ed8c)

**Issue:** Deleting a project did not cascade-delete its teams or tasks, leaving orphaned rows

**Root Cause:**
- `deleteProject()` transaction deleted tasks and teams, but NOT students
- Students table stored separately, referenced teams that no longer existed
- When "Blaa" project deleted, orphaned team `fde846fc-...` and task `73bbd919-...` remained
- Student belonged to both orphaned team AND real team
- `firstOrNull` in team membership lookup picked orphaned team first
- Result: "No Active Project" bug despite real project existing

**Evidence (Sept 1, 2026 logcat):**
- Team count: 3 (orphaned + 2 real)
- Orphaned team referenced dead project `93ab134c-...`
- Project lookup failed: "NOT FOUND"
- Task count mismatch: 3 on home (includes orphan), 2 in-project (excludes orphan)

**Fix (Commit 600ed8c):**
- Added student cleanup to `deleteProject()` cascade in `ProjectRepositoryImpl.kt` lines 167-180
- Now deletes atomically: tasks → students → teams → project
- One-time orphan cleanup executed via temporary long-press trigger (removed after verification)
- Verified working: student sees correct project card, task counts match

**Status:** ✅ RESOLVED

**Future Recommendation:**
- Add ON DELETE CASCADE foreign key constraints at DB schema level
- Would make cascade delete automatic and more robust
- Current manual cascade works but requires maintaining delete order in code

---

## Multi-Project Support (DESIGN NEEDED)

**Issue:** `StudentHomeViewModel` uses `firstOrNull` to select ONE team when student belongs to multiple

**Current Behavior:**
- Student enrolled in multiple projects (multiple teams)
- `firstOrNull` silently picks one project (unpredictable order)
- No indication to student that other projects exist
- No way to switch between projects

**Impact:**
- Works fine for single-project case (current typical usage)
- Breaks down when students enroll in multiple courses/projects
- Task list aggregates across ALL projects, but project card shows only one

**Design Options:** See `docs/MULTI_PROJECT_DESIGN_OPTIONS.md`
- Option 1: Most-recently-active project (minimal UI change)
- Option 2: Horizontal carousel (swipe between projects)
- Option 3: Stacked summary cards (all visible, tap to expand)
- Option 4: Tabs (standard navigation pattern)
- Option 5: Dropdown/spinner (minimal chrome)
- Option 6: Multi-project dashboard + drill-down (full IA redesign)

**Status:** ⏳ DESIGN DECISION NEEDED - user must choose intended behavior before implementation

**Location:** `StudentHomeViewModel.kt` line ~70 (team lookup logic)

**Priority:** LOW if students typically have 1 project, HIGH if multi-enrollment is common
