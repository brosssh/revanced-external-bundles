package me.brosssh.bundles.domain.models

import me.brosssh.bundles.api.dto.BundleResponseDto

data class Bundle(
    val version: String,
    val description: String?,
    val createdAt: String,
    val downloadUrl: String,
    val signatureDownloadUrl: String?,
    val sourceFk: Int
)

data class BundleMetadata(
    val bundle: Bundle,
    val isPrerelease: Boolean,
    val fileHash: String?,
    val isBundleV3: Boolean
)

sealed class BundleImportError : Exception() {
    class ReleaseFileNotFoundError : BundleImportError()
}

fun Bundle.toResponseDto() = BundleResponseDto(
    createdAt = createdAt.substringBefore("Z"),
    description = description ?: "",
    version = version,
    downloadUrl = downloadUrl,
    signatureDownloadUrl = signatureDownloadUrl ?: ""
)
