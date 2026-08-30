# Phase 2: Team Creation - Design Document

## Executive Summary

This document details the design for **Team Creation** functionality in TeamPulse. Teams are created within the context of a specific project, accessible from a new **Project Detail Screen**. This phase introduces:

1. **New Screen**: `ProjectDetailFragment` — displays project metadata, team list, and action buttons
2. **New Bottom Sheet**: `CreateTeamBottomSheet` — modal form for creating teams within a project
3. **Repository Extension**: `ProjectRepository.createTeam()` — persistence layer for team creation
4. **Navigation**: Deep-link from project card → Project Detail screen

---

## 1. User Flow & Navigation

### 1.1 Entry Point

**From**: `TeacherHomeFragment` (existing)  
**Action**: User taps a project card  
**To**: `ProjectDetailFragment` (new)

```
TeacherHomeFragment
    └─> [Tap Project Card]
        └─> ProjectDetailFragment (projectId passed as nav arg)
            └─> [Tap "+ Create Team" button]
                └─> CreateTeamBottomSheet
                    └─> [Fill form + Save]
                        └─> Toast confirmation → Dismiss → remain on ProjectDetailFragment
```

### 1.2 Navigation Graph Changes

**File**: `app/src/main/res/navigation/nav_graph.xml`

Add new destination:

```xml
<fragment
    android:id="@+id/projectDetailFragment"
    android:name="com.cutm.TeamPulse.ui.teacher.ProjectDetailFragment"
    android:label="Project Details"
    tools:layout="@layout/fragment_project_detail">
    <argument
        android:name="projectId"
        app:argType="string" />
</fragment>
```

Update existing `teacherHomeFragment`:

```xml
<fragment
    android:id="@+id/teacherHomeFragment"
    ...>
    <action
        android:id="@+id/action_teacherHome_to_projectDetail"
        app:destination="@id/projectDetailFragment"
        app:enterAnim="@anim/slide_in_right"
        app:exitAnim="@anim/fade_out"
        app:popEnterAnim="@anim/fade_in"
        app:popExitAnim="@anim/slide_out_right" />
</fragment>
```

---

## 2. UI Design: ProjectDetailFragment

### 2.1 Layout Structure (`fragment_project_detail.xml`)

```
┌─────────────────────────────────────┐
│ [← Back]   Project Name             │  ← Toolbar
├─────────────────────────────────────┤
│ NestedScrollView                    │
│                                     │
│ ┌─────────────────────────────────┐│
│ │ PROJECT INFO CARD               ││
│ │                                 ││
│ │ Due Date: Mar 15, 2027          ││
│ │ Status: Active                  ││
│ │ Created: Jan 10, 2027           ││
│ │ GitHub: [placeholder]           ││
│ └─────────────────────────────────┘│
│                                     │
│ ┌─────────────────────────────────┐│
│ │ Teams (3)        [+ Create Team]││ ← Header + Button
│ └─────────────────────────────────┘│
│                                     │
│ ┌─────────────────────────────────┐│
│ │ Team Alpha              3 members││ ← Team Card (non-interactive)
│ │ ○ alice@cutm.ac.in              ││
│ │ ○ bob@cutm.ac.in                ││
│ │ ○ carol@cutm.ac.in              ││
│ └─────────────────────────────────┘│
│                                     │
│ ┌─────────────────────────────────┐│
│ │ Team Beta               2 members││
│ │ ○ dave@cutm.ac.in               ││
│ │ ○ eve@cutm.ac.in                ││
│ └─────────────────────────────────┘│
│                                     │
│ ┌─────────────────────────────────┐│
│ │ Team Gamma             No members││ ← Zero-member team
│ │ [Member area hidden]            ││
│ └─────────────────────────────────┘│
│                                     │
│ [... more teams ...]                │
│                                     │
│ [OR if no teams yet:]               │
│                                     │
│ ┌─────────────────────────────────┐│
│ │   No teams yet                  ││ ← Empty state
│ │   Tap "+ Create Team" to start  ││
│ └─────────────────────────────────┘│
└─────────────────────────────────────┘
```

### 2.2 Key UI Components

| Component | Type | Purpose |
|-----------|------|---------|
| Toolbar | MaterialToolbar | Back button + project name |
| Project Info Card | MaterialCardView | Display project metadata (read-only) |
| Teams Header | TextView + Button | Section title + "+ Create Team" action |
| Teams List | LinearLayout (programmatic) | Dynamic team cards |
| Empty State | MaterialCardView | Shown when no teams exist |
| Team Card | MaterialCardView | Team name + member count<br>• 0 members: "No members" label, member list hidden<br>• 1-3 members: Show all emails<br>• 4+ members: Show 3 emails + "+N more" |

