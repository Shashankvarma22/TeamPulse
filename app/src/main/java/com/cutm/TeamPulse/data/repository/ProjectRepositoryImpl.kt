package com.cutm.TeamPulse.data.repository

import com.cutm.TeamPulse.data.local.dao.ProjectDao
import com.cutm.TeamPulse.data.mapper.toDomain
import com.cutm.TeamPulse.domain.model.Project
import com.cutm.TeamPulse.domain.repository.ProjectRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ProjectRepositoryImpl @Inject constructor(
    private val projectDao: ProjectDao,
) : ProjectRepository {

    override fun observeProjects(): Flow<List<Project>> {
        return projectDao.observeAll().map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override fun observeProject(projectId: String): Flow<Project?> {
        return projectDao.observeById(projectId).map { entity ->
            entity?.toDomain()
        }
    }
}
