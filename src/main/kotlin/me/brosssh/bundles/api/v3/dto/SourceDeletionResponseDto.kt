package me.brosssh.bundles.api.v3.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import me.brosssh.bundles.domain.models.SourceDeletionResult

@Serializable
data class SourceDeletionResponseDto(
    @SerialName("deleted_sources")
    val deletedSources: Int,
    @SerialName("deleted_bundles")
    val deletedBundles: Int,
    @SerialName("deleted_patches")
    val deletedPatches: Int
)

fun SourceDeletionResult.Deleted.toResponseDto() = SourceDeletionResponseDto(
    deletedSources = sources,
    deletedBundles = bundles,
    deletedPatches = patches
)
