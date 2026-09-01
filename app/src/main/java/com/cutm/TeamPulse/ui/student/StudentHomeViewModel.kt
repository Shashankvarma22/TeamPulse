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
            if (session == null) {
                return@flatMapLatest flowOf(null)
            }

            android.util.Log.d("StudentHome", "=== DIAGNOSTIC: Looking for student project ===")
            android.util.Log.d("StudentHome", "Student email: ${session.email}")

            teamRepository.observeTeams().flatMapLatest { teams ->
                android.util.Log.d("StudentHome", "All teams in DB: ${teams.size}")
                teams.forEach { team ->
                    android.util.Log.d("StudentHome", "  Team: ${team.teamId}, project: ${team.projectId}, name: ${team.teamName}")
                }

                val studentTeam = teams.firstOrNull { team ->
                    team.memberEmails.contains(session.email)
                }
                
                if (studentTeam == null) {
                    android.util.Log.w("StudentHome", "Student not found in any team: ${session.email}")
                    return@flatMapLatest flowOf(null)
                }

                android.util.Log.d("StudentHome", "Found student team: ${studentTeam.teamId}, projectId: ${studentTeam.projectId}")

                combine(
                    projectRepository.observeProject(studentTeam.projectId),
                    taskRepository.observeTasksForTeam(studentTeam.teamId)
                ) { project, tasks ->
                    if (project == null) {
                        android.util.Log.w("StudentHome", "Project ${studentTeam.projectId} not found in DB!")
                        return@combine null
                    }

                    android.util.Log.d("StudentHome", "=== PROJECT DATA ===")
                    android.util.Log.d("StudentHome", "Project ID: ${project.projectId}")
                    android.util.Log.d("StudentHome", "Project Name: ${project.name}")
                    android.util.Log.d("StudentHome", "Due Date: ${project.dueDate} (${java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).format(java.util.Date(project.dueDate))})")
                    android.util.Log.d("StudentHome", "Status: ${project.status}")

                    val completedTasks = tasks.count { it.status == TaskStatus.DONE }
                    val totalTasks = tasks.size
                    val currentTime = System.currentTimeMillis()
                    val daysUntil = ((project.dueDate - currentTime) / (1000 * 60 * 60 * 24)).toInt()

                    android.util.Log.d("StudentHome", "Tasks for team: $totalTasks total, $completedTasks completed")
                    android.util.Log.d("StudentHome", "===================")
                    
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
                android.util.Log.d("StudentHome", "=== DIAGNOSTIC: Querying tasks for ${session.email} ===")
                taskRepository.observeTasksForStudent(session.email)
            }
        }
        .map { tasks ->
            android.util.Log.d("StudentHome", "=== TASKS RECEIVED ===")
            android.util.Log.d("StudentHome", "Total tasks: ${tasks.size}")
            tasks.forEach { task ->
                android.util.Log.d("StudentHome", "  Task: ${task.taskId}")
                android.util.Log.d("StudentHome", "    Title: ${task.title}")
                android.util.Log.d("StudentHome", "    Assignee: ${task.assigneeEmail}")
                android.util.Log.d("StudentHome", "    Status: ${task.status}")
                android.util.Log.d("StudentHome", "    Due: ${task.dueDate} (${java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).format(java.util.Date(task.dueDate))})")
            }
            android.util.Log.d("StudentHome", "===================")

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
