# Phase 3: Project and Team Deletion - Design Document

## Overview
Add deletion capabilities for Projects and Teams to match existing Task deletion functionality. Both features use hard delete (no soft-delete/trash), consistent with current architecture. Team deletion reuses existing stale-assignee validation; Project deletion cascades to teams and tasks.

---

## 1. Feature Requirements

### 1.1 Team Deletion
- **Trigger**: Delete action on each team card in ProjectDetailFragment
- **Confirmation**: Required, using MaterialAlertDialogBuilder matching task deletion pattern
- **Action**: Delete TeamEntity from database
- **Side Effect**: Tasks with assigneeEmail belonging to deleted team members become "stale assignees" - handled naturally by existing EditTaskBottomSheet validation (no new logic needed)
- **Scope**: Hard delete only

### 1.2 Project Deletion
- **Trigger**: Delete action on ProjectDetailFragment toolbar (AND/OR long-press on home screen project card - see Open Questions)
- **Confirmation**: Required, with explicit cascade warning: "This will also delete all teams and tasks in this project"
- **Action**: Cascade delete in order: Project → Teams → Tasks (respecting FK constraints if any)
- **Scope**: Hard delete only

---

## 2. UI Design

### 2.1 Team Deletion UI

#### Current State
- Team cards in ProjectDetailFragment are **non-interactive** (no tap/click handlers)
- Cards display: team name, member count, up to 3 member emails
- Cards are generated dynamically in `createTeamCard()`

#### Proposed Design: **Option A - Delete Icon on Card (RECOMMENDED)**
```
┌─────────────────────────────────────┐
│ Team Alpha          3 members    🗑️ │ ← Delete icon in header
│ • user1@example.com                 │
│ • user2@example.com                 │
│ • user3@example.com                 │
└─────────────────────────────────────┘
```

**Rationale**: 
- Least disruptive - no new interaction pattern needed
- Delete is always visible and discoverable
- Consistent with common mobile patterns (delete actions on list items)
- No additional tap required (vs long-press or expand-to-reveal)

**Implementation**:
- Add `IconButton` (trash icon) to team card header layout
- Icon positioned at `layout_gravity="end"` after member count
- Click triggers confirmation dialog directly

#### Alternative: Option B - Long-Press on Card
- Require long-press on entire card to show delete option
- Pro: Cleaner visual, no extra UI element
- Con: Hidden affordance, requires user discovery, conflicts with potential future "tap to view team details" interaction

**Decision**: **Option A** recommended - explicit delete icon is more discoverable and won't conflict with future tap-to-view functionality.

#### Confirmation Dialog
```kotlin
MaterialAlertDialogBuilder(requireContext())
    .setTitle(R.string.delete_team_confirm_title)  // "Delete Team?"
    .setMessage(R.string.delete_team_confirm_message)  // "Team '[name]' will be permanently removed."
    .setNegativeButton(R.string.create_task_button_cancel) { dialog, _ -> 
        dialog.dismiss() 
    }
    .setPositiveButton(R.string.delete_team_confirm_delete) { dialog, _ ->  // "Delete"
        deleteTeam()
        dialog.dismiss()
    }
    .show()
```

**Match existing pattern**: Same structure/style as `EditTaskBottomSheet.showDeleteConfirmation()`

---

### 2.2 Project Deletion UI

#### Proposed Design: **Option A - Toolbar Menu Item (RECOMMENDED)**
```
┌─────────────────────────────────────┐
│ ← Project Details              ⋮    │ ← Overflow menu
│                                     │
│ Project Name: Alpha                 │
│ Due: Dec 31, 2026                   │
│ ...                                 │
└─────────────────────────────────────┘

Overflow menu items:
- Delete Project
```

**Rationale**:
- Standard Android pattern for destructive actions
- Keeps delete action accessible but not prominent (appropriate for high-impact action)
- Doesn't clutter the main UI
- Easy to implement with `Toolbar.inflateMenu()`

**Implementation**:
- Add menu resource `res/menu/menu_project_detail.xml`
- Inflate in `ProjectDetailFragment.setupToolbar()`
- Handle `onMenuItemSelected` → show confirmation dialog

#### Alternative: Option B - Long-Press on Home Screen Project Card
- Add long-press listener to project cards in `TeacherHomeFragment`
- Show context menu with "Delete Project"
- Pro: Delete accessible from home screen without entering detail
- Con: Hidden affordance, potential conflict with future interactions
- **Can be added in addition to Option A** if desired

