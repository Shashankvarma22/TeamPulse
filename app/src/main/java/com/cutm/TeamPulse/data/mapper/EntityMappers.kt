package com.cutm.TeamPulse.data.mapper

import com.cutm.TeamPulse.data.local.entity.ProjectEntity
import com.cutm.TeamPulse.data.local.entity.TaskAssignmentEntity
import com.cutm.TeamPulse.data.local.entity.TeamEntity
import com.cutm.TeamPulse.data.local.entity.UserSessionEntity
import com.cutm.TeamPulse.domain.model.Project
import com.cutm.TeamPulse.domain.model.TaskAssignment
import com.cutm.TeamPulse.domain.model.Team
import com.cutm.TeamPulse.domain.model.UserSession

fun UserSessionEntity.toDomain(): UserSession = UserSession(
    email = email,
    displayName = displayName,
    role = role,
    photoUrl = photoUrl,
    lastSignInAt = lastSignInAt,
)

fun UserSession.toEntity(): UserSessionEntity = UserSessionEntity(
    email = email,
    displayName = displayName,
    role = role,
    photoUrl = photoUrl,
    lastSignInAt = lastSignInAt,
)

fun ProjectEntity.toDomain(): Project = Project(
    projectId = projectId,
    name = name,
    teacherEmail = teacherEmail,
    spreadsheetId = spreadsheetId,
    driveFolderId = driveFolderId,
    startDate = startDate,
    dueDate = dueDate,
    status = status,
    githubRepo = githubRepo,
    localDirty = localDirty,
    lastModifiedLocal = lastModifiedLocal,
    lastSyncedAt = lastSyncedAt,
)

fun TeamEntity.toDomain(): Team = Team(
    teamId = teamId,
    projectId = projectId,
    teamName = teamName,
    memberEmails = memberEmails,
    createdAt = createdAt,
    localDirty = localDirty,
    lastModifiedLocal = lastModifiedLocal,
)

fun TaskAssignmentEntity.toDomain(): TaskAssignment = TaskAssignment(
    taskId = taskId,
    teamId = teamId,
    projectId = projectId,
    assigneeEmail = assigneeEmail,
    title = title,
    description = description,
    weight = weight,
    dueDate = dueDate,
    status = status,
    localDirty = localDirty,
    lastModifiedLocal = lastModifiedLocal,
    remoteRowIndex = remoteRowIndex,
)
