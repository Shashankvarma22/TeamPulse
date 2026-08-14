package com.cutm.TeamPulse.domain.repository

import com.cutm.TeamPulse.domain.model.Team
import kotlinx.coroutines.flow.Flow

interface TeamRepository {

    fun observeTeams(projectId: String): Flow<List<Team>>

    fun observeTeams(): Flow<List<Team>>
}
