package com.cutm.TeamPulse.debug

import com.cutm.TeamPulse.BuildConfig
import com.cutm.TeamPulse.data.local.dao.ProjectDao
import com.cutm.TeamPulse.data.local.dao.StudentDao
import com.cutm.TeamPulse.data.local.dao.TeamDao
import com.cutm.TeamPulse.data.local.entity.ProjectEntity
import com.cutm.TeamPulse.data.local.entity.StudentEntity
import com.cutm.TeamPulse.data.local.entity.TeamEntity
import com.cutm.TeamPulse.domain.model.ProjectStatus
import javax.inject.Inject
import javax.inject.Singleton

/**
 * DEBUG ONLY: Utility for seeding test data directly into Room database.
 * Used exclusively for testing task assignment stale-state scenarios.
 * 
 * MUST BE REMOVED before production release.
 */
@Singleton
class DebugSeedUtil @Inject constructor(
    private val projectDao: ProjectDao,
    private val teamDao: TeamDao,
    private val studentDao: StudentDao,
) {

    /**
     * Seeds a test project with one team and two students (Alice and Bob).
     * Returns the projectId and teamId for subsequent operations.
     * 
     * Throws IllegalStateException if called in release build.
     */
    suspend fun seedTestProjectWithTeam(): Pair<String, String> {
        check(BuildConfig.DEBUG) { "DebugSeedUtil can only run in DEBUG builds" }

        val projectId = "debug-project-1"
        val teamId = "debug-team-1"
        val currentTime = System.currentTimeMillis()

        // Insert project
        projectDao.upsert(
            ProjectEntity(
                projectId = projectId,
                name = "Debug Test Project",
                teacherEmail = "teacher@example.com",
                spreadsheetId = "debug-spreadsheet-placeholder-id",
                driveFolderId = "debug-folder-placeholder-id",
                startDate = currentTime,
                dueDate = currentTime + (30L * 24 * 60 * 60 * 1000), // 30 days from now
                status = ProjectStatus.ACTIVE,
                githubRepo = null,
                localDirty = false,
                lastModifiedLocal = currentTime,
                lastSyncedAt = null
            )
        )

        // Insert team with Alice and Bob
        teamDao.upsert(
            TeamEntity(
                teamId = teamId,
                projectId = projectId,
                teamName = "Team Alpha",
                memberEmails = listOf("alice@example.com", "bob@example.com"),
                createdAt = currentTime,
                localDirty = false,
                lastModifiedLocal = currentTime
            )
        )

        // Insert Alice
        studentDao.upsert(
            StudentEntity(
                studentEmail = "alice@example.com",
                displayName = "Alice Johnson",
                teamId = teamId,
                projectId = projectId,
                joinedAt = currentTime,
                localDirty = false
            )
        )

        // Insert Bob
        studentDao.upsert(
            StudentEntity(
                studentEmail = "bob@example.com",
                displayName = "Bob Smith",
                teamId = teamId,
                projectId = projectId,
                joinedAt = currentTime,
                localDirty = false
            )
        )

        return Pair(projectId, teamId)
    }

    /**
     * Removes a student's email from the team's memberEmails list.
     * This simulates "student no longer on team" for stale assignee testing (Case 2).
     * 
     * Does NOT delete the StudentEntity row — the student record remains in database
     * but is no longer listed in Team.memberEmails.
     * 
     * Throws IllegalStateException if called in release build.
     */
    suspend fun removeStudentFromTeamRoster(teamId: String, studentEmail: String) {
        check(BuildConfig.DEBUG) { "DebugSeedUtil can only run in DEBUG builds" }

        val team = teamDao.getById(teamId) 
            ?: throw IllegalArgumentException("Team $teamId not found")
        
        val updatedMembers = team.memberEmails.filter { it != studentEmail }
        
        teamDao.upsert(
            team.copy(
                memberEmails = updatedMembers,
                lastModifiedLocal = System.currentTimeMillis()
            )
        )
    }

    /**
     * Removes ALL students from a team's memberEmails list.
     * This simulates "team has no members" for empty roster testing (Case 6).
     * 
     * Student entities remain in the database, but Team.memberEmails becomes empty.
     * 
     * Throws IllegalStateException if called in release build.
     */
    suspend fun clearAllTeamMembers(teamId: String) {
        check(BuildConfig.DEBUG) { "DebugSeedUtil can only run in DEBUG builds" }

        val team = teamDao.getById(teamId) 
            ?: throw IllegalArgumentException("Team $teamId not found")
        
        teamDao.upsert(
            team.copy(
                memberEmails = emptyList(),
                lastModifiedLocal = System.currentTimeMillis()
            )
        )
    }

    /**
     * Seeds multiple teams for a given project ID.
     * Used for testing the ProjectDetailFragment team list rendering.
     * 
     * Creates:
     * - Team Alpha (3 members)
     * - Team Beta (2 members)
     * - Team Gamma (0 members - for zero-member card testing)
     * 
     * Throws IllegalStateException if called in release build.
     */
    suspend fun seedTeamsForProject(projectId: String) {
        check(BuildConfig.DEBUG) { "DebugSeedUtil can only run in DEBUG builds" }

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
                localDirty = false, // Seeded data treated as pre-synced
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
            ),
            TeamEntity(
                teamId = "debug-team-gamma",
                projectId = projectId,
                teamName = "Team Gamma",
                memberEmails = emptyList(), // Zero-member team for T11 testing
                createdAt = now,
                localDirty = false,
                lastModifiedLocal = now
            )
        )

        teams.forEach { teamDao.upsert(it) }
    }

    /**
     * Seeds a single team with 2 members into an existing project.
     * Minimal seed for Phase 3 deletion testing - creates just enough to test
     * task assignment followed by team deletion.
     * 
     * Returns the teamId of the created team.
     * 
     * Throws IllegalStateException if called in release build or if project doesn't exist.
     */
    suspend fun seedTestTeamForExistingProject(projectId: String): String {
        check(BuildConfig.DEBUG) { "DebugSeedUtil can only run in DEBUG builds" }

        // Verify project exists
        val project = projectDao.getById(projectId)
            ?: throw IllegalArgumentException("Project $projectId not found")

        val teamId = "debug-quick-team-${System.currentTimeMillis()}"
        val now = System.currentTimeMillis()

        // Insert team with Alice and Bob
        teamDao.upsert(
            TeamEntity(
                teamId = teamId,
                projectId = projectId,
                teamName = "Debug Team",
                memberEmails = listOf("alice@example.com", "bob@example.com"),
                createdAt = now,
                localDirty = false,
                lastModifiedLocal = now
            )
        )

        // Insert Alice
        studentDao.upsert(
            StudentEntity(
                studentEmail = "alice@example.com",
                displayName = "Alice Test",
                teamId = teamId,
                projectId = projectId,
                joinedAt = now,
                localDirty = false
            )
        )

        // Insert Bob
        studentDao.upsert(
            StudentEntity(
                studentEmail = "bob@example.com",
                displayName = "Bob Test",
                teamId = teamId,
                projectId = projectId,
                joinedAt = now,
                localDirty = false
            )
        )

        return teamId
    }

    /**
     * Repairs stale TeamEntity.memberEmails by recomputing from actual StudentEntity rows.
     * 
     * Use case: Fixes desync caused by adding a student to Team B when they were already in Team A.
     * The StudentEntity.teamId gets updated (moved to Team B), but Team A's memberEmails list
     * never gets cleaned up, causing stale member counts.
     * 
     * This function:
     * 1. Queries all teams
     * 2. For each team, queries actual StudentEntity rows with that teamId
     * 3. Rebuilds memberEmails list from actual students
     * 4. Updates team if memberEmails changed
     * 
     * @return Pair<teamsChecked, teamsRepaired>
     * 
     * Throws IllegalStateException if called in release build.
     */
    suspend fun repairTeamMemberLists(): Pair<Int, Int> {
        check(BuildConfig.DEBUG) { "DebugSeedUtil can only run in DEBUG builds" }

        var teamsChecked = 0
        var teamsRepaired = 0

        try {
            // Get all projects
            val projects = projectDao.getAllSync()
            
            projects.forEach { project ->
                // Get all teams in project
                val teams = teamDao.getByProjectSync(project.projectId)
                
                teams.forEach { team ->
                    teamsChecked++
                    
                    // Get actual students in this team
                    val actualStudents = studentDao.getByTeamSync(team.teamId)
                    val actualEmails = actualStudents.map { it.studentEmail }.sorted()
                    val currentEmails = team.memberEmails.sorted()
                    
                    // Check if memberEmails is stale
                    if (actualEmails != currentEmails) {
                        android.util.Log.w("DebugSeedUtil", "REPAIR: Team ${team.teamName} (${team.teamId})")
                        android.util.Log.w("DebugSeedUtil", "  Old memberEmails: $currentEmails")
                        android.util.Log.w("DebugSeedUtil", "  New memberEmails: $actualEmails")
                        android.util.Log.w("DebugSeedUtil", "  Added: ${(actualEmails.toSet() - currentEmails.toSet()).sorted()}")
                        android.util.Log.w("DebugSeedUtil", "  Removed: ${(currentEmails.toSet() - actualEmails.toSet()).sorted()}")
                        
                        val repairedTeam = team.copy(
                            memberEmails = actualEmails,
                            localDirty = true,
                            lastModifiedLocal = System.currentTimeMillis()
                        )
                        
                        teamDao.upsert(repairedTeam)
                        teamsRepaired++
                    }
                }
            }
            
            android.util.Log.i("DebugSeedUtil", "Repair complete: $teamsChecked teams checked, $teamsRepaired teams repaired")
            return Pair(teamsChecked, teamsRepaired)
        } catch (e: Exception) {
            android.util.Log.e("DebugSeedUtil", "Failed to repair team member lists", e)
            return Pair(teamsChecked, teamsRepaired)
        }
    }

    /**
     * Logs all teams and their member states for debugging.
     * Shows both TeamEntity.memberEmails and actual StudentEntity rows.
     * 
     * Returns a formatted string report.
     * 
     * Throws IllegalStateException if called in release build.
     */
    suspend fun logTeamMemberStates(): String {
        check(BuildConfig.DEBUG) { "DebugSeedUtil can only run in DEBUG builds" }

        try {
            val output = StringBuilder()
            output.appendLine("=== TEAM MEMBER STATES ===")
            
            val projects = projectDao.getAllSync()
            projects.forEach { project ->
                output.appendLine("\nProject: ${project.name}")
                
                val teams = teamDao.getByProjectSync(project.projectId)
                teams.forEach { team ->
                    output.appendLine("  Team: ${team.teamName} (${team.teamId})")
                    output.appendLine("    TeamEntity.memberEmails: ${team.memberEmails.sorted()}")
                    
                    val actualStudents = studentDao.getByTeamSync(team.teamId)
                    output.appendLine("    Actual StudentEntity rows: ${actualStudents.map { it.studentEmail }.sorted()}")
                    
                    val match = team.memberEmails.toSet() == actualStudents.map { it.studentEmail }.toSet()
                    output.appendLine("    Status: ${if (match) "✓ SYNCED" else "✗ STALE"}")
                }
            }
            
            val result = output.toString()
            android.util.Log.i("DebugSeedUtil", result)
            return result
        } catch (e: Exception) {
            val error = "Failed to log team states: ${e.message}"
            android.util.Log.e("DebugSeedUtil", error, e)
            return error
        }
    }
}
