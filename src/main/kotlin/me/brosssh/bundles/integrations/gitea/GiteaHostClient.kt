package me.brosssh.bundles.integrations.gitea

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.HttpHeaders
import me.brosssh.bundles.integrations.common.GitHostClient
import me.brosssh.bundles.integrations.common.GitHostCredentials
import me.brosssh.bundles.integrations.common.GitHostClientFactory
import me.brosssh.bundles.integrations.common.RepoInfo
import me.brosssh.bundles.integrations.common.RepoRef
import me.brosssh.bundles.integrations.common.ReleaseInfo
import me.brosssh.bundles.integrations.common.AssetInfo
import me.brosssh.bundles.integrations.common.nextPageUrl
import me.brosssh.bundles.integrations.common.resolveAvatar

/**
 * Client for Gitea-compatible instances, including Forgejo and the Forgejo-based Codeberg service.
 * Uses the Gitea/Forgejo REST API v1 exposed at `<baseUrl>/api/v1` and optionally authenticates
 * with an authority-specific PAT.
 */
class GiteaHostClientFactory(
    private val client: HttpClient,
    private val credentials: GitHostCredentials
) : GitHostClientFactory {
    override fun create(scheme: String, authority: String): GitHostClient =
        GiteaHostClient(client, "$scheme://$authority", credentials.patFor(authority))
}

class GiteaHostClient(
    private val client: HttpClient,
    private val baseUrl: String,
    private val pat: String? = null
) : GitHostClient {

    private fun HttpRequestBuilder.authenticate() {
        pat?.let { header(HttpHeaders.Authorization, "token $it") }
    }

    override suspend fun getRepo(ref: RepoRef): RepoInfo =
        client
            .get("$baseUrl/api/v1/repos/${ref.namespace}/${ref.repo}") {
                authenticate()
            }
            .body<GiteaRepoDto>()
            .toRepoInfo(baseUrl)

    override suspend fun getReleases(ref: RepoRef): List<ReleaseInfo> {
        val releases = mutableListOf<ReleaseInfo>()
        var nextUrl: String? =
            "$baseUrl/api/v1/repos/${ref.namespace}/${ref.repo}/releases?limit=100&page=1"

        while (nextUrl != null) {
            val response = client.get(nextUrl) {
                authenticate()
            }
            releases += response.body<List<GiteaReleaseDto>>()
                .filter { !it.draft }
                .map { it.toReleaseInfo() }
            nextUrl = nextPageUrl(response.headers[HttpHeaders.Link])
        }

        return releases
    }
}

fun GiteaRepoDto.toRepoInfo(baseUrl: String) = RepoInfo(
    ownerName = owner.name,
    ownerAvatarUrl = resolveAvatar(owner.avatarUrl, baseUrl),
    repoName = repoName,
    repoDescription = repoDescription,
    repoStars = stars,
    isRepoArchived = archived,
    repoPushedAt = pushedAt ?: ""
)

fun GiteaReleaseDto.toReleaseInfo() = ReleaseInfo(
    tagName = tagName,
    body = body ?: "",
    prerelease = prerelease,
    createdAt = createdAt,
    assets = assets.map { AssetInfo(it.name, it.browserDownloadUrl, it.digest) }
)
