package com.cutm.TeamPulse.ui.student

import android.util.Log
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

    suspend fun signOut() {
        authRepository.signOut()
    }

    fun updateTaskStatus(taskId: String, newStatus: TaskStatus) {
        if (newStatus != TaskStatus.TODO && newStatus != TaskStatus.IN_PROGRESS) {
            return
        }
        
        viewModelScope.launch {
            taskRepository.updateTaskStatus(taskId, newStatus)
        }
    }

    private val _focusedProjectId = kotlinx.coroutines.flow.MutableStateFlow<String?>(null)
    val focusedProjectId: StateFlow<String?> = _focusedProjectId.asStateFlow()

    /**
     * FIXED: Project discovery now uses student's assigned tasks as the source of truth.
     * 
     * PREVIOUS LOGIC (broken):
     * - Filter all teams by: team.memberEmails.contains(session.email)
     * - Map filtered teams to projects
     * - Result: If student email not in memberEmails list, NO PROJECTS shown
     * 
     * NEW LOGIC (working):
     * - Get all tasks assigned to student by email: taskRepository.observeTasksForStudent(session.email)
     * - Extract unique (projectId, teamId) pairs from tasks
     * - Load project and team info for those pairs
     * - Combine with task count data
     * - Result: Projects display correctly based on actual task assignments
     * 
     * RATIONALE: Tasks are the direct evidence of project membership. If a task exists
     * for a student, they are part of that project's team, regardless of how memberEmails
     * is stored. This bypasses the memberEmails list corruption issue.
     */
    val currentProjects: StateFlow<List<CurrentProjectData>> = userSession
        .flatMapLatest { session ->
            Log.d("TEAM_PULSE_PROJECTS", "STUDENT_ID_RESOLVED: email=")
            
            if (session == null) {
                Log.d("TEAM_PULSE_PROJECTS", "currentProjects: session is null, returning empty")
                return@flatMapLatest flowOf(emptyList())
            }

            // FIX: Use tasks as source of truth instead of team membership
            taskRepository.observeTasksForStudent(session.email).flatMapLatest { studentTasks ->
                Log.d("TEAM_PULSE_PROJECTS", "currentProjects: Student  has  assigned tasks")
                studentTasks.forEach { task ->
                    Log.d("TEAM_PULSE_PROJECTS", "  Task:  | Project:  | Team:  | Title: ")
                }

                if (studentTasks.isEmpty()) {
                    Log.d("TEAM_PULSE_PROJECTS", "currentProjects: No tasks assigned, returning empty projects")
                    return@flatMapLatest flowOf(emptyList())
                }

                // Extract unique project-team combinations from tasks
                val projectTeamPairs = studentTasks.map { it.projectId to it.teamId }.distinct()
                Log.d("TEAM_PULSE_PROJECTS", "PROJECT_IDS_RESOLVED:  unique project-team pairs")
                projectTeamPairs.forEach { (projId, teamId) ->
                    Log.d("TEAM_PULSE_PROJECTS", "  ProjectId:  | TeamId: ")
                }

                // Build flows to fetch project and team data
                val projectDataFlows = projectTeamPairs.map { (projectId, teamId) ->
                    combine(
                        projectRepository.observeProject(projectId),
                        teamRepository.observeTeams().map { allTeams -> allTeams.find { it.teamId == teamId } }
                    ) { project, team ->
                        Log.d("TEAM_PULSE_PROJECTS", "Combining project  and team ")
                        if (project == null || team == null) {
                            Log.d("TEAM_PULSE_PROJECTS", "  Skipping: project=, team=")
                            return@combine null
                        }

                        // Count tasks for this project
                        val projectTasks = studentTasks.filter { it.projectId == projectId }
                        val completedTasks = projectTasks.count { it.status == TaskStatus.DONE }
                        val totalTasks = projectTasks.size
                        val currentTime = System.currentTimeMillis()
                        val daysUntil = ((project.dueDate - currentTime) / (1000 * 60 * 60 * 24)).toInt()

                        Log.d("TEAM_PULSE_PROJECTS", "  Created CurrentProjectData:  | / tasks |  days")

                        CurrentProjectData(
                            project = project,
                            team = team,
                            completedTasks = completedTasks,
                            totalTasks = totalTasks,
                            daysUntilDeadline = daysUntil
                        )
                    }
                }

                if (projectDataFlows.isEmpty()) {
                    Log.d("TEAM_PULSE_PROJECTS", "currentProjects: No project flows, returning empty")
                    flowOf(emptyList())
                } else {
                    Log.d("TEAM_PULSE_PROJECTS", "currentProjects: Combining  project flows")
                    combine(projectDataFlows) { projectArray ->
                        val filtered = projectArray.filterNotNull()
                        Log.d("TEAM_PULSE_PROJECTS", "PROJECTS_FETCHED_COUNT:  projects retrieved")
                        Log.d("TEAM_PULSE_PROJECTS", "ACTIVE_PROJECTS_COUNT:  projects (all tasks-derived)")
                        filtered.forEach { proj ->
                            Log.d("TEAM_PULSE_PROJECTS", "  Active: ")
                        }
                        filtered
                    }
                }
            }
        }.map { projects ->
            Log.d("TEAM_PULSE_PROJECTS", "VIEWMODEL_PROJECTS_EMITTED:  projects to UI")
            
            if (projects.isNotEmpty() && _focusedProjectId.value == null) {
                _focusedProjectId.value = projects[0].project.projectId
                Log.d("TEAM_PULSE_PROJECTS", "CAROUSEL_RENDERED: Set focused project to ")
            }
            projects
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    fun setFocusedProject(projectId: String) {
        _focusedProjectId.value = projectId
    }

    val myTasks: StateFlow<List<StudentTaskData>> = userSession
        .flatMapLatest { session ->
            if (session == null) {
                flowOf(emptyList<TaskAssignment>())
            } else {
                // FIX: Use observeTasksForStudent directly instead of deriving from currentProjects
                // This ensures tasks display correctly even if project carousel has issues
                taskRepository.observeTasksForStudent(session.email)
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