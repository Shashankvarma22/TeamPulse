# Phase 6: Gamification (XP, TaskMaster Badge, Team Leaderboard) - Design Document

**Status:** Approved  
**Author:** Kiro  
**Date:** 2026-08-28  
**Supersedes:** None (new feature)  
**Depends On:** Task completion tracking (existing), task weight field (existing)

---

## 1. Executive Summary

### Purpose
Add basic gamification layer to incentivize task completion: XP awarded on task completion, TaskMaster achievement badge, and per-team leaderboard. Designed to work entirely from local Room data with no external dependencies.

### Scope

**In Scope:**
- XP awarded when task status changes to `COMPLETED`
- XP weighted by `TaskAssignment.weight` field if present, else flat amount
- TaskMaster badge awarded at threshold (e.g., 10 tasks completed)
- Per-team leaderboard rendered on `ProjectDetailFragment` (existing UI pattern)
- Local-only computation from Room database

**Explicitly Out of Scope (deferred to future PRDs):**
- Collaborator/Communicator/MVP badges (require peer feedback/commits/wiki data not yet tracked)
- Sheets Achievements tab sync (Sheets sync still stubbed project-wide)
- Class-wide leaderboard (only one project/class exists in current build)
- XP decay, seasons, multipliers, or other XP mechanics beyond weight
- Badge UI (icons, animations, celebration toasts) — deferred to polish phase
- Student-facing UI (students can't see their own XP/badges yet — teacher-view only for now)

### Success Criteria
1. Teacher completes a task for a student → student gains XP
2. Student reaches TaskMaster threshold → badge awarded
3. Team leaderboard shows top 3 students by XP in each team card (ProjectDetailFragment)
4. All computation happens locally (no network calls)
5. Build successful, no crashes on edge cases (zero tasks, ties, etc.)

---

## 2. Data Model

### 2.1 Authorization and Status Change Rules

**Task Status Transitions:**
- **Teachers** (via EditTaskBottomSheet): Can set any status (TODO, IN_PROGRESS, COMPLETED)
- **Students** (via TaskDetailBottomSheet): Can only set TODO or IN_PROGRESS
  - Attempting to set COMPLETED is silently rejected (StudentHomeViewModel check)
  - DONE button disabled in student UI (alpha=0.5f, isEnabled=false)
  - Prevents self-XP-farming exploit

**XP Award Trigger:**
- Automatic on first transition to COMPLETED status
- Guarded by `hasEverBeenCompleted` flag (one-time only, never re-awards)
- No client-side control (repository-level logic, inside transaction)

### 2.2 New Entity: StudentProgress

**Purpose:** Track XP, badges, and task completion stats per student.

**Table:** `student_progress`

```kotlin
@Entity(
    tableName = "student_progress",
    foreignKeys = [
        ForeignKey(
            entity = StudentEntity::class,
            parentColumns = ["studentEmail"],
            childColumns = ["studentEmail"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class StudentProgressEntity(
    @PrimaryKey
    val studentEmail: String,              // FK to students.studentEmail
    
    val totalXp: Int = 0,                  // Cumulative XP earned
    val tasksCompleted: Int = 0,           // Count of tasks completed (status = COMPLETED)
    
    val hasTaskMasterBadge: Boolean = false,  // TaskMaster achievement unlocked?
    
    val lastModifiedLocal: Long,           // Timestamp for sync purposes (future)
    val localDirty: Boolean = false        // Sync flag (unused for now, but consistent with other entities)
)
```

**Indexes:**
```kotlin
@Entity(
    // ... existing fields ...
    indices = [
        Index(value = ["studentEmail"])  // PK already indexed, but explicit for clarity
    ]
)
```

**Why not embed in StudentEntity?**
- StudentEntity represents identity/team membership (stable)
- StudentProgressEntity represents mutable game state (changes frequently with task completions)
- Separation of concerns: progress can be reset/wiped without affecting student records

### 2.2 Domain Model: StudentProgress

```kotlin
data class StudentProgress(
    val studentEmail: String,
    val displayName: String,  // Joined from StudentEntity for leaderboard display
    val totalXp: Int,
    val tasksCompleted: Int,
    val hasTaskMasterBadge: Boolean
)
```

### 2.25 Modified Entity: TaskAssignment

**New field added (v2 → v3 schema migration):**
```kotlin
val hasEverBeenCompleted: Boolean = false  // One-time XP guard
```

**Purpose:** Prevent XP re-award on repeated COMPLETED→IN_PROGRESS→COMPLETED cycles

**Behavior:**
- Set to `true` on first transition to COMPLETED status
- Never reset (permanent flag)
- Checked before awarding XP: `justCompleted && !hasEverBeenCompleted`

**Migration:** `MIGRATION_2_3` adds `hasEverBeenCompleted INTEGER NOT NULL DEFAULT 0` to task_assignments table

### 2.3 XP Calculation Rules

**Base XP per task:**
- If `TaskAssignment.weight > 0`: award `weight * 10` XP (e.g., weight=3 → 30 XP)
- If `TaskAssignment.weight == 0 or null`: award `10` XP (flat default)

**Rationale for 10x multiplier:**
- Task weights are typically small integers (1-5)
- Multiplying by 10 makes XP feel more "gamified" (30 XP vs. 3 XP)
- Avoids fractional XP
- Easy mental math for teachers/students

**When XP is awarded:**
- ONLY when `TaskAssignment.status` transitions FROM any non-COMPLETED state TO `COMPLETED`
- Changing task from COMPLETED back to IN_PROGRESS does NOT deduct XP (prevents gaming/abuse)
- Reassigning task to different student does NOT transfer XP

**Edge case: Task reassignment after completion**
- Scenario: Task completed by Alice (30 XP awarded), then reassigned to Bob
- Alice keeps her 30 XP (earned it by completing)
- If Bob completes it again, Bob earns 30 XP (duplicate XP possible — acceptable for v1)
- Future: Add `completedByEmail` field to TaskAssignment to prevent double-credit

### 2.4 TaskMaster Badge Logic

**Threshold:** `tasksCompleted >= 10`

**When checked:**
- After every task completion that increments `tasksCompleted`
- If threshold crossed and `hasTaskMasterBadge == false`, set to `true`

**Badge is permanent:**
- Once awarded, never revoked (even if tasks later deleted/changed)

**Why 10 tasks?**
- Low enough to be achievable in a typical semester project (10-20 tasks per student)
- High enough to require consistent work (not one-and-done)
- Round number (easy to communicate)

**Future badges (out of scope):**
- Collaborator: 20+ commits (requires GitHub integration)
- Communicator: 5+ peer feedbacks (requires peer review feature)
- MVP: Top 3 on class leaderboard at semester end (requires class-wide data)

---

## 3. DAO Layer

### 3.1 StudentProgressDao

**File:** `app/src/main/java/com/cutm/TeamPulse/data/local/dao/StudentProgressDao.kt`

```kotlin
@Dao
interface StudentProgressDao {
    
    /**
     * Get progress for a single student (null if not exists)
     */
    @Query("SELECT * FROM student_progress WHERE studentEmail = :email")
    suspend fun getByEmail(email: String): StudentProgressEntity?
    
    /**
     * Get all progress records for students in a team (for leaderboard)
     * Returns empty list if no records exist
     * 
     * Order: XP descending, then tasks completed descending, then email ascending (deterministic ties)
     */
    @Query("""
        SELECT sp.* FROM student_progress sp
        INNER JOIN students s ON sp.studentEmail = s.studentEmail
        WHERE s.teamId = :teamId
        ORDER BY sp.totalXp DESC, sp.tasksCompleted DESC, sp.studentEmail ASC
    """)
    suspend fun getByTeamOrderedByXp(teamId: String): List<StudentProgressEntity>
    
    /**
     * Insert or replace progress record
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(progress: StudentProgressEntity)
    
    /**
     * Delete progress for a student (cascade when student deleted)
     */
    @Query("DELETE FROM student_progress WHERE studentEmail = :email")
    suspend fun deleteByEmail(email: String)
}
```

**No observe methods needed:**
- Leaderboard refreshes on demand (teacher navigates to ProjectDetailFragment)
- No real-time updates required for v1

### 3.2 Add Migration to TeamPulseDatabase

**New table creation in migration:**
```kotlin
// In TeamPulseDatabase.kt
@Database(
    entities = [
        // ... existing entities ...
        StudentProgressEntity::class  // NEW
    ],
    version = X  // Increment from current version
)
abstract class TeamPulseDatabase : RoomDatabase() {
    // ... existing DAOs ...
    abstract fun studentProgressDao(): StudentProgressDao  // NEW
}
```

**Migration script (to be added):**
```kotlin
val MIGRATION_X_to_X+1 = object : Migration(X, X+1) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("""
            CREATE TABLE IF NOT EXISTS student_progress (
                studentEmail TEXT NOT NULL PRIMARY KEY,
                totalXp INTEGER NOT NULL DEFAULT 0,
                tasksCompleted INTEGER NOT NULL DEFAULT 0,
                hasTaskMasterBadge INTEGER NOT NULL DEFAULT 0,
                lastModifiedLocal INTEGER NOT NULL,
                localDirty INTEGER NOT NULL DEFAULT 0,
                FOREIGN KEY(studentEmail) REFERENCES students(studentEmail) ON DELETE CASCADE
            )
        """)
        database.execSQL("""
            CREATE INDEX IF NOT EXISTS index_student_progress_studentEmail 
            ON student_progress(studentEmail)
        """)
    }
}
```

---

## 4. Repository Layer

### 4.1 TaskRepository Changes

**Add XP award logic to `updateTask()`:**

```kotlin
// In TaskRepositoryImpl.kt
override suspend fun updateTask(
    taskId: String,
    // ... existing params ...
    status: TaskStatus,
    // ... existing params ...
): ApiResult<Unit> = withContext(dispatchers.io) {
    try {
        val task = taskDao.getById(taskId)
            ?: return@withContext ApiResult.Error("Task not found")
        
        // Check if status changed to COMPLETED (XP trigger)
        val wasCompleted = task.status == TaskStatus.COMPLETED
        val isNowCompleted = status == TaskStatus.COMPLETED
        val justCompleted = !wasCompleted && isNowCompleted
        val isFirstTimeCompletion = !task.hasEverBeenCompleted
        
        // Update task
        val updatedTask = task.copy(
            // ... existing updates ...
            status = status,
            hasEverBeenCompleted = task.hasEverBeenCompleted || justCompleted,
            // ... existing updates ...
        )
        
        database.withTransaction {
            taskDao.upsert(updatedTask)
            
            // Award XP only on first-time completion
            if (justCompleted && isFirstTimeCompletion && updatedTask.assigneeEmail.isNotEmpty()) {
                awardXpForTaskCompletion(
                    studentEmail = updatedTask.assigneeEmail,
                    taskWeight = updatedTask.weight
                )
            }
        }
        
        ApiResult.Success(Unit)
    } catch (e: Exception) {
        Log.e("TaskRepository", "Failed to update task", e)
        ApiResult.Error("Failed to update task")
    }
}

private suspend fun awardXpForTaskCompletion(
    studentEmail: String,
    taskWeight: Float
) {
    // Verify student still exists before awarding XP
    // (Prevents FK constraint violation if student was deleted)
    // CRITICAL: This check MUST remain inside the same withTransaction {} block
    // as the subsequent studentProgressDao.upsert() call to prevent race condition
    // where student is deleted between check and insert.
    val studentExists = studentDao.getByEmailSync(studentEmail) != null
    if (!studentExists) {
        Log.w("TaskRepository", "Skipping XP award: student $studentEmail no longer exists")
        return  // Exit early, task completion proceeds without XP award
    }
    
    // Calculate XP
    val xpAmount = if (taskWeight > 0) {
        (taskWeight * 10).toInt()
    } else {
        10  // Flat default
    }
    
    // Get or create progress record
    val currentProgress = studentProgressDao.getByEmail(studentEmail)
    val updatedProgress = if (currentProgress != null) {
        currentProgress.copy(
            totalXp = currentProgress.totalXp + xpAmount,
            tasksCompleted = currentProgress.tasksCompleted + 1,
            hasTaskMasterBadge = currentProgress.tasksCompleted + 1 >= 10 || currentProgress.hasTaskMasterBadge,
            lastModifiedLocal = System.currentTimeMillis(),
            localDirty = true
        )
    } else {
        // First task completed by this student
        StudentProgressEntity(
            studentEmail = studentEmail,
            totalXp = xpAmount,
            tasksCompleted = 1,
            hasTaskMasterBadge = false,  // Won't reach threshold on first task
            lastModifiedLocal = System.currentTimeMillis(),
            localDirty = true
        )
    }
    
    studentProgressDao.upsert(updatedProgress)
    
    Log.d("TaskRepository", "Awarded $xpAmount XP to $studentEmail (total: ${updatedProgress.totalXp}, tasks: ${updatedProgress.tasksCompleted})")
}
```

**Why in TaskRepository, not a separate GamificationRepository?**
- XP award is tightly coupled to task status change
- Avoids distributed transaction logic across multiple repositories
- Single atomic operation: updateTask + awardXp in one `database.withTransaction {}`

### 4.2 New: StudentProgressRepository (optional, for leaderboard queries)

**Alternative: Query directly from ProjectRepository**

Since leaderboard is displayed on ProjectDetailFragment (project context), add leaderboard method to `ProjectRepository`:

```kotlin
// In ProjectRepository.kt (interface)
suspend fun getTeamLeaderboard(teamId: String): List<StudentProgress>

// In ProjectRepositoryImpl.kt
override suspend fun getTeamLeaderboard(teamId: String): List<StudentProgress> = withContext(dispatchers.io) {
    try {
        val progressEntities = studentProgressDao.getByTeamOrderedByXp(teamId)
        val students = studentDao.getByTeamSync(teamId)
        
        // Join progress with student names
        progressEntities.mapNotNull { progress ->
            val student = students.find { it.studentEmail == progress.studentEmail }
            if (student != null) {
                StudentProgress(
                    studentEmail = progress.studentEmail,
                    displayName = student.displayName,
                    totalXp = progress.totalXp,
                    tasksCompleted = progress.tasksCompleted,
                    hasTaskMasterBadge = progress.hasTaskMasterBadge
                )
            } else {
                null  // Student deleted but progress remains (orphaned) — skip
            }
        }
    } catch (e: Exception) {
        Log.e("ProjectRepository", "Failed to get team leaderboard", e)
        emptyList()
    }
}
```

---

## 5. UI Design

### 5.1 ProjectDetailFragment - Team Card with Leaderboard

**Current team card (collapsed):**
```
┌─────────────────────────────────────────────┐
│ Team Alpha                        [×] [▼]   │
│ 3 members                                   │
└─────────────────────────────────────────────┘
```

**Proposed team card (expanded with leaderboard):**
```
┌─────────────────────────────────────────────┐
│ Team Alpha                        [×] [▼]   │
│ 3 members                                   │
│                                             │
│   Members:                                  │
│   ┌─────────────────────────────────────┐  │
│   │ Alice Johnson    alice@...      [×] │  │
│   │ Bob Smith        bob@...        [×] │  │
│   │ Carol White      carol@...      [×] │  │
│   └─────────────────────────────────────┘  │
│                                             │
│   Top Contributors (XP):                    │
│   ┌─────────────────────────────────────┐  │
│   │ 🏆 Alice Johnson         120 XP     │  │
│   │ 🥈 Bob Smith              80 XP     │  │
│   │ 🥉 Carol White            50 XP     │  │
│   └─────────────────────────────────────┘  │
│                                             │
│   [+ Add Member]                            │
└─────────────────────────────────────────────┘
```

**Layout changes:**
- Add "Top Contributors (XP)" section after member list
- Show top 3 students by XP (medal emojis: 🏆🥈🥉)
- If < 3 students, show only available students
- If all have 0 XP, show "No XP earned yet"

**Rendering Logic:**
```kotlin
private fun renderLeaderboard(team: Team, container: LinearLayout) {
    viewLifecycleOwner.lifecycleScope.launch {
        val leaderboard = viewModel.getTeamLeaderboard(team.teamId)
        
        // Clear previous leaderboard
        container.removeAllViews()
        
        if (leaderboard.isEmpty() || leaderboard.all { it.totalXp == 0 }) {
            // Show empty state
            val emptyText = TextView(requireContext()).apply {
                text = "No XP earned yet"
                // ... styling ...
            }
            container.addView(emptyText)
        } else {
            // Show top 3
            val medals = listOf("🏆", "🥈", "🥉")
            leaderboard.take(3).forEachIndexed { index, student ->
                val row = LinearLayout(requireContext()).apply {
                    orientation = LinearLayout.HORIZONTAL
                    // ... layout params ...
                }
                
                val medalText = TextView(requireContext()).apply {
                    text = medals.getOrNull(index) ?: ""
                    // ... styling ...
                }
                
                val nameText = TextView(requireContext()).apply {
                    text = student.displayName
                    // ... styling ...
                }
                
                val xpText = TextView(requireContext()).apply {
                    text = "${student.totalXp} XP"
                    // ... styling ...
                }
                
                row.addView(medalText)
                row.addView(nameText)
                row.addView(xpText)
                container.addView(row)
            }
        }
    }
}
```

**When to render:**
- On team card expansion (user taps chevron)
- Refresh on returning to ProjectDetailFragment (in case tasks completed elsewhere)

---

## 6. ViewModel Layer

### 6.1 ProjectDetailViewModel

**Add leaderboard method:**
```kotlin
suspend fun getTeamLeaderboard(teamId: String): List<StudentProgress> {
    return projectRepository.getTeamLeaderboard(teamId)
}
```

**No caching needed for v1:**
- Leaderboard queried on-demand when team card expanded
- Small dataset (typically 3-5 students per team)

---

## 7. Edge Cases and Validation

### 7.1 No Progress Records

**Scenario:** New student added, no tasks completed yet  
**Behavior:** `studentProgressDao.getByEmail()` returns `null`, leaderboard shows student with 0 XP  
**Handled by:** Lazy creation in `awardXpForTaskCompletion()` — progress record created on first task completion

### 7.2 Task Weight Edge Cases

| Task Weight | XP Awarded | Note |
|-------------|------------|------|
| `0.0` | 10 | Default flat amount |
| `null` | 10 | Treated as zero |
| `1.0` | 10 | 1 * 10 |
| `3.5` | 35 | 3.5 * 10, rounded to int |
| `10.0` | 100 | 10 * 10 |
| `100.0` | 1000 | 100 * 10 (no explicit cap) |

**Float to Int conversion:** `(weight * 10).toInt()` — truncates decimals (3.9 → 39 XP, not 40)

**Upper bound:** No explicit cap on XP calculation. Task weight is trusted teacher input (set via task creation/edit UI). Task creation UI currently allows any float value. This is acceptable for v1 — no adversarial gaming concern (teachers don't benefit from inflating student XP). If abuse observed in production, add validation: `weight.coerceIn(0f, 10f)` before XP calculation.

### 7.3 Task Reassignment

**Scenario:** Task assigned to Alice (completed, 30 XP awarded), then reassigned to Bob (not completed)  
**Behavior:**
- Alice keeps 30 XP (already in her progress record)
- If Bob completes it, Bob earns 30 XP (duplicate possible)
- Future: Add `completedByEmail` field to prevent double-credit

### 7.4 Student Deletion

**Scenario:** Student with progress record is deleted  
**Behavior:**
- `ON DELETE CASCADE` removes progress record automatically
- No orphaned progress records

### 7.5 Ties in Leaderboard

**Scenario:** Alice and Bob both have 80 XP  
**Behavior:**
- Tiebreaker 1: `tasksCompleted DESC` (student with more tasks completed ranks higher)
- Tiebreaker 2: `studentEmail ASC` (alphabetical by email for deterministic order)
- SQL: `ORDER BY totalXp DESC, tasksCompleted DESC, studentEmail ASC`

**Example:**
- Alice: 80 XP, 10 tasks → ranks higher than Bob: 80 XP, 8 tasks
- Alice: 80 XP, 10 tasks, alice@example.com → ranks higher than Bob: 80 XP, 10 tasks, bob@example.com

### 7.6 Zero Members Team

**Scenario:** Team with no members (all removed)  
**Behavior:**
- `getByTeamOrderedByXp()` returns empty list
- Leaderboard shows "No XP earned yet"

### 7.7 XP Award to Removed Team Member

**Scenario 1:** Task assigned to Alice, Alice removed from team (StudentEntity still exists, just teamId changed), task completed  
**Behavior:**
- `studentDao.getByEmailSync()` finds Alice (she still exists)
- `awardXpForTaskCompletion()` proceeds normally
- Progress record created/updated for Alice
- Alice's XP counts toward her new team's leaderboard (or no team if teamId empty)
- **Rationale:** Student identity persists across team changes; XP follows the student, not the team slot

**Scenario 2:** Task assigned to Alice, Alice fully deleted (StudentEntity cascade-deleted, including progress record), task completed  
**Behavior:**
- `studentDao.getByEmailSync(alice@example.com)` returns null (student doesn't exist)
- `awardXpForTaskCompletion()` logs warning and returns early (no XP award attempted)
- No progress record created (no FK constraint violation)
- Task status update succeeds (task marked COMPLETED)
- Transaction commits successfully (task completion not blocked by missing student)
- **Rationale:** Task completion is primary action; XP award is side-effect. Check student existence before attempting insert to avoid FK constraint violation that would roll back entire transaction.

---

## 8. Test Scenarios

### TC1: First Task Completion Awards XP
**Setup:** Student Alice exists, no progress record yet  
**Action:** Complete a task (weight=3) assigned to Alice  
**Expected:**
- Progress record created: `totalXp=30, tasksCompleted=1, hasTaskMasterBadge=false`
- Log: "Awarded 30 XP to alice@example.com (total: 30, tasks: 1)"

### TC2: Subsequent Task Completion Increments XP
**Setup:** Alice has `totalXp=30, tasksCompleted=1`  
**Action:** Complete another task (weight=2) assigned to Alice  
**Expected:**
- Progress updated: `totalXp=50, tasksCompleted=2, hasTaskMasterBadge=false`

### TC3: TaskMaster Badge Awarded at Threshold
**Setup:** Alice has `tasksCompleted=9, hasTaskMasterBadge=false`  
**Action:** Complete 10th task  
**Expected:**
- Progress updated: `tasksCompleted=10, hasTaskMasterBadge=true`
- Badge remains `true` even if future tasks deleted

### TC4: Changing Task from COMPLETED to IN_PROGRESS Does Not Deduct XP
**Setup:** Alice completed a task (30 XP awarded), `totalXp=30`  
**Action:** Change task status from COMPLETED → IN_PROGRESS  
**Expected:**
- Progress unchanged: `totalXp=30, tasksCompleted=1` (no deduction)

### TC5: Unassigned Task Completion Does Not Award XP
**Setup:** Task with `assigneeEmail=""` (unassigned)  
**Action:** Change task status to COMPLETED  
**Expected:**
- No progress record created
- No XP awarded (no student to credit)

### TC6: Leaderboard Shows Top 3
**Setup:** Team Alpha has 5 students with XP: Alice=120, Bob=80, Carol=50, Dave=30, Eve=10  
**Action:** Expand Team Alpha card on ProjectDetailFragment  
**Expected:**
- Leaderboard shows:
  - 🏆 Alice Johnson 120 XP
  - 🥈 Bob Smith 80 XP
  - 🥉 Carol White 50 XP
- Dave and Eve not shown (only top 3)

### TC7: Leaderboard with < 3 Students
**Setup:** Team Beta has 2 students: Frank=40, Grace=20  
**Action:** Expand Team Beta card  
**Expected:**
- Leaderboard shows:
  - 🏆 Frank 40 XP
  - 🥈 Grace 20 XP
- No third entry (graceful handling)

### TC8: Leaderboard with All Zero XP
**Setup:** Team Gamma has 3 students, all with 0 XP (no tasks completed)  
**Action:** Expand Team Gamma card  
**Expected:**
- Leaderboard shows "No XP earned yet" (empty state)

### TC9: Task Weight = 0 Awards Flat XP
**Setup:** Task with `weight=0.0`  
**Action:** Complete task assigned to Alice  
**Expected:**
- Progress updated: `totalXp += 10` (flat default)

### TC10: Task Weight = 10 Awards 100 XP
**Setup:** Task with `weight=10.0`  
**Action:** Complete task assigned to Alice  
**Expected:**
- Progress updated: `totalXp += 100` (10 * 10)

### TC11: Leaderboard Tie Resolves Deterministically
**Setup:** Team Alpha with 3 students:
- Alice: totalXp=80, tasksCompleted=10, email=alice@example.com
- Bob: totalXp=80, tasksCompleted=8, email=bob@example.com
- Carol: totalXp=80, tasksCompleted=10, email=carol@example.com

**Action:** Expand Team Alpha card, render leaderboard  

**Expected:** Leaderboard order (per `ORDER BY totalXp DESC, tasksCompleted DESC, studentEmail ASC`):
1. 🏆 Alice Johnson (80 XP, 10 tasks) — alice@example.com wins email tiebreaker over carol@example.com
2. 🥈 Carol White (80 XP, 10 tasks)
3. 🥉 Bob Smith (80 XP, 8 tasks) — fewer tasks completed, ranks last

### TC12: Repeated Complete-Revert-Complete Cycle Does NOT Re-Award XP
**Setup:** Alice has totalXp=30, tasksCompleted=1. Task T1 (weight=2, status=COMPLETED, hasEverBeenCompleted=true, assignee=Alice)  
**Action:**
1. Change T1 status: COMPLETED → IN_PROGRESS (no XP deduction per §2.3)
2. Change T1 status: IN_PROGRESS → COMPLETED (XP award blocked by hasEverBeenCompleted flag)
**Expected:**
- After step 1: Alice still has totalXp=30, tasksCompleted=1 (no change)
- After step 2: Alice still has totalXp=30, tasksCompleted=1 (NO XP re-awarded, hasEverBeenCompleted prevents it)
**Rationale:** `hasEverBeenCompleted` flag set on first completion prevents all subsequent re-awards. This closes both student self-farming (blocked separately by student status restriction) and teacher-initiated re-toggle XP farming.

### TC13: XP Award to Removed Team Member
**Scenario A: Student moved to different team**  
**Setup:**
- Team Alpha has Alice (alice@example.com)
- Task T1 assigned to Alice in Team Alpha
- Alice removed from Team Alpha, added to Team Beta (StudentEntity.teamId changed)
- Task T1 still shows assigneeEmail=alice@example.com
**Action:** Complete Task T1  
**Expected:**
- XP awarded to alice@example.com
- Alice's progress record updated (totalXp += 20, tasksCompleted += 1)
- Alice's XP appears on Team Beta's leaderboard (her current team), NOT Team Alpha's
- Task completion succeeds

**Scenario B: Student fully deleted**  
**Setup:**
- Team Alpha has Alice
- Task T1 assigned to Alice
- Alice fully deleted (StudentEntity cascade-deleted, progress record also deleted per FK CASCADE)
**Action:** Complete Task T1  
**Expected:**
- Task status becomes COMPLETED (task update succeeds)
- No StudentProgressEntity exists for alice@example.com (XP award skipped due to student not found)
- Log message: "Skipping XP award: student alice@example.com no longer exists"
- Transaction commits (task completion not blocked by missing student)

---

## 9. String Resources

```xml
<!-- Gamification -->
<string name="leaderboard_title">Top Contributors (XP)</string>
<string name="leaderboard_empty">No XP earned yet</string>
<string name="leaderboard_xp_format">%d XP</string>

<!-- Badge (future UI) -->
<string name="badge_taskmaster">TaskMaster</string>
<string name="badge_taskmaster_description">Completed 10 tasks</string>
```

---

## 10. Open Questions

### Q1: Should XP be deducted if task reverted from COMPLETED?
**Options:**
- **A)** No deduction (current design) — simpler, prevents gaming/abuse
- **B)** Deduct XP on revert — more "realistic" but complex edge cases

**Recommendation:** Option A (no deduction) — prevents negative XP, abuse scenarios (complete → revert → complete again), and simplifies logic.

### Q2: Should duplicate XP be prevented on task reassignment?
**Options:**
- **A)** Allow duplicate XP (current design) — simpler for v1
- **B)** Add `completedByEmail` field to track who earned XP — prevents double-credit

