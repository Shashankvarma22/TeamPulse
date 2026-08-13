package com.cutm.TeamPulse.core.sheets

import com.cutm.TeamPulse.core.config.SheetsConfig
import com.cutm.TeamPulse.core.network.ApiResult
import com.cutm.TeamPulse.data.remote.SheetsApiService
import javax.inject.Inject
import javax.inject.Singleton
import retrofit2.HttpException

@Singleton
class SheetsProbeReaderImpl @Inject constructor(
    private val sheetsApiService: SheetsApiService,
) : SheetsProbeReader {

    override suspend fun readUsersRegistryRowCount(): ApiResult<Int> {
        return try {
            val response = sheetsApiService.getValues(
                spreadsheetId = SheetsConfig.USERS_REGISTRY_SPREADSHEET_ID,
                range = SheetsConfig.USERS_REGISTRY_RANGE,
            )
            ApiResult.Success(response.values?.size ?: 0)
        } catch (e: HttpException) {
            ApiResult.Error(
                message = "Failed to read Users Registry (HTTP ${e.code()}).",
                cause = e,
            )
        } catch (e: Exception) {
            ApiResult.Error(
                message = e.message ?: "Failed to read Users Registry.",
                cause = e,
            )
        }
    }
}
