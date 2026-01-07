package me.brosssh.bundles.api.dto

import kotlinx.serialization.Serializable

@Serializable
data class SnapshotResponseDto(
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
    val isBundleV3: Boolean,

    val patches: List<SnapshotPatchResponseDto>
)

@Serializable
data class SnapshotPatchResponseDto(
    val name: String?,
    val description: String?,
    val compatiblePackages: List<SnapshotPackageResponseDto>
)

@Serializable
data class SnapshotPackageResponseDto(
    val name: String,
    val versions: List<String?>,
)
