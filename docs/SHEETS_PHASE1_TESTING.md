# Sheets Migration Phase 1 - Testing Guide

## Phase 1 Scope: Read-Only Sync (Projects → Teams → Students)

**What's implemented:**
- SheetsProbeReader extensions (readProjects, readTeams, readStudents)
- Row-to-entity mapping with null-safe parsing
- SyncRepository.pullFromSheets() - atomic sync of all three tabs
- ProjectRepository.syncFromSheets() - repository-layer wrapper
- TeacherHomeViewModel.syncProjectFromSheets() - ViewModel method
- Temporary UI trigger (long-press greeting in TeacherHomeFragment)

**What's NOT implemented (Phase 2/3):**
- Write operations (push Room → Sheets)
- Task assignment sync
- Conflict resolution
- Automatic background sync (WorkManager)
- Incremental sync (only syncs full pull)

---

## Prerequisites

### 1. Google Sheet Setup

Create a test spreadsheet with these tabs:

**Projects Tab** (Columns A-I):
```
project_id | name | teacher_email | start_date | due_date | drive_folder_id | github_repo | status
test-proj-1 | Test Project | your-email@gmail.com | 1735689600000 | 1738368000000 | folder-id-123 | | ACTIVE
```

**Teams Tab** (Columns A-E):
```
team_id | project_id | team_name | member_emails | created_at
team-1 | test-proj-1 | Alpha | student1@example.com,student2@example.com | 1735689600000
```

**Students Tab** (Columns A-F):
```
student_email | display_name | team_id | project_id | joined_at | role
student1@example.com | Alice Smith | team-1 | test-proj-1 | 1735689600000 | MEMBER
student2@example.com | Bob Jones | team-1 | test-proj-1 | 1735689600000 | MEMBER
```

**Date format:** Epoch milliseconds (use online converter or: `new Date('2025-01-01').getTime()`)

### 2. Sheet Permissions

- Share sheet with your Google account (the one you'll sign in with)
- Grant at least "Viewer" access (read-only for Phase 1)

### 3. App Configuration

Update `SheetsConfig.kt` if needed, or pass spreadsheet ID dynamically (current impl uses spreadsheet from first project entity).

---

## Test Procedure

### Step 1: Install APK

```powershell
adb install -r app\build\outputs\apk\debug\app-debug.apk
```

### Step 2: Sign In

- Launch app
- Sign in with Google account that has access to test spreadsheet
- Verify you're on Teacher Home screen

### Step 3: Create Local Project First (Workaround)

**Why:** Current sync trigger pulls from first project entity's spreadsheetId.

Create a project in the app with:
- Name: "Test Project" (match Sheets)
- Spreadsheet ID: (paste your test spreadsheet ID)
- Due date: (match Sheets)

**Alternative:** Modify TeacherHomeFragment trigger to accept hardcoded spreadsheet ID for testing.

### Step 4: Trigger Sync

- **Long-press the greeting text** at top of Teacher Home
- Wait for toast showing "Sync completed successfully" or error message

### Step 5: Verify with logcat

```powershell
adb logcat -s SyncRepository:D ProjectRepository:D
```

**Expected output:**
```
SyncRepository: pullFromSheets: Starting for spreadsheet [your-id]
SyncRepository: pullFromSheets: Success - synced 1 projects, 1 teams, 2 students
```

### Step 6: Verify Room Database

**Option A: Android Studio Database Inspector**
1. Tools → Database Inspector
2. Connect to running app
3. Check tables: `projects`, `teams`, `students`
4. Verify rows match Sheets data

**Option B: Logcat queries**
Add temporary logging to ProjectRepository or ViewModel to dump Room contents after sync.

### Step 7: Verify UI Updates

- Navigate to project detail (tap project card)
- Verify team name shows "Alpha"
- Navigate to team members screen
- Verify students "Alice Smith" and "Bob Jones" appear

---

## Expected Results

**Success criteria:**
- ✅ Toast shows "Sync completed successfully"
- ✅ Logcat shows "synced X projects, Y teams, Z students"
- ✅ Room tables populated with data matching Sheets
- ✅ UI reflects synced data (project card, team name, student list)
- ✅ `localDirty` = false for all synced entities
- ✅ `lastSyncedAt` = current timestamp for all synced entities

**Failure scenarios:**

| Error | Likely Cause | Fix |
|-------|--------------|-----|
| "Session expired" | Not signed in | Sign in with Google |
| "Failed to read Projects tab (HTTP 403)" | Sheet not shared | Grant access to your account |
| "Failed to read Projects tab (HTTP 404)" | Invalid spreadsheet ID | Check ID in project entity |
| Toast shows "No projects to sync" | No local projects yet | Create project first with spreadsheet ID |
| "Sync failed: Unresolved reference" | Compilation error | Check build output |

---

## Known Limitations (Phase 1)

1. **No automatic sync** - must trigger manually via long-press
2. **Full pull only** - always syncs all rows, no incremental updates
3. **No write-back** - Room changes don't push to Sheets yet (Phase 2)
4. **No conflict detection** - last-write-wins when re-syncing
5. **Date format rigid** - expects epoch millis, not human-readable dates
6. **No validation** - malformed rows silently skipped (logged as mapNotNull)

---

## Troubleshooting

### Build Errors

**"Unresolved reference: COMPLETED"**
- Fixed: ProjectStatus enum doesn't have COMPLETED, maps to ARCHIVED

**"Unresolved reference: syncRepository"**
- Check ProjectRepositoryImpl constructor includes SyncRepository injection

### Runtime Errors

**"No projects to sync" toast**
- Create a local project first with valid spreadsheet ID
- Or hardcode spreadsheet ID in TeacherHomeFragment trigger for testing

**Toast never appears**
- Check logcat for exceptions
- Verify long-press is registered (add log at trigger entry)

### Data Not Appearing

**Room populated but UI doesn't update**
- Check if Flow observers are active
- Verify entity IDs match between Sheets and local
- Try navigating away/back to refresh

**Partial data synced**
- Check Sheets for missing columns (null values)
- Review logcat for parsing errors (rows skipped)

---

## Cleanup After Testing

1. **Remove temporary trigger** from TeacherHomeFragment (long-press handler)
2. **Move sync to proper UI location** (e.g., project detail menu → "Sync from Sheets")
3. **Add loading indicator** (ProgressBar during sync)
4. **Add error snackbar** (replace toast with persistent error message)
5. **Test with multiple projects** (verify spreadsheetId routing)

---

## Next Steps (Phase 2)

**Blocked until design decisions made:**
1. **findTaskRowIndex()** - How to locate task rows for updates (remoteRowIndex vs search vs hybrid)
2. **Conflict resolution** - How teacher-wins reconciles with hasEverBeenCompleted XP guard

**Do NOT start Phase 2 code until both answered.**
