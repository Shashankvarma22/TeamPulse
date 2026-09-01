# Google Sheets Sync Migration Plan

## Context

Currently, the app has:
- ✅ Complete local-first architecture with Room database
- ✅ Sync queue infrastructure (`sync_queue` table, `SyncQueueEntity`)
- ❌ SheetsSyncWorker stub (not implemented)
- ❌ No actual Sheets API read/write logic
- ❌ No conflict resolution strategy

**Migration Goal:** Implement real bidirectional sync with Google Sheets while maintaining local-first behavior.

---

## Phase 1: Read-Only Sync (Low Risk Start)

### Why Start Here?
- **No conflict resolution needed** (read-only = no writes from app)
- Tests the entire Sheets API integration safely
- Validates spreadsheet schema matches expectations
- Low user impact if bugs occur (app keeps working with local data)

### Tables to Migrate First

**Recommended Order:**

1. **`projects` table** (Lowest risk)
   - Single source of truth: Teacher creates in Sheets
   - App reads project metadata (name, dates, status)
   - No student writes, no task dependency yet
   - Clear success criteria: Projects appear in teacher home screen

2. **`teams` table**
   - Depends on projects (FK: `projectId`)
   - Teacher-managed in Sheets
   - Validates multi-row Sheets parsing (memberEmails list)
   - Tests Moshi JSON converter with Sheets data

3. **`students` table**
   - Links to teams and projects
   - Validates email matching (critical for sign-in flow)
   - Tests that roster sync doesn't break auth

**Skip for Phase 1:**
- `task_assignments` - Involves task status updates (write-heavy)
- `student_progress` - XP/badges still in development (Phase 6)
- `user_sessions` - Local-only (never sync)

### Implementation Steps

#### 1.1: SheetsProbeReader Enhancement
```kotlin
// Expand existing SheetsProbeReader to fetch actual data
interface SheetsProbeReader {
    suspend fun readProjectsSheet(spreadsheetId: String): ApiResult<List<ProjectSheet>>
    suspend fun readTeamsSheet(spreadsheetId: String): ApiResult<List<TeamSheet>>
    suspend fun readStudentsSheet(spreadsheetId: String): ApiResult<List<StudentSheet>>
    // ... existing probe methods
}
```

**Data Classes:**
```kotlin
data class ProjectSheet(
    val projectId: String,
    val name: String,
    val teacherEmail: String,
    val startDate: Long,
    val dueDate: Long,
    val status: String,  // "ACTIVE" | "COMPLETED" | "ARCHIVED"
    // spreadsheetId/driveFolderId come from context, not Sheets
)
```

#### 1.2: Repository Integration
```kotlin
class ProjectRepositoryImpl {
    suspend fun syncProjectsFromSheets(spreadsheetId: String): ApiResult<Unit> {
        return when (val result = sheetsProbeReader.readProjectsSheet(spreadsheetId)) {
            is ApiResult.Success -> {
                val entities = result.data.map { it.toEntity() }
                // Upsert to local DB (REPLACE strategy)
                projectDao.upsertAll(entities)
                ApiResult.Success(Unit)
            }
            is ApiResult.Error -> result
        }
    }
}
```

#### 1.3: Sync Trigger (Manual for Phase 1)
- **Teacher action:** "Sync Project" button in project detail screen
- **OR:** Automatic sync on app launch (if last sync > 1 hour ago)
- **NO:** Background worker yet (Phase 2)

**Success Criteria:**
- Teacher opens app → sees Sheets projects
- Project metadata matches Sheets exactly
- No data loss if sync fails (local data preserved)

---

## Phase 2: Conflict Resolution Strategy

**DECISION NEEDED:** How to handle conflicts when both Sheets and app are modified?

### Scenario: Task Status Conflict
1. Teacher marks task DONE in Sheets (timestamp T1)
2. Student marks task IN_PROGRESS in app offline (timestamp T2)
3. App comes online and syncs

**Which wins?**

### Option A: Last-Write-Wins (Timestamp-Based)
```kotlin
fun resolveConflict(local: TaskEntity, remote: TaskSheet): TaskEntity {
    return if (local.lastModifiedLocal > remote.lastModifiedRemote) {
        local  // Local is newer, keep it
    } else {
        remote.toEntity()  // Sheets is newer, overwrite local
    }
}
```

