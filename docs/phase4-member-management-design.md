# Phase 4: Team Member Management - Design Document

**Status:** Draft - Revised (Awaiting Approval)  
**Author:** Kiro  
**Date:** 2026-08-28  
**Supersedes:** None (new feature)  
**Blocks:** Phase 3 deletion testing (cannot test task-assignee flows without ability to add/remove members)

**Revision History:**
- **Rev 1 (2026-08-28):** Initial draft
- **Rev 2 (2026-08-28):** Addressed review feedback:
  1. Added Section 3.0 with traced evidence of member-removal stale-assignee detection (N→N-1 scenario)
  2. Fixed transactional consistency: wrapped addMemberToTeam/removeMemberFromTeam in database.withTransaction
  3. Changed return type from Kotlin Result to ApiResult for codebase consistency

---

## 1. Executive Summary

### Purpose
Add UI for teachers to add and remove team members, enabling them to manage team composition without deleting entire teams. This unblocks testing of the already-implemented stale-assignee validation mechanism (designed for team deletion) in the more common real-world scenario: individual member removal.

### Scope
- **In Scope:**
  - Add member to existing team (by email)
  - Remove member from existing team
  - Update StudentEntity and TeamEntity.memberEmails in sync
  - Email validation, duplicate checking
  - Integration with existing stale-assignee detection (EditTaskBottomSheet)
  
- **Out of Scope:**
  - Bulk member operations (add/remove multiple at once)
  - Invite flow (external notifications, email confirmation)
  - Role assignment (team lead, etc.)
  - Editing member details after creation
  - Student self-enrollment

### Success Criteria
1. Teacher can add a student to a team by entering their email
2. Teacher can remove a student from a team
3. Removing a member triggers stale-assignee detection for tasks previously assigned to them
4. No orphaned StudentEntity records (cleanup on removal)
5. TeamEntity.memberEmails stays synchronized with StudentEntity records

---

## 2. Current State Analysis

### Existing Data Model
```kotlin
// TeamEntity (already exists)
data class TeamEntity(
    val teamId: String,
    val projectId: String,
    val teamName: String,
    val memberEmails: List<String>,  // ← Source of truth for "who is on team"
    val createdAt: Long,
    val localDirty: Boolean,
    val lastModifiedLocal: Long
)

// StudentEntity (already exists)
data class StudentEntity(
    @PrimaryKey val studentEmail: String,
    val displayName: String,
    val teamId: String,
    val projectId: String,
    val joinedAt: Long,
    val localDirty: Boolean
)
```

### Existing UI Entry Points
**ProjectDetailFragment** currently shows:
- Project header (name, due date)
- List of team cards (team name, member count)
- Each team card has a **delete icon** (added in Phase 3)
- "Tasks" button to navigate to task list

**Proposal:** Add member management actions directly to team cards via:
- **Option A:** Long-press on team card → context menu (Add Member, Remove Member, Delete Team)
- **Option B:** Expand team card to show member list inline, with +/− icons per member
- **Option C:** Tap team card → navigate to dedicated TeamDetailFragment with full member list

**Recommended: Option B** (inline expansion) - most discoverable, least navigation overhead, consistent with ProjectDetailFragment's existing card-based design.

### Existing Stale-Assignee Mechanism
**EditTaskBottomSheet.kt** (lines 157-221) already implements:
1. `loadTeamMembers()` → observes students by `teamId`
2. `updateAssigneeDropdown()` → checks if `selectedAssigneeEmail` is in `teamMembers` list
3. If assignee not found → sets `isAssigneeStale = true` and displays `"$selectedAssigneeEmail — no longer on team"`
4. `saveTask()` blocks save if `isAssigneeStale == true`

**This mechanism should work unchanged for member removal** because:
- `studentRepository.observeStudentsByTeam(teamId)` queries `SELECT * FROM students WHERE teamId = :teamId`
- Removing a member deletes their StudentEntity → they disappear from query results → assignee dropdown detects stale state
- **No code changes needed** — just need to verify it actually triggers as designed