**Recommendation:** Option A for v1 (acceptable edge case), defer Option B to future milestone.

### Q3: Should leaderboard refresh automatically or on-demand?
**Options:**
- **A)** On-demand (current design) — query when team card expanded
- **B)** Observe with Flow — real-time updates

**Recommendation:** Option A (on-demand) — sufficient for teacher use case, avoids unnecessary queries.

### Q4: Should students see their own XP/badges?
**Options:**
- **A)** Teacher-view only (current design) — defer student UI to future phase
- **B)** Add student dashboard with XP/badges

**Recommendation:** Option A (teacher-view only) for v1 — no student login/role exists yet.

### Q5: What's the tiebreaker for leaderboard order?
**Decision:** Add tiebreaker — `ORDER BY totalXp DESC, tasksCompleted DESC, studentEmail ASC`

**Rationale:** Deterministic ordering improves UX (consistent leaderboard across page refreshes). Small code addition (already included in DAO query §3.1). Secondary tiebreaker (tasksCompleted) rewards consistency over lucky high-weight task assignment. Tertiary tiebreaker (email) is arbitrary but deterministic.

### Q6: Should badge unlock trigger a toast/notification?
**Options:**
- **A)** Silent unlock (current design) — badge visible in leaderboard only
- **B)** Toast notification when badge awarded

