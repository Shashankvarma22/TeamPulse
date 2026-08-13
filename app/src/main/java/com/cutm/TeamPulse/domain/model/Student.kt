package com.cutm.TeamPulse.domain.model

data class Student(
    val studentEmail: String,
    val displayName: String,
    val teamId: String,
    val projectId: String,
    val joinedAt: Long,
    val localDirty: Boolean,
)
