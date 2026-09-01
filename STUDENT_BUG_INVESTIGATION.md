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


## CRITICAL HYPOTHESIS: Unified Root Cause

**Both bugs might stem from a single issue: orphaned team from deleted "Blaa" project**

### The Theory

When project "Blaa" (ID: `93ab134c-fe14-4101-9a02-9956c4c7c7cd`) was created/deleted during earlier testing:
1. Project was deleted (or never properly created)
2. BUT team + tasks were **NOT cascade-deleted**
3. Student remains in that orphaned team's memberEmails
4. Orphaned task "Hi" due 1d still points to that orphaned teamId

### How This Explains Both Bugs

**Bug #1: "No Active Project"**
- `currentProject` Flow finds orphaned "Blaa" team by email match
- Tries to load project: `projectRepository.observeProject(orphanedTeam.projectId)`
- Project "Blaa" doesn't exist (was deleted or never created)
- Returns `null` → "No Active Project" empty state

**Bug #2: Task Mismatch (3 vs 2)**
- Home screen: `assigneeEmail = student` returns ALL student tasks (including orphaned one)
- In-project: `teamId = currentTeam` should show current team's tasks
- But `currentProject` is null (Bug #1), so in-project list shows nothing OR shows different team
- Orphaned "Hi" task has old teamId, doesn't match current "alpha" team

### This Also Answers Earlier Session Question

**"Does project deletion cascade-delete teams?"** (Bug C from investigation)

Code says YES:
```kotlin
// ProjectRepositoryImpl.kt deleteProject()
database.withTransaction {
    val teams = teamDao.getByProjectSync(projectId)
    teams.forEach { team ->
        taskDao.deleteByTeam(team.teamId)  // Delete tasks
    }
    teamDao.deleteByProject(projectId)  // Delete teams
    projectDao.deleteById(projectId)  // Delete project
}
```

But if orphaned team exists, one of these happened:
1. Delete was never executed for "Blaa" (user didn't actually delete it)
2. Transaction rolled back silently (exception not logged)
3. "Blaa" was created with team but project insert failed (partial creation)

### Critical Evidence Needed

When capturing logcat, specifically look for:

**1. Team Membership:**
```
Student email: 231801371093@cutmap.ac.in
Total teams in DB: X
Team: [NAME] ([TEAM_ID])
  ProjectId: [PROJECT_ID]  <-- Is this "Blaa" ID (93ab134c-...) or "Hi" ID?
  MemberEmails: [231801371093@cutmap.ac.in, ...]
  Contains student? true
```

**2. Project Lookup Result:**
```
Student team found: [NAME] ([TEAM_ID])
```

Then EITHER:
```
Project found: [NAME] ([PROJECT_ID])  <-- Success
```

OR:
```
!!! PROJECT [PROJECT_ID] NOT FOUND !!!  <-- Confirms orphaned team
```

**3. Task TeamId:**
```
Task: Hi ([TASK_ID])
  TeamId: [TEAM_ID]  <-- Does this match student's current team or orphaned team?
  ProjectId: [PROJECT_ID]  <-- "Blaa" (93ab134c-...) or "Hi"?
  AssigneeEmail: 231801371093@cutmap.ac.in
  DueDate: [timestamp for "due 1d"]
```

### If Hypothesis is Correct

**We should see:**
- Student's team has `projectId = 93ab134c-fe14-4101-9a02-9956c4c7c7cd` (Blaa)
- Log shows: `!!! PROJECT 93ab134c-... NOT FOUND !!!`
- Task "Hi" due 1d has same orphaned teamId
- Task "Hi" due 1d has `projectId = 93ab134c-...` (Blaa)

**This proves:**
- Project deletion did NOT cascade-delete teams (contrary to code)
- OR "Blaa" was never deleted (user attempted but failed silently)
- Either way: orphaned team/tasks are the root cause of both bugs

### Evidence-Driven Fix

**DO NOT propose fix until logcat confirms:**
1. Which team contains student email
2. What projectId that team references
3. Whether that project exists in DB
4. Which teamId the orphaned "Hi" task references

Once evidence is captured, fix will be clear:
- If cascade delete failed: fix the cascade delete logic
- If orphaned data exists: add cleanup migration to remove orphaned teams/tasks
- If FK constraints missing: add proper ON DELETE CASCADE constraints
