package com.cutm.TeamPulse.domain.repository

import com.cutm.TeamPulse.domain.model.Student
import kotlinx.coroutines.flow.Flow

interface StudentRepository {

    fun observeStudentsByTeam(teamId: String): Flow<List<Student>>
}
