package me.brosssh.bundles.integrations.common

import me.brosssh.bundles.domain.models.Bundle
import me.brosssh.bundles.domain.models.BundleImportError
import me.brosssh.bundles.domain.models.BundleMetadata
import me.brosssh.bundles.domain.models.BundleType
import me.brosssh.bundles.domain.models.SourceMetadata

fun RepoInfo.toDomainModel(sourceId: Int) = SourceMetadata(
    id = sourceId,
    ownerName = ownerName,
    ownerAvatarUrl = ownerAvatarUrl,
    repoName = repoName,
    repoDescription = repoDescription,
    repoStars = repoStars,
    isRepoArchived = isRepoArchived,
    repoPushedAt = repoPushedAt
)

fun ReleaseInfo.toDomainModel(sourceId: Int): BundleMetadata {
    val asset = assets
        .firstOrNull { it.bundleTypeValue() != null }
        ?: throw BundleImportError.ReleaseFileNotFoundError()

    val bundleType = BundleType.from(asset.bundleTypeValue()!!)
    val downloadUrl = asset.browserDownloadUrl
    val digestHash = asset.digest

    return BundleMetadata(
        bundle = Bundle.create(
            bundleType,
            tagName,
            body,
            createdAt,
            downloadUrl,
            assets.firstOrNull { it.isSignature() }?.browserDownloadUrl,
            sourceId
        ),
        fileHash = digestHash,
        isPrerelease = prerelease
    )
}