**Decision**: **Option A (toolbar menu)** is sufficient; Option B deferred unless explicitly requested.

#### Confirmation Dialog - **Enhanced for Cascade**
```kotlin
MaterialAlertDialogBuilder(requireContext())
    .setTitle(R.string.delete_project_confirm_title)  // "Delete Project?"
    .setMessage(
        getString(
            R.string.delete_project_confirm_message,  
            project.name,
            teamCount,
            taskCount
        )
        // "Project '[name]' will be permanently deleted. 
        // This will also delete [X] team(s) and [Y] task(s)."
    )
    .setNegativeButton(R.string.create_task_button_cancel) { dialog, _ -> 
        dialog.dismiss() 
    }
    .setPositiveButton(R.string.delete_project_confirm_delete) { dialog, _ ->  // "Delete"
        deleteProject()
        dialog.dismiss()
    }
    .show()
```

**Key difference from Team deletion**: Message explicitly warns about cascade delete with counts.

---

## 3. Data Flow

### 3.1 Team Deletion Flow

```
User taps delete icon on team card
    ↓
ProjectDetailFragment shows confirmation dialog
    ↓
User confirms
    ↓
ProjectDetailFragment calls ViewModel.deleteTeam(teamId)
    ↓
ProjectDetailViewModel calls ProjectRepository.deleteTeam(teamId)
    ↓
ProjectRepositoryImpl calls TeamDao.deleteById(teamId)
    ↓
Room deletes TeamEntity
    ↓
ProjectDetailViewModel.teams Flow emits updated list (team removed)
    ↓
ProjectDetailFragment.renderTeams() updates UI
    ↓
Toast: "Team deleted successfully"
```

**Side Effect - Stale Assignee Handling**:
```
Task has assigneeEmail = "user@example.com"
Team containing "user@example.com" is deleted
    ↓
Next time EditTaskBottomSheet opens for that task:
    ↓
EditTaskBottomSheet.loadTeamMembers() fetches fresh team.memberEmails
    ↓
Validation detects assigneeEmail NOT in current memberEmails list
    ↓
Sets isAssigneeStale = true
    ↓
Shows error: "This task is assigned to someone no longer on the team..."
    ↓
User must reassign or unassign before save
```

**Validation**: ✅ No new logic needed - existing EditTaskBottomSheet validation handles this automatically via fresh team member fetch.

---

### 3.2 Project Deletion Flow

```
User taps overflow menu → Delete Project
    ↓
ProjectDetailFragment fetches team/task counts for confirmation message
    ↓
Shows confirmation dialog with cascade warning
    ↓
User confirms
    ↓
ProjectDetailFragment calls ViewModel.deleteProject(projectId)
    ↓
ProjectDetailViewModel calls ProjectRepository.deleteProject(projectId)
    ↓
ProjectRepositoryImpl executes cascade delete:
    1. Fetch all teams for projectId
    2. For each team: delete all tasks (TaskAssignmentDao.deleteByTeam())
    3. Delete all teams (TeamDao.deleteByProject())
    4. Delete project (ProjectDao.deleteById())
    ↓
Room deletes all entities
    ↓
ProjectDetailFragment navigates back to TeacherHomeFragment
    ↓
TeacherHomeViewModel.projects Flow emits updated list (project removed)
    ↓
TeacherHomeFragment.renderProjects() updates UI
    ↓
Toast: "Project deleted successfully"
```

---

## 4. Repository Layer Changes

### 4.1 ProjectRepository Interface
```kotlin
// Add to: app/src/main/java/com/cutm/TeamPulse/domain/repository/ProjectRepository.kt

interface ProjectRepository {
    // ... existing methods ...
    
    /**
     * Delete a team. Tasks assigned to team members will become stale assignees.
     */
    suspend fun deleteTeam(teamId: String): ApiResult<Unit>
    
    /**
     * Delete a project and cascade to all teams and tasks.
     * @return ApiResult.Success if deleted, ApiResult.Error if session expired or operation failed
     */
    suspend fun deleteProject(projectId: String): ApiResult<Unit>
}
```

