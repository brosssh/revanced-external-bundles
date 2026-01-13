package me.brosssh.bundles.api.dto

import kotlinx.serialization.Serializable
import me.brosssh.bundles.domain.models.RefreshJob

@Serializable
data class JobStatusResponseDto(
    val jobId: String,
    val type: String,
    val status: String,
    val startedAt: String,
    val completedAt: String? = null,
    val error: String? = null
)

fun RefreshJob.toResponseDto() = JobStatusResponseDto(
    jobId = jobId,
    type = type.toString(),
    status = status.toString(),
    startedAt = startedAt.toInstant().toString(),
    completedAt = completedAt?.toInstant().toString(),
    error = error
)
