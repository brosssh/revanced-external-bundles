package me.brosssh.bundles.api.v3.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import me.brosssh.bundles.domain.models.Bundle
import me.brosssh.bundles.integrations.common.ParsedRepoUrl

/** A cached bundle together with the registered source that owns it. */
@Serializable
data class BundleResponseDto(
    @SerialName("created_at")
    val createdAt: String,
    val description: String,
    val version: String,
    @SerialName("download_url")
    val downloadUrl: String,
    @SerialName("signature_download_url")
    val signatureDownloadUrl: String,
    @SerialName("bundle_type")
    val bundleType: String,
    val source: BundleSourceResponseDto
)

@Serializable
data class BundleSourceResponseDto(
    val url: String,
    val host: String,
    val namespace: String,
    val repo: String
)

fun Bundle.toResponseDto(source: ParsedRepoUrl) = BundleResponseDto(
    createdAt = createdAt.substringBefore("Z"),
    description = description ?: "",
    version = version,
    downloadUrl = downloadUrl,
    signatureDownloadUrl = signatureDownloadUrl ?: "",
    bundleType = bundleType.value,
    source = BundleSourceResponseDto(
        url = source.canonicalUrl,
        host = source.authority,
        namespace = source.ref.namespace,
        repo = source.ref.repo
    )
)