**Pros:** Simple, predictable  
**Cons:** Can lose legitimate changes (no merge)

### Option B: Teacher-Wins (Role-Based)
```kotlin
fun resolveConflict(local: TaskEntity, remote: TaskSheet): TaskEntity {
    // If Sheets changed (teacher edit), always take Sheets
    if (remote.lastModifiedRemote > local.lastSyncedAt) {
        return remote.toEntity()  // Teacher override
    }
    // Otherwise, keep local change and push to Sheets
    return local
}
```

**Pros:** Clear authority hierarchy  
**Cons:** Student changes can be overwritten silently

### Option C: Field-Level Merge (Complex but Safe)
```kotlin
fun resolveConflict(local: TaskEntity, remote: TaskSheet): TaskEntity {
    return local.copy(
        status = if (remote.status == TaskStatus.DONE) {
            remote.status  // Teacher can always mark DONE
        } else {
            local.status  // Student status update wins for TODO/IN_PROGRESS
        },
        // Other fields: take newer timestamp
        description = if (remote.lastModified > local.lastModified) remote.description else local.description
    )
}
```

**Pros:** Preserves both changes when possible  
**Cons:** Complex logic, field-specific rules

### **RECOMMENDED: Option B (Teacher-Wins)** for v1
**Rationale:**
- Simple to implement and reason about
- Matches actual workflow (teacher is authority)
- Student changes are low-risk (TODO ↔ IN_PROGRESS only, no DONE)
- Can upgrade to Option C later if needed

---

## Phase 3: Write-Back (Sync Queue Implementation)

### sync_queue Table Usage

**Current Schema:**
```kotlin
@Entity(tableName = "sync_queue")
data class SyncQueueEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val entityType: String,  // "TASK", "PROJECT", "STUDENT", etc.
    val entityId: String,    // taskId, projectId, etc.
    val operation: String,   // "UPDATE", "INSERT", "DELETE"
    val payload: String,     // JSON of changed fields
    val createdAt: Long,
    val attemptCount: Int = 0,
    val lastAttemptAt: Long? = null,
    val error: String? = null
)
```

### SheetsSyncWorker Implementation

**Architecture Decision: Retry-Until-Confirmed**

```kotlin
class SheetsSyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    
    override suspend fun doWork(): Result {
        val pendingItems = syncQueueDao.getOldestPending(limit = 10)
        
        if (pendingItems.isEmpty()) {
            return Result.success()  // Nothing to sync
        }
        
        var allSucceeded = true
        
        for (item in pendingItems) {
            when (val result = syncItem(item)) {
                is ApiResult.Success -> {
                    // Write succeeded, remove from queue
                    syncQueueDao.deleteById(item.id)
                }
                is ApiResult.Error -> {
                    allSucceeded = false
                    
                    // Update retry metadata
                    syncQueueDao.updateAttempt(
                        id = item.id,
                        attemptCount = item.attemptCount + 1,
                        lastAttemptAt = System.currentTimeMillis(),
                        error = result.message
                    )
                    
                    // Exponential backoff: 1min, 5min, 15min, then hourly
                    if (item.attemptCount >= 3) {
                        // Log persistent failure (potential manual intervention)
                        Log.e("SheetsSyncWorker", "Item ${item.id} failed ${item.attemptCount} times")
                    }
                }
            }
        }
        
        return if (allSucceeded) {
            Result.success()
        } else {
            // Retry with backoff
            Result.retry()
        }
    }
    
    private suspend fun syncItem(item: SyncQueueEntity): ApiResult<Unit> {
        val entity = when (item.entityType) {
            "TASK" -> taskDao.getById(item.entityId) ?: return ApiResult.Error("Task not found")
            // ... other entity types
            else -> return ApiResult.Error("Unknown entity type")
        }
        
        return sheetsWriter.writeTask(entity)  // NEW: SheetsWriter interface
    }
}
```

### SheetsWriter Interface (NEW)

