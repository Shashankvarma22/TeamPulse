package com.cutm.TeamPulse.domain.model

data class Team(
    val teamId: String,
    val projectId: String,
    val teamName: String,
    val memberEmails: List<String>,
    val createdAt: Long,
    val localDirty: Boolean,
    val lastModifiedLocal: Long,
)
