package com.cutm.TeamPulse.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.cutm.TeamPulse.data.local.entity.TeamEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TeamDao {

    @Query("SELECT * FROM teams WHERE projectId = :projectId ORDER BY createdAt ASC")
    fun observeByProject(projectId: String): Flow<List<TeamEntity>>

    @Query("SELECT * FROM teams")
    fun observeAll(): Flow<List<TeamEntity>>

    @Query("SELECT * FROM teams WHERE teamId = :teamId LIMIT 1")
    suspend fun getById(teamId: String): TeamEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(team: TeamEntity)

    @Query("DELETE FROM teams WHERE teamId = :teamId")
    suspend fun deleteById(teamId: String)

    @Query("DELETE FROM teams WHERE projectId = :projectId")
    suspend fun deleteByProject(projectId: String)

    @Query("SELECT * FROM teams WHERE projectId = :projectId")
    suspend fun getByProjectSync(projectId: String): List<TeamEntity>
}
