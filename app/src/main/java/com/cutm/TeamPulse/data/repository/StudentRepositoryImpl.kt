package com.cutm.TeamPulse.data.repository

import com.cutm.TeamPulse.data.local.dao.StudentDao
import com.cutm.TeamPulse.data.mapper.toDomain
import com.cutm.TeamPulse.domain.model.Student
import com.cutm.TeamPulse.domain.repository.StudentRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class StudentRepositoryImpl @Inject constructor(
    private val studentDao: StudentDao,
) : StudentRepository {

    override fun observeStudentsByTeam(teamId: String): Flow<List<Student>> {
        return studentDao.observeByTeam(teamId).map { entities ->
            entities.map { it.toDomain() }
        }
    }
}