### 2.3 Visual Specifications

- **Card Elevation**: 2dp (consistent with project cards)
- **Spacing**: 16dp margins, 12dp internal padding
- **Typography**: 
  - Section headers: `textAppearanceTitleMedium`
  - Team name: `textAppearanceBodyLarge` + bold
  - Member emails: `textAppearanceBodyMedium`, `colorOnSurfaceVariant`
- **Colors**: Material 3 surface/primary scheme (matches existing app theme)

### 2.4 Interaction States

- **Team Card**: Non-interactive (no ripple, no click listener) for this phase
  - **Rationale**: Avoids placeholder toast and reinforces that team detail/management is not yet implemented
  - **Future**: Will navigate to team detail screen when task assignment is built
- **Create Team Button**: Primary button style, same as FAB pattern in TeacherHomeFragment
- **Empty State**: Non-interactive, instructional only

### 2.5 Zero-Member Team Card Rendering

**Scenario**: Team created via CreateTeamBottomSheet (initial state: `memberEmails = emptyList()`)

**Visual Treatment**:
```
┌─────────────────────────────────┐
│ Team Gamma             No members│  ← Header: team name + "No members"
│                                  │  ← Member list area: hidden/collapsed
└─────────────────────────────────┘
```

**Implementation Details**:
- **Header**: Team name (left) + "No members" (right, `colorOnSurfaceVariant`)
- **Member List Container**: Visibility set to `GONE` when `memberEmails.isEmpty()`
- **Card Height**: Collapses to single-line header height (no wasted space)
- **Styling**: Identical card elevation/padding as populated teams (no visual distinction)

**Why This Approach**:
- Clear communication that team exists but has no members yet
- Consistent card treatment (no special "empty team" card style)
- Avoids ambiguity ("0 members" could imply a count bug; "No members" is clearer)
- Gracefully handles state until member assignment is implemented

---

## 3. UI Design: CreateTeamBottomSheet

### 3.1 Layout Structure (`bottom_sheet_create_team.xml`)

```
┌─────────────────────────────────────┐
│ Create Team                         │  ← Title
├─────────────────────────────────────┤
│                                     │
│ Team Name *                         │  ← TextInputLayout
│ ┌─────────────────────────────────┐│
│ │ [Text input field]              ││
│ └─────────────────────────────────┘│
│                                     │
│ ⚠ [Error message area]             │  ← Initially hidden
│                                     │
│ [Cancel]              [Create Team] │  ← Action buttons
└─────────────────────────────────────┘
```

### 3.2 Form Fields

| Field | Type | Required | Validation Rules |
|-------|------|----------|------------------|
| Team Name | TextInputEditText | Yes | • Not blank<br>• Max 100 characters<br>• No uniqueness check (deferred) |

**Note**: Member assignment is **intentionally omitted** in this phase. Teams are created empty; member assignment happens separately (future phase: task assignment UI will link students → teams).

### 3.3 Button States

| Button | State | Behavior |
|--------|-------|----------|
| Cancel | Always enabled | Dismiss bottom sheet, no action |
| Create Team | Enabled (default) | Validate → Create → Toast → Dismiss |
| Create Team | Disabled during save | Show loading state, prevent double-tap |

### 3.4 Validation Rules

1. **On Field Change**: Clear error message + TextInputLayout error
2. **On Save Click**:
   - **Team Name blank** → Show error: "Team name is required"
   - **Team Name > 100 chars** → Show error: "Team name cannot exceed 100 characters"
   - **Valid** → Proceed to create team

### 3.5 Error Handling

| Scenario | UI Response |
|----------|-------------|
| Validation failure | Red error text below field + TextInputLayout error |
| Session expired | Error message: "Session expired. Please sign in again." |
| Database error | Error message: "[error details from ApiResult.Error]" |
| Success | Toast: "Team created successfully" → Dismiss |

---

## 4. Data Flow

### 4.1 Architecture Layers

