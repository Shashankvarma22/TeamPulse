package com.cutm.TeamPulse.core.auth

import android.app.Activity
import android.content.Intent

/**
 * Requests the incremental (Sheets read-only) OAuth authorization on top of
 * the existing Credential Manager identity sign-in. This is intentionally
 * narrow: it only knows how to ask for/parse an authorization outcome. It
 * never holds an ActivityResultLauncher and never touches UI state — that is
 * the caller's (SignInFragment's) job.
 */
interface AuthorizationManager {

    /**
     * Requests read-only access to the Users Registry spreadsheet. May
     * complete immediately with [AuthorizationOutcome.Authorized], or may
     * return [AuthorizationOutcome.ResolutionRequired] if the user needs to
     * consent via a system UI first.
     */
    suspend fun requestSheetsReadAuthorization(activity: Activity): AuthorizationOutcome

    /**
     * Parses the Intent delivered to an ActivityResultLauncher callback after
     * resolving an [AuthorizationOutcome.ResolutionRequired]. Expected to
     * resolve to [AuthorizationOutcome.Authorized] or
     * [AuthorizationOutcome.Error] — a further resolution is not expected
     * at this stage.
     */
    suspend fun handleAuthorizationResult(intent: Intent): AuthorizationOutcome
}