---

## 3. Proposed Solution

### 3.0 Stale-Assignee Detection: Member Removal Trace

**Scenario:** Team has 3 members (Alice, Bob, Carol). Task is assigned to Alice. Alice is removed from team.

**Code Trace:**

1. **Member Removal** (ProjectRepositoryImpl):
   ```kotlin
   database.withTransaction {
       studentDao.deleteByEmail("alice@cutm.ac.in")  // ← Deletes StudentEntity
       teamDao.upsert(updatedTeam)  // ← memberEmails now [bob@..., carol@...]
   }
   ```

2. **Database Change** (Room):
   - `students` table: Row with `studentEmail='alice@cutm.ac.in'` deleted
   - `teams` table: `memberEmails` column updated to `["bob@cutm.ac.in", "carol@cutm.ac.in"]`

3. **Flow Re-Emission** (StudentDao):
   ```kotlin
   @Query("SELECT * FROM students WHERE teamId = :teamId")
   fun observeByTeam(teamId: String): Flow<List<StudentEntity>>
   ```
   - Room detects change to `students` table WHERE `teamId = <team-alpha-id>`
   - **Flow emits new list: [StudentEntity(bob), StudentEntity(carol)]** (Alice gone)

4. **Repository Mapping** (StudentRepositoryImpl):
   ```kotlin
   override fun observeStudentsByTeam(teamId: String): Flow<List<Student>> {
       return studentDao.observeByTeam(teamId).map { entities ->
           entities.map { it.toDomain() }  // ← Maps to domain models
       }
   }
   ```
   - **Emits: [Student(bob), Student(carol)]**

5. **ViewModel Pass-Through** (TeacherTaskListViewModel):
   ```kotlin
   fun getTeamMembers(teamId: String) = studentRepository.observeStudentsByTeam(teamId)
   ```
   - **Returns Flow<List<Student>>** directly to UI

6. **EditTaskBottomSheet Collection** (line 161):
   ```kotlin
   viewModel.getTeamMembers(teamId).collectLatest { students ->
       teamMembers = students  // ← [Student(bob), Student(carol)] (Alice missing)
       teamMembersLoaded = true
       updateAssigneeDropdown()  // ← Re-runs dropdown update
   }
   ```
   - `collectLatest` **receives new emission** with 2-member list
   - **`teamMembers` now excludes Alice**
   - `updateAssigneeDropdown()` called

7. **Dropdown Update Logic** (line 193, else branch):
   ```kotlin
   } else {
       // Team members loaded and present
       binding.assigneeDropdownLayout.isEnabled = true
       items.addAll(teamMembers.map { it.displayName })  // ← ["Bob Smith", "Carol White"]
       
       val currentSelection = if (selectedAssigneeEmail.isEmpty()) {
           // ... (not this path)
       } else {
           val matchingStudent = teamMembers.find { 
               it.studentEmail == selectedAssigneeEmail  // ← Searching for "alice@cutm.ac.in"
           }
           if (matchingStudent != null) {
               // ... (not this path - Alice not found)
           } else {
               // ✅ THIS PATH EXECUTES
               // Assignee no longer in team - mark as stale but preserve email
               isAssigneeStale = true  // ← Sets stale flag
               "$selectedAssigneeEmail — no longer on team"  // ← "alice@cutm.ac.in — no longer on team"
           }
       }
       binding.assigneeDropdown.setText(currentSelection, false)
   }
   ```
   - `teamMembers.find()` searches `[Student(bob), Student(carol)]` for `alice@cutm.ac.in`
   - **Not found** → `matchingStudent = null`
   - **Else branch executes:**
     - `isAssigneeStale = true`
     - Dropdown text set to `"alice@cutm.ac.in — no longer on team"`

8. **Save Validation** (line 268):
   ```kotlin
   // Validate assignee is not stale
   if (isAssigneeStale) {
       showError("This task is assigned to someone no longer on the team. Please select 'Unassigned' or assign to a current team member.")
       return  // ← Blocks save
   }
   ```