```
┌─────────────────────────────────────────────────────────────┐
│ UI Layer                                                    │
│                                                             │
│  ProjectDetailFragment                                      │
│    ├─> Observes: ProjectDetailViewModel.projectWithTeams   │
│    └─> Action: Opens CreateTeamBottomSheet                  │
│                                                             │
│  CreateTeamBottomSheet                                      │
│    └─> Calls: ProjectRepository.createTeam()               │
│                                                             │
└─────────────────────────────────────────────────────────────┘
                            ↓
┌─────────────────────────────────────────────────────────────┐
│ Domain Layer                                                │
│                                                             │
│  ProjectRepository                                          │
│    └─> createTeam(teamId, projectId, teamName)             │
│                                                             │
└─────────────────────────────────────────────────────────────┘
                            ↓
┌─────────────────────────────────────────────────────────────┐
│ Data Layer                                                  │
│                                                             │
│  ProjectRepositoryImpl                                      │
│    ├─> Insert: TeamEntity via TeamDao                      │
│    └─> Enqueue: SyncQueueEntity (stubbed)                  │
│                                                             │
└─────────────────────────────────────────────────────────────┘
```

### 4.2 State Flow Diagram

```
┌─────────────────────┐
│ User taps project   │
│ card on Home screen │
└──────────┬──────────┘
           │
           ▼
┌──────────────────────────────────┐
│ ProjectDetailFragment.onCreate() │
│  • Parse projectId nav arg       │
│  • Initialize ViewModel          │
└──────────┬───────────────────────┘
           │
           ▼
┌─────────────────────────────────────────┐
│ ViewModel.init(projectId)               │
│  • observeProject(projectId)            │
│  • observeTeamsForProject(projectId)    │
│  • Combine into projectWithTeams Flow   │
└──────────┬──────────────────────────────┘
           │
           ▼
┌────────────────────────────────────┐
│ Fragment observes projectWithTeams │
│  • Render project info card        │
│  • Render team list or empty state │
└────────────────────────────────────┘
           │
           ▼ (User taps "+ Create Team")
┌───────────────────────────────────┐
│ CreateTeamBottomSheet.show()      │
│  • User fills team name           │
│  • User taps "Create Team"        │
└──────────┬────────────────────────┘
           │
           ▼
┌────────────────────────────────────┐
│ Validation                         │
│  • Team name not blank?            │
│  • Team name ≤ 100 chars?          │
└──────────┬─────────────────────────┘
           │
     ┌─────┴─────┐
     │           │
  Invalid      Valid
     │           │
     ▼           ▼
┌─────────┐  ┌────────────────────────────┐
│ Show    │  │ ProjectRepository          │
│ error   │  │  .createTeam()             │
└─────────┘  │   • Generate UUID          │
             │   • Insert TeamEntity      │
             │   • Enqueue sync (stubbed) │
             │   • Return ApiResult       │
             └──────────┬─────────────────┘
                        │
                  ┌─────┴─────┐
                  │           │
               Success      Error
                  │           │
                  ▼           ▼
          ┌─────────────┐  ┌─────────┐
          │ Toast msg   │  │ Show    │
          │ Dismiss     │  │ error   │
          └─────────────┘  └─────────┘
```

---

## 5. Repository & Data Layer

### 5.1 Repository Interface Extension

**File**: `app/src/main/java/com/cutm/TeamPulse/domain/repository/ProjectRepository.kt`

```kotlin
interface ProjectRepository {
    // ... existing methods ...

    /**
     * Observe all teams for a specific project
     */
    fun observeTeamsForProject(projectId: String): Flow<List<Team>>

    /**
     * Create a new team within a project
     * 
     * @param teamId Unique team identifier (UUID)
     * @param projectId Parent project ID
     * @param teamName Team name (1-100 chars)
     * @return ApiResult.Success on success, ApiResult.Error on failure
     */
    suspend fun createTeam(
        teamId: String,
        projectId: String,
        teamName: String
    ): ApiResult<Unit>
}
```

### 5.2 Implementation Pattern

**File**: `app/src/main/java/com/cutm/TeamPulse/data/repository/ProjectRepositoryImpl.kt`

```kotlin
override fun observeTeamsForProject(projectId: String): Flow<List<Team>> {
    return teamDao.observeByProject(projectId)
        .map { entities -> entities.map { it.toTeam() } }
}

override suspend fun createTeam(
    teamId: String,
    projectId: String,
    teamName: String
): ApiResult<Unit> = withContext(dispatchers.io) {
    try {
        val now = System.currentTimeMillis()
        
        val teamEntity = TeamEntity(
            teamId = teamId,
            projectId = projectId,
            teamName = teamName,
            memberEmails = emptyList(), // Empty on creation
            createdAt = now,
            localDirty = true,
            lastModifiedLocal = now
        )

        teamDao.insert(teamEntity)

        // Enqueue sync (stubbed - no actual Sheets call yet)
        syncQueueDao.insert(
            SyncQueueEntity(
                id = UUID.randomUUID().toString(),
                entityType = "team",
                entityId = teamId,
                operation = "insert",
                enqueuedAt = now
            )
        )

        ApiResult.Success(Unit)
    } catch (e: Exception) {
        Log.e("ProjectRepository", "Failed to create team", e)
        ApiResult.Error("Failed to create team: ${e.message}")
    }
}
```

