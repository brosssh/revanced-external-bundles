package me.brosssh.bundles.models

import kotlinx.serialization.Serializable

@Serializable
data class GithubReleaseDto(
    val tag_name: String,
    val prerelease: Boolean,
    val created_at: String,
    val assets: List<GithubAssetDto>
)

@Serializable
data class GithubAssetDto(
    val name: String,
    val browser_download_url: String
)

enum class ReleaseType {
    STABLE,
    PRERELEASE
}