**Conclusion:** ✅ **Member removal DOES trigger stale-assignee detection correctly.**

**Key Difference from "Team Has Zero Members" Case:**
- Zero-members path (line 179-189): `if (teamMembers.isEmpty())` → different error message (`"— team has no members"`)
- Non-empty-but-missing path (line 203-207): `else { matchingStudent == null }` → error message (`"— no longer on team"`)
- **Both paths set `isAssigneeStale = true` and block save** — behavior is identical, only message differs

**Flow Reactivity Confirmed:**
- Room's `@Query` with `Flow` return type **auto-emits on table changes**
- `DELETE FROM students WHERE studentEmail = :email` **triggers re-emission** for any observer watching `WHERE teamId = :teamId` (if deleted student had that teamId)
- `collectLatest` **cancels previous coroutine and runs updateAssigneeDropdown() with new data**

---

## 3.1 UI Design

#### ProjectDetailFragment - Expanded Team Card

**Current (collapsed state):**
```
┌─────────────────────────────────────────────┐
│ Team Alpha                        [×]       │  ← Delete icon (Phase 3)
│ 3 members                                   │
└─────────────────────────────────────────────┘
```

**Proposed (expanded state):**
```
┌─────────────────────────────────────────────┐
│ Team Alpha                        [×] [▼]   │  ← Expand/collapse chevron + Delete
│ 3 members                                   │
│                                             │
│   ┌─────────────────────────────────────┐  │
│   │ Alice Johnson    alice@cutm.ac.in [×]│  │  ← Remove member icon
│   ├─────────────────────────────────────┤  │
│   │ Bob Smith        bob@cutm.ac.in   [×]│  │
│   ├─────────────────────────────────────┤  │
│   │ Carol White      carol@cutm.ac.in [×]│  │
│   └─────────────────────────────────────┘  │
│                                             │
│   [+ Add Member]                            │  ← Add member button
└─────────────────────────────────────────────┘
```

**Interaction:**
1. **Tap team card** → expands to show member list
2. **Tap member's [×] icon** → confirmation dialog → removes member
3. **Tap [+ Add Member]** → bottom sheet with email input
4. **Tap [▼] chevron** → collapses back to compact view

#### AddMemberBottomSheet (New)

```
┌─────────────────────────────────────────────┐
│ Add Member to Team Alpha                    │
│                                             │
│ ┌─────────────────────────────────────────┐│
│ │ Student Email                           ││
│ │ example@cutm.ac.in                      ││
│ └─────────────────────────────────────────┘│
│                                             │
│ ┌─────────────────────────────────────────┐│
│ │ Display Name                            ││
│ │ Alice Johnson                           ││
│ └─────────────────────────────────────────┘│
│                                             │
│ [Error: Email already in this team]         │  ← Validation error area
│                                             │
│ [Cancel]                    [Add Member]    │
└─────────────────────────────────────────────┘
```

### 3.2 Data Flow

#### Add Member Flow

```
User Action: Tap [+ Add Member] on expanded team card
    ↓
ProjectDetailFragment.showAddMemberDialog(teamId, projectId)
    ↓
AddMemberBottomSheet displayed
    ↓
User enters: email, displayName
    ↓
Validation:
  - Email format (standard email regex)
  - Not empty
  - Not already in team (check TeamEntity.memberEmails)
    ↓
ProjectDetailViewModel.addMemberToTeam(teamId, projectId, email, displayName)
    ↓
ProjectRepository.addMemberToTeam():
  1. Load TeamEntity by teamId
  2. Check email not in memberEmails (revalidate)
  3. Create StudentEntity(email, displayName, teamId, projectId, joinedAt, localDirty=true)
  4. Update TeamEntity.memberEmails += email, localDirty=true
  5. ATOMIC TRANSACTION:
     database.withTransaction {
       studentDao.upsert(student)
       teamDao.upsert(updatedTeam)
     }
     → Both operations succeed or both fail (no partial state)
    ↓
UI updates (Flow observes teams → automatic refresh)
```

