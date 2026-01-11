package me.brosssh.bundles.integrations.github

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class GithubReleaseDto(
    @SerialName("tag_name")
    val tagName: String,
    val body: String,
    val prerelease: Boolean,
    @SerialName("created_at")
    val createdAt: String,
    val assets: List<GithubAssetDto>
)

@Serializable
data class GithubAssetDto(
    val name: String,
    @SerialName("browser_download_url")
    val browserDownloadUrl: String,
    val digest: String? // GitHub doesn't always compute this, idk why
)

@Serializable
data class GithubRepoDto(
    @SerialName("name")
    val repoName: String,
    @SerialName("description")
    val repoDescription: String?,
    @SerialName("stargazers_count")
    val stars: Int,
    @SerialName("owner")
    val owner: GithubRepoOwnerDto,
    @SerialName("archived")
    val archived: Boolean,
    @SerialName("pushed_at")
    val pushedAt: String
)

@Serializable
data class GithubRepoOwnerDto(
    @SerialName("login")
    val name: String,
    @SerialName("avatar_url")
    val avatarUrl: String
)