### 5.3 DAO Extension

**File**: `app/src/main/java/com/cutm/TeamPulse/data/local/dao/TeamDao.kt`

Add method:

```kotlin
@Query("SELECT * FROM teams WHERE projectId = :projectId ORDER BY createdAt ASC")
fun observeByProject(projectId: String): Flow<List<TeamEntity>>
```

---

## 6. ViewModel Design

### 6.1 New ViewModel: ProjectDetailViewModel

**File**: `app/src/main/java/com/cutm/TeamPulse/ui/teacher/ProjectDetailViewModel.kt`

```kotlin
@HiltViewModel
class ProjectDetailViewModel @Inject constructor(
    private val projectRepository: ProjectRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val projectId: String = savedStateHandle.get<String>("projectId")
        ?: throw IllegalArgumentException("projectId required")

    val project: StateFlow<Project?> = projectRepository
        .observeProject(projectId)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = null
        )

    val teams: StateFlow<List<Team>> = projectRepository
        .observeTeamsForProject(projectId)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )
}
```

**Design Notes**:
- Uses `SavedStateHandle` to receive nav arg `projectId`
- Exposes two separate StateFlows for UI consumption
- No business logic in ViewModel — repository handles all data operations
- ViewModel survives configuration changes (rotation), but form state in CreateTeamBottomSheet does not (accepted limitation)

---

## 7. Testing Strategy

### 7.1 Manual Test Scenarios

| Test ID | Scenario | Steps | Expected Result |
|---------|----------|-------|-----------------|
| T1 | Happy path: Create team | 1. Tap project card<br>2. Tap "+ Create Team"<br>3. Enter "Team Alpha"<br>4. Tap "Create Team" | Toast: "Team created successfully"<br>Team appears in list |
| T2 | Validation: Empty name | 1. Open create team sheet<br>2. Leave name blank<br>3. Tap "Create Team" | Error: "Team name is required" |
| T3 | Validation: Name too long | 1. Open create team sheet<br>2. Enter 101 character name<br>3. Tap "Create Team" | Error: "Team name cannot exceed 100 characters" |
| T4 | Cancel action | 1. Open create team sheet<br>2. Enter team name<br>3. Tap "Cancel" | Sheet dismisses, no team created |
| T5 | Empty state display | 1. Tap project with no teams | "No teams yet" card displayed |
| T6 | Multiple teams display | 1. Create 3+ teams<br>2. Return to project detail | All teams listed in creation order |
| T7 | Back navigation | 1. From project detail<br>2. Tap back button | Returns to TeacherHomeFragment |
| T8 | Rotation handling | 1. Open create team sheet<br>2. Type team name<br>3. Rotate device | Form state lost (accepted limitation) |
| T9 | Session expired handling | 1. Force session expiry<br>2. Try to create team | Error: "Session expired" |
| T10 | Team card display (>3 members) | 1. Manually seed team with 5 members<br>2. View project detail | Show 3 members + "+2 more" |
| T11 | Team card display (zero members) | 1. Create team via UI (no member assignment yet)<br>2. Verify rendering | Team card shows:<br>• Header: "Team [Name] · No members"<br>• Member list area: hidden (GONE)<br>• Card height: collapsed to single line |

### 7.2 Debug Seed Utility Extension

**File**: `app/src/main/java/com/cutm/TeamPulse/debug/DebugSeedUtil.kt`

Add method:

```kotlin
suspend fun seedTeamsForProject(
    projectId: String,
    teamDao: TeamDao,
    studentDao: StudentDao
) {
    val now = System.currentTimeMillis()
    
    val teams = listOf(
        TeamEntity(
            teamId = "debug-team-alpha",
            projectId = projectId,
            teamName = "Team Alpha",
            memberEmails = listOf(
                "alice@cutm.ac.in",
                "bob@cutm.ac.in",
                "carol@cutm.ac.in"
            ),
            createdAt = now - 10000,
            localDirty = false,
            lastModifiedLocal = now
        ),
        TeamEntity(
            teamId = "debug-team-beta",
            projectId = projectId,
            teamName = "Team Beta",
            memberEmails = listOf(
                "dave@cutm.ac.in",
                "eve@cutm.ac.in"
            ),
            createdAt = now - 5000,
            localDirty = false,
            lastModifiedLocal = now
        )
    )

    teams.forEach { teamDao.insert(it) }

    // Optionally seed matching StudentEntity records
    // (if StudentEntity is used for display — TBD based on existing architecture)
}
```

