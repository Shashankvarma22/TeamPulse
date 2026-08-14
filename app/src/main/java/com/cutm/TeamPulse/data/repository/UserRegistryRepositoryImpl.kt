package com.cutm.TeamPulse.data.repository

import com.cutm.TeamPulse.core.auth.SessionRole
import com.cutm.TeamPulse.core.config.SheetsConfig
import com.cutm.TeamPulse.core.network.ApiResult
import com.cutm.TeamPulse.data.remote.SheetsApiService
import com.cutm.TeamPulse.domain.repository.UserRegistryRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Reads from the central Users Registry Sheet to determine whether a signed-in
 * user is a Teacher or Student. The Users Registry Sheet has columns:
 * A: email, B: display_name, C: role (TEACHER or STUDENT), D: enrolled_at, E: status, F: notes
 *
 * Row 1 is the header; data starts at row 2.
 */
@Singleton
class UserRegistryRepositoryImpl @Inject constructor(
    private val sheetsApiService: SheetsApiService,
) : UserRegistryRepository {

    override suspend fun lookupUser(email: String): ApiResult<SessionRole> =
        withContext(Dispatchers.IO) {
            try {
                val response = sheetsApiService.getValues(
                    spreadsheetId = SheetsConfig.USERS_REGISTRY_SPREADSHEET_ID,
                    range = SheetsConfig.USERS_REGISTRY_RANGE,
                )

                // Skip header row (row 1), check rows starting from index 1
                val userRow = response.values
                    ?.drop(1) // Skip header
                    ?.firstOrNull { row ->
                        // Row format: [email, display_name, role, enrolled_at, status, notes]
                        row.isNotEmpty() && row[0].equals(email, ignoreCase = true)
                    }

                if (userRow == null) {
                    return@withContext ApiResult.Error(
                        message = "User not found in Users Registry. Please contact your teacher to be enrolled.",
                    )
                }

                // Column C (index 2) contains the role
                val roleString = userRow.getOrNull(2)?.uppercase()

                val role = when (roleString) {
                    "TEACHER" -> SessionRole.TEACHER
                    "STUDENT" -> SessionRole.STUDENT
                    else -> {
                        return@withContext ApiResult.Error(
                            message = "Invalid role in Users Registry: $roleString. Please contact support.",
                        )
                    }
                }

                ApiResult.Success(role)
            } catch (e: Exception) {
                ApiResult.Error(
                    message = "Failed to lookup user role: ${e.message ?: "Unknown error"}",
                )
            }
        }
}
