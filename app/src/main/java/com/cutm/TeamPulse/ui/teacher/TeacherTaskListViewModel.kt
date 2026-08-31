package com.cutm.TeamPulse.ui.teacher

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cutm.TeamPulse.domain.model.TaskAssignment
import com.cutm.TeamPulse.domain.model.TaskStatus
import com.cutm.TeamPulse.domain.model.Team
import com.cutm.TeamPulse.domain.model.Student
import com.cutm.TeamPulse.domain.repository.TaskRepository
import com.cutm.TeamPulse.domain.repository.TeamRepository
import com.cutm.TeamPulse.domain.repository.StudentRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

data class TeacherTaskData(
    val task: TaskAssignment,
    val daysUntilDue: Int
)

@HiltViewModel
class TeacherTaskListViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val taskRepository: TaskRepository,
    private val teamRepository: TeamRepository,
    private val studentRepository: StudentRepository,
) : ViewModel() {

    private val projectId: String = savedStateHandle.get<String>("projectId") ?: ""

    val availableTeams: StateFlow<List<Team>> = teamRepository.observeTeams(projectId)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val teamName: StateFlow<String?> = availableTeams
        .map { teams -> 
            when (teams.size) {
                0 -> null
                1 -> teams.first().teamName
                else -> "${teams.size} Teams"
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = null
        )

    val tasks: StateFlow<List<TeacherTaskData>> = taskRepository.observeTasksForProject(projectId)
        .map { tasks ->
            val currentTime = System.currentTimeMillis()
            tasks.map { task ->
                val daysUntil = ((task.dueDate - currentTime) / (1000 * 60 * 60 * 24)).toInt()
                TeacherTaskData(task = task, daysUntilDue = daysUntil)
            }.sortedWith(
                compareBy<TeacherTaskData> { it.task.status.ordinal }
                    .thenBy { it.daysUntilDue }
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    fun createTask(
        title: String,
        description: String,
        teamId: String,
        dueDate: Long,
        assigneeEmail: String = ""
    ) {
        viewModelScope.launch {
            val team = availableTeams.value.find { it.teamId == teamId } ?: return@launch

            // Validate assignee is in team if not empty
            if (assigneeEmail.isNotEmpty() && assigneeEmail !in team.memberEmails) {
                // Validation failed - should not happen with proper UI
                android.util.Log.e("TeacherTaskListViewModel", "Assignee $assigneeEmail not in team ${team.teamId}")
                return@launch
            }

            val task = TaskAssignment(
                taskId = UUID.randomUUID().toString(),
                teamId = teamId,
                projectId = projectId,
                assigneeEmail = assigneeEmail,
                title = title,
                description = description,
                weight = 1.0f,
                dueDate = dueDate,
                status = TaskStatus.TODO,
                localDirty = true,
                lastModifiedLocal = System.currentTimeMillis(),
                remoteRowIndex = null
            )

            taskRepository.createTask(task)
        }
    }

    fun updateTask(
        taskId: String,
        title: String,
        description: String,
        dueDate: Long,
        status: TaskStatus,
        teamId: String,
        projectId: String,
        assigneeEmail: String,
        weight: Float,
        remoteRowIndex: Int?
    ) {
        viewModelScope.launch {
            val task = TaskAssignment(
                taskId = taskId,
                teamId = teamId,
                projectId = projectId,
                assigneeEmail = assigneeEmail,
                title = title,
                description = description,
                weight = weight,
                dueDate = dueDate,
                status = status,
                localDirty = true,
                lastModifiedLocal = System.currentTimeMillis(),
                remoteRowIndex = remoteRowIndex
            )

            taskRepository.updateTask(task)
        }
    }

    fun deleteTask(taskId: String) {
        viewModelScope.launch {
            taskRepository.deleteTask(taskId)
        }
    }

    fun getTeamMembers(teamId: String) = studentRepository.observeStudentsByTeam(teamId)
}
