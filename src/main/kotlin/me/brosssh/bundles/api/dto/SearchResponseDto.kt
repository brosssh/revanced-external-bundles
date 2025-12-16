package me.brosssh.bundles.api.dto

import kotlinx.serialization.Serializable

@Serializable
data class SearchResponseDto(
    val ownerName: String,
    val ownerAvatarUrl: String,
    val repoName: String,
    val repoDescription: String?,
    val sourceUrl: String,
    val repoStars: Int,

    val bundleId: Int,
    val createdAt: String,
    val description: String,
    val version: String,
    val downloadUrl: String,
    val signatureDownloadUrl: String,
    val isPrerelease: Boolean,

    val patches: List<SearchResponsePatchDto>
)

@Serializable
data class SearchResponsePatchDto(
    val name: String?,
    val description: String?,
    val compatiblePackages: List<SearchResponsePatchPackageDto>
)

@Serializable
data class SearchResponsePatchPackageDto(
    val name: String,
    val versions: List<String?>,
)
