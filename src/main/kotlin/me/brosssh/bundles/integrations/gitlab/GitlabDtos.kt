package me.brosssh.bundles.integrations.gitlab

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class GitlabProjectDto(
    @SerialName("star_count")
    val stars: Int? = 0,
    val description: String? = null,
    // GitLab may return null here.
    val archived: Boolean? = false,
    @SerialName("last_activity_at")
    val lastActivityAt: String? = null,
    val path: String = "",
    val namespace: GitlabNamespaceDto? = null,
    val owner: GitlabOwnerDto? = null
)

@Serializable
data class GitlabNamespaceDto(
    val name: String? = null,
    @SerialName("avatar_url")
    val avatarUrl: String? = null
)

@Serializable
data class GitlabOwnerDto(
    val username: String? = null,
    @SerialName("avatar_url")
    val avatarUrl: String? = null
)

@Serializable
data class GitlabReleaseDto(
    @SerialName("tag_name")
    val tagName: String,
    val description: String? = null,
    @SerialName("released_at")
    val releasedAt: String? = null,
    @SerialName("created_at")
    val createdAt: String? = null,
    val assets: GitlabAssetsDto = GitlabAssetsDto()
)

@Serializable
data class GitlabAssetsDto(
    // GitLab release "links" are user-provided download links (where bundle files are attached).
    val links: List<GitlabLinkDto> = emptyList()
)

@Serializable
data class GitlabLinkDto(
    val name: String = "",
    val url: String = ""
)
