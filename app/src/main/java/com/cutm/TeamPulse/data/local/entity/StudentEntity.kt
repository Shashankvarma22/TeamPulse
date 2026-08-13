package com.cutm.TeamPulse.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "students")
data class StudentEntity(
    @PrimaryKey val studentEmail: String,
    val displayName: String,
    val teamId: String,
    val projectId: String,
    val joinedAt: Long,
    val localDirty: Boolean,
)
