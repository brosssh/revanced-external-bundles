package me.brosssh.bundles.models.frontend

import kotlinx.serialization.Serializable

@Serializable
data class SearchResponseDto(
    //val owner: String,
    //val repo: String,
    val sourceUrl: String,
    //val stars: Int,

    val bundleId: Int,
    val createdAt: String,
    val description: String,
    val version: String,
    val downloadUrl: String,
    val signatureDownloadUrl: String,

    val patches: List<SearchResponsePatchDto>
)

@Serializable
data class SearchResponsePatchDto(
    val name: String?,
    val description: String?
)