#### Remove Member Flow

```
User Action: Tap [×] on member row in expanded team card
    ↓
ProjectDetailFragment.showRemoveMemberConfirmation(teamId, studentEmail, displayName)
    ↓
MaterialAlertDialog: "Remove Alice Johnson from Team Alpha?"
    ↓
User confirms
    ↓
ProjectDetailViewModel.removeMemberFromTeam(teamId, studentEmail)
    ↓
ProjectRepository.removeMemberFromTeam():
  1. Load TeamEntity by teamId
  2. Update TeamEntity.memberEmails -= studentEmail, localDirty=true
  3. ATOMIC TRANSACTION:
     database.withTransaction {
       studentDao.deleteByEmail(studentEmail)
       teamDao.upsert(updatedTeam)
     }
     → Both operations succeed or both fail (no partial state)
    ↓
UI updates (Flow observes teams → automatic refresh)
    ↓
[Side Effect] Any EditTaskBottomSheet observing this team's members:
  - studentRepository.observeStudentsByTeam(teamId) emits updated list (without removed member)
  - updateAssigneeDropdown() detects assignee no longer in list
  - Sets isAssigneeStale = true
  - Shows "alice@cutm.ac.in — no longer on team"
  - Blocks save until reassigned
```

**Transaction Safety Note:** Both operations use `database.withTransaction { }` to ensure atomicity. This prevents the consistency gap where `StudentEntity` could be created/deleted but `TeamEntity.memberEmails` update fails (or vice versa), which would desynchronize the two sources of truth. This matches the pattern established in Phase 3's `deleteProject()` method.

### 3.3 DAO Layer

**StudentDao** (existing - no changes needed):
```kotlin
@Query("SELECT * FROM students WHERE teamId = :teamId")
fun observeByTeam(teamId: String): Flow<List<StudentEntity>>

@Insert(onConflict = OnConflictStrategy.REPLACE)
suspend fun upsert(student: StudentEntity)

@Query("DELETE FROM students WHERE studentEmail = :email")
suspend fun deleteByEmail(email: String)
```

**TeamDao** (add new method):
```kotlin
@Query("SELECT * FROM teams WHERE teamId = :teamId")
suspend fun getById(teamId: String): TeamEntity?

@Insert(onConflict = OnConflictStrategy.REPLACE)
suspend fun upsert(team: TeamEntity)
```

*(Note: `getById()` already exists from Phase 3, just documenting for completeness)*

### 3.4 Repository Layer

**ProjectRepository** (add new methods):
```kotlin
interface ProjectRepository {
    // ... existing methods ...
    
    suspend fun addMemberToTeam(
        teamId: String,
        projectId: String,
        studentEmail: String,
        displayName: String
    ): ApiResult<Unit>
    
    suspend fun removeMemberFromTeam(
        teamId: String,
        studentEmail: String
    ): ApiResult<Unit>
    
    suspend fun isEmailInTeam(
        teamId: String,
        email: String
    ): Boolean
}
```

