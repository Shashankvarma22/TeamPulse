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
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
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

@HiltViewModel
class TeacherHomeViewModel @Inject constructor(
    authRepository: AuthRepository,
    private val projectRepository: ProjectRepository,
    private val taskRepository: TaskRepository,
) : ViewModel() {

    val userSession: StateFlow<UserSession?> = authRepository.observeSession()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = null
        )

    val projectsWithProgress: StateFlow<List<ProjectWithProgress>> = combine(
        projectRepository.observeProjects(),
        userSession
    ) { projects, session ->
        if (session == null) return@combine emptyList()

        projects.mapNotNull { project ->
            // Only show projects belonging to this teacher
            if (project.teacherEmail != session.email) return@mapNotNull null

            // Note: Task progress calculation deferred - requires aggregating async task data
            // For now, showing projects with 0 progress until proper data aggregation is implemented
            val currentTime = System.currentTimeMillis()
            val daysUntil = ((project.dueDate - currentTime) / (1000 * 60 * 60 * 24)).toInt()

            ProjectWithProgress(
                project = project,
                completedTasks = 0,
                totalTasks = 0,
                daysUntilDeadline = daysUntil
            )
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val upcomingDeadlines: StateFlow<List<UpcomingDeadline>> = projectsWithProgress
        .combine(userSession) { projects, _ ->
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
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )
}
