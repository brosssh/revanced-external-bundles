package me.brosssh.bundles.integrations.gitlab

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import me.brosssh.bundles.integrations.common.AssetInfo
import me.brosssh.bundles.integrations.common.GitHostClient
import me.brosssh.bundles.integrations.common.GitHostCredentials
import me.brosssh.bundles.integrations.common.GitHostClientFactory
import me.brosssh.bundles.integrations.common.RepoInfo
import me.brosssh.bundles.integrations.common.RepoRef
import me.brosssh.bundles.integrations.common.ReleaseInfo
import me.brosssh.bundles.integrations.common.resolveAvatar
import java.time.OffsetDateTime
import java.time.temporal.ChronoUnit

// GitLab has no native prerelease flag, so use the SemVer prerelease component.
private val semanticVersionPrerelease = Regex(
    """^[vV]?(?:0|[1-9]\d*)\.(?:0|[1-9]\d*)\.(?:0|[1-9]\d*)-""" +
        """[0-9A-Za-z-]+(?:\.[0-9A-Za-z-]+)*(?:\+[0-9A-Za-z-]+(?:\.[0-9A-Za-z-]+)*)?$"""
)

internal fun isGitlabPrereleaseTag(tagName: String): Boolean =
    semanticVersionPrerelease.matches(tagName)

internal fun normalizeGitlabTimestamp(value: String?): String =
    value
        ?.takeIf { it.isNotBlank() }
        ?.let {
            OffsetDateTime.parse(it)
                .toInstant()
                .truncatedTo(ChronoUnit.SECONDS)
                .toString()
        }
        .orEmpty()

/**
 * Client for GitLab (gitlab.com and self-hosted instances).
 * Uses the GitLab REST API at `<baseUrl>/api/v4`. Project identifiers are URL-encoded
 * `owner/repo` paths (slashes become `%2F`). An authority-specific PAT is optional.
 */
class GitlabHostClientFactory(
    private val client: HttpClient,
    private val credentials: GitHostCredentials
) : GitHostClientFactory {
    override fun create(scheme: String, authority: String): GitHostClient =
        GitlabHostClient(client, "$scheme://$authority", credentials.patFor(authority))
}

class GitlabHostClient(
    private val client: HttpClient,
    private val baseUrl: String,
    private val pat: String? = null
) : GitHostClient {

    private fun HttpRequestBuilder.authenticate() {
        pat?.let { header("PRIVATE-TOKEN", it) }
    }

    private fun projectId(ref: RepoRef) = "${ref.namespace}/${ref.repo}".replace("/", "%2F")

    override suspend fun getRepo(ref: RepoRef): RepoInfo {
        val project = client
            .get("$baseUrl/api/v4/projects/${projectId(ref)}") {
                authenticate()
            }
            .body<GitlabProjectDto>()

        val ownerName = project.namespace?.name ?: project.owner?.username ?: ""
        val ownerAvatarUrl = resolveAvatar(
            project.namespace?.avatarUrl ?: project.owner?.avatarUrl,
            baseUrl
        )

        return RepoInfo(
            ownerName = ownerName,
            ownerAvatarUrl = ownerAvatarUrl,
            repoName = project.path,
            repoDescription = project.description,
            repoStars = project.stars ?: 0,
            isRepoArchived = project.archived ?: false,
            repoPushedAt = normalizeGitlabTimestamp(project.lastActivityAt)
        )
    }

    override suspend fun getReleases(ref: RepoRef): List<ReleaseInfo> {
        val releases = mutableListOf<ReleaseInfo>()
        var nextPage: Int? = 1

        while (nextPage != null) {
            val page = nextPage
            val response = client
                .get("$baseUrl/api/v4/projects/${projectId(ref)}/releases?per_page=100&page=$page") {
                    authenticate()
                }
            releases += response.body<List<GitlabReleaseDto>>().map { it.toReleaseInfo() }
            nextPage = parseNextPage(response.headers["X-Next-Page"], page)
        }

        return releases
    }

    private fun parseNextPage(header: String?, currentPage: Int): Int? {
        if (header.isNullOrBlank()) return null
        val nextPage = header.toIntOrNull()
            ?: error("GitLab returned an invalid X-Next-Page header.")
        check(nextPage > currentPage) {
            "GitLab returned a non-advancing X-Next-Page header."
        }
        return nextPage
    }
}

fun GitlabReleaseDto.toReleaseInfo() = ReleaseInfo(
    tagName = tagName,
    body = description ?: "",
    prerelease = isGitlabPrereleaseTag(tagName),
    createdAt = (releasedAt ?: createdAt ?: ""),
    assets = assets.links.map { AssetInfo(it.name, it.url, null) }
)
