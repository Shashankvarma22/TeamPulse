# Student Home Screen Bug Investigation

## Bug #1: "No Active Project" Despite Having One

### Code Evidence

**StudentHomeViewModel.kt lines 71-88:**
```kotlin
teamRepository.observeTeams().flatMapLatest { teams ->
    val studentTeam = teams.firstOrNull { team ->
        team.memberEmails.contains(session.email)
    }
    
    if (studentTeam == null) {
        return@flatMapLatest flowOf(null)  // Shows "No Active Project"
    }
```

**TeamRepositoryImpl.kt line 24:**
```kotlin
override fun observeTeams(): Flow<List<Team>> {
    return teamDao.observeAll().map { ... }  // Returns ALL teams
}
```

**TeamDao.kt line 17:**
```kotlin
@Query("SELECT * FROM teams")
fun observeAll(): Flow<List<TeamEntity>>  // NO filter
```

### Root Cause Hypothesis

The query fetches ALL teams from DB, then filters in-memory for `team.memberEmails.contains(session.email)`.

**Possible reasons for null:**
1. **Team exists but memberEmails is empty** - student was never added to team's memberEmails list
2. **Team exists but has wrong projectId** - FK mismatch
3. **Team doesn't exist** - was deleted but tasks remain orphaned
4. **Student email mismatch** - session.email ≠ actual email in team.memberEmails

### Required DB Evidence

Need to query actual DB state for student `231801371093@cutmap.ac.in`:

```sql
-- Check if student is in ANY team
SELECT teamId, teamName, projectId, memberEmails FROM teams;

-- Check student's actual tasks
SELECT taskId, title, teamId, projectId, assigneeEmail, dueDate FROM task_assignments 
WHERE assigneeEmail = '231801371093@cutmap.ac.in' OR assigneeEmail = '';

-- Check project "Hi" exists
SELECT projectId, name, teacherEmail, status FROM projects WHERE name = 'Hi';
```

## Bug #2: Task List Mismatch (3 on home, 2 in project)

### Code Evidence

**StudentHomeViewModel.kt lines 109-117 (Home screen task list):**
```kotlin
val myTasks = userSession.flatMapLatest { session ->
    if (session == null) {
        flowOf(emptyList())
    } else {
        taskRepository.observeTasksForStudent(session.email)  // Uses assigneeEmail
    }
}
```

**TaskAssignmentDao.kt line 19:**
```kotlin
@Query("SELECT * FROM task_assignments WHERE assigneeEmail = :email ORDER BY dueDate ASC")
fun observeByAssignee(email: String): Flow<List<TaskAssignmentEntity>>
```

**StudentHomeViewModel.kt lines 85-87 (In-project task list via currentProject):**
```kotlin
combine(
    projectRepository.observeProject(studentTeam.projectId),
    taskRepository.observeTasksForTeam(studentTeam.teamId)  // Uses teamId
)
```

**TaskAssignmentDao.kt line 13:**
```kotlin
@Query("SELECT * FROM task_assignments WHERE teamId = :teamId ORDER BY dueDate ASC")
fun observeByTeam(teamId: String): Flow<List<TaskAssignmentEntity>>
```

### Root Cause Found

**Home screen query:** `assigneeEmail = student.email` - returns ONLY personally-assigned tasks  
**In-project query:** `teamId = team.id` - returns ALL tasks for that team (personal + whole-team)

**This explains the mismatch if:**
- "Hi" (due 1 day) and "Hi" (due 9 days) are personally assigned → show on home screen
- "Hello" (due 7 days) is whole-team assigned (`assigneeEmail = ""`) → does NOT show on home screen
- But home screen shows 3 tasks, in-project shows 2

**Wait, that's backwards!** User reported:
- Home: 3 tasks ("Hi" due 1d, "Hello" due 7d, "Hi" due 9d)
- In-project: 2 tasks ("Hello" and "Hi" due 9d)

So the "Hi" due 1d task:
- Shows on home screen (passes `assigneeEmail = student.email` query)
- Does NOT show in project detail (fails `teamId = team.id` query)

**This means the "Hi" due 1d task has:**
- `assigneeEmail = 231801371093@cutmap.ac.in` (so it shows on home)
- `teamId` ≠ student's actual team (so it DOESN'T show in project)

This is an **orphaned task** from a different team/project!

### Required DB Evidence

Need to check actual task rows:

```sql
-- All tasks for this student email
SELECT taskId, title, teamId, projectId, assigneeEmail, dueDate, status 
FROM task_assignments 
WHERE assigneeEmail = '231801371093@cutmap.ac.in';

-- All tasks for team "alpha"
SELECT taskId, title, teamId, projectId, assigneeEmail, dueDate, status 
FROM task_assignments 
WHERE teamId = (SELECT teamId FROM teams WHERE teamName = 'alpha' AND projectId = (SELECT projectId FROM projects WHERE name = 'Hi'));

-- Check for orphaned tasks (teamId doesn't exist)
SELECT t.taskId, t.title, t.teamId, t.projectId, t.assigneeEmail 
FROM task_assignments t 
LEFT JOIN teams tm ON t.teamId = tm.teamId 
WHERE tm.teamId IS NULL;
```

## Summary

**Bug #1:** `observeTeams()` returns all teams, filters for student membership in-memory
- **Hypothesis:** Student's team exists but `memberEmails` doesn't contain their email
- OR team doesn't exist at all

**Bug #2:** Home screen uses `assigneeEmail = student`, in-project uses `teamId = team`
- **Confirmed:** "Hi" due 1d task has wrong teamId (orphaned from different team)
- Shows on home because assigneeEmail matches
- Doesn't show in project because teamId doesn't match student's team

## Next Steps

1. Add temporary diagnostic logging to dump:
   - All teams in DB with their memberEmails
   - All tasks for this student (by assigneeEmail)
   - All tasks for team "alpha" (by teamId)
   
2. Install on device, sign in as student, capture logs

3. Compare actual DB state to expected state

4. Fix based on evidence, not assumption
