package com.cutm.TeamPulse.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.cutm.TeamPulse.data.local.entity.ProjectEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ProjectDao {

    @Query("SELECT * FROM projects ORDER BY lastModifiedLocal DESC")
    fun observeAll(): Flow<List<ProjectEntity>>

    @Query("SELECT * FROM projects WHERE projectId = :projectId")
    fun observeById(projectId: String): Flow<ProjectEntity?>

    @Query("SELECT * FROM projects WHERE projectId = :projectId")
    suspend fun getById(projectId: String): ProjectEntity?

    @Query("DELETE FROM projects WHERE projectId = :projectId")
    suspend fun deleteById(projectId: String)

    @Query("UPDATE projects SET dueDate = :dueDate, lastModifiedLocal = :lastModified WHERE projectId = :projectId")
    suspend fun updateDueDate(projectId: String, dueDate: Long, lastModified: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(project: ProjectEntity)
}
