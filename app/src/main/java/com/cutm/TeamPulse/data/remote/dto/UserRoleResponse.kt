package com.cutm.TeamPulse.data.remote.dto

import com.squareup.moshi.Json

data class UserRoleResponse(
    @Json(name = "role")
    val role: String,

    @Json(name = "displayName")
    val displayName: String? = null,

    @Json(name = "enrolledAt")
    val enrolledAt: String? = null,

    @Json(name = "status")
    val status: String? = null,
)
