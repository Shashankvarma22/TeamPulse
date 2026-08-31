package com.cutm.TeamPulse.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Tracks gamification progress per student (XP, badges, task completion stats).
 * 
 * Separate from StudentEntity to distinguish identity/team-membership (stable)
 * from mutable game state (changes frequently with task completions).
 */
@Entity(
    tableName = "student_progress",
    foreignKeys = [
        ForeignKey(
            entity = StudentEntity::class,
            parentColumns = ["studentEmail"],
            childColumns = ["studentEmail"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["studentEmail"])
    ]
)
data class StudentProgressEntity(
    @PrimaryKey
    val studentEmail: String,              // FK to students.studentEmail
    
    val totalXp: Int = 0,                  // Cumulative XP earned
    val tasksCompleted: Int = 0,           // Count of tasks completed (status = COMPLETED)
    
    val hasTaskMasterBadge: Boolean = false,  // TaskMaster achievement unlocked?
    
    val lastModifiedLocal: Long,           // Timestamp for sync purposes (future)
    val localDirty: Boolean = false        // Sync flag (unused for now, but consistent with other entities)
)