**ProjectRepositoryImpl**:
```kotlin
override suspend fun addMemberToTeam(
    teamId: String,
    projectId: String,
    studentEmail: String,
    displayName: String
): ApiResult<Unit> = withContext(dispatchers.io) {
    try {
        val team = teamDao.getById(teamId)
            ?: return@withContext ApiResult.Error("Team not found")
        
        // Check duplicate
        if (studentEmail in team.memberEmails) {
            return@withContext ApiResult.Error("Student already in team")
        }
        
        val now = System.currentTimeMillis()
        
        // Create student entity
        val student = StudentEntity(
            studentEmail = studentEmail,
            displayName = displayName,
            teamId = teamId,
            projectId = projectId,
            joinedAt = now,
            localDirty = true
        )
        
        // Update team entity
        val updatedTeam = team.copy(
            memberEmails = team.memberEmails + studentEmail,
            localDirty = true,
            lastModifiedLocal = now
        )
        
        // ATOMIC TRANSACTION: Both operations must succeed or both fail
        database.withTransaction {
            studentDao.upsert(student)
            teamDao.upsert(updatedTeam)
        }
        
        ApiResult.Success(Unit)
    } catch (e: Exception) {
        ApiResult.Error("Failed to add member", e)
    }
}

override suspend fun removeMemberFromTeam(
    teamId: String,
    studentEmail: String
): ApiResult<Unit> = withContext(dispatchers.io) {
    try {
        val team = teamDao.getById(teamId)
            ?: return@withContext ApiResult.Error("Team not found")
        
        val now = System.currentTimeMillis()
        
        // Update team entity (remove email from list)
        val updatedTeam = team.copy(
            memberEmails = team.memberEmails - studentEmail,
            localDirty = true,
            lastModifiedLocal = now
        )
        
        // ATOMIC TRANSACTION: Both operations must succeed or both fail
        database.withTransaction {
            studentDao.deleteByEmail(studentEmail)
            teamDao.upsert(updatedTeam)
        }
        
        ApiResult.Success(Unit)
    } catch (e: Exception) {
        ApiResult.Error("Failed to remove member", e)
    }
}

override suspend fun isEmailInTeam(teamId: String, email: String): Boolean {
    val team = teamDao.getById(teamId) ?: return false
    return email in team.memberEmails
}
```

### 3.5 ViewModel Layer

**ProjectDetailViewModel** (add new methods):
```kotlin
fun addMemberToTeam(
    teamId: String,
    projectId: String,
    email: String,
    displayName: String
) {
    viewModelScope.launch {
        when (val result = projectRepository.addMemberToTeam(teamId, projectId, email, displayName)) {
            is ApiResult.Success -> {
                // Success handled by Flow observation
            }
            is ApiResult.Error -> {
                // Emit error state (handled by UI)
                android.util.Log.e("ProjectDetailVM", "Add member failed: ${result.message}", result.cause)
            }
        }
    }
}

fun removeMemberFromTeam(teamId: String, studentEmail: String) {
    viewModelScope.launch {
        when (val result = projectRepository.removeMemberFromTeam(teamId, studentEmail)) {
            is ApiResult.Success -> {
                // Success handled by Flow observation
            }
            is ApiResult.Error -> {
                android.util.Log.e("ProjectDetailVM", "Remove member failed: ${result.message}", result.cause)
            }
        }
    }
}

fun isEmailInTeam(teamId: String, email: String): Flow<Boolean> = flow {
    emit(projectRepository.isEmailInTeam(teamId, email))
}
```

---

## 4. Validation Rules

### Add Member Validation

| Rule | Check | Error Message |
|------|-------|---------------|
| Email Format | `android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()` | "Invalid email format" |
| Email Not Empty | `email.trim().isNotBlank()` | "Email is required" |
| Display Name Not Empty | `displayName.trim().isNotBlank()` | "Display name is required" |
| Not Already in Team | `email !in team.memberEmails` | "Student already in this team" |
| Team Still Exists | `teamDao.getById(teamId) != null` | "Team not found" |

### Remove Member Validation

| Rule | Check | Error Message |
|------|-------|---------------|
| Team Still Exists | `teamDao.getById(teamId) != null` | "Team not found" |
| Member Exists | `email in team.memberEmails` | (Silent fail - member already gone) |

**Note on "Last Member":**
- **No restriction on removing the last member** — a team with zero members is valid (already tested in Phase 3 TC11)
- Empty teams should display "0 members" on the card
- Tasks assigned to members of now-empty teams become stale-assigned (expected behavior)

---

## 5. UI Component Details

### ProjectDetailFragment Changes

**Add to existing layout:**
1. **Team card expand/collapse toggle** (chevron icon, right of delete icon)
2. **Expandable member list container** (RecyclerView or LinearLayout, initially `visibility=GONE`)
3. **"Add Member" button** at bottom of expanded section

