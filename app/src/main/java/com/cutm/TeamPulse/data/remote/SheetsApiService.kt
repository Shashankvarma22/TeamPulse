package com.cutm.TeamPulse.data.remote

import com.cutm.TeamPulse.data.remote.dto.SheetsValuesResponse
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.PUT
import retrofit2.http.POST
import retrofit2.http.Path

interface SheetsApiService {

    @GET("v4/spreadsheets/{spreadsheetId}/values/{range}")
    suspend fun getValues(
        @Path("spreadsheetId") spreadsheetId: String,
        @Path("range") range: String,
    ): SheetsValuesResponse

    @PUT("v4/spreadsheets/{spreadsheetId}/values/{range}?valueInputOption=RAW")
    suspend fun updateValues(
        @Path("spreadsheetId") spreadsheetId: String,
        @Path("range") range: String,
        @Body body: SheetsUpdateRequest,
    ): SheetsValuesResponse

    @POST("v4/spreadsheets/{spreadsheetId}/values/{range}:append?valueInputOption=RAW")
    suspend fun appendValues(
        @Path("spreadsheetId") spreadsheetId: String,
        @Path("range") range: String,
        @Body body: SheetsUpdateRequest,
    ): SheetsValuesResponse
}

/**
 * DTO for Sheets API v4 update/append request body.
 * TRACED: Based on standard Sheets API v4 ValueRange format.
 */
data class SheetsUpdateRequest(
    val values: List<List<Any?>>
)
