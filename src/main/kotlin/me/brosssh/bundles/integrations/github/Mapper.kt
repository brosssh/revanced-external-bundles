package me.brosssh.bundles.integrations.github

import me.brosssh.bundles.domain.models.BundleImportError
import me.brosssh.bundles.domain.models.BundleMetadata
import me.brosssh.bundles.domain.models.SourceMetadata

fun GithubRepoDto.toDomainModel(sourceId: Int) = SourceMetadata(
        id = sourceId,
        ownerName = owner.name,
        ownerAvatarUrl = owner.avatarUrl,
        repoName = repoName,
        repoDescription = repoDescription,
        repoStars = stars
    )

fun GithubReleaseDto.toDomainModel(sourceId: Int): BundleMetadata {
    val (isBundleV3, downloadUrl, digestHash) =
        assets.firstOrNull { it.name.endsWith(".rvp") }?.let { asset ->
            Triple(false, asset.browserDownloadUrl, asset.digest)
        } ?: assets.firstOrNull { it.name.endsWith(".jar") }?.let { asset ->
            Triple(true, asset.browserDownloadUrl, asset.digest)
        } ?: throw BundleImportError.ReleaseFileNotFoundError()

    return BundleMetadata(
        sourceFk = sourceId,
        version = tagName,
        description = body,
        createdAt = createdAt,
        downloadUrl = downloadUrl,
        fileHash = digestHash,
        signatureDownloadUrl = assets.firstOrNull { it.name.endsWith(".rvp.asc") }?.browserDownloadUrl,
        isPrerelease = prerelease,
        isBundleV3 = isBundleV3
    )
}
