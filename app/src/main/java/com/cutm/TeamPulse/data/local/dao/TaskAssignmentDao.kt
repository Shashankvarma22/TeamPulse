package com.cutm.TeamPulse.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.cutm.TeamPulse.data.local.entity.TaskAssignmentEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TaskAssignmentDao {

    @Query("SELECT * FROM task_assignments WHERE teamId = :teamId ORDER BY dueDate ASC")
    fun observeByTeam(teamId: String): Flow<List<TaskAssignmentEntity>>

    @Query("SELECT * FROM task_assignments WHERE assigneeEmail = :email ORDER BY dueDate ASC")
    fun observeByAssignee(email: String): Flow<List<TaskAssignmentEntity>>

    @Query("SELECT * FROM task_assignments WHERE taskId = :taskId")
    suspend fun getById(taskId: String): TaskAssignmentEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(task: TaskAssignmentEntity)

    @Query("DELETE FROM task_assignments WHERE taskId = :taskId")
    suspend fun deleteById(taskId: String)

    @Query("DELETE FROM task_assignments WHERE teamId = :teamId")
    suspend fun deleteByTeam(teamId: String)

    @Query("SELECT * FROM task_assignments WHERE teamId = :teamId")
    suspend fun getByTeamSync(teamId: String): List<TaskAssignmentEntity>
}