### 4.2 ProjectRepositoryImpl Implementation
```kotlin
// Add to: app/src/main/java/com/cutm/TeamPulse/data/repository/ProjectRepositoryImpl.kt

@Inject
constructor(
    // ... existing dependencies ...
    private val database: TeamPulseDatabase,  // ← ADD THIS for transaction support
) : ProjectRepository {

override suspend fun deleteTeam(teamId: String): ApiResult<Unit> = withContext(dispatchers.io) {
    try {
        // Verify session
        val session = sessionDao.getActive() 
            ?: return@withContext ApiResult.Error("Session expired")
        
        // Delete team locally (single operation, no transaction needed)
        teamDao.deleteById(teamId)
        
        // TODO (future): Queue sync operation to delete from Google Sheets
        // syncQueueDao.enqueue(SyncOperation.DELETE_TEAM, teamId)
        
        ApiResult.Success(Unit)
    } catch (e: Exception) {
        ApiResult.Error(e.message ?: "Failed to delete team")
    }
}

override suspend fun deleteProject(projectId: String): ApiResult<Unit> = withContext(dispatchers.io) {
    try {
        // Verify session
        val session = sessionDao.getActive() 
            ?: return@withContext ApiResult.Error("Session expired")
        
        // Cascade delete in ATOMIC TRANSACTION
        database.withTransaction {
            // 1. Get all teams in project
            val teams = teamDao.getByProjectSync(projectId)
            
            // 2. Delete all tasks for each team
            teams.forEach { team ->
                taskDao.deleteByTeam(team.teamId)
            }
            
            // 3. Delete all teams
            teamDao.deleteByProject(projectId)
            
            // 4. Delete project
            projectDao.deleteById(projectId)
        }
        // All deletes succeed atomically or none do - no partial state possible
        
        // TODO (future): Queue sync operations to delete from Google Sheets
        // syncQueueDao.enqueue(SyncOperation.DELETE_PROJECT, projectId)
        
        ApiResult.Success(Unit)
    } catch (e: Exception) {
        ApiResult.Error(e.message ?: "Failed to delete project")
    }
}
```

---

## 5. DAO Layer Changes

### 5.1 TeamDao Additions
```kotlin
// Add to: app/src/main/java/com/cutm/TeamPulse/data/local/dao/TeamDao.kt

@Dao
interface TeamDao {
    // ... existing methods ...
    
    @Query("DELETE FROM teams WHERE teamId = :teamId")
    suspend fun deleteById(teamId: String)
    
    @Query("DELETE FROM teams WHERE projectId = :projectId")
    suspend fun deleteByProject(projectId: String)
    
    @Query("SELECT * FROM teams WHERE projectId = :projectId")
    suspend fun getByProjectSync(projectId: String): List<TeamEntity>
}
```

### 5.2 TaskAssignmentDao Additions
```kotlin
// Add to: app/src/main/java/com/cutm/TeamPulse/data/local/dao/TaskAssignmentDao.kt

@Dao
interface TaskAssignmentDao {
    // ... existing methods ...
    
    @Query("DELETE FROM task_assignments WHERE teamId = :teamId")
    suspend fun deleteByTeam(teamId: String)
}
```

**Note**: `ProjectDao.deleteById()` already exists (added in Phase 1).

---

## 6. ViewModel Layer Changes

### 6.1 ProjectDetailViewModel Additions
```kotlin
// Add to: app/src/main/java/com/cutm/TeamPulse/ui/teacher/ProjectDetailViewModel.kt

@HiltViewModel
class ProjectDetailViewModel @Inject constructor(
    // ... existing dependencies ...
) : ViewModel() {
    
    // ... existing properties ...
    
    fun deleteTeam(teamId: String) {
        viewModelScope.launch {
            val result = projectRepository.deleteTeam(teamId)
            // Note: teams Flow will automatically update via Room's Flow observation
            // No explicit UI update needed - renderTeams() will be called automatically
        }
    }
    
    fun deleteProject(projectId: String) {
        viewModelScope.launch {
            val result = projectRepository.deleteProject(projectId)
            // Note: Fragment will navigate back to home screen after calling this
            // TeacherHomeViewModel.projects Flow will automatically update
        }
    }
    
    suspend fun getTeamCount(projectId: String): Int {
        return projectRepository.getTeamCount(projectId)
    }
    
    suspend fun getTaskCount(projectId: String): Int {
        return projectRepository.getTaskCount(projectId)
    }
}
```

**Note**: Need to add `getTeamCount()` and `getTaskCount()` to repository for confirmation dialog message.

---

## 7. String Resources

