package com.cutm.TeamPulse.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.cutm.TeamPulse.data.local.entity.StudentProgressEntity

@Dao
interface StudentProgressDao {
    
    /**
     * Get progress for a single student (null if not exists)
     */
    @Query("SELECT * FROM student_progress WHERE studentEmail = :email")
    suspend fun getByEmail(email: String): StudentProgressEntity?
    
    /**
     * Get all progress records for students in a team (for leaderboard)
     * Returns empty list if no records exist
     * 
     * Order: XP descending, then tasks completed descending, then email ascending (deterministic ties)
     */
    @Query("""
        SELECT sp.* FROM student_progress sp
        INNER JOIN students s ON sp.studentEmail = s.studentEmail
        WHERE s.teamId = :teamId
        ORDER BY sp.totalXp DESC, sp.tasksCompleted DESC, sp.studentEmail ASC
    """)
    suspend fun getByTeamOrderedByXp(teamId: String): List<StudentProgressEntity>
    
    /**
     * Insert or replace progress record
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(progress: StudentProgressEntity)
    
    /**
     * Delete progress for a student (cascade when student deleted)
     */
    @Query("DELETE FROM student_progress WHERE studentEmail = :email")
    suspend fun deleteByEmail(email: String)
}
