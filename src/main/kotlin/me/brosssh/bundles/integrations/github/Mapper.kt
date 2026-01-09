package me.brosssh.bundles.integrations.github

import me.brosssh.bundles.domain.models.Bundle
import me.brosssh.bundles.domain.models.BundleImportError
import me.brosssh.bundles.domain.models.BundleMetadata
import me.brosssh.bundles.domain.models.BundleType
import me.brosssh.bundles.domain.models.SourceMetadata

fun GithubRepoDto.toDomainModel(sourceId: Int) = SourceMetadata(
        id = sourceId,
        ownerName = owner.name,
        ownerAvatarUrl = owner.avatarUrl,
        repoName = repoName,
        repoDescription = repoDescription,
        repoStars = stars
    )

private fun String.toBundleType(): BundleType = when {
    endsWith(".rvp") -> BundleType.REVANCED_V4
    endsWith(".mpp") -> BundleType.MORPHE_V1
    endsWith(".jar") -> BundleType.REVANCED_V3
    else -> throw BundleImportError.ReleaseFileNotFoundError()
}

fun GithubReleaseDto.toDomainModel(sourceId: Int): BundleMetadata {
    val asset = assets
        .firstOrNull {
            it.name.endsWith(".rvp") ||
                    it.name.endsWith(".mpp") ||
                    it.name.endsWith(".jar")
        }
        ?: throw BundleImportError.ReleaseFileNotFoundError()

    val bundleType = asset.name.toBundleType()
    val downloadUrl = asset.browserDownloadUrl
    val digestHash = asset.digest

    return BundleMetadata(
        bundle = Bundle.create(
            bundleType,
            tagName,
            body,
            createdAt,
            downloadUrl,
            assets.firstOrNull { it.name.endsWith(".asc") }?.browserDownloadUrl,
            sourceId
        ),
        fileHash = digestHash,
        isPrerelease = prerelease
    )
}
