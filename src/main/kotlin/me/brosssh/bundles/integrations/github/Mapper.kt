package me.brosssh.bundles.integrations.github

import me.brosssh.bundles.domain.models.SourceMetadata

fun GithubRepoDto.toDomainModel(sourceId: Int): SourceMetadata {
    return SourceMetadata(
        id = sourceId,
        ownerName = owner.name,
        ownerAvatarUrl = owner.avatarUrl,
        repoName = repoName,
        repoDescription = repoDescription,
        repoStars = stars
    )
}
