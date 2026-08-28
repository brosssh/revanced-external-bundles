package me.brosssh.bundles.integrations.gitea

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class GiteaRepoDto(
    @SerialName("name")
    val repoName: String,
    @SerialName("description")
    val repoDescription: String?,
    @SerialName("stars_count")
    val stars: Int,
    @SerialName("owner")
    val owner: GiteaOwnerDto,
    @SerialName("archived")
    val archived: Boolean,
    // Forgejo may return null here for some repositories.
    @SerialName("pushed_at")
    val pushedAt: String? = null
)

@Serializable
data class GiteaOwnerDto(
    @SerialName("login")
    val name: String,
    @SerialName("avatar_url")
    val avatarUrl: String
)

@Serializable
data class GiteaReleaseDto(
    @SerialName("tag_name")
    val tagName: String,
    val body: String?,
    val prerelease: Boolean,
    val draft: Boolean = false,
    @SerialName("created_at")
    val createdAt: String = "",
    val assets: List<GiteaAssetDto> = emptyList()
)

@Serializable
data class GiteaAssetDto(
    val name: String,
    @SerialName("browser_download_url")
    val browserDownloadUrl: String,
    // Gitea rarely populates this; kept nullable.
    val digest: String? = null
)
