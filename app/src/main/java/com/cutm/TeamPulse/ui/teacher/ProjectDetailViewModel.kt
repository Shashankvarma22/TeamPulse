package com.cutm.TeamPulse.ui.teacher

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cutm.TeamPulse.domain.model.Project
import com.cutm.TeamPulse.domain.model.Team
import com.cutm.TeamPulse.domain.repository.ProjectRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ProjectDetailViewModel @Inject constructor(
    private val projectRepository: ProjectRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val projectId: String = savedStateHandle.get<String>("projectId")
        ?: throw IllegalArgumentException("projectId required")

    val project: StateFlow<Project?> = projectRepository
        .observeProject(projectId)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = null
        )

    val teams: StateFlow<List<Team>> = projectRepository
        .observeTeamsForProject(projectId)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    fun deleteTeam(teamId: String) {
        viewModelScope.launch {
            val result = projectRepository.deleteTeam(teamId)
            // Note: teams Flow will automatically update via Room's Flow observation
            // No explicit UI update needed - renderTeams() will be called automatically
        }
    }

    fun deleteProject(projectId: String) {
        viewModelScope.launch {
            val result = projectRepository.deleteProject(projectId)
            // Note: Fragment will navigate back to home screen after calling this
            // TeacherHomeViewModel.projects Flow will automatically update
        }
    }

    suspend fun getTeamCount(projectId: String): Int {
        return projectRepository.getTeamCount(projectId)
    }

    suspend fun getTaskCount(projectId: String): Int {
        return projectRepository.getTaskCount(projectId)
    }
}
