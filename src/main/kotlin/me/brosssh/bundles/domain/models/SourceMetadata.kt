package me.brosssh.bundles.domain.models

data class SourceMetadata(
    val id: Int,
    val ownerName: String,
    val ownerAvatarUrl: String,
    val repoName: String,
    val repoDescription: String?,
    val repoStars: Int,
    val isRepoArchived: Boolean,
    val repoPushedAt: String
)
