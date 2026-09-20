package com.cutm.TeamPulse.data.repository

import com.cutm.TeamPulse.core.auth.SessionRole
import com.cutm.TeamPulse.core.network.ApiResult
import com.cutm.TeamPulse.data.remote.CloudFunctionsApiService
import com.cutm.TeamPulse.data.remote.dto.UserRoleRequest
import com.cutm.TeamPulse.domain.repository.UserRegistryRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Reads user role from TeamPulse Cloud Function (getUserRole), which internally
 * queries the Users Registry Sheet using a service account.
 *
 * The Cloud Function verifies the Google ID token passed in the Authorization header,
 * ensuring only authenticated users can lookup their own role.
 *
 * This replaces direct Sheets API access, eliminating the need for public sheet access.
 */
@Singleton
class UserRegistryRepositoryImpl @Inject constructor(
    private val cloudFunctionsApiService: CloudFunctionsApiService,
) : UserRegistryRepository {

    override suspend fun lookupUser(email: String, idToken: String): ApiResult<SessionRole> =
        withContext(Dispatchers.IO) {
            try {
                val response = cloudFunctionsApiService.getUserRole(
                    authorization = "Bearer $idToken",
                    request = UserRoleRequest(email = email),
                )

                val role = when (response.role.uppercase()) {
                    "TEACHER" -> SessionRole.TEACHER
                    "STUDENT" -> SessionRole.STUDENT
                    else -> {
                        return@withContext ApiResult.Error(
                            message = "Invalid role returned: ${response.role}. Please contact support.",
                        )
                    }
                }

                ApiResult.Success(role)
            } catch (e: retrofit2.HttpException) {
                // Parse error response from Cloud Function
                val errorBody = e.response()?.errorBody()?.string()
                val errorMessage = when (e.code()) {
                    401 -> "Authentication failed. Please sign in again."
                    403 -> "Unauthorized access. Please contact support."
                    404 -> "User not found in Users Registry. Please contact your teacher to be enrolled."
                    else -> errorBody ?: "Failed to lookup user role: ${e.message}"
                }
                ApiResult.Error(message = errorMessage)
            } catch (e: Exception) {
                ApiResult.Error(
                    message = "Failed to lookup user role: ${e.message ?: "Unknown error"}",
                )
            }
        }
}
