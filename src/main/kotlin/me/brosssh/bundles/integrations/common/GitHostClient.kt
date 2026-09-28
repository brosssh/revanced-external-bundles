package me.brosssh.bundles.integrations.common

import io.ktor.http.Headers
import io.ktor.http.HttpStatusCode
import me.brosssh.bundles.domain.models.BundleType
import java.net.URI
import java.time.OffsetDateTime

/**
 * Identifies a repository on any supported git host.
 * [namespace] may contain slashes for nested groups (e.g. "group/subgroup").
 */
data class RepoRef(val namespace: String, val repo: String)

data class ParsedRepoUrl(
    val scheme: String,
    val authority: String,
    val ref: RepoRef
) {
    val canonicalUrl: String
        get() = URI(
            scheme,
            authority,
            "/${ref.namespace}/${ref.repo}",
            null,
            null
        ).toASCIIString()
}

/**
 * Host-agnostic representation of a repository, mapped onto
 * [me.brosssh.bundles.domain.models.SourceMetadata].
 */
data class RepoInfo(
    val ownerName: String,
    val ownerAvatarUrl: String,
    val repoName: String,
    val repoDescription: String?,
    val repoStars: Int,
    val isRepoArchived: Boolean,
    val repoPushedAt: String
)

data class AssetInfo(
    val name: String,
    val browserDownloadUrl: String,
    val digest: String?
)

private fun String.hasExtension(extension: String): Boolean =
    substringBefore('?')
        .substringBefore('#')
        .endsWith(extension, ignoreCase = true)

private fun String.bundleTypeFromExtension(): BundleType? = when {
    hasExtension(".rvp") -> BundleType.REVANCED_V4
    hasExtension(".mpp") -> BundleType.MORPHE_V1
    hasExtension(".jar") -> BundleType.REVANCED_V3
    else -> null
}

private fun String.withoutQueryOrFragment(): String =
    substringBefore('?')
        .substringBefore('#')

private fun String.assetFileName(): String =
    withoutQueryOrFragment()
        .substringAfterLast('/')

private fun String.hasConcreteUrlFileExtension(): Boolean {
    val uri = runCatching { URI(this) }.getOrNull()
    val path = uri?.rawPath ?: withoutQueryOrFragment()
    val fileName = path.substringAfterLast('/')
    val dotIndex = fileName.lastIndexOf('.')
    return dotIndex > 0 && dotIndex < fileName.lastIndex
}

private fun String.bundleFileNameOrNull(): String? =
    takeIf { bundleTypeFromExtension() != null }?.assetFileName()

private fun String.signatureFileNameOrNull(): String? =
    takeIf { hasExtension(".asc") }?.assetFileName()

private fun String.equalsAssetName(other: String): Boolean =
    equals(other, ignoreCase = true)

private fun String.appendBeforeQueryOrFragment(suffix: String): String {
    val queryIndex = indexOf('?').takeIf { it >= 0 } ?: length
    val fragmentIndex = indexOf('#').takeIf { it >= 0 } ?: length
    val suffixStart = minOf(queryIndex, fragmentIndex)
    return substring(0, suffixStart) + suffix + substring(suffixStart)
}

private fun String.isSignaturePathFor(bundleUrl: String): Boolean {
    val signatureUri = runCatching { URI(this) }.getOrNull() ?: return false
    val bundleUri = runCatching { URI(bundleUrl) }.getOrNull() ?: return false
    if (!signatureUri.scheme.orEmpty().equals(bundleUri.scheme.orEmpty(), ignoreCase = true)) {
        return false
    }
    if (!signatureUri.rawAuthority.orEmpty()
            .equals(bundleUri.rawAuthority.orEmpty(), ignoreCase = true)
    ) {
        return false
    }
    return signatureUri.rawPath == "${bundleUri.rawPath}.asc"
}

private fun String.hasSameResourcePathAs(other: String): Boolean {
    val left = runCatching { URI(this) }.getOrNull() ?: return false
    val right = runCatching { URI(other) }.getOrNull() ?: return false
    if (!left.scheme.orEmpty().equals(right.scheme.orEmpty(), ignoreCase = true)) return false
    if (!left.rawAuthority.orEmpty().equals(right.rawAuthority.orEmpty(), ignoreCase = true)) {
        return false
    }
    return left.rawPath == right.rawPath
}

