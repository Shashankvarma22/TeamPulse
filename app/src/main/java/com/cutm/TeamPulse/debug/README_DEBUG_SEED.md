# DEBUG SEED UTILITY

**⚠️ DEBUG ONLY - REMOVE BEFORE PRODUCTION RELEASE**

## Purpose

Provides test data for validating task assignment stale-state scenarios (Cases 2 & 6).

## Files

- `DebugSeedUtil.kt` - Core seeding logic (gated by `BuildConfig.DEBUG`)
- `DebugSeedFragment.kt` - UI for triggering seed operations
- `fragment_debug_seed.xml` - Layout for debug fragment

## How to Access

### Option 1: Direct Navigation (Simplest)

Add this temporary code anywhere in the app (e.g., in `TeacherHomeFragment.onViewCreated()`):

```kotlin
// DEBUG ONLY - Remove after testing
if (BuildConfig.DEBUG) {
    view.postDelayed({
        findNavController().navigate(R.id.debugSeedFragment)
    }, 1000)
}
```

### Option 2: Fragment Transaction

From any Fragment:

```kotlin
if (BuildConfig.DEBUG) {
    parentFragmentManager.beginTransaction()
        .replace(R.id.nav_host_fragment, DebugSeedFragment())
        .addToBackStack(null)
        .commit()
}
```

### Option 3: Deep Link via adb

```bash
adb shell am start -a android.intent.action.VIEW \
    -d "teampulse://debug_seed"
```

(Requires adding deep link intent filter to manifest - not included to avoid production code)

## Usage Flow

1. **Seed Test Data**
   - Tap "Seed Test Data" button
   - Creates:
     - Project: `debug-project-1`
     - Team: `debug-team-1`
     - Students: Alice Johnson, Bob Smith

2. **Create Task** (in main app)
   - Navigate to the seeded project
   - Create a task
   - Assign it to Alice

3. **Test Case 2 - Stale Assignee**
   - Return to Debug Seed Fragment
   - Tap "Remove Alice from Team"
   - Navigate back to edit the task
   - Expected: Dropdown shows "alice@example.com — no longer on team"
   - Edit only the title
   - Tap Save
   - Expected: Save blocked with error message

4. **Test Case 6 - Empty Roster**
   - Tap "Clear All Team Members"
   - Navigate to edit a task assigned to Alice
   - Expected: Dropdown shows "alice@example.com — team has no members"
   - Tap dropdown, select "Unassigned"
   - Tap Save
   - Expected: Save succeeds, task becomes unassigned

## Implementation Details

### How "No Longer on Team" Works

The assignment stale detection checks `Team.memberEmails`:

```kotlin
// In EditTaskBottomSheet.updateAssigneeDropdown()
val matchingStudent = teamMembers.find { it.studentEmail == selectedAssigneeEmail }
if (matchingStudent != null) {
    // Student found in team roster
} else {
    // Student NOT in team roster → stale
}
```

### removeStudentFromTeamRoster()

- Fetches `TeamEntity` by ID
- Removes student email from `Team.memberEmails` list
- Does NOT delete `StudentEntity` row
- Result: Student exists in DB but not in team roster

### clearAllTeamMembers()

- Sets `Team.memberEmails = emptyList()`
- All student entities remain in database
- Result: Team has no members in roster

## Removal Instructions

Before production release, delete:

1. `app/src/main/java/com/cutm/TeamPulse/debug/` (entire package)
2. `app/src/main/res/layout/fragment_debug_seed.xml`
3. From `nav_graph.xml`, remove `debugSeedFragment` declaration
4. From `TeamDao.kt`, remove `getById()` method (unless needed elsewhere)
5. This README file

## Build Safety

All functions check `BuildConfig.DEBUG` and throw if called in release:

```kotlin
check(BuildConfig.DEBUG) { "DebugSeedUtil can only run in DEBUG builds" }
```

Fragment constructor also checks and throws in release builds.
