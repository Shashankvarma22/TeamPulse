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

    @Query("SELECT * FROM projects ORDER BY lastModifiedLocal DESC")
    suspend fun getAllSync(): List<ProjectEntity>

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

    // TEMPORARY DEBUG: Check all projects in DB
    @Query("SELECT projectId, name, teacherEmail, status FROM projects ORDER BY lastModifiedLocal DESC")
    suspend fun debugGetAllProjects(): List<DebugProject>
}

// TEMPORARY DEBUG: Lightweight project data for logging
data class DebugProject(
    val projectId: String,
    val name: String,
    val teacherEmail: String,
    val status: com.cutm.TeamPulse.domain.model.ProjectStatus
)
