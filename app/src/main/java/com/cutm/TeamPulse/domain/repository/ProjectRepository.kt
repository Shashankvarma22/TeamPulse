package com.cutm.TeamPulse.domain.repository

import com.cutm.TeamPulse.domain.model.Project
import kotlinx.coroutines.flow.Flow

interface ProjectRepository {

    fun observeProjects(): Flow<List<Project>>

    fun observeProject(projectId: String): Flow<Project?>
}
