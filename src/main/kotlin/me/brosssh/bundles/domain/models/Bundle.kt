package me.brosssh.bundles.domain.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Bundle(
    @SerialName("created_at")
    val createdAt: String,
    val description: String,
    val version: String,
    @SerialName("download_url")
    val downloadUrl: String,
    @SerialName("signature_download_url")
    val signatureDownloadUrl: String
)
