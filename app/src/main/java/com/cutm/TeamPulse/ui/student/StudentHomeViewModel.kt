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
    authRepository: AuthRepository,
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

    val currentProject: StateFlow<CurrentProjectData?> = userSession
        .flatMapLatest { session ->
            if (session == null) return@flatMapLatest flowOf(null)

            teamRepository.observeTeams().flatMapLatest { teams ->
                val studentTeam = teams.firstOrNull { team ->
                    team.memberEmails.contains(session.email)
                } ?: return@flatMapLatest flowOf(null)

                combine(
                    projectRepository.observeProject(studentTeam.projectId),
                    taskRepository.observeTasksForTeam(studentTeam.teamId)
                ) { project, tasks ->
                    if (project == null) return@combine null

                    val completedTasks = tasks.count { it.status == TaskStatus.DONE }
                    val totalTasks = tasks.size
                    val currentTime = System.currentTimeMillis()
                    val daysUntil = ((project.dueDate - currentTime) / (1000 * 60 * 60 * 24)).toInt()

                    CurrentProjectData(
                        project = project,
                        team = studentTeam,
                        completedTasks = completedTasks,
                        totalTasks = totalTasks,
                        daysUntilDeadline = daysUntil
                    )
                }
            }
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = null
        )

    val myTasks: StateFlow<List<StudentTaskData>> = userSession
        .flatMapLatest { session ->
            if (session == null) {
                flowOf(emptyList())
            } else {
                taskRepository.observeTasksForStudent(session.email)
            }
        }
        .map { tasks ->
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
