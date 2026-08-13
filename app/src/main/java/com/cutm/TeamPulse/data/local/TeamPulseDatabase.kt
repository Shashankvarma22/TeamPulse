package com.cutm.TeamPulse.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.cutm.TeamPulse.data.local.converter.Converters
import com.cutm.TeamPulse.data.local.dao.ProjectDao
import com.cutm.TeamPulse.data.local.dao.StudentDao
import com.cutm.TeamPulse.data.local.dao.SyncMetadataDao
import com.cutm.TeamPulse.data.local.dao.SyncQueueDao
import com.cutm.TeamPulse.data.local.dao.TaskAssignmentDao
import com.cutm.TeamPulse.data.local.dao.TeamDao
import com.cutm.TeamPulse.data.local.dao.UserSessionDao
import com.cutm.TeamPulse.data.local.entity.ProjectEntity
import com.cutm.TeamPulse.data.local.entity.StudentEntity
import com.cutm.TeamPulse.data.local.entity.SyncMetadataEntity
import com.cutm.TeamPulse.data.local.entity.SyncQueueEntity
import com.cutm.TeamPulse.data.local.entity.TaskAssignmentEntity
import com.cutm.TeamPulse.data.local.entity.TeamEntity
import com.cutm.TeamPulse.data.local.entity.UserSessionEntity

@Database(
    entities = [
        UserSessionEntity::class,
        ProjectEntity::class,
        TeamEntity::class,
        StudentEntity::class,
        TaskAssignmentEntity::class,
        SyncQueueEntity::class,
        SyncMetadataEntity::class,
    ],
    version = 1,
    exportSchema = false,
)
@TypeConverters(Converters::class)
abstract class TeamPulseDatabase : RoomDatabase() {

    abstract fun userSessionDao(): UserSessionDao
    abstract fun projectDao(): ProjectDao
    abstract fun teamDao(): TeamDao
    abstract fun studentDao(): StudentDao
    abstract fun taskAssignmentDao(): TaskAssignmentDao
    abstract fun syncQueueDao(): SyncQueueDao
    abstract fun syncMetadataDao(): SyncMetadataDao
}