### 7.1 Team Deletion Strings
```xml
<!-- Add to: app/src/main/res/values/strings.xml -->

<!-- Team Deletion -->
<string name="action_delete_team">Delete Team</string>
<string name="delete_team_confirm_title">Delete Team?</string>
<string name="delete_team_confirm_message">Team \'%1$s\' will be permanently removed.</string>
<string name="delete_team_success">Team deleted successfully</string>
<string name="delete_team_error">Failed to delete team</string>
```

### 7.2 Project Deletion Strings
```xml
<!-- Project Deletion -->
<string name="action_delete_project">Delete Project</string>
<string name="delete_project_confirm_title">Delete Project?</string>
<string name="delete_project_confirm_message">Project \'%1$s\' will be permanently deleted.\n\nThis will also delete %2$d team(s) and %3$d task(s).</string>
<string name="delete_project_success">Project deleted successfully</string>
<string name="delete_project_error">Failed to delete project</string>
```

---

## 8. Navigation Changes

### Project Deletion Navigation
After successful project deletion:
```kotlin
// In ProjectDetailFragment.deleteProject()
viewModel.deleteProject(projectId)
Toast.makeText(requireContext(), R.string.delete_project_success, Toast.LENGTH_SHORT).show()
findNavController().navigateUp()  // Return to TeacherHomeFragment
```

**Rationale**: Project no longer exists, so ProjectDetailFragment has no data to render. Must navigate back.

### Team Deletion Navigation
No navigation change needed - user remains on ProjectDetailFragment. UI updates automatically via Flow observation.

---

## 9. Foreign Key Constraints Analysis

### Current Schema
Based on entity examination:
- **No explicit Room ForeignKey constraints defined** in `@Entity` annotations
- Relationships are **logical only** (projectId/teamId fields link entities)
- Room will NOT enforce cascade deletes automatically

### Implication
**Manual cascade delete required** in repository layer (already designed above):
```kotlin
// Must explicitly delete in order:
1. Tasks (by teamId, then by projectId)
2. Teams (by projectId)
3. Project
```

**This is correct** - current architecture already uses this pattern (e.g., no FK constraints in existing entities).

---

## 10. Validation Rules

### 10.1 Team Deletion Validation
- ✅ **Session Required**: Check session exists (already handled by repository layer)
- ✅ **Team Exists**: TeamDao query handles missing team gracefully (no-op delete)
- ✅ **Confirmation Required**: User must explicitly confirm in dialog
- ⚠️ **No orphaned task check**: Tasks remain in DB with now-invalid teamId/assigneeEmail
  - **Mitigation**: Existing stale-assignee validation catches this when task is edited
  - **Acceptable**: Matches current architecture (no defensive validation at delete time)

### 10.2 Project Deletion Validation
- ✅ **Session Required**: Check session exists
- ✅ **Project Exists**: ProjectDao query handles missing project gracefully
- ✅ **Confirmation with Counts**: User sees how many teams/tasks will be deleted
- ✅ **Cascade Order**: Delete tasks → teams → project (correct order)
- ✅ **Transaction Atomicity**: All deletes wrapped in `database.withTransaction { ... }` - ensures all-or-nothing behavior, no partial state if any delete fails

---

## 11. Test Scenarios

### 11.1 Team Deletion Test Cases

#### TC1: Delete Empty Team (No Members)
**Given**: Team exists with 0 members and 0 tasks  
**When**: User taps delete icon → confirms  
**Then**: Team deleted, UI updates, toast shown  
**Verify**: Team no longer in DB, no orphaned records

#### TC2: Delete Team with Members but No Tasks
**Given**: Team exists with 3 members, 0 tasks  
**When**: User deletes team  
**Then**: Team deleted successfully  
**Verify**: No tasks affected (none existed)

#### TC3: Delete Team with Assigned Tasks
**Given**: Team "Alpha" with member "user@example.com", Task1 assigned to "user@example.com"  
**When**: User deletes team "Alpha"  
**Then**: Team deleted, Task1 remains in DB with teamId="alpha-id" (now invalid)  
**When**: User opens EditTaskBottomSheet for Task1  
**Then**: `StudentDao.observeByTeam("alpha-id")` returns empty list (team gone)  
**Then**: EditTaskBottomSheet detects `teamMembers.isEmpty() = true`  
**Then**: Since `selectedAssigneeEmail = "user@example.com"` (assigned), code branch executes:
```kotlin
if (teamMembers.isEmpty()) {
    if (selectedAssigneeEmail.isEmpty()) {
        isAssigneeStale = false
    } else {
        // Task is assigned but team has no members - stale
        isAssigneeStale = true
        binding.assigneeDropdown.setText("$selectedAssigneeEmail — team has no members")
    }
}
```
**Then**: Shows error: `"user@example.com — team has no members"` with `isAssigneeStale = true`  
**Then**: Save button validation blocks save: `"This task is assigned to someone no longer on the team. Please select 'Unassigned' or assign to a current team member."`  
**Verify**: User cannot save until reassigning or unassigning