**Rendering Logic:**
```kotlin
private fun renderTeams(teams: List<Team>) {
    binding.teamsContainer.removeAllViews()
    
    teams.forEach { team ->
        val teamCard = createTeamCard(team, expanded = false)  // ← New expanded parameter
        binding.teamsContainer.addView(teamCard)
    }
}

private fun createTeamCard(team: Team, expanded: Boolean): MaterialCardView {
    // ... existing card creation logic ...
    
    // Add expand/collapse toggle
    chevronIcon.setOnClickListener {
        toggleTeamExpansion(team.teamId)
    }
    
    // Add member list (visible only when expanded)
    if (expanded) {
        memberListContainer.visibility = View.VISIBLE
        renderMemberList(team)
    } else {
        memberListContainer.visibility = View.GONE
    }
}

private fun renderMemberList(team: Team) {
    // Query StudentRepository for students by teamId
    // Render each with name, email, remove icon
}
```

### AddMemberBottomSheet (New Component)

**Layout:** `bottom_sheet_add_member.xml`
- TextInputLayout for email (hint: "student@cutm.ac.in")
- TextInputLayout for display name (hint: "Alice Johnson")
- Error TextView (visibility=GONE by default)
- Cancel and "Add Member" buttons

**Kotlin Logic:**
```kotlin
class AddMemberBottomSheet : BottomSheetDialogFragment() {
    private val viewModel: ProjectDetailViewModel by viewModels({ requireParentFragment() })
    
    private val teamId: String by lazy { arguments?.getString(ARG_TEAM_ID) ?: "" }
    private val projectId: String by lazy { arguments?.getString(ARG_PROJECT_ID) ?: "" }
    private val teamName: String by lazy { arguments?.getString(ARG_TEAM_NAME) ?: "" }
    
    private fun validateAndAddMember() {
        val email = binding.emailInput.text.toString().trim()
        val displayName = binding.nameInput.text.toString().trim()
        
        when {
            email.isEmpty() -> showError("Email is required")
            !Patterns.EMAIL_ADDRESS.matcher(email).matches() -> showError("Invalid email format")
            displayName.isEmpty() -> showError("Display name is required")
            else -> {
                viewModel.addMemberToTeam(teamId, projectId, email, displayName)
                dismiss()
            }
        }
    }
}
```

---

## 6. Navigation Flow

```
TeacherHomeFragment
    ↓ (tap project card)
ProjectDetailFragment
    ↓ (tap team card to expand)
[Team card expands inline, shows member list]
    ↓ (tap [+ Add Member])
AddMemberBottomSheet
    ↓ (enter email, name, tap Add)
[Bottom sheet dismisses, team card updates via Flow]
```

**No new navigation destinations** — all operations happen in ProjectDetailFragment with bottom sheets for input.

---

## 7. Test Scenarios

### TC1: Add Member to Empty Team
**Setup:** Team with 0 members  
**Action:** Expand team, tap [+ Add Member], enter alice@cutm.ac.in / "Alice Test"  
**Expected:**
- Member added successfully
- Team card shows "1 member"
- Expanded view shows Alice in member list
- StudentEntity created with correct teamId, projectId

### TC2: Add Member to Team with Existing Members
**Setup:** Team with 2 members (Alice, Bob)  
**Action:** Add carol@cutm.ac.in / "Carol Test"  
**Expected:**
- Member added successfully
- Team card shows "3 members"
- Member list shows Alice, Bob, Carol in order
- TeamEntity.memberEmails = [alice@..., bob@..., carol@...]

### TC3: Add Duplicate Member (Validation)
**Setup:** Team with Alice (alice@cutm.ac.in)  
**Action:** Try to add alice@cutm.ac.in again  
**Expected:**
- Error: "Student already in this team"
- No duplicate StudentEntity created
- TeamEntity.memberEmails unchanged

### TC4: Add Member with Invalid Email (Validation)
**Setup:** Any team  
**Action:** Try to add "not-an-email" / "Test User"  
**Expected:**
- Error: "Invalid email format"
- No StudentEntity created

