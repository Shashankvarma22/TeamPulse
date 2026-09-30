package com.cutm.TeamPulse.ui.student

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cutm.TeamPulse.domain.model.Project
import com.cutm.TeamPulse.domain.model.TaskAssignment
import com.cutm.TeamPulse.domain.model.TaskStatus
import com.cutm.TeamPulse.domain.model.Team
import com.cutm.TeamPulse.domain.model.UserSession
import com.cutm.TeamPulse.domain.repository.AuthRepository
import com.cutm.TeamPulse.domain.repository.ProjectRepository
import com.cutm.TeamPulse.domain.repository.TaskRepository
import com.cutm.TeamPulse.domain.repository.TeamRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class CurrentProjectData(
    val project: Project,
    val team: Team,
    val completedTasks: Int,
    val totalTasks: Int,
    val daysUntilDeadline: Int
)

data class StudentTaskData(
    val task: TaskAssignment,
    val daysUntilDue: Int
)

@HiltViewModel
class StudentHomeViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val projectRepository: ProjectRepository,
    private val teamRepository: TeamRepository,
    private val taskRepository: TaskRepository,
) : ViewModel() {

    val userSession: StateFlow<UserSession?> = authRepository.observeSession()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = null
        )

    /**
     * Sign out: Clear session from Room and CredentialManager cache.
     * Next sign-in will show Google account picker.
     */
    suspend fun signOut() {
        authRepository.signOut()
    }

    fun updateTaskStatus(taskId: String, newStatus: TaskStatus) {
        // Students can only set TODO or IN_PROGRESS, not DONE/COMPLETED
        // (Prevents self-XP-farming exploit - only teachers can mark tasks complete)
        if (newStatus != TaskStatus.TODO && newStatus != TaskStatus.IN_PROGRESS) {
            return  // Silently reject student attempt to set DONE
        }
        
        viewModelScope.launch {
            taskRepository.updateTaskStatus(taskId, newStatus)
        }
    }

    /**
     * Focused project ID for carousel tracking.
     * Updated when user swipes between project cards.
     */
    private val _focusedProjectId = kotlinx.coroutines.flow.MutableStateFlow<String?>(null)
    val focusedProjectId: StateFlow<String?> = _focusedProjectId.asStateFlow()

    /**
     * All projects the student is enrolled in (via team membership).
     * Replaces single currentProject with list support for carousel.
     */
    val currentProjects: StateFlow<List<CurrentProjectData>> = userSession
        .flatMapLatest { session ->
            if (session == null) {
                return@flatMapLatest flowOf(emptyList())
            }

            teamRepository.observeTeams().flatMapLatest { teams ->
                val studentTeams = teams.filter { team ->
                    team.memberEmails.contains(session.email)
                }
                
                if (studentTeams.isEmpty()) {
                    return@flatMapLatest flowOf(emptyList())
                }

                // Combine all project data for each student team
                val projectFlows = studentTeams.map { team ->
                    combine(
                        projectRepository.observeProject(team.projectId),
                        taskRepository.observeTasksForTeam(team.teamId)
                    ) { project, tasks ->
                        if (project == null) {
                            return@combine null
                        }

                        val completedTasks = tasks.count { it.status == TaskStatus.DONE }
                        val totalTasks = tasks.size
                        val currentTime = System.currentTimeMillis()
                        val daysUntil = ((project.dueDate - currentTime) / (1000 * 60 * 60 * 24)).toInt()
                        
                        CurrentProjectData(
                            project = project,
                            team = team,
                            completedTasks = completedTasks,
                            totalTasks = totalTasks,
                            daysUntilDeadline = daysUntil
                        )
                    }
                }

                // Combine all project flows into a single list
                if (projectFlows.isEmpty()) {
                    flowOf(emptyList())
                } else {
                    combine(projectFlows) { projectArray ->
                        projectArray.filterNotNull()
                    }
                }
            }
        }.map { projects ->
            // Auto-focus first project on first emission if not already focused
            if (projects.isNotEmpty() && _focusedProjectId.value == null) {
                _focusedProjectId.value = projects[0].project.projectId
            }
            projects
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    /**
     * Update focused project (called when carousel page changes).
     */
    fun setFocusedProject(projectId: String) {
        _focusedProjectId.value = projectId
    }

    val myTasks: StateFlow<List<StudentTaskData>> = combine(
        userSession,
        focusedProjectId,
        currentProjects
    ) { session: UserSession?, focusedId: String?, projects: List<CurrentProjectData> ->
        Triple(session, focusedId, projects)
    }
        .flatMapLatest { (session, focusedId, projects) ->
            if (session == null || focusedId == null) {
                flowOf(emptyList<TaskAssignment>())
            } else {
                // Find the focused project to get its team ID
                val focusedProject = projects.find { it.project.projectId == focusedId }
                if (focusedProject == null) {
                    flowOf(emptyList<TaskAssignment>())
                } else {
                    // Observe only tasks for the focused project's team
                    taskRepository.observeTasksForTeam(focusedProject.team.teamId)
                }
            }
        }
        .map { tasks: List<TaskAssignment> ->
            val currentTime = System.currentTimeMillis()

            tasks.map { task ->
                val daysUntil = ((task.dueDate - currentTime) / (1000 * 60 * 60 * 24)).toInt()
                StudentTaskData(task = task, daysUntilDue = daysUntil)
            }
                .sortedWith(
                    compareBy<StudentTaskData> { it.task.status == TaskStatus.DONE }
                        .thenBy { it.daysUntilDue }
                )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )
}