**Trace Evidence**: 
- When team is completely deleted (not just member removed), `StudentDao.observeByTeam(teamId)` returns `emptyList()`
- `EditTaskBottomSheet.updateAssigneeDropdown()` treats `teamMembers.isEmpty()` the same whether team has zero members OR team doesn't exist at all
- Existing stale-assignee validation catches both cases identically - **no new logic needed**

#### TC4: Delete Team with Unassigned Tasks
**Given**: Team "Beta" exists, Task2 has teamId="beta-id" but assigneeEmail="" (unassigned)  
**When**: User deletes team "Beta"  
**Then**: Team deleted, Task2 remains with now-invalid teamId  
**When**: User opens EditTaskBottomSheet for Task2  
**Then**: `StudentDao.observeByTeam("beta-id")` returns empty list (team gone)  
**Then**: EditTaskBottomSheet detects `teamMembers.isEmpty() = true`  
**Then**: Since `assigneeEmail = ""` (unassigned), sets `isAssigneeStale = false` and shows "Unassigned (whole team)"  
**Verify**: Unassigned tasks remain valid even after team deletion (assignee dropdown shows "Unassigned", no error)

**Trace Evidence**: 
- `StudentDao.observeByTeam(teamId)` queries `SELECT * FROM students WHERE teamId = :teamId`
- When team doesn't exist, query returns `emptyList()` (not null, not crash)
- `updateAssigneeDropdown()` branch: `if (teamMembers.isEmpty()) { ... if (selectedAssigneeEmail.isEmpty()) { isAssigneeStale = false } }`
- **Team completely gone behaves identically to team with zero members** - both result in empty list

#### TC5: Cancel Delete Confirmation
**Given**: User taps delete icon on team  
**When**: Dialog appears → user taps "Cancel"  
**Then**: Dialog dismissed, team not deleted, no changes

#### TC6: Delete Last Team in Project
**Given**: Project has exactly 1 team  
**When**: User deletes that team  
**Then**: Team deleted, empty state shown: "No teams yet"  
**Verify**: "Create Team" button still functional

---

### 11.2 Project Deletion Test Cases

#### TC7: Delete Empty Project (No Teams/Tasks)
**Given**: Project exists with 0 teams, 0 tasks  
**When**: User opens overflow menu → Delete Project → confirms  
**Then**: Project deleted, navigates back to home, toast shown  
**Verify**: Project no longer in home screen list

#### TC8: Delete Project with Multiple Teams and Tasks
**Given**: Project "Gamma" has 2 teams (Alpha, Beta), 5 tasks total  
**When**: User deletes project  
**Then**: Confirmation shows "This will also delete 2 team(s) and 5 task(s)"  
**When**: User confirms  
**Then**: All 5 tasks deleted, both teams deleted, project deleted  
**Verify**: No orphaned records in any table

#### TC9: Cascade Delete Order
**Given**: Project "Delta" with Team1 (has 2 tasks), Team2 (has 1 task)  
**When**: User deletes project  
**Then**: Delete order: Task1, Task2, Task3 → Team1, Team2 → Project  
**Verify**: All entities removed from DB

#### TC10: Cancel Project Delete Confirmation
**Given**: User opens delete dialog  
**When**: User taps "Cancel"  
**Then**: Dialog dismissed, project not deleted, still on detail screen

#### TC11: Delete Project from Home Screen vs Detail Screen
**Given**: Project exists  
**When**: User long-presses project card on home screen (IF Option B implemented)  
**Then**: Same confirmation dialog, same cascade delete behavior  
**Verify**: Both entry points produce identical results

#### TC12: Navigation After Delete
**Given**: User is on ProjectDetailFragment  
**When**: User deletes the project  
**Then**: Automatically navigates back to TeacherHomeFragment  
**Verify**: Home screen no longer shows deleted project

---

### 11.3 Edge Cases

