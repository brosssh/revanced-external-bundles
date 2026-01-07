package me.brosssh.bundles.api.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class BundleResponseDto(
    @SerialName("created_at")
    val createdAt: String,
    val description: String,
    val version: String,
    @SerialName("download_url")
    val downloadUrl: String,
    @SerialName("signature_download_url")
    val signatureDownloadUrl: String
)

@Serializable
data class BundleMetadataResponseDto(
    val sourceUrl: String,
    val ownerName: String,
    val ownerAvatarUrl: String,
    val repoName: String,
    val repoDescription: String?,
    val repoStars: Int
)