### TC5: Add Member with Empty Display Name (Validation)
**Setup:** Any team  
**Action:** Try to add "alice@cutm.ac.in" / "" (empty name)  
**Expected:**
- Error: "Display name is required"
- No StudentEntity created

### TC6: Remove Member from Team (No Tasks Assigned)
**Setup:** Team with Alice, Bob  
**Action:** Expand team, tap [×] on Alice, confirm  
**Expected:**
- Alice removed
- Team card shows "1 member"
- StudentEntity for Alice deleted
- TeamEntity.memberEmails = [bob@...]
- No errors

### TC7: Remove Member with Assigned Task (Stale-Assignee Trigger)
**Setup:**
1. Team with Alice, Bob
2. Task assigned to Alice
**Action:** Remove Alice from team  
**Expected:**
- Alice removed from team
- Open task in EditTaskBottomSheet
- Assignee dropdown shows: "alice@cutm.ac.in — no longer on team"
- isAssigneeStale = true
- Cannot save task without selecting new assignee or "Unassigned"

### TC8: Remove Last Member (Zero-Member Team)
**Setup:** Team with only Alice  
**Action:** Remove Alice  
**Expected:**
- Team card shows "0 members"
- Team still exists (not deleted)
- Expanded view shows empty list + "Add Member" button

### TC9: Remove Member Then Re-Add Same Email
**Setup:** Team with Alice  
**Action:**
1. Remove Alice
2. Add alice@cutm.ac.in / "Alice Johnson" again
**Expected:**
- Re-add succeeds (no "already exists" error)
- New StudentEntity created with fresh `joinedAt` timestamp
- TeamEntity.memberEmails contains alice@... again

### TC10: Concurrent Modification (Edge Case)
**Setup:** Two devices/sessions editing same team  
**Action:**
- Device A: Remove Alice
- Device B: Assign task to Alice (before sync)
**Expected:**
- Device B's assign succeeds locally
- After sync, EditTaskBottomSheet detects stale assignee (Alice not in team)
- localDirty flags ensure correct sync resolution

---

## 8. String Resources

```xml
<!-- Member Management -->
<string name="add_member_title">Add Member to %s</string>  <!-- Team name -->
<string name="add_member_hint_email">Student Email</string>
<string name="add_member_hint_name">Display Name</string>
<string name="add_member_button">Add Member</string>
<string name="add_member_success">Member added</string>

<string name="remove_member_confirm_title">Remove Member?</string>
<string name="remove_member_confirm_message">Remove %s from this team? Tasks assigned to them will need reassignment.</string>  <!-- Display name -->
<string name="remove_member_button">Remove</string>
<string name="remove_member_success">Member removed</string>

<string name="member_list_empty">No members yet</string>

<!-- Validation Errors -->
<string name="error_email_required">Email is required</string>
<string name="error_email_invalid">Invalid email format</string>
<string name="error_name_required">Display name is required</string>
<string name="error_member_duplicate">Student already in this team</string>
<string name="error_team_not_found">Team not found</string>
</string>
```

---

## 9. Open Questions

### Q1: Should removing a member also delete their task assignments?
**Options:**
- **A)** Leave tasks intact, mark as stale-assigned (current design)
- **B)** Auto-unassign tasks (set assigneeEmail = "")
- **C)** Block removal if member has assigned tasks

**Recommendation:** **Option A** (leave intact, mark stale) — matches team deletion behavior, gives teacher explicit control over reassignment, avoids silent data loss.

### Q2: Should we validate email domain (@cutm.ac.in)?
**Options:**
- **A)** No domain restriction (accept any valid email)
- **B)** Require @cutm.ac.in domain
- **C)** Configurable domain whitelist

**Recommendation:** **Option A** (no restriction) — flexibility for testing, guest collaborators, or non-institutional emails. Can add domain validation later if needed.

### Q3: Member list ordering in expanded view?
**Options:**
- **A)** Alphabetical by display name
- **B)** Chronological by joinedAt (oldest first)
- **C)** Order in TeamEntity.memberEmails (insertion order)