```kotlin
interface SheetsWriter {
    suspend fun writeTask(task: TaskAssignmentEntity): ApiResult<Unit>
    suspend fun writeProject(project: ProjectEntity): ApiResult<Unit>
    // ... other entity types
}

class SheetsWriterImpl @Inject constructor(
    private val sheetsService: GoogleSheetsApi,  // Retrofit service
    private val authManager: AuthorizationManager
) : SheetsWriter {
    
    override suspend fun writeTask(task: TaskAssignmentEntity): ApiResult<Unit> {
        // Find row index by taskId (may require reading entire sheet first)
        val rowIndex = findTaskRowIndex(task.projectId, task.taskId)
            ?: return ApiResult.Error("Task row not found in Sheets")
        
        // Update specific row with new values
        val values = listOf(
            listOf(
                task.taskId,
                task.title,
                task.status.name,
                task.assigneeEmail,
                // ... other columns
            )
        )
        
        return try {
            sheetsService.updateRange(
                spreadsheetId = task.projectId,  // Assuming project has spreadsheetId
                range = "Tasks!A${rowIndex}:G${rowIndex}",  // Adjust columns
                valueInputOption = "RAW",
                body = UpdateValuesRequest(values)
            )
            ApiResult.Success(Unit)
        } catch (e: Exception) {
            ApiResult.Error(e.message ?: "Sheets write failed")
        }
    }
}
```

### Enqueue Strategy

**When to add to sync_queue:**
```kotlin
// In TaskRepositoryImpl.applyTaskUpdate()
database.withTransaction {
    // ... existing task update logic
    
    // If task marked as dirty, enqueue for sync
    if (finalEntity.localDirty) {
        syncQueueDao.enqueue(
            SyncQueueEntity(
                entityType = "TASK",
                entityId = finalEntity.taskId,
                operation = "UPDATE",
                payload = Json.encodeToString(finalEntity),
                createdAt = System.currentTimeMillis()
            )
        )
        
        // Trigger sync worker immediately (with backoff)
        enqueueSyncWorker()
    }
}
```

---

## Phase 4: Cache TTL & Offline Support

### Cache TTL Strategy

**Decision:** How long is local data valid without Sheets sync?

**Recommended: 1-hour TTL for active projects**

```kotlin
@Entity(tableName = "sync_metadata")
data class SyncMetadataEntity(
    @PrimaryKey val key: String,  // "projects_last_sync", "tasks_project_123_last_sync"
    val lastSyncAt: Long,
    val nextSyncAt: Long
)

// In ProjectRepository
suspend fun observeProjects(): Flow<List<Project>> {
    val lastSync = syncMetadataDao.getByKey("projects_last_sync")
    
    if (lastSync == null || System.currentTimeMillis() > lastSync.nextSyncAt) {
        // TTL expired, trigger background sync
        lifecycleScope.launch {
            syncProjectsFromSheets()
        }
    }
    
    // Return local data immediately (local-first)
    return projectDao.observeAll().map { entities -> entities.map { it.toDomain() } }
}
```

### Offline-First Guarantees

**Critical Invariants:**
1. ✅ App ALWAYS works offline (reads from local DB)
2. ✅ Writes succeed immediately to local DB (sync queue handles later)
3. ✅ Sync failures NEVER block user actions
4. ✅ User sees "Last synced X minutes ago" indicator (optional UX)

---

## Open Questions

### 1. Retry Semantics
**Question:** What's the maximum retry count before giving up?

**Options:**
- A. Infinite retries (keep trying forever) → Risk: Queue grows unbounded
- B. Max 10 retries, then mark as "failed permanently" → Risk: Data loss
- C. Max 10 retries, then prompt user to "Resolve Conflict" → Best UX, complex

**RECOMMENDATION: Option C** for user-initiated changes (task status updates), **Option B** for background syncs (metadata).

### 2. Conflict Resolution UI
**Question:** Should students see conflict warnings?

**Scenario:** Student updates task status offline, comes online, Sheets has different value.

**Options:**
- A. Silent resolution (teacher-wins, no UI) → Simplest, may confuse students
- B. Toast notification: "Task updated by teacher" → Informative, non-blocking
- C. Dialog: "Conflict detected. Keep your change or accept teacher's?" → Complex

**RECOMMENDATION: Option B** (toast) for v1.

### 3. Batch vs. Real-Time Sync
**Question:** When does sync happen?

