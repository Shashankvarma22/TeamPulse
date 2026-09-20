package com.cutm.TeamPulse.ui.teacher

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cutm.TeamPulse.domain.model.Project
import com.cutm.TeamPulse.domain.model.TaskAssignment
import com.cutm.TeamPulse.domain.model.TaskStatus
import com.cutm.TeamPulse.domain.model.UserSession
import com.cutm.TeamPulse.domain.repository.AuthRepository
import com.cutm.TeamPulse.domain.repository.ProjectRepository
import com.cutm.TeamPulse.domain.repository.TaskRepository
import com.cutm.TeamPulse.domain.repository.TeamRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

data class ProjectWithProgress(
    val project: Project,
    val completedTasks: Int,
    val totalTasks: Int,
    val daysUntilDeadline: Int
)

data class UpcomingDeadline(
    val title: String,
    val daysUntil: Int,
    val isProject: Boolean
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class TeacherHomeViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val projectRepository: ProjectRepository,
    private val taskRepository: TaskRepository,
    private val teamRepository: TeamRepository,
) : ViewModel() {

    init {
        val vmStartTime = System.currentTimeMillis()
        android.util.Log.d("TeacherHomeVM", "ViewModel init START at $vmStartTime")
        
        // This is where Hilt constructs the ViewModel and all its dependencies
        // Any work in constructor parameters or init block happens here
        
        val vmEndTime = System.currentTimeMillis()
        android.util.Log.d("TeacherHomeVM", "ViewModel init COMPLETE (took ${vmEndTime - vmStartTime}ms)")
    }

    val userSession: StateFlow<UserSession?> = authRepository.observeSession()
        .onEach { 
            android.util.Log.d("TeacherHomeVM", "userSession emitted at ${System.currentTimeMillis()}: ${it?.email ?: "null"}")
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = null
        )

    /**
     * Phase 1: Sync a specific project from Sheets → Room (read-only).
     * Called explicitly from UI (e.g., sync button in project detail).
     * 
     * @param spreadsheetId The project's spreadsheet ID
     * @return ApiResult indicating success or error
     */
    suspend fun syncProjectFromSheets(spreadsheetId: String) =
        projectRepository.syncFromSheets(spreadsheetId)

    /**
     * Sign out: Clear session from Room and CredentialManager cache.
     * Next sign-in will show Google account picker.
     */
    suspend fun signOut() {
        authRepository.signOut()
    }

    val projectsWithProgress: StateFlow<List<ProjectWithProgress>> = projectRepository.observeProjects()
        .onEach { android.util.Log.d("TeacherHomeVM", "[${System.currentTimeMillis()}] observeProjects() emitted: ${it.size} projects") }
        .combine(userSession) { projects, session ->
            android.util.Log.d("TeacherHomeVM", "[${System.currentTimeMillis()}] combine: projects=${projects.size}, session=${session?.email}")
            if (session == null) return@combine emptyList()
            projects.filter { it.teacherEmail == session.email }
        }
        .onEach { android.util.Log.d("TeacherHomeVM", "[${System.currentTimeMillis()}] After filter: ${it.size} teacher projects") }
        .flatMapLatest { teacherProjects ->
            android.util.Log.d("TeacherHomeVM", "[${System.currentTimeMillis()}] flatMapLatest entered with ${teacherProjects.size} projects")
            if (teacherProjects.isEmpty()) {
                flowOf(emptyList())
            } else {
                // Combine each project with ALL its teams' tasks to calculate real progress
                combine(
                    teacherProjects.map { project ->
                        combine(
                            flowOf(project),
                            teamRepository.observeTeams(project.projectId)
                        ) { proj, teams ->
                            proj to teams
                        }.flatMapLatest { (proj, teams) ->
                            if (teams.isEmpty()) {
                                flowOf(Triple(proj, 0, 0))
                            } else if (teams.size == 1) {
                                // Single team optimization
                                taskRepository.observeTasksForTeam(teams.first().teamId)
                                    .map { tasks ->
                                        val completed = tasks.count { it.status == TaskStatus.DONE }
                                        Triple(proj, completed, tasks.size)
                                    }
                            } else {
                                // Multiple teams: observe each and merge
                                combine(
                                    teams.map { team ->
                                        taskRepository.observeTasksForTeam(team.teamId)
                                    }
                                ) { teamTaskArrays: Array<List<TaskAssignment>> ->
                                    val allTasks = teamTaskArrays.flatMap { it }
                                    val completed = allTasks.count { it.status == TaskStatus.DONE }
                                    Triple(proj, completed, allTasks.size)
                                }
                            }
                        }
                    }
                ) { projectDataArray: Array<Triple<Project, Int, Int>> ->
                    val currentTime = System.currentTimeMillis()
                    projectDataArray.map { (project, completed, total) ->
                        val daysUntil = ((project.dueDate - currentTime) / (1000 * 60 * 60 * 24)).toInt()
                        ProjectWithProgress(
                            project = project,
                            completedTasks = completed,
                            totalTasks = total,
                            daysUntilDeadline = daysUntil
                        )
                    }
                }
            }
        }
        .onEach { android.util.Log.d("TeacherHomeVM", "[${System.currentTimeMillis()}] FINAL projectsWithProgress emitted: ${it.size} items") }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = emptyList()
        )

    val upcomingDeadlines: StateFlow<List<UpcomingDeadline>> = projectsWithProgress
        .onEach { android.util.Log.d("TeacherHomeVM", "[${System.currentTimeMillis()}] upcomingDeadlines source: ${it.size} projects") }
        .combine(userSession) { projects, _ ->
            android.util.Log.d("TeacherHomeVM", "[${System.currentTimeMillis()}] upcomingDeadlines combine: computing from ${projects.size} projects")
            val currentTime = System.currentTimeMillis()
            val deadlines = mutableListOf<UpcomingDeadline>()

            // Add project deadlines
            projects.forEach { projectProgress ->
                val daysUntil = projectProgress.daysUntilDeadline
                if (daysUntil >= 0 && daysUntil <= 14) {
                    deadlines.add(
                        UpcomingDeadline(
                            title = projectProgress.project.name,
                            daysUntil = daysUntil,
                            isProject = true
                        )
                    )
                }
            }

            // Sort by closest deadline first
            deadlines.sortedBy { it.daysUntil }.take(5)
        }
        .onEach { android.util.Log.d("TeacherHomeVM", "[${System.currentTimeMillis()}] FINAL upcomingDeadlines emitted: ${it.size} items") }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = emptyList()
        )

    override fun onCleared() {
        android.util.Log.d("TeacherHomeVM", "ViewModel cleared at ${System.currentTimeMillis()}")
        super.onCleared()
    }
}
