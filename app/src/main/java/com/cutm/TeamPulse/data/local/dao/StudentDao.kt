package com.cutm.TeamPulse.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.cutm.TeamPulse.data.local.entity.StudentEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface StudentDao {

    @Query("SELECT * FROM students WHERE projectId = :projectId")
    fun observeByProject(projectId: String): Flow<List<StudentEntity>>

    @Query("SELECT * FROM students WHERE teamId = :teamId")
    fun observeByTeam(teamId: String): Flow<List<StudentEntity>>

    @Query("SELECT * FROM students WHERE teamId = :teamId")
    suspend fun getByTeamSync(teamId: String): List<StudentEntity>

    @Query("SELECT * FROM students WHERE studentEmail = :email")
    suspend fun getByEmailSync(email: String): StudentEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(student: StudentEntity)

    @Query("DELETE FROM students WHERE studentEmail = :email")
    suspend fun deleteByEmail(email: String)
}