private fun String.hasSameQueryAs(other: String): Boolean {
    val left = runCatching { URI(this) }.getOrNull() ?: return false
    val right = runCatching { URI(other) }.getOrNull() ?: return false
    return left.rawQuery == right.rawQuery
}

private fun AssetInfo.resolvedBundleType(): BundleType? =
    browserDownloadUrl.bundleTypeFromExtension()
        ?: if (
            browserDownloadUrl.hasExtension(".asc") ||
            browserDownloadUrl.hasConcreteUrlFileExtension()
        ) {
            null
        } else {
            name.bundleTypeFromExtension()
        }

private fun AssetInfo.bundleFileNames(): Set<String> {
    browserDownloadUrl.bundleFileNameOrNull()?.let { return setOf(it) }
    if (
        browserDownloadUrl.hasExtension(".asc") ||
        browserDownloadUrl.hasConcreteUrlFileExtension()
    ) {
        return emptySet()
    }
    return name.bundleFileNameOrNull()?.let(::setOf) ?: emptySet()
}

private fun AssetInfo.signatureFileNames(): Set<String> {
    browserDownloadUrl.signatureFileNameOrNull()?.let { return setOf(it) }
    if (
        browserDownloadUrl.bundleTypeFromExtension() != null ||
        browserDownloadUrl.hasConcreteUrlFileExtension()
    ) {
        return emptySet()
    }
    return name.signatureFileNameOrNull()?.let(::setOf) ?: emptySet()
}

/**
 * The bundle type value for this asset, derived from its file extension.
 *
 * The download URL is authoritative when it contains a recognized extension because some hosts
 * (e.g. GitLab) may expose an arbitrary human-readable link title in [name]. The name is used when
 * the URL does not identify a bundle type.
 */
fun AssetInfo.explicitBundleType(): BundleType? {
    return resolvedBundleType()
        ?.takeUnless { it == BundleType.REVANCED_V3 }
}

/**
 * The generic JAR bundle type for this asset.
 */
fun AssetInfo.genericJarBundleType(): BundleType? {
    return resolvedBundleType()
        ?.takeIf { it == BundleType.REVANCED_V3 }
}

/**
 * Chooses a bundle type from the assets, preferring explicit bundle types
 * over generic JARs.
 */
fun Iterable<AssetInfo>.choosePatchBundle(): Pair<AssetInfo, BundleType>? {
    return firstNotNullOfOrNull { asset ->
        asset.explicitBundleType()?.let { asset to it }
    } ?: firstNotNullOfOrNull { asset ->
        asset.genericJarBundleType()?.let { asset to it }
    }
}

/** Whether this asset is a detached signature (`.asc`). */
fun AssetInfo.isSignature(): Boolean {
    if (browserDownloadUrl.bundleTypeFromExtension() != null) return false
    if (browserDownloadUrl.hasExtension(".asc")) return true
    if (browserDownloadUrl.hasConcreteUrlFileExtension()) return false
    return name.hasExtension(".asc")
}

/**
 * Chooses the detached signature associated with [bundleAsset]. If the release contains only one
 * bundle candidate, preserve the historical behavior of accepting its first signature even when
 * the signature does not include the bundle file name.
 */
