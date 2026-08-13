package com.cutm.TeamPulse.data.remote

import com.cutm.TeamPulse.data.remote.dto.SheetsValuesResponse
import retrofit2.http.GET
import retrofit2.http.Path

interface SheetsApiService {

    @GET("v4/spreadsheets/{spreadsheetId}/values/{range}")
    suspend fun getValues(
        @Path("spreadsheetId") spreadsheetId: String,
        @Path("range") range: String,
    ): SheetsValuesResponse
}
