package me.brosssh.bundles.domain.models

import java.time.OffsetDateTime

data class RefreshJob(
    val jobId: String,
    val type: RefreshJobType,
    val status: RefreshJobStatus,
    val startedAt: OffsetDateTime,
    val completedAt: OffsetDateTime? = null,
    val error: String? = null
) {
    enum class RefreshJobType {
        BUNDLES,
        PATCHES
    }

    enum class RefreshJobStatus {
        STARTED,
        COMPLETED,
        FAILED
    }
}

