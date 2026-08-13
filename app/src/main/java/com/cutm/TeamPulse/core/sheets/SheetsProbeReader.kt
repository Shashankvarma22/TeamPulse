package com.cutm.TeamPulse.core.sheets

import com.cutm.TeamPulse.core.network.ApiResult

/**
 * Reads only a row COUNT from the Users Registry sheet, as a proof-of-access
 * step after Sheets read authorization. Intentionally separate from
 * UserRegistryRepository (which stays a stub this checkpoint) and never
 * exposes row contents to callers — keeping raw sheet data out of the
 * ViewModel/UI entirely.
 */
interface SheetsProbeReader {

    suspend fun readUsersRegistryRowCount(): ApiResult<Int>
}
