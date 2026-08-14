package com.cutm.TeamPulse.data.repository

import com.cutm.TeamPulse.data.local.dao.TeamDao
import com.cutm.TeamPulse.data.mapper.toDomain
import com.cutm.TeamPulse.domain.model.Team
import com.cutm.TeamPulse.domain.repository.TeamRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TeamRepositoryImpl @Inject constructor(
    private val teamDao: TeamDao,
) : TeamRepository {

    override fun observeTeams(projectId: String): Flow<List<Team>> {
        return teamDao.observeByProject(projectId).map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override fun observeTeams(): Flow<List<Team>> {
        return teamDao.observeAll().map { entities ->
            entities.map { it.toDomain() }
        }
    }
}
