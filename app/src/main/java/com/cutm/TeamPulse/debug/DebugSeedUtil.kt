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
}
