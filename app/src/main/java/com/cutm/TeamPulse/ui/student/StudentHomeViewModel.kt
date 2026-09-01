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

    init {
        // ONE-TIME CLEANUP: Remove orphaned data from "Blaa" project
        // This will execute once when ViewModel is created
        // TODO: Remove this block after confirming cleanup worked
        viewModelScope.launch {
            try {
                projectRepository.cleanupOrphanedBlaaData()
                android.util.Log.d("StudentHome", "ONE-TIME CLEANUP: Orphaned data removed")
            } catch (e: Exception) {
                android.util.Log.e("StudentHome", "ONE-TIME CLEANUP failed", e)
            }
        }
    }

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

            teamRepository.observeTeams().flatMapLatest { teams ->
                // DIAGNOSTIC: Log all teams and student membership
                android.util.Log.d("StudentHome", "=== TEAM MEMBERSHIP CHECK ===")
                android.util.Log.d("StudentHome", "Student email: ${session.email}")
                android.util.Log.d("StudentHome", "Total teams in DB: ${teams.size}")
                teams.forEach { team ->
                    android.util.Log.d("StudentHome", "  Team: ${team.teamName} (${team.teamId})")
                    android.util.Log.d("StudentHome", "    ProjectId: ${team.projectId}")
                    android.util.Log.d("StudentHome", "    MemberEmails: ${team.memberEmails}")
                    android.util.Log.d("StudentHome", "    Contains student? ${team.memberEmails.contains(session.email)}")
                }
                
                val studentTeam = teams.firstOrNull { team ->
                    team.memberEmails.contains(session.email)
                }
                
                if (studentTeam == null) {
                    android.util.Log.w("StudentHome", "!!! NO TEAM FOUND FOR STUDENT !!!")
                    return@flatMapLatest flowOf(null)
                }
                
                android.util.Log.d("StudentHome", "Student team found: ${studentTeam.teamName} (${studentTeam.teamId})")

                combine(
                    projectRepository.observeProject(studentTeam.projectId),
                    taskRepository.observeTasksForTeam(studentTeam.teamId)
                ) { project, tasks ->
                    if (project == null) {
                        android.util.Log.w("StudentHome", "!!! PROJECT ${studentTeam.projectId} NOT FOUND !!!")
                        return@combine null
                    }

                    android.util.Log.d("StudentHome", "Project found: ${project.name} (${project.projectId})")
                    android.util.Log.d("StudentHome", "Team tasks: ${tasks.size} total")

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
            // DIAGNOSTIC: Log all tasks for student
            android.util.Log.d("StudentHome", "=== MY TASKS (Home Screen) ===")
            android.util.Log.d("StudentHome", "Total tasks: ${tasks.size}")
            tasks.forEach { task ->
                android.util.Log.d("StudentHome", "  Task: ${task.title} (${task.taskId})")
                android.util.Log.d("StudentHome", "    TeamId: ${task.teamId}")
                android.util.Log.d("StudentHome", "    ProjectId: ${task.projectId}")
                android.util.Log.d("StudentHome", "    AssigneeEmail: ${task.assigneeEmail}")
                android.util.Log.d("StudentHome", "    DueDate: ${task.dueDate}")
                android.util.Log.d("StudentHome", "    Status: ${task.status}")
            }
            
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