**Recommendation:** Option A (silent) for v1 — defer celebration UI to polish phase.

### Q7: Should repeated COMPLETED→IN_PROGRESS→COMPLETED cycles re-award XP?
**Scenario:** Teacher completes task (XP awarded), reverts to IN_PROGRESS (no deduction per Q1), completes again.

**Decision:** Option B (block re-award) - IMPLEMENTED

**Implementation:**
- Added `hasEverBeenCompleted: Boolean` field to TaskAssignmentEntity
- Set to `true` on first transition to COMPLETED status
- XP award logic checks: `justCompleted && !hasEverBeenCompleted`
- Once set, field never resets (permanent one-time guard)

**Rationale:**
- Prevents XP farming from both teacher-initiated and student-initiated toggles
- Eliminates need to track "trusted actor" vs "student actor" at XP-award time
- Simpler than completion history table (single boolean vs timestamped log)
- Covers edge case: teacher accidentally reverts completed task, re-completing doesn't double-award

**Migration:** MIGRATION_2_3 adds `hasEverBeenCompleted INTEGER NOT NULL DEFAULT 0` to task_assignments table

---

## 11. Implementation Plan

### Phase 6.1: Data Layer
- Create `StudentProgressEntity` and `StudentProgressDao`
- Add database migration to create `student_progress` table
- Update `TeamPulseDatabase` to include new entity/DAO