fun Iterable<AssetInfo>.chooseSignature(bundleAsset: AssetInfo): AssetInfo? {
    val assets = toList()
    val signatures = assets.filter { it.isSignature() }
    val bundleCandidates = assets.filter { asset ->
        asset.resolvedBundleType() != null
    }

    bundleAsset.browserDownloadUrl
        .takeIf { it.bundleTypeFromExtension() != null }
        ?.appendBeforeQueryOrFragment(".asc")
        ?.let { expectedSignatureUrl ->
            val matches = signatures.filter { signature ->
                signature.browserDownloadUrl == expectedSignatureUrl
            }
            if (matches.size == 1) return matches.single()
            if (matches.size > 1) return null
        }

    bundleAsset.browserDownloadUrl
        .takeIf { it.bundleTypeFromExtension() != null }
        ?.let { bundleUrl ->
            val pathMatches = signatures.filter { signature ->
                signature.browserDownloadUrl.isSignaturePathFor(bundleUrl)
            }
            val queryMatches = pathMatches.filter { signature ->
                signature.browserDownloadUrl.hasSameQueryAs(bundleUrl)
            }
            if (queryMatches.size == 1) return queryMatches.single()
            if (queryMatches.size > 1) return null

            val bundlePathIsUnique = bundleCandidates.count { candidate ->
                candidate.browserDownloadUrl.bundleTypeFromExtension() != null &&
                    candidate.browserDownloadUrl.hasSameResourcePathAs(bundleUrl)
            } == 1
            if (pathMatches.size == 1 && bundlePathIsUnique) return pathMatches.single()
            if (pathMatches.isNotEmpty()) return null
        }

    val selectedFileNames = bundleAsset.bundleFileNames().filter { selectedName ->
        bundleCandidates.count { candidate ->
            candidate.bundleFileNames()
                .any { candidateName -> candidateName.equalsAssetName(selectedName) }
        } == 1
    }

    val matches = signatures.filter { signature ->
        signature.signatureFileNames().any { signatureName ->
            selectedFileNames.any { selectedName ->
                signatureName.equalsAssetName("$selectedName.asc")
            }
        }
    }
    if (matches.size == 1) return matches.single()
    if (matches.size > 1) return null

    return if (bundleCandidates.size == 1) signatures.firstOrNull() else null
}

data class ReleaseInfo(
    val tagName: String,
    val body: String,
    val prerelease: Boolean,
    val createdAt: String,
    val assets: List<AssetInfo>
)

/**
 * Abstraction over a git hosting provider used to discover repositories and their releases.
 * Implementations are expected to require no authentication for public data (tokens are only
 * used where configured, e.g. GitHub).
 */
interface GitHostClient {
    suspend fun getRepo(ref: RepoRef): RepoInfo
    suspend fun getReleases(ref: RepoRef): List<ReleaseInfo>

    /** Returns the provider-specific retry deadline when [status] and [headers] indicate throttling. */
    fun rateLimitDeadline(
        status: HttpStatusCode,
        headers: Headers,
        now: OffsetDateTime
    ): OffsetDateTime?

    /**
     * Response-body-aware rate-limit hook for providers that describe throttling in an error body.
     * Header-only clients retain the default behavior.
     */
    fun rateLimitDeadline(
        status: HttpStatusCode,
        headers: Headers,
        now: OffsetDateTime,
        responseBody: String?
    ): OffsetDateTime? = rateLimitDeadline(status, headers, now)
}

/** Creates a provider client for a resolved scheme and authority. */
fun interface GitHostClientFactory {
    fun create(scheme: String, authority: String): GitHostClient
}

/** Resolves an owner/repo avatar URL, making relative paths absolute against [baseUrl]. */
fun resolveAvatar(url: String?, baseUrl: String): String {
    if (url == null) return ""
    if (url.startsWith("/")) return "$baseUrl$url"
    return url
}

private fun URI.safeLocation(): String = buildString {
    append(scheme)
    append("://")
    append(rawAuthority.orEmpty().substringAfterLast('@'))
    append(rawPath.orEmpty())
}

/**
 * Parses and validates a repository URL. The namespace may contain slashes for nested groups.
 * Host selection is performed separately by [me.brosssh.bundles.integrations.HostResolver].
 */
fun parseRepoUrl(url: String): ParsedRepoUrl {
    val uri = runCatching { URI(url) }
        .getOrElse { throw IllegalArgumentException("Invalid repository URL.", it) }
    val scheme = uri.scheme?.lowercase()
        ?: throw IllegalArgumentException(
            "Repository URL is missing a scheme " +
                "(expected e.g. https://gitlab.com/namespace/repo)."
        )
    require(scheme == "http" || scheme == "https") {
        "Repository URL must use the http or https scheme."
    }
    require(uri.userInfo == null) {
        "Repository URL '${uri.safeLocation()}' must not contain user information."
    }
    require(!uri.host.isNullOrBlank()) {
        "Repository URL must contain a host."
    }
    val authority = uri.rawAuthority!!.lowercase()

    val parts = uri.path.orEmpty()
        .trim('/')
        .split('/')
        .filter { it.isNotEmpty() }
    require(parts.size >= 2) {
        "Repository URL path must contain a namespace and repo."
    }

    return ParsedRepoUrl(
        scheme = scheme,
        authority = authority,
        ref = RepoRef(
            namespace = parts.dropLast(1).joinToString("/"),
            repo = parts.last()
        )
    )
}