#### TC13: Session Expired During Delete
**Given**: User session expired (token invalid)  
**When**: User attempts delete operation  
**Then**: Repository returns ApiResult.Error("Session expired")  
**Then**: Fragment shows error toast, does NOT delete, remains on current screen

#### TC14: Concurrent Delete (Multiple Users, Future)
**Given**: Two teachers have access to same project (hypothetical multi-user scenario)  
**When**: Teacher A deletes project while Teacher B viewing it  
**Then**: Teacher B's Flow observation detects project = null  
**Then**: Teacher B sees error state or navigates back  
**Note**: Current architecture single-user, but Flow will handle this gracefully

#### TC15: Rapid Delete Clicks (Double-Tap)
**Given**: User taps delete icon rapidly twice  
**When**: First tap shows dialog  
**Then**: Second tap should not show duplicate dialog (dialog already showing)  
**Implementation**: MaterialAlertDialogBuilder handles this by default (single instance)

---

## 12. Open Questions

### Q1: Team Deletion UI Pattern
**Question**: Delete icon on card (Option A) vs long-press (Option B)?  
**Recommendation**: **Option A (delete icon)** - more discoverable, no affordance hiding  
**Approval Needed**: Yes

### Q2: Project Deletion Entry Points
**Question**: Toolbar menu only (Option A) vs toolbar + long-press on home card (Option A + B)?  
**Recommendation**: **Toolbar only** for Phase 3 - simpler, sufficient  
**Defer**: Long-press on home screen can be added later if needed  
**Approval Needed**: Yes

### Q3: Repository Count Methods
**Question**: Add `ProjectRepository.getTeamCount()` and `getTaskCount()` for confirmation message?  
**Alternative**: Hardcode generic message "This will also delete all teams and tasks"  
**Recommendation**: **Add count methods** - more informative, helps user make informed decision  
**Approval Needed**: Yes

### Q4: Toast vs Snackbar for Success
**Question**: Use Toast (quick, simple) or Snackbar (allows Undo - future enhancement)?  
**Current**: Task deletion uses Toast  
**Recommendation**: **Toast for consistency** - Undo would require soft-delete architecture (not in scope)  
**Approval Needed**: Yes (assuming Toast is approved)

### Q5: Stale Assignee - Proactive Notification?
**Question**: Should team deletion trigger a notification/alert to affected task assignees?  
**Current**: Detection happens lazily when EditTaskBottomSheet opens  
**Recommendation**: **Lazy detection only** (current approach) - simpler, avoids notification complexity  
**Rationale**: Tasks are teacher-managed; teachers can reassign as needed  
**Approval Needed**: Confirm lazy approach is acceptable

### Q6: Error Handling - What to Show User?
**Question**: If delete fails (e.g., DB error), show generic "Failed to delete" or specific error message?  
**Recommendation**: **Generic message** - specific DB errors not actionable by user  
**Exception**: "Session expired" should be specific (user can re-authenticate)  
**Approval Needed**: Yes

### Q7: Menu Resource - Reusable or Project-Only?
**Question**: Create `menu_project_detail.xml` specifically for project deletion, or make reusable `menu_teacher_actions.xml`?  
**Recommendation**: **Project-specific menu** for Phase 3 - can refactor to shared menu later if needed  
**Approval Needed**: Yes

---

## 13. Architectural Conflicts Check

### ✅ Confirmed No Conflicts

#### Existing Patterns Followed
- **Delete confirmation dialogs**: Matches `EditTaskBottomSheet.showDeleteConfirmation()` exactly
- **Repository pattern**: Uses existing `ApiResult<T>` return type
- **ViewModel orchestration**: Calls repository, lets Flow update UI automatically
- **Hard delete**: Consistent with existing `TaskAssignmentDao.deleteById()`
- **No FK constraints**: Manual cascade matches current schema design
- **Toast notifications**: Matches existing success messages

#### Stale Assignee Reuse Verified
- ✅ `EditTaskBottomSheet` already validates assigneeEmail against fresh `team.memberEmails`
- ✅ No new validation logic needed - existing code handles deleted team members
- ✅ `loadTeamMembers()` fetches current team state, detects stale assignee automatically

#### Cascade Delete Order Correct
- ✅ Tasks → Teams → Project order respects logical relationships
- ✅ No Room FK constraints exist (verified in entities), so manual cascade is required
- ✅ All deletes wrapped in `database.withTransaction { ... }` ensures atomicity

---

## 14. Implementation Checklist

