package com.cutm.TeamPulse.data.remote

import com.cutm.TeamPulse.data.remote.dto.UserRoleRequest
import com.cutm.TeamPulse.data.remote.dto.UserRoleResponse
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST

/**
 * Cloudflare Worker API for getUserRole - verifies Google ID token and returns user role
 * from the TeamPulse_Users_Registry sheet.
 *
 * Authentication: Google ID token passed in Authorization header (Bearer token)
 * The Worker verifies the token and ensures users can only lookup their own role.
 */
interface UserRoleApiService {

    @POST("api/role")
    suspend fun getUserRole(
        @Header("Authorization") authorization: String,
        @Body request: UserRoleRequest,
    ): UserRoleResponse
}
