package me.brosssh.bundles.integrations.github

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.HttpHeaders
import me.brosssh.bundles.integrations.common.*

class GithubClientFactory(
    private val client: HttpClient,
    private val credentials: GitHostCredentials
) : GitHostClientFactory {
    override fun create(scheme: String, authority: String): GitHostClient {
        val baseUrl =
            if (authority == "github.com") "https://api.github.com" else "$scheme://$authority/api/v3"
        return GithubClient(client, baseUrl, credentials.patFor(authority))
    }
}

class GithubClient(
    private val client: HttpClient,
    private val baseUrl: String = "https://api.github.com",
    private val pat: String? = null
) : GitHostClient {

    private fun HttpRequestBuilder.authenticate() {
        pat?.let { header(HttpHeaders.Authorization, "Bearer $it") }
    }

    override suspend fun getReleases(ref: RepoRef): List<ReleaseInfo> {
        val releases = mutableListOf<ReleaseInfo>()

        var nextUrl: String? = "$baseUrl/repos/${ref.namespace}/${ref.repo}/releases?per_page=100"

        while (nextUrl != null) {
            val response = client.get(nextUrl) {
                authenticate()
                header(HttpHeaders.Accept, "application/vnd.github+json")
            }

            releases += response.body<List<GithubReleaseDto>>().map { it.toReleaseInfo() }

            nextUrl = nextPageUrl(response.headers[HttpHeaders.Link])
        }

        return releases
    }

    override suspend fun getRepo(ref: RepoRef): RepoInfo =
        client
            .get("$baseUrl/repos/${ref.namespace}/${ref.repo}") {
                authenticate()
            }
            .body<GithubRepoDto>()
            .toRepoInfo()

}

fun GithubRepoDto.toRepoInfo() = RepoInfo(
    ownerName = owner.name,
    ownerAvatarUrl = owner.avatarUrl,
    repoName = repoName,
    repoDescription = repoDescription,
    repoStars = stars,
    isRepoArchived = archived,
    repoPushedAt = pushedAt
)

fun GithubReleaseDto.toReleaseInfo() = ReleaseInfo(
    tagName = tagName,
    body = body,
    prerelease = prerelease,
    createdAt = createdAt,
    assets = assets.map { AssetInfo(it.name, it.browserDownloadUrl, it.digest) }
)