### Phase 6.2: Repository Layer
- Add XP award logic to `TaskRepositoryImpl.updateTask()`
- Implement `awardXpForTaskCompletion()` helper
- Add `getTeamLeaderboard()` to `ProjectRepositoryImpl`
- **Critical:** Ensure `taskDao.upsert()` and `awardXpForTaskCompletion()` execute inside same `database.withTransaction {}` block for atomicity (both succeed or both fail)

### Phase 6.3: ViewModel Layer
- Add `getTeamLeaderboard()` to `ProjectDetailViewModel`

### Phase 6.4: UI Layer
- Add leaderboard section to team card expansion in `ProjectDetailFragment`
- Implement `renderLeaderboard()` method
- Add string resources

### Phase 6.5: Testing
- Manual test TC1-TC10
- Verify badge threshold logic
- Test edge cases (zero XP, ties, empty teams)

---

## 12. Risks and Mitigations

| Risk | Impact | Mitigation |
|------|--------|------------|
| XP awarded twice on rapid status changes | Medium | Use `database.withTransaction {}` to ensure atomic operation |
| Orphaned progress records if student deleted | Low | `ON DELETE CASCADE` handles cleanup automatically |
| Leaderboard query performance with large teams | Low | Query limited to single team (< 10 students typical), indexed by `studentEmail` |
| Badge threshold tuning (10 tasks too easy/hard) | Medium | Make threshold configurable in future (hardcoded 10 for v1) |