**Recommendation:** **Option A** (alphabetical) — most intuitive for scanning, especially with larger teams.

### Q4: What happens to removed member's StudentEntity if they're in multiple teams?
**Current Model Issue:** StudentEntity has `teamId` as a single field, not a list. A student can only be in one team per project.

**Options:**
- **A)** Keep current model (one team per student per project) — simpler, matches Sheets data model
- **B)** Refactor to many-to-many relationship (StudentEntity can have multiple teamIds)

**Recommendation:** **Option A** (keep current model) — design doc for Sheets sync likely assumes one team per student. Multi-team support is a larger architectural change that should be its own phase.

### Q5: Should expanded team state persist across navigation?
**Options:**
- **A)** Collapse all teams when navigating away (reset on return)
- **B)** Remember expanded state per session (in ViewModel)
- **C)** Persist expanded state to preferences

**Recommendation:** **Option A** (reset) — simpler, avoids stale state, teams expand quickly on tap.

---

## 10. Implementation Plan

### Phase 4.1: DAO and Repository Layer
- Add `StudentDao.deleteByEmail()`
- Add `ProjectRepository.addMemberToTeam()`
- Add `ProjectRepository.removeMemberFromTeam()`
- Add `ProjectRepository.isEmailInTeam()`
- Implement repository methods with validation

### Phase 4.2: ViewModel Layer
- Add member management methods to `ProjectDetailViewModel`
- Wire Flow observations for real-time updates

### Phase 4.3: UI - Add Member
- Create `AddMemberBottomSheet` layout and logic
- Add "Add Member" button to expanded team card
- Implement validation and error display

### Phase 4.4: UI - Remove Member
- Add remove icons to member list items
- Implement confirmation dialog
- Wire to ViewModel

### Phase 4.5: UI - Expand/Collapse
- Add chevron toggle to team cards
- Implement expand/collapse animation
- Render member list in expanded state
- Observe students Flow for each team

### Phase 4.6: String Resources
- Add all member management strings

### Phase 4.7: Testing
- Manually verify TC1-TC10
- Confirm stale-assignee detection triggers on member removal (TC7)

---

## 11. Risks and Mitigations

| Risk | Impact | Mitigation |
|------|--------|------------|
| TeamEntity.memberEmails and StudentEntity records get out of sync | HIGH - assignee dropdown shows wrong members | **MITIGATED:** Both operations wrapped in `database.withTransaction { }` - atomic commit ensures consistency |
| Removing member doesn't trigger stale-assignee detection | HIGH - broken validation | **VERIFIED:** Code trace (Section 3.0) confirms Flow re-emission and stale detection work correctly for N→N-1 member removal |
| Duplicate StudentEntity with same email in different teams | MEDIUM - data inconsistency | Confirm one-team-per-student constraint in data model; add uniqueness check |
| User removes member, immediately reassigns task, then undo | LOW - UI race condition | Not supporting undo in this phase; consider for future |
| Very large teams (50+ members) cause performance issues | LOW - unlikely in classroom context | Test with 20-30 members; lazy-load if needed |

---

## 12. Future Enhancements (Out of Scope)

- **Bulk Operations:** Add/remove multiple members at once via CSV upload
- **Member Roles:** Designate team lead, assign permissions
- **Invite Flow:** Send email invitations with confirmation links
- **Edit Member Details:** Change display name, email after creation
- **Member Activity Log:** Track when members joined, were removed, etc.
- **Multi-Team Support:** Allow students to be in multiple teams per project
- **Undo/Redo:** Reverse accidental member removal

---

## Approval Checklist

- [ ] UI mockups reviewed and approved
- [ ] Data flow matches existing patterns (Phase 3 deletion)
- [ ] Validation rules cover edge cases
- [ ] Test scenarios comprehensive
- [ ] Open questions resolved
- [ ] No conflicts with existing features (stale-assignee detection, team deletion)
- [ ] String resources complete
- [ ] Implementation plan clear and sequenced

**Awaiting approval to proceed with implementation.**