### Phase 3.1: Team Deletion
- [ ] Add `TeamDao.deleteById()` and `TeamDao.deleteByProject()`
- [ ] Add `ProjectRepository.deleteTeam()` interface method
- [ ] Implement `ProjectRepositoryImpl.deleteTeam()` (session check + single delete operation)
- [ ] Add `ProjectDetailViewModel.deleteTeam()`
- [ ] Add delete icon to team card in `ProjectDetailFragment.createTeamCard()`
- [ ] Add `showDeleteTeamConfirmation()` in ProjectDetailFragment
- [ ] Add team deletion string resources
- [ ] Build and test TC1-TC6

### Phase 3.2: Project Deletion
- [ ] **Inject `TeamPulseDatabase` into ProjectRepositoryImpl constructor** for transaction support
- [ ] Add `TaskAssignmentDao.deleteByTeam()`
- [ ] Add `TeamDao.getByProjectSync()`
- [ ] Add `ProjectRepository.deleteProject()` interface method
- [ ] Add `ProjectRepository.getTeamCount()` and `getTaskCount()` interface methods
- [ ] Implement `ProjectRepositoryImpl.deleteProject()` **wrapped in `database.withTransaction { ... }`**
- [ ] Implement count methods in ProjectRepositoryImpl
- [ ] Add `ProjectDetailViewModel.deleteProject()`, `getTeamCount()`, `getTaskCount()`
- [ ] Create `res/menu/menu_project_detail.xml`
- [ ] Add menu inflation in `ProjectDetailFragment.setupToolbar()`
- [ ] Add `showDeleteProjectConfirmation()` in ProjectDetailFragment
- [ ] Add project deletion string resources
- [ ] Build and test TC7-TC12

### Phase 3.3: Edge Cases & Polish
- [ ] Test TC13-TC15 (session expiry, concurrent, rapid clicks)
- [ ] Verify stale assignee detection still works after team deletion
- [ ] Verify cascade delete order with DB inspector
- [ ] Code review against task deletion implementation for consistency
- [ ] Update this design doc with any implementation deviations

---

## 15. Future Enhancements (Out of Scope)

- **Soft Delete / Trash**: Add `deletedAt` timestamp, filter out deleted items, allow restore
- **Undo**: Snackbar with Undo action (requires soft delete first)
- **Multi-select Delete**: Select multiple teams/projects, delete in batch
- **Long-press on Home Card**: Add project deletion from home screen (deferred to future phase)
- **Sync to Google Sheets**: Currently local-only delete; sync operations queued for future
- **Audit Trail**: Log deletion events (who deleted what, when)
- **Confirmation Checkbox**: For project delete, require "I understand this cannot be undone" checkbox

---

## 17. Design Corrections Applied (Pre-Implementation)

### Correction 1: Transaction Atomicity (Section 4.2, 10.2)
**Issue**: Original design claimed `withContext(dispatchers.io)` provided "transaction safety" - **incorrect**.  
**Root Cause**: `withContext` only controls threading (IO dispatcher), NOT database atomicity.  
**Risk**: Sequential delete operations (tasks loop → teams delete → project delete) could fail partway through, leaving corrupted partial state (orphaned tasks, deleted teams but project remains).  
**Fix Applied**: 
- Wrap entire cascade delete in `database.withTransaction { ... }`
- Ensures all-or-nothing semantics: either all deletes succeed or all roll back
- Updated code sample in Section 4.2 to show correct implementation
- Updated validation text in Section 10.2 to reflect actual atomicity guarantee
- Added checklist item in Section 14 to inject `TeamPulseDatabase` dependency

**Evidence**: Room's `withTransaction` provides ACID transaction - all operations commit together or none commit.

---

