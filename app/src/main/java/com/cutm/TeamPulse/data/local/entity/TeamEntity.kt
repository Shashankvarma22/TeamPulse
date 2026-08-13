package com.cutm.TeamPulse.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "teams")
data class TeamEntity(
    @PrimaryKey val teamId: String,
    val projectId: String,
    val teamName: String,
    val memberEmails: List<String>,
    val createdAt: Long,
    val localDirty: Boolean,
    val lastModifiedLocal: Long,
)
