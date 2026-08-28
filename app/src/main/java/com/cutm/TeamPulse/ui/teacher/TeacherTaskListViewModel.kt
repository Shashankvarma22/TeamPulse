package com.cutm.TeamPulse.ui.teacher

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cutm.TeamPulse.domain.model.TaskAssignment
import com.cutm.TeamPulse.domain.model.TaskStatus
import com.cutm.TeamPulse.domain.model.Team
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
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

data class TeacherTaskData(
    val task: TaskAssignment,
    val daysUntilDue: Int
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class TeacherTaskListViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val taskRepository: TaskRepository,
    private val teamRepository: TeamRepository,
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

    val tasks: StateFlow<List<TeacherTaskData>> = teamRepository.observeTeams(projectId)
        .flatMapLatest { teams ->
            if (teams.isEmpty()) {
                flowOf(emptyList())
            } else if (teams.size == 1) {
                // Single team optimization
                taskRepository.observeTasksForTeam(teams.first().teamId)
            } else {
                // Multiple teams: observe each and merge
                combine(
                    teams.map { team ->
                        taskRepository.observeTasksForTeam(team.teamId)
                    }
                ) { teamTaskArrays: Array<List<TaskAssignment>> ->
                    teamTaskArrays.flatMap { it }
                }
            }
        }
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
        dueDate: Long
    ) {
        viewModelScope.launch {
            val team = availableTeams.value.find { it.teamId == teamId } ?: return@launch

            val task = TaskAssignment(
                taskId = UUID.randomUUID().toString(),
                teamId = teamId,
                projectId = projectId,
                assigneeEmail = "",
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
}