---

## 8. Validation Rules (Detailed)

### 8.1 Team Name Validation

| Rule | Implementation | Error Message | Error Location |
|------|----------------|---------------|----------------|
| Required | `name.isNullOrBlank()` | "Team name is required" | TextInputLayout.error + error TextView |
| Max length | `name.length > 100` | "Team name cannot exceed 100 characters" | TextInputLayout.error + error TextView |
| Uniqueness | **DEFERRED** | N/A | N/A |

### 8.2 Validation Timing

- **On Text Change**: Clear all errors
- **On Save**: Run all validations, show first error only
- **No Real-Time Validation**: Unlike date picker in CreateProjectBottomSheet, team name has no real-time constraints

### 8.3 Session Validation

- Check `userSession.first { it != null }` before repository call
- If null → show error: "Session expired. Please sign in again."
- Follows same pattern as `CreateProjectBottomSheet.createProject()`

---

## 9. UI Copy & Strings

### 9.1 New String Resources

**File**: `app/src/main/res/values/strings.xml`

```xml
<!-- Project Detail Screen -->
<string name="project_detail_title">Project Details</string>
<string name="project_detail_due_date">Due: %1$s</string>
<string name="project_detail_status">Status: %1$s</string>
<string name="project_detail_created">Created: %1$s</string>
<string name="project_detail_github">GitHub: %1$s</string>
<string name="project_detail_teams_header">Teams (%1$d)</string>
<string name="project_detail_create_team">Create Team</string>
<string name="project_detail_empty_teams">No teams yet</string>
<string name="project_detail_empty_teams_hint">Tap "+ Create Team" to start organizing your project</string>
<string name="project_detail_team_members">%1$d members</string>
<string name="project_detail_team_no_members">No members</string>
<string name="project_detail_team_more_members">+%1$d more</string>

<!-- Create Team Bottom Sheet -->
<string name="create_team_title">Create Team</string>
<string name="create_team_name_label">Team Name</string>
<string name="create_team_name_hint">Enter team name</string>
<string name="create_team_button">Create Team</string>
<string name="create_team_cancel">Cancel</string>

<!-- Validation Errors -->
<string name="create_team_error_no_name">Team name is required</string>
<string name="create_team_error_name_too_long">Team name cannot exceed 100 characters</string>
<string name="create_team_error_session_expired">Session expired. Please sign in again.</string>

<!-- Success Messages -->
<string name="create_team_success">Team created successfully</string>
```

---

## 10. Edge Cases & Deferred Items

### 10.1 Handled in This Phase

| Edge Case | Handling |
|-----------|----------|
| No teams exist yet | Show empty state card with instructional text |
| Team with zero members | Show "No members" label, hide member list container |
| Project not found | Show error state (project == null in ViewModel) |
| Session expired | Validation error before repository call |
| Database error during insert | ApiResult.Error → show error message |
| Team with >3 members | Show first 3 + "+N more" label |

### 10.2 Explicitly Deferred (Do Not Implement)

| Item | Reason | Future Phase |
|------|--------|--------------|
| Team name uniqueness check | Not required per design review | TBD |
| Member assignment during creation | Members added via task assignment UI | Phase 3+ |
| Team editing/deletion | CRUD completion tracked separately | Phase 3+ |
| Team detail/task view | Requires task assignment architecture | Phase 3+ |
| Form state preservation on rotation | Accepted cross-cutting limitation | Future SavedStateHandle refactor |
| Live Sheets sync | Stubbed repository layer only | Future sync milestone |

### 10.3 Known Limitations (Accepted)

- **Rotation**: Typed team name lost on rotation (no SavedStateHandle in bottom sheet)
- **No Undo**: Team creation is immediate, no confirmation dialog
- **No Inline Editing**: Team name cannot be edited from detail screen (requires separate edit feature)

---

## 11. Implementation Checklist

