package me.brosssh.bundles.integrations.common

import java.net.URI

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

/**
 * The bundle type value for this asset, derived from its file extension.
 *
 * The extension is detected from both the [name] and the [browserDownloadUrl] because some
 * hosts (e.g. GitLab) may expose a human-readable link title in [name] while the actual file
 * extension lives in the download URL.
 */
fun AssetInfo.bundleTypeValue(): String? {
    return when {
        name.hasExtension(".rvp") ||
            browserDownloadUrl.hasExtension(".rvp") -> "ReVanced:V4"

        name.hasExtension(".mpp") ||
            browserDownloadUrl.hasExtension(".mpp") -> "Morphe:V1"

        name.hasExtension(".jar") ||
            browserDownloadUrl.hasExtension(".jar") -> "ReVanced:V3"

        else -> null
    }
}

/** Whether this asset is a detached signature (`.asc`). */
fun AssetInfo.isSignature(): Boolean =
    name.hasExtension(".asc") ||
        browserDownloadUrl.hasExtension(".asc")

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
