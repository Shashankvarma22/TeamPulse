package com.cutm.TeamPulse.data.remote.dto

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class SheetsValuesResponse(
    val range: String?,
    val majorDimension: String?,
    val values: List<List<String>>?,
)