---

## 13. Future Enhancements (Deferred)

**Phase 7 (Collaborator/Communicator/MVP badges):**
- Requires GitHub commit tracking (Collaborator)
- Requires peer review feature (Communicator)
- Requires class-wide leaderboard (MVP)

**Phase 8 (Sheets Achievements sync):**
- Write XP/badges to Google Sheets "Achievements" tab
- Requires Sheets sync implementation (currently stubbed)

**Phase 9 (Student-facing UI):**
- Student login/role system
- Student dashboard with XP progress bar
- Badge showcase page
- Notification system for badge unlocks

**Phase 10 (Advanced XP mechanics):**
- XP decay (lose XP if tasks not completed by due date)
- Seasonal leaderboards (reset per semester)
- Multipliers (double XP events, team bonuses)

---

## Appendix A: Database Schema Summary

**New Table:**
```sql
CREATE TABLE student_progress (
    studentEmail TEXT NOT NULL PRIMARY KEY,
    totalXp INTEGER NOT NULL DEFAULT 0,
    tasksCompleted INTEGER NOT NULL DEFAULT 0,
    hasTaskMasterBadge INTEGER NOT NULL DEFAULT 0,
    lastModifiedLocal INTEGER NOT NULL,
    localDirty INTEGER NOT NULL DEFAULT 0,
    FOREIGN KEY(studentEmail) REFERENCES students(studentEmail) ON DELETE CASCADE
);

CREATE INDEX index_student_progress_studentEmail ON student_progress(studentEmail);
```

**Relationships:**
- `student_progress.studentEmail` → `students.studentEmail` (1:1, CASCADE delete)
- No relationship to `task_assignments` directly (XP awarded via repository logic, not FK)

---

## Appendix B: XP Calculation Examples

| Task Weight | Formula | XP Awarded |
|-------------|---------|------------|
| 0.0 | default | 10 |
| 1.0 | 1 * 10 | 10 |
| 2.0 | 2 * 10 | 20 |
| 3.0 | 3 * 10 | 30 |
| 5.0 | 5 * 10 | 50 |
| 10.0 | 10 * 10 | 100 |
| 3.5 | 3.5 * 10 | 35 (truncated) |

---

**End of Design Document**

---

**Ready for Review:** Please review all sections, especially XP calculation rules, badge threshold (10 tasks), and Open Questions Q1-Q6, and approve/modify before implementation begins.
