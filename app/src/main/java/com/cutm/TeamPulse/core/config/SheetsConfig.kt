package com.cutm.TeamPulse.core.config

object SheetsConfig {

    const val SHEETS_BASE_URL = "https://sheets.googleapis.com/"

    const val CLOUD_FUNCTIONS_BASE_URL = "https://teampulse-getuserrole.teampulse.workers.dev/"

    /**
     * Single shared cloud data store for ALL teachers and projects.
     * Contains tabs: Projects, Teams, Students, Tasks
     * Data is filtered by project_id and teacher_email — no spreadsheet-per-project.
     * Teachers and students never edit this directly; it's purely an API backend.
     */
    const val SHARED_DATA_SPREADSHEET_ID = "13X4HDjbZIZyb8_1AgFYpqpfSMzaKZq_Yvm3tTiFgwT8"

    const val USERS_REGISTRY_SPREADSHEET_ID =
        "1MzysETdhkqVYxPvlehkTxEXd1qG8y85cqmB6g2qmzK0"

    const val USERS_REGISTRY_RANGE = "Users!A:F"
}
