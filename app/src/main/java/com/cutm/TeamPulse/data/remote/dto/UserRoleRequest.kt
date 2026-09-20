package com.cutm.TeamPulse.data.remote.dto

import com.squareup.moshi.Json

data class UserRoleRequest(
    @Json(name = "email")
    val email: String,
)
