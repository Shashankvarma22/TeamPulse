# Phase 5: Linked Team + Assignee Dropdowns Design Document

## Executive Summary

Add linked Team and Assignee dropdowns to both CreateTaskBottomSheet and EditTaskBottomSheet. Selecting a team dynamically populates the assignee dropdown with that team's members. This fixes the current dropdown population bugs and provides a clearer UX for task assignment.

**Key Changes:**
- CreateTaskBottomSheet: Replace auto-select single-team logic with explicit team dropdown (always visible)
- EditTaskBottomSheet: Add new team dropdown field (currently missing)
- Both: Link team selection → assignee dropdown population
- Both: Switching teams clears assignee selection and repopulates with new team's members

---

## 1. Current State Analysis

### CreateTaskBottomSheet (Current Behavior)
- ✅ Has team dropdown (teamSpinner)
- ✅ Handles 0/1/N teams with auto-select logic
- ❌ No assignee dropdown (assigns to entire team only)
- ❌ Blocks creation if no teams exist

### EditTaskBottomSheet (Current Behavior)
- ❌ No team dropdown (teamId passed as hidden argument from task list)
- ✅ Has assignee dropdown (assigneeDropdown)
- ✅ Loads members via `getTeamMembers(teamId)`
- ❌ Cannot change team (stuck with task's original team)
- ❌ If original team deleted, shows stale assignee but can't select from any valid team

### Bug Context
User reported two dropdown failures:
1. EditTaskBottomSheet assignee dropdown shows no members for existing team with members
2. CreateTaskBottomSheet team dropdown appears blank when 2 teams exist

Root cause analysis showed **no code regression** from Phase 4 work - likely data/timing issue. This redesign will fix the underlying UX problem by making team selection explicit and linked.

---

## 2. Proposed UI Changes

### 2.1 CreateTaskBottomSheet

**New Field Order:**
```
[Drag Handle]
Create Task

Title: [___________________]

Description: [_______________
              _______________
              _______________]

Team: [Team Alpha        ▼]  ← Always visible dropdown (even if 1 team)

Assign To: [Unassigned   ▼]  ← NEW: Populated after team selection
                               Options: Unassigned + team members

Due Date: [Dec 15, 2024]

[Cancel]              [Create]
```

**Behavior Changes:**
- Remove auto-select single-team logic
- Team dropdown always enabled (unless 0 teams → show error state)
- Assignee dropdown disabled until team selected
- Assignee dropdown shows "Select a team first" when no team selected
- After team selection, assignee dropdown shows: "Unassigned" + member display names
- Default assignee: "Unassigned" (user must explicitly assign)

### 2.2 EditTaskBottomSheet

**New Field Order:**
```
[Drag Handle]
Edit Task

Title: [Research API patterns]

Description: [Compare REST vs. GraphQL
              for our mobile app]

Team: [Team Alpha        ▼]  ← NEW: Shows current team, allows change

Assign To: [Alice         ▼]  ← Repopulates when team changes

Due Date: [Dec 15, 2024]

Status: [TODO] [IN PROGRESS] [DONE]

[Delete]         [Cancel] [Save]
```

**Behavior Changes:**
- Add team dropdown before assignee dropdown
- Team dropdown defaults to task's current team (if exists)
- If current team deleted: dropdown shows "Team not found" and is enabled for user to select valid team
- Changing team:
  - Clears current assignee selection → "Unassigned"
  - Repopulates assignee dropdown with new team's members
  - Shows warning if current assignee will be lost
- Assignee dropdown behavior matches CreateTaskBottomSheet (disabled if no team, shows members after team selection)

---

## 3. Data Flow

### 3.1 CreateTaskBottomSheet Flow

```
1. onViewCreated()
   ↓
2. setupTeamDropdown()
   - Observe viewModel.availableTeams (StateFlow<List<Team>>)
   - Populate team dropdown with team names
   - If 0 teams: show error, disable Create button
   - If ≥1 teams: enable dropdown, no auto-select
   ↓
3. User selects team from dropdown
   ↓
4. onTeamSelected(teamId)
   - Store selectedTeamId
   - Call setupAssigneeDropdown(teamId)
   ↓
5. setupAssigneeDropdown(teamId)
   - Observe viewModel.getTeamMembers(teamId) (Flow<List<Student>>)
   - Populate assignee dropdown: ["Unassigned"] + member display names
   - Enable assignee dropdown
   - Default selection: "Unassigned"
   ↓
6. User selects assignee (optional)
   ↓
7. User clicks Create
   ↓
8. Validation:
   - title not blank ✓
   - dueDate not null ✓
   - teamId not null ✓
   - (assigneeEmail can be empty = unassigned) ✓
   ↓
9. viewModel.createTask(title, desc, teamId, dueDate, assigneeEmail)
   ↓
10. Dismiss sheet
```

### 3.2 EditTaskBottomSheet Flow

```
1. onViewCreated()
   - Store task.teamId, task.assigneeEmail from arguments
   ↓
2. setupTeamDropdown()
   - Observe viewModel.availableTeams (StateFlow<List<Team>>)
   - Populate team dropdown with team names
   - Default selection: find team matching task.teamId
   - If task.teamId not in list (deleted): show "Team not found", no selection
   ↓
3. If valid team selected: setupAssigneeDropdown(task.teamId)
   - Observe viewModel.getTeamMembers(task.teamId)
   - Populate assignee dropdown
   - Default selection: find member matching task.assigneeEmail, or "Unassigned"
   - Handle stale assignee: show "email — no longer on team" if not found
   ↓
4. User changes team selection
   ↓
5. onTeamChanged(newTeamId)
   - If current assigneeEmail not empty:
     → Show confirmation: "Changing team will clear the current assignee. Continue?"
   - If confirmed or no assignee:
     → selectedTeamId = newTeamId
     → selectedAssigneeEmail = "" (clear to unassigned)
     → setupAssigneeDropdown(newTeamId) (repopulate with new team's members)
   ↓
6. User clicks Save
   ↓
7. Validation:
   - title not blank ✓
   - teamMembersLoaded = true ✓
   - teamId not null ✓
   - assignee not stale ✓
   ↓
8. viewModel.updateTask(..., teamId = selectedTeamId, assigneeEmail = selectedAssigneeEmail)
   - Note: If team changed, this effectively "moves" the task to new team
   ↓
9. Dismiss sheet
```

---

## 4. ViewModel Changes

### 4.1 TeacherTaskListViewModel (used by both sheets)

**Already Exists:**
```kotlin
val availableTeams: StateFlow<List<Team>> // ✓ Already implemented
fun getTeamMembers(teamId: String): Flow<List<Student>> // ✓ Already implemented
```

**No Changes Needed** - existing APIs sufficient.

### 4.2 New Parameters for Task Operations

**createTask() signature change:**
```kotlin
// BEFORE:
fun createTask(title: String, description: String, teamId: String, dueDate: Long)

// AFTER:
fun createTask(
    title: String, 
    description: String, 
    teamId: String, 
    dueDate: Long,
    assigneeEmail: String = "" // NEW: defaults to unassigned
)
```

**updateTask() signature change:**
```kotlin
// BEFORE:
fun updateTask(
    taskId: String,
    title: String,
    description: String,
    dueDate: Long,
    status: TaskStatus,
    teamId: String,      // Was passed but never changed
    projectId: String,
    assigneeEmail: String,
    weight: Float,
    remoteRowIndex: Int?
)

// AFTER: (same signature, but now teamId can actually change)
// No signature change needed, but implementation must handle teamId updates
```

---

## 5. Repository Changes

### 5.1 TaskRepository

**New requirement: Support changing task's team**

Currently, tasks are immutable w.r.t. teamId (passed to repository but assumed unchanging). If teamId changes:
- Task moves from Team A to Team B
- Task's assigneeEmail might become stale (assignee not in new team)
- Need validation: assigneeEmail must be in new team's memberEmails or empty

**Proposed validation in TaskRepository.updateTask():**
```kotlin
override suspend fun updateTask(
    taskId: String,
    title: String,
    description: String,
    dueDate: Long,
    status: TaskStatus,
    teamId: String,
    assigneeEmail: String,
    weight: Float,
    remoteRowIndex: Int?
): ApiResult<Unit> = withContext(dispatchers.io) {
    try {
        val task = taskDao.getById(taskId)
            ?: return@withContext ApiResult.Error("Task not found")
        
        // NEW: If team changed, validate assignee is in new team
        if (task.teamId != teamId && assigneeEmail.isNotEmpty()) {
            val newTeam = teamDao.getById(teamId)
                ?: return@withContext ApiResult.Error("Team not found")
            
            if (assigneeEmail !in newTeam.memberEmails) {
                return@withContext ApiResult.Error("Assignee not in selected team")
            }
        }
        
        val updatedTask = task.copy(
            title = title,
            description = description,
            dueDate = dueDate,
            status = status,
            teamId = teamId,  // Can now change
            assigneeEmail = assigneeEmail,
            weight = weight,
            remoteRowIndex = remoteRowIndex,
            localDirty = true,
            lastModifiedLocal = System.currentTimeMillis()
        )
        
        taskDao.upsert(updatedTask)
        ApiResult.Success(Unit)
    } catch (e: Exception) {
        Log.e("TaskRepository", "Failed to update task", e)
        ApiResult.Error("Failed to update task")
    }
}
```

### 5.2 TaskRepository.createTask()

**Add assigneeEmail parameter:**
```kotlin
// Update signature:
suspend fun createTask(
    title: String,
    description: String,
    teamId: String,
    projectId: String,
    dueDate: Long,
    assigneeEmail: String = ""  // NEW
): ApiResult<Unit>

// Validation: if assigneeEmail not empty, verify it's in team
if (assigneeEmail.isNotEmpty()) {
    val team = teamDao.getById(teamId)
        ?: return ApiResult.Error("Team not found")
    
    if (assigneeEmail !in team.memberEmails) {
        return ApiResult.Error("Assignee not in selected team")
    }
}
```

---

## 6. Layout Changes

### 6.1 bottom_sheet_create_task.xml

**Add Assignee Dropdown (between Team and Due Date):**
```xml
<!-- Existing Team Dropdown -->
<TextView
    android:id="@+id/teamLabel"
    android:text="@string/create_task_label_team" />

<com.google.android.material.textfield.TextInputLayout
    android:id="@+id/teamSpinnerLayout"
    style="@style/Widget.Material3.TextInputLayout.OutlinedBox.ExposedDropdownMenu">
    <MaterialAutoCompleteTextView
        android:id="@+id/teamSpinner" />
</com.google.android.material.textfield.TextInputLayout>

<!-- NEW: Assignee Dropdown -->
<TextView
    android:id="@+id/assignToLabel"
    android:text="@string/task_assign_to_label" />

<com.google.android.material.textfield.TextInputLayout
    android:id="@+id/assigneeDropdownLayout"
    style="@style/Widget.Material3.TextInputLayout.OutlinedBox.ExposedDropdownMenu"
    android:enabled="false"
    android:hint="@string/task_assign_select_team_first">
    <MaterialAutoCompleteTextView
        android:id="@+id/assigneeDropdown"
        android:inputType="none" />
</com.google.android.material.textfield.TextInputLayout>

<!-- Existing Due Date section... -->
```

### 6.2 bottom_sheet_edit_task.xml

**Add Team Dropdown (before Assignee Dropdown):**
```xml
<!-- Existing Description field... -->

<!-- NEW: Team Dropdown -->
<TextView
    android:id="@+id/teamLabel"
    android:text="@string/edit_task_label_team" />

<com.google.android.material.textfield.TextInputLayout
    android:id="@+id/teamDropdownLayout"
    style="@style/Widget.Material3.TextInputLayout.OutlinedBox.ExposedDropdownMenu">
    <MaterialAutoCompleteTextView
        android:id="@+id/teamDropdown"
        android:inputType="none" />
</com.google.android.material.textfield.TextInputLayout>

<!-- Existing Assignee Dropdown (reorder after Team) -->
<TextView
    android:id="@+id/assignToLabel"
    android:text="@string/task_assign_to_label" />

<com.google.android.material.textfield.TextInputLayout
    android:id="@+id/assigneeDropdownLayout"
    ... />

<!-- Existing Due Date section... -->
```

---

## 7. String Resources

```xml
<!-- Team dropdown -->
<string name="create_task_label_team">Team</string>
<string name="edit_task_label_team">Team</string>
<string name="task_select_team">Select team</string>
<string name="task_team_not_found">Team not found</string>

<!-- Assignee dropdown -->
<string name="task_assign_select_team_first">Select a team first</string>

<!-- Confirmation dialogs -->
<string name="change_team_warning_title">Change Team?</string>
<string name="change_team_warning_message">Changing the team will clear the current assignee. Continue?</string>
<string name="change_team_confirm">Change Team</string>

<!-- Validation errors -->
<string name="error_assignee_not_in_team">Selected assignee is not in the selected team</string>
```

---

## 8. Validation Rules

### CreateTaskBottomSheet
1. ✅ Title not blank
2. ✅ Due date selected
3. ✅ Team selected (teamId not null)
4. ✅ Assignee either empty (unassigned) OR valid email in selected team

### EditTaskBottomSheet
1. ✅ Title not blank
2. ✅ Team selected (teamId not null)
3. ✅ Team members loaded (teamMembersLoaded = true)
4. ✅ If assignee selected: must exist in selected team (not stale)
5. ✅ If assignee empty: valid (unassigned is allowed)

**Edge Cases:**
- User selects team, selects assignee, changes team → assignee cleared automatically
- User selects assignee, that member is removed by another device → stale assignee validation blocks save (existing logic)
- Team is deleted while sheet is open → team dropdown repopulates via Flow, selected team becomes invalid

---

## 9. Test Scenarios

### TC1: Create Task - No Teams Available
**Setup:** Project has 0 teams
**Steps:**
1. Open CreateTaskBottomSheet
2. Observe UI state

**Expected:**
- Team dropdown disabled, shows error message
- Assignee dropdown disabled
- Create button disabled
- Error message: "No teams available. Create a team first."

---

### TC2: Create Task - Single Team, No Members
**Setup:** Project has 1 team "Alpha" with 0 members
**Steps:**
1. Open CreateTaskBottomSheet
2. Team dropdown enabled, select "Alpha"
3. Observe assignee dropdown

**Expected:**
- Team dropdown shows "Alpha"
- Assignee dropdown enabled, shows only "Unassigned"
- Can create task successfully (unassigned)

---

### TC3: Create Task - Single Team, With Members
**Setup:** Project has 1 team "Alpha" with members [alice@x.com, bob@x.com]
**Steps:**
1. Open CreateTaskBottomSheet
2. Select team "Alpha"
3. Observe assignee dropdown
4. Select "Alice"
5. Create task

**Expected:**
- Assignee dropdown shows ["Unassigned", "Alice", "Bob"]
- Task created with teamId=Alpha, assigneeEmail=alice@x.com

---

### TC4: Create Task - Multiple Teams
**Setup:** Project has 2 teams ["Alpha", "Beta"]
**Steps:**
1. Open CreateTaskBottomSheet
2. Observe team dropdown (no selection)
3. Try to open assignee dropdown
4. Select team "Alpha"
5. Observe assignee dropdown enabled

**Expected:**
- Team dropdown shows both teams, no default selection
- Assignee dropdown disabled, shows "Select a team first"
- After team selection, assignee dropdown populates with Alpha's members

---

### TC5: Create Task - Switch Teams
**Setup:** Project has 2 teams, Alpha (Alice, Bob), Beta (Charlie)
**Steps:**
1. Open CreateTaskBottomSheet
2. Select team "Alpha"
3. Select assignee "Alice"
4. Change team to "Beta"
5. Observe assignee dropdown

**Expected:**
- Assignee dropdown clears to "Unassigned"
- Assignee dropdown repopulates with ["Unassigned", "Charlie"]
- Previous selection "Alice" is gone

---

### TC6: Edit Task - Current Team Exists
**Setup:** Task assigned to alice@x.com in Team Alpha
**Steps:**
1. Open EditTaskBottomSheet for task
2. Observe team dropdown
3. Observe assignee dropdown

**Expected:**
- Team dropdown shows "Alpha" selected
- Assignee dropdown shows "Alice" selected

---

### TC7: Edit Task - Current Team Deleted
**Setup:** Task was in Team Alpha, but Alpha deleted before opening sheet
**Steps:**
1. Delete Team Alpha
2. Open EditTaskBottomSheet for task
3. Observe team dropdown
4. Select a valid team "Beta"
5. Observe assignee dropdown

**Expected:**
- Team dropdown shows "Team not found" or empty selection
- Assignee dropdown disabled or shows error
- After selecting "Beta", assignee dropdown populates with Beta's members
- Previous assignee cleared to "Unassigned"

---

### TC8: Edit Task - Change Team with Assignee
**Setup:** Task assigned to alice@x.com in Team Alpha
**Steps:**
1. Open EditTaskBottomSheet
2. Change team from "Alpha" to "Beta"
3. Observe confirmation dialog

**Expected:**
- Dialog: "Changing the team will clear the current assignee. Continue?"
- If Cancel: team stays "Alpha", assignee stays "Alice"
- If Confirm: team changes to "Beta", assignee clears to "Unassigned", dropdown repopulates with Beta's members

---

### TC9: Edit Task - Change Team, Reassign, Save
**Setup:** Task assigned to alice@x.com in Team Alpha, Beta has member charlie@x.com
**Steps:**
1. Open EditTaskBottomSheet
2. Change team to "Beta" (confirm dialog)
3. Select "Charlie" from assignee dropdown
4. Save task

**Expected:**
- Task updated: teamId=Beta, assigneeEmail=charlie@x.com
- Task appears in Beta's task list
- No longer appears in Alpha's task list (if filtered by team)

---

### TC10: Edit Task - Assignee Removed While Sheet Open
**Setup:** Task assigned to Alice, sheet open
**Steps:**
1. Open EditTaskBottomSheet (Alice selected)
2. On another device: remove Alice from team
3. Observe assignee dropdown (Flow re-emits)
4. Try to save

**Expected:**
- Assignee dropdown updates to show "alice@x.com — no longer on team"
- Save button blocked with error: "This task is assigned to someone no longer on the team. Please select 'Unassigned' or assign to a current team member."

---

### TC11: Create Task - Validation Error
**Setup:** Project has teams
**Steps:**
1. Open CreateTaskBottomSheet
2. Select team "Alpha"
3. Select assignee "Alice"
4. Clear title
5. Click Create

**Expected:**
- Error: "Task title is required"
- Sheet does not dismiss
- Team and assignee selections preserved

---

### TC12: Edit Task - Team Changed in Repository Validation
**Setup:** Task in Team Alpha assigned to Alice, Beta has no member alice@x.com
**Steps:**
1. Manually bypass UI validation (e.g., via debugger or API call)
2. Attempt updateTask(teamId=Beta, assigneeEmail=alice@x.com)

**Expected:**
- Repository returns ApiResult.Error("Assignee not in selected team")
- Task not updated
- UI shows error toast

---

## 10. Open Questions

### Q1: Should changing team in EditTaskBottomSheet show confirmation dialog?
**Options:**
- **Option A (Recommended):** Show confirmation only if current assignee is non-empty (i.e., losing an assignment)
  - Pro: Prevents accidental data loss
  - Con: Extra click for user
- **Option B:** No confirmation, silently clear assignee
  - Pro: Faster workflow
  - Con: Easy to accidentally lose assignee

**Recommendation:** Option A - show confirmation if assignee will be lost.

---

### Q2: Should EditTaskBottomSheet allow selecting a deleted team's ID?
**Context:** If task.teamId references a deleted team, should dropdown show "Team not found" as a disabled item, or leave it empty?

**Options:**
- **Option A (Recommended):** Empty selection, force user to pick valid team
  - Pro: Clean UX, user must make explicit choice
  - Con: Original team name not visible
- **Option B:** Show "Team not found (Original Name)" as disabled item
  - Pro: User sees what team it was
  - Con: Requires looking up team name from somewhere (might not exist)

**Recommendation:** Option A - empty selection forces explicit choice.

---

### Q3: Should CreateTaskBottomSheet auto-select the only team if N=1?
**Context:** Current behavior auto-selects single team. New design makes team selection explicit.

**Options:**
- **Option A (Recommended):** No auto-select, user must click team dropdown even if N=1
  - Pro: Consistent behavior regardless of N
  - Pro: User consciously chooses team (builds mental model)
  - Con: Extra click when only 1 option
- **Option B:** Auto-select if N=1, require selection if N≥2
  - Pro: Saves a click in common case
  - Con: Inconsistent behavior (sometimes auto, sometimes manual)

**Recommendation:** Option A - no auto-select. Consistency and explicit choice trump saving one click.

---

### Q4: Should assignee dropdown default to "Unassigned" or first member?
**Context:** After selecting a team with members, what's the default assignee selection?

**Options:**
- **Option A (Recommended):** "Unassigned" (user must explicitly assign)
  - Pro: No assumptions about who should do the work
  - Pro: Matches EditTaskBottomSheet's "clear to unassigned" behavior
  - Con: Extra click to assign
- **Option B:** First member in list (auto-assign)
  - Pro: Saves a click if teacher always assigns
  - Con: Easy to accidentally assign to wrong person

**Recommendation:** Option A - default to "Unassigned".

---

### Q5: Should team change validation happen in UI or Repository?
**Context:** When user changes team in EditTaskBottomSheet, should we validate assignee-in-team in UI (before calling updateTask) or in Repository (server-side style validation)?

**Options:**
- **Option A (Recommended):** Both layers
  - UI: Clear assignee automatically when team changes (prevent invalid state)
  - Repository: Validate as defensive check (catch bugs, API misuse, race conditions)
  - Pro: Defense in depth
  - Con: Duplication of logic
- **Option B:** UI only
  - Pro: Simpler, less code
  - Con: No protection against API misuse or race conditions

**Recommendation:** Option A - validate in both layers.

---

### Q6: What happens to task list filtering when task's team changes?
**Context:** TeacherTaskListFragment shows tasks for a project. If task moves from Team A to Team B, does it stay visible in A's filtered view?

**Current Behavior:** Tasks are queried by projectId, not filtered by team in UI.

**Options:**
- **Option A (Recommended):** No change - task list shows all project tasks regardless of team
  - Pro: No additional work
  - Pro: Teacher sees all tasks in project
  - Con: If team-level filtering added later, this needs revisiting
- **Option B:** Add team filter to task list
  - Pro: Teacher can focus on one team's tasks
  - Con: Out of scope for this phase

**Recommendation:** Option A - defer team-level filtering to future phase.

---

## 11. Implementation Phases

### Phase 5.1: CreateTaskBottomSheet
1. Add assignee dropdown to layout
2. Update setupTeamSelection() to NOT auto-select
3. Add setupAssigneeDropdown(teamId) with Flow collection
4. Add team change handler (clear assignee, repopulate)
5. Update createTask() call with assigneeEmail parameter
6. Update TaskRepository.createTask() signature and validation

### Phase 5.2: EditTaskBottomSheet
1. Add team dropdown to layout
2. Add setupTeamDropdown() with availableTeams Flow
3. Update setupAssigneeDropdown() to accept teamId parameter (not from arguments)
4. Add team change handler with confirmation dialog
5. Update task arguments to include current teamId visibility
6. Update TaskRepository.updateTask() validation for team changes

### Phase 5.3: Testing
1. Manual test TC1-TC12
2. Verify dropdown population with real data
3. Verify confirmation dialogs
4. Verify repository validation catches invalid states

---

## 12. Risk Analysis

### Risk 1: Breaking existing task creation workflow
**Likelihood:** Medium
**Impact:** High
**Mitigation:** Phase 5.1 fully tested before starting 5.2

### Risk 2: Dropdown still not populating after redesign
**Likelihood:** Low (but previously observed)
**Impact:** High
**Mitigation:** Add logging to confirm Flow emissions before implementation

### Risk 3: Team change causing data loss (assignee)
**Likelihood:** Medium
**Impact:** Medium
**Mitigation:** Confirmation dialog + clear UX messaging

### Risk 4: Repository validation conflict with UI state
**Likelihood:** Low
**Impact:** Medium
**Mitigation:** Defensive validation in both layers with clear error messages

---

## 13. Success Criteria

1. ✅ CreateTaskBottomSheet has working assignee dropdown linked to team selection
2. ✅ EditTaskBottomSheet has working team dropdown
3. ✅ Changing team repopulates assignee dropdown
4. ✅ All 12 test scenarios pass
5. ✅ No regression in existing task creation/editing
6. ✅ Dropdown population bugs resolved (team and assignee both visible and selectable)

---

## Appendix A: Current vs. Proposed Comparison

| Feature | CreateTask (Current) | CreateTask (Proposed) | EditTask (Current) | EditTask (Proposed) |
|---------|---------------------|----------------------|-------------------|-------------------|
| Team Dropdown | ✅ Exists | ✅ Exists (no auto-select) | ❌ Missing | ✅ NEW |
| Assignee Dropdown | ❌ Missing | ✅ NEW | ✅ Exists | ✅ Exists |
| Team Selection | Auto-select if N=1 | Always manual | Hidden (from args) | Explicit dropdown |
| Assignee Selection | N/A | Linked to team | Fixed team | Linked to team |
| Team Change | N/A | Clears assignee | N/A (can't change) | Clears assignee + confirm |
| Deleted Team | Blocks creation | Blocks creation | Stale state | Force reselection |

---

**End of Design Document**

---

**Ready for Review:** Please review all sections, especially Open Questions Q1-Q6, and approve/modify before implementation begins.


---

## 14. Implementation Status

### ✅ Phase 5.1: CreateTaskBottomSheet - COMPLETED
- Assignee dropdown added to layout
- Team selection no longer auto-selects single team
- Assignee dropdown linked to team selection
- Team change handler clears assignee and repopulates dropdown
- TaskRepository.createTask() updated with assigneeEmail parameter

### ✅ Phase 5.2: EditTaskBottomSheet - COMPLETED
- Team dropdown added to layout
- Team dropdown populated with availableTeams Flow
- Assignee dropdown linked to team selection
- Team change handler with confirmation dialog implemented
- TaskRepository.updateTask() validates team changes

### ✅ Additional Fixes Implemented

#### Cross-Team Membership Blocking (Fixed: 2026-08-28)
**Issue:** Adding a student already in Team A to Team B caused the student to disappear from Team A's member list (StudentEntity.teamId updated), but Team A's member count still showed the old number (stale TeamEntity.memberEmails).

**Fix:** Block adding a student already in another team with clear error message.

**Implementation:**
- Added `StudentDao.getByEmailSync()` for cross-team validation
- Added `ProjectDao.getAllSync()` for repair utility
- Modified `ProjectRepositoryImpl.addMemberToTeam()` to check if student exists in any other team
- Returns `ApiResult.Error("Student already in another team")` if cross-team membership detected
- UI surfaces error via Snackbar to user

**Files Modified:**
- `StudentDao.kt` - Added `getByEmailSync()`
- `ProjectDao.kt` - Added `getAllSync()`
- `ProjectRepositoryImpl.kt` - Added cross-team validation in `addMemberToTeam()`

**Verified:** Build successful, error properly surfaced to user.

---

#### "No members" Placeholder Fix (Fixed: 2026-08-28)
**Issue:** In EditTaskBottomSheet, the "Assign To" dropdown showed placeholder text "Assign To" when no team was selected yet (Team field still says "Select team"), which was confusing.

**Fix:** When no team is selected, the "Assign To" dropdown now shows "No members" as its placeholder/empty state.

**Implementation:**
- Added check in `updateAssigneeDropdown()`: if `selectedTeamId.isEmpty()`, set adapter to single item ["No members"]
- Added call to `updateAssigneeDropdown()` in `handleTeamSelection()` when user clears team selection
- Scope limited to "no team selected" case only - did not change "team selected, zero members" case (still shows "Unassigned")

**Files Modified:**
- `EditTaskBottomSheet.kt` - Added `selectedTeamId.isEmpty()` check in `updateAssigneeDropdown()`, wired to `handleTeamSelection()`

**Verified:** Build successful, "No members" appears when no team selected.

---

#### Debug Utility: Team Member List Repair
**Purpose:** One-time repair utility to fix stale TeamEntity.memberEmails by recomputing from actual StudentEntity rows.

**Implementation:**
- Added `DebugSeedUtil.repairTeamMemberLists()` - recomputes memberEmails for all teams
- Added `DebugSeedUtil.logTeamMemberStates()` - diagnostic logging for debugging
- Added `StudentDao.getByTeamSync()` for batch student queries
- All functions gated by `BuildConfig.DEBUG` check

**Access:** Manual debugging via Evaluate Expression only (not wired to any production UI).

**Structure Verification:** Full top-to-bottom read confirmed all 7 functions properly inside class body, no stray code after closing brace. No production references to repair functions.

**Files Modified:**
- `DebugSeedUtil.kt` - Added repair and logging functions
- `StudentDao.kt` - Added `getByTeamSync()`

**Status:** Clean structure verified, zero production exposure.

---

### Test Coverage (This Session)
**Fixes verified in this session:**
- ✅ Cross-team membership blocking: Error surfaced correctly when attempting to add student already in another team
- ✅ "No members" placeholder: Displays correctly in EditTaskBottomSheet when no team selected
- ✅ Build successful with all changes
- ✅ DebugSeedUtil structure verified clean (no appended-outside-class risk)

**Note:** Original Phase 5 test scenarios (TC1-TC12 in Section 9) cover team/assignee dropdown linking functionality implemented in prior session, not re-tested here.

### Known Issues
**Stale Assignee Display (unfixed, out of scope):**
- When a task's assignee is no longer on the team, EditTaskBottomSheet shows "[email] — team has no members" in the assignee dropdown
- This message is technically incorrect (should say "no longer on team" not "team has no members")
- **Status:** Deferred - explicitly excluded from this fix scope per user request
- **Workaround:** User must manually change assignee to "Unassigned" or select a current team member

**Dropdown population bugs:** Resolved (original issue from Phase 5 design).

### Open Questions Resolution
All 6 open questions (Q1-Q6) resolved per recommendations:
- Q1: Confirmation dialog only if assignee will be lost ✅
- Q2: Empty selection for deleted team ✅
- Q3: No auto-select (even if N=1 team) ✅
- Q4: Default assignee = "Unassigned" ✅
- Q5: Validate in both UI + Repository layers ✅
- Q6: Defer task list filtering ✅

---

**Phase 5 Status: COMPLETE**