### Correction 2: Team Completely Gone Behavior (TC3, TC4)
**Issue**: Original design claimed stale-assignee validation worked for deleted teams but didn't trace the actual code path.  
**Question**: Does `EditTaskBottomSheet.loadTeamMembers()` handle "team completely deleted" (teamId doesn't exist) the same as "team exists but member removed"?  
**Traced Behavior**:
1. `StudentDao.observeByTeam(teamId)` executes `SELECT * FROM students WHERE teamId = :teamId`
2. If team doesn't exist, Room returns `emptyList()` (not null, not crash)
3. Flow emits `List<Student>` = `[]`
4. `EditTaskBottomSheet.updateAssigneeDropdown()` receives `teamMembers.isEmpty() = true`
5. Code executes branch: `if (teamMembers.isEmpty()) { ... }`
6. If task is assigned (`selectedAssigneeEmail != ""`): sets `isAssigneeStale = true`, shows `"$email — team has no members"`
7. If task is unassigned (`selectedAssigneeEmail = ""`): sets `isAssigneeStale = false`, shows "Unassigned"

**Conclusion**: ✅ **Team completely gone behaves identically to team with zero members.**  
**Result**: Existing stale-assignee validation handles deleted teams automatically - **no new logic needed** (claim verified with evidence).

**Updated Test Cases**:
- TC3: Expanded with detailed trace showing deleted team → empty list → stale assignee detection
- TC4: Expanded to show unassigned tasks remain valid after team deletion (no error shown)

---

## 18. Summary

### What's Being Built
- **Team deletion** with icon-based UI, confirmation dialog, leveraging existing stale-assignee validation
- **Project deletion** with toolbar menu, cascade delete (tasks → teams → project), enhanced confirmation with counts

### Why This Design
- **Consistency**: Matches existing task deletion pattern exactly
- **Safety**: Confirmation dialogs prevent accidental deletion
- **Simplicity**: Reuses existing validation, no new architecture patterns
- **Correctness**: Manual cascade respects current schema (no FK constraints)

### What's Not Being Built
- Long-press interactions (deferred)
- Soft delete/Undo (requires architecture change)
- Sync to Google Sheets (future enhancement)

### Approval Required For
1. ✅ Team deletion: delete icon on card (Option A) - **Approve?**
2. ✅ Project deletion: toolbar menu only (Option A) - **Approve?**
3. ✅ Add count methods for confirmation message - **Approve?**
4. ✅ Toast for success notifications - **Approve?**
5. ✅ Lazy stale-assignee detection (no proactive notification) - **Approve?**
6. ✅ Generic error messages (except session expired) - **Approve?**
7. ✅ Project-specific menu resource - **Approve?**

---

**Ready for Implementation?** Awaiting approval on Open Questions before proceeding.
branch: `if (teamMembers.isEmpty()) { ... }`
6. If task is assigned (`selectedAssigneeEmail != ""`): sets `isAssigneeStale = true`, shows `"$email — team has no members"`
7. If task is unassigned (`selectedAssigneeEmail = ""`): sets `isAssigneeStale = false`, shows "Unassigned"

**Conclusion**: ✅ **Team completely gone behaves identically to team with zero members.**  
**Result**: Existing stale-assignee validation handles deleted teams automatically - **no new logic needed** (claim verified with evidence).

**Updated Test Cases**:
- TC3: Expanded with detailed trace showing deleted team → empty list → stale assignee detection
- TC4: Expanded to show unassigned tasks remain valid after team deletion (no error shown)

---

## 18. Summary

### What's Being Built
- **Team deletion** with icon-based UI, confirmation dialog, leveraging existing stale-assignee validation
- **Project deletion** with toolbar menu, **atomic** cascade delete (tasks → teams → project), enhanced confirmation with counts

### Why This Design
- **Consistency**: Matches existing task deletion pattern exactly
- **Safety**: Confirmation dialogs prevent accidental deletion; **database transaction ensures atomicity**
- **Simplicity**: Reuses existing validation, no new architecture patterns
- **Correctness**: Manual cascade respects current schema (no FK constraints); **verified existing stale-assignee validation handles deleted teams**

### What's Not Being Built
- Long-press interactions (deferred)
- Soft delete/Undo (requires architecture change)
- Sync to Google Sheets (future enhancement)

### Approval Status
**ALL 7 OPEN QUESTIONS APPROVED** (as recommended):
1. ✅ Team deletion: delete icon on card (Option A)
2. ✅ Project deletion: toolbar menu only (Option A)
3. ✅ Add count methods for confirmation message
4. ✅ Toast for success notifications
5. ✅ Lazy stale-assignee detection (no proactive notification)
6. ✅ Generic error messages (except session expired)
7. ✅ Project-specific menu resource

### Design Corrections Applied
1. ✅ **Transaction atomicity**: Fixed Section 4.2 to use `database.withTransaction { ... }` instead of falsely claiming `withContext` provides transaction safety
2. ✅ **Team-gone behavior**: Traced and verified TC3/TC4 with actual code paths - confirmed existing validation handles deleted teams identically to zero-member teams

---

**Ready for Implementation** - All issues addressed with traced evidence.
