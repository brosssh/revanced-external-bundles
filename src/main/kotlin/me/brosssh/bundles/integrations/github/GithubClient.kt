package me.brosssh.bundles.integrations.github

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.expectSuccess
import io.ktor.client.request.*
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import me.brosssh.bundles.integrations.common.*
import java.time.OffsetDateTime

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

    override fun rateLimitDeadline(
        status: HttpStatusCode,
        headers: Headers,
        now: OffsetDateTime
    ): OffsetDateTime? = rateLimitDeadline(status, headers, now, null)

    override fun rateLimitDeadline(
        status: HttpStatusCode,
        headers: Headers,
        now: OffsetDateTime,
        responseBody: String?
    ): OffsetDateTime? {
        val retryAfter = headers[HttpHeaders.RetryAfter]
        val rateLimited =
            status == HttpStatusCode.TooManyRequests ||
                (status == HttpStatusCode.Forbidden &&
                    (headers["X-RateLimit-Remaining"]?.trim() == "0" ||
                        retryAfter != null ||
                        responseBody.indicatesSecondaryRateLimit()))
        if (!rateLimited) return null

        return resolvedRateLimitDeadline(
            retryAfter = retryAfter,
            reset = parseEpochSeconds(headers["X-RateLimit-Reset"]),
            now = now
        )
    }

    override suspend fun getReleases(ref: RepoRef): List<ReleaseInfo> {
        val releases = mutableListOf<ReleaseInfo>()

        var nextUrl: String? = "$baseUrl/repos/${ref.namespace}/${ref.repo}/releases?per_page=100"

        while (nextUrl != null) {
            val response = client.get(nextUrl) {
                expectSuccess = true
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
                expectSuccess = true
                authenticate()
            }
            .body<GithubRepoDto>()
            .toRepoInfo()

}

private fun String?.indicatesSecondaryRateLimit(): Boolean =
    this?.let { body ->
        body.contains("secondary rate limit", ignoreCase = true) ||
            body.contains("abuse detection", ignoreCase = true)
    } == true

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