**Current Plan:**
- ✅ Manual trigger (Phase 1)
- ✅ Periodic background (WorkManager, every 1 hour)
- ❓ Real-time on every write? (Too aggressive, quota concerns)

**RECOMMENDATION:** Stick with hourly background + manual trigger for v1.

---

## Migration Checklist

**Phase 1: Read-Only Sync**
- [ ] Implement `SheetsProbeReader.readProjectsSheet()`
- [ ] Implement `SheetsProbeReader.readTeamsSheet()`
- [ ] Implement `SheetsProbeReader.readStudentsSheet()`
- [ ] Add `ProjectRepository.syncProjectsFromSheets()`
- [ ] Add manual sync button in teacher project detail screen
- [ ] Test with real Sheets spreadsheet (test project)
- [ ] Verify no data loss on sync failure

**Phase 2: Conflict Resolution**
- [ ] Document teacher-wins strategy
- [ ] Implement conflict detection in repository layer
- [ ] Add unit tests for conflict scenarios
- [ ] Test manual conflict (teacher edit in Sheets, student edit in app)

**Phase 3: Write-Back**
- [ ] Implement `SheetsWriter` interface
- [ ] Implement `SheetsSyncWorker.doWork()`
- [ ] Add `syncQueueDao.enqueue()` calls to repositories
- [ ] Test queue persistence across app restarts
- [ ] Test exponential backoff on API failures

**Phase 4: Production Readiness**
- [ ] Add cache TTL logic with `sync_metadata` table
- [ ] Add "Last synced" UI indicator
- [ ] Add quota monitoring (Sheets API limits)
- [ ] Document manual intervention procedure for stuck queue items

---

## Risk Mitigation

### High-Risk Areas

1. **Quota Exhaustion** (Sheets API has limits)
   - Mitigation: Batch updates, exponential backoff
   - Monitoring: Log API call counts per hour

2. **Schema Mismatch** (Sheets columns don't match app expectations)
   - Mitigation: Strict validation in `SheetsProbeReader`
   - Fallback: Show error to teacher, don't crash app

3. **Data Loss** (Sync overwrites local changes)
   - Mitigation: Always write to `sync_queue` first, never delete local data until Sheets confirm
   - Audit: Log all conflict resolutions

### Testing Strategy

**Critical Test Cases:**
1. ✅ Sync succeeds on first try
2. ✅ Sync fails due to network, retries successfully
3. ✅ Sync fails 10 times, item marked as permanently failed
4. ✅ Teacher edits Sheets while student offline, conflict resolved correctly
5. ✅ App works fully offline (no crashes, no data loss)
6. ✅ Queue persists across app kill/restart

---

## Timeline Estimate

**Phase 1 (Read-Only):** 2-3 days
- Day 1: Sheets API integration (SheetsProbeReader expansion)
- Day 2: Repository sync methods, manual trigger UI
- Day 3: Testing with real Sheets data

**Phase 2 (Conflict Resolution):** 1 day
- Document strategy, add conflict detection logic
- Unit tests

**Phase 3 (Write-Back):** 3-4 days
- Day 1: SheetsWriter interface + implementation
- Day 2: SheetsSyncWorker + queue logic
- Day 3: Integration testing
- Day 4: Edge case testing (retries, failures)

**Phase 4 (Polish):** 1-2 days
- Cache TTL, UI indicators, monitoring

**Total:** ~7-10 days of implementation work

---

## Dependencies

**Blocked by:**
- None (Sheets auth already working)

**Blocks:**
- Phase 6.3 leaderboard (needs task completion data synced)
- Production release (sync is core feature)

**Parallel Work:**
- Phase 6.3 can proceed (uses local DB, not dependent on sync)
- UI polish, bug fixes can continue

---

## Next Steps

1. **Review this plan** - Get approval on:
   - Phase 1 table order (projects → teams → students)
   - Conflict resolution strategy (teacher-wins)
   - Retry semantics (max 10, then fail)

2. **After approval, start Phase 1:**
   - Branch: `feat/sheets-sync-phase1-read-only`
   - First PR: Projects read-only sync
   - Incremental PRs for teams, students

3. **Phase 2+ after Phase 1 validated in staging**
