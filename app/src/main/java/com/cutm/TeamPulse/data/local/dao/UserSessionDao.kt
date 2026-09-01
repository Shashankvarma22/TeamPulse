package com.cutm.TeamPulse.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.cutm.TeamPulse.data.local.entity.UserSessionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface UserSessionDao {

    @Query("SELECT * FROM user_sessions ORDER BY lastSignInAt DESC LIMIT 1")
    fun observeCurrentSession(): Flow<UserSessionEntity?>

    @Query("SELECT * FROM user_sessions ORDER BY lastSignInAt DESC LIMIT 1")
    suspend fun getActive(): UserSessionEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(session: UserSessionEntity)

    @Query("DELETE FROM user_sessions")
    suspend fun clear()
}
