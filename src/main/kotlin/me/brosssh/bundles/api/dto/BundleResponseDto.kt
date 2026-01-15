package me.brosssh.bundles.api.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import me.brosssh.bundles.domain.models.Bundle

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

fun Bundle.toResponseDto() = BundleResponseDto(
    createdAt = createdAt.substringBefore("Z"),
    description = description ?: "",
    version = version,
    downloadUrl = downloadUrl,
    signatureDownloadUrl = signatureDownloadUrl ?: ""
)
