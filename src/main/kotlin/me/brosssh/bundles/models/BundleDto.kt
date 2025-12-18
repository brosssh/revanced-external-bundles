package me.brosssh.bundles.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class BundleDto(
    val createdAt: String,
    val description: String,
    val version: String,
    @SerialName("downloadUrl")
    val downloadUrl: String,
    @SerialName("signature_download_url")
    val signatureDownloadUrl: String
)