### Phase 2a: Data Layer (Foundation)
- [ ] Extend `ProjectRepository` interface with `observeTeamsForProject()` and `createTeam()`
- [ ] Implement methods in `ProjectRepositoryImpl`
- [ ] Add `TeamDao.observeByProject()` query
- [ ] Extend debug seed utility with `seedTeamsForProject()`
- [ ] Add string resources to `strings.xml`

### Phase 2b: UI Layer (Project Detail)
- [ ] Create `fragment_project_detail.xml` layout
- [ ] Create `ProjectDetailViewModel`
- [ ] Create `ProjectDetailFragment`
- [ ] Implement project info card rendering
- [ ] Implement team list rendering (dynamic cards)
- [ ] Implement empty state logic
- [ ] Add navigation action in `nav_graph.xml`
- [ ] Update `TeacherHomeFragment` to navigate on project card tap

### Phase 2c: UI Layer (Team Creation)
- [ ] Create `bottom_sheet_create_team.xml` layout
- [ ] Create `CreateTeamBottomSheet` fragment
- [ ] Implement form validation
- [ ] Wire up repository call
- [ ] Implement error handling
- [ ] Implement success toast + dismiss
- [ ] Connect "+ Create Team" button to show bottom sheet

### Phase 2d: Testing & Polish
- [ ] Manual test all scenarios from section 7.1
- [ ] Verify empty state ↔ list state transitions
- [ ] Verify back navigation
- [ ] Test with debug seed data
- [ ] Verify rotation behavior (accepted limitation)
- [ ] Code review: consistent naming, no hardcoded strings
- [ ] Remove debug seed call from `ProjectDetailFragment` (if added for testing)

---

## 12. Open Questions for Review

1. ~~**Team Card Interaction**: Should tapping a team card do anything in this phase, or remain non-functional until task assignment is built?~~
   - **RESOLVED**: Cards are non-interactive (no ripple, no click listener) to avoid placeholder messaging

2. ~~**Member Display**: If `StudentEntity.teamId` links students to teams, should we join and display student names instead of emails?~~
   - **RESOLVED**: Stick with `TeamEntity.memberEmails` for now (simpler, no JOIN needed)

3. ~~**Project Info Card Actions**: Should project metadata be editable from this screen?~~
   - **RESOLVED**: No, make it read-only for this phase (editing is a separate feature)

4. ~~**Sync Queue Details**: Should team creation enqueue a specific Sheets API operation, or generic "team/insert"?~~
   - **RESOLVED**: Generic "team/insert" — sync implementation will map this later

5. ~~**Animation**: Should team cards have entrance animations like project cards?~~
   - **RESOLVED**: No entrance animations. Rationale: Reduces complexity and avoids reintroducing visibility-toggle animation bugs (same class as recently fixed TeacherHomeFragment animateEntrance/crossFade interaction). Keep this reasoning for future reference if animations are proposed later.

### Confirmation Note: Debug Seed `localDirty` Flag

**Observation**: Debug seed teams use `localDirty = false` while real `createTeam()` uses `localDirty = true`

**Confirmation**: This is **intentional**, consistent with existing debug seed philosophy:
- **Seeded data** (`localDirty = false`): Treated as pre-synced test data, skipped by sync queue processing
- **User-created data** (`localDirty = true`): Marked for eventual Sheets sync (stubbed for now)
- **Precedent**: Existing `ProjectEntity` seed data in `DebugSeedUtil` also uses `localDirty = false`

**Conclusion**: Not an oversight; maintains architectural consistency.

---

## 13. Success Criteria

Phase 2 is complete when:

✅ User can navigate from any project card to Project Detail screen  
✅ Project Detail screen displays project metadata (read-only)  
✅ Project Detail screen lists all teams for the project  
✅ Project Detail screen shows "No teams yet" when applicable  
✅ Zero-member teams display "No members" with collapsed member list area  
✅ User can tap "+ Create Team" to open bottom sheet  
✅ User can create a team with valid name (1-100 chars)  
✅ Validation errors display correctly for invalid input  
✅ Team creation shows Toast confirmation  
✅ Newly created team appears in list immediately  
✅ All manual test scenarios (T1-T11) pass  
✅ No hardcoded strings remain in code  
✅ Debug seed utility supports team creation  
✅ Code follows existing architecture patterns (repository, ViewModel, Flow)

---

**Document Version**: 1.1  
**Date**: 2027-08-30  
**Status**: Approved - Ready for Implementation  
**Revision Notes**: Added zero-member team card specification (Section 2.5), test scenario T11, resolved all open questions  
**Next Step**: Implement Phase 2a → 2b → 2c → 2d in sequence
