# Clean Test Data - Manual DB Operations

## Orphaned Test Projects to Remove

1. **"Blaa"** - ID: `93ab134c-fe14-4101-9a02-9956c4c7c7cd`
   - Teacher: `231801371093@cutmap.ac.in` (student account)
   - Reason: Test artifact created under wrong account

2. **"Debug Test Project"** - ID: `debug-project-1`
   - Teacher: varies (whoever signs in first)
   - Reason: Debug seed data, no longer needed

## Cleanup Approach

Since both projects have no UI surface to delete them (wrong ownership/debug data), and we've removed the DB dump tool, cleanup options:

### Option 1: Clear App Data (Nuclear)
```powershell
adb shell pm clear com.cutm.TeamPulse
```
**Effect:** Removes ALL local data, forces fresh start  
**Use when:** Ready to start clean slate

### Option 2: Conditional Seed Data Removal
Modify `TeacherHomeFragment.seedTestData()` to check and delete debug-project-1 before re-creating:

```kotlin
// At start of seedTestData(), before insert:
val existingDebug = projectDao.getById("debug-project-1")
if (existingDebug != null) {
    // Delete debug project and all related data
    projectDao.deleteById("debug-project-1")
    // Teams/tasks will cascade delete via foreign keys
}
```

**Problem with Option 2:** Won't clean up "Blaa" (different ID)

### Option 3: One-Time Migration Query
Add temporary code to TeacherHomeFragment.onViewCreated() that runs once:

```kotlin
// ONE-TIME CLEANUP - Remove after deploying
viewLifecycleOwner.lifecycleScope.launch {
    try {
        // Delete orphaned "Blaa" project
        projectDao.deleteById("93ab134c-fe14-4101-9a02-9956c4c7c7cd")
        android.util.Log.d("TeacherHome", "Cleaned up orphaned 'Blaa' project")
    } catch (e: Exception) {
        // Silently ignore if already deleted
    }
}
```

**Problem:** Runs on every launch until removed

### Option 4: Do Nothing
**Rationale:**
- These are local dev artifacts
- Don't affect production users (fresh installs start clean)
- Will be cleared when user does fresh install or clear data
- No functional impact (filtered from UI)

## Recommendation

**Use Option 4 (Do Nothing) for now:**
- "Blaa" and "debug-project-1" are harmless (filtered from UI)
- Production users won't have these (fresh DB)
- Dev can clear data manually if needed
- Avoids adding cleanup code that needs removal later

When ready for production:
- Remove `seedTestData()` function entirely
- Remove debug seed logic from `TeacherHomeFragment.onViewCreated()`
- Ship with empty DB schema only

## Status

✅ Diagnostic scaffolding removed (this commit)  
⏭️ Test data cleanup: DEFERRED (no action - harmless local artifacts)  
⏭️ Seed data removal: DEFERRED (will remove in production build)
