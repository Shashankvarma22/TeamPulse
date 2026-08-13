package com.cutm.TeamPulse.data.local.converter

import androidx.room.TypeConverter
import com.cutm.TeamPulse.core.auth.SessionRole
import com.cutm.TeamPulse.domain.model.ProjectStatus
import com.cutm.TeamPulse.domain.model.SyncOperationType
import com.cutm.TeamPulse.domain.model.SyncQueueStatus
import com.cutm.TeamPulse.domain.model.TaskStatus
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types

class Converters {

    private val moshi = Moshi.Builder().build()
    private val stringListType = Types.newParameterizedType(List::class.java, String::class.java)
    private val stringListAdapter = moshi.adapter<List<String>>(stringListType)

    @TypeConverter
    fun fromStringList(value: List<String>): String {
        return stringListAdapter.toJson(value)
    }

    @TypeConverter
    fun toStringList(value: String): List<String> {
        return stringListAdapter.fromJson(value) ?: emptyList()
    }

    @TypeConverter
    fun fromSessionRole(role: SessionRole): String = role.name

    @TypeConverter
    fun toSessionRole(value: String): SessionRole = SessionRole.valueOf(value)

    @TypeConverter
    fun fromProjectStatus(status: ProjectStatus): String = status.name

    @TypeConverter
    fun toProjectStatus(value: String): ProjectStatus = ProjectStatus.valueOf(value)

    @TypeConverter
    fun fromTaskStatus(status: TaskStatus): String = status.name

    @TypeConverter
    fun toTaskStatus(value: String): TaskStatus = TaskStatus.valueOf(value)

    @TypeConverter
    fun fromSyncOperationType(type: SyncOperationType): String = type.name

    @TypeConverter
    fun toSyncOperationType(value: String): SyncOperationType = SyncOperationType.valueOf(value)

    @TypeConverter
    fun fromSyncQueueStatus(status: SyncQueueStatus): String = status.name

    @TypeConverter
    fun toSyncQueueStatus(value: String): SyncQueueStatus = SyncQueueStatus.valueOf(value)
}
