package com.cutm.TeamPulse.domain.model

/**
 * Domain model for student gamification progress.
 * 
 * Used for leaderboard display (includes joined displayName from StudentEntity).
 */
data class StudentProgress(
    val studentEmail: String,
    val displayName: String,  // Joined from StudentEntity for leaderboard display
    val totalXp: Int,
    val tasksCompleted: Int,
    val hasTaskMasterBadge: Boolean
)
