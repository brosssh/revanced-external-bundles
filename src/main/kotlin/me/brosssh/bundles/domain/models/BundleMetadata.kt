package me.brosssh.bundles.domain.models


data class BundleMetadata(
    val sourceFk: Int,
    val version: String,
    val description: String,
    val createdAt: String,
    val downloadUrl: String,
    val signatureDownloadUrl: String?,
    val isPrerelease: Boolean,
    val fileHash: String?,
    val isBundleV3: Boolean
)

sealed class BundleImportError : Exception() {
    class ReleaseFileNotFoundError : BundleImportError()
}
