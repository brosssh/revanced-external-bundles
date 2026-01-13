package me.brosssh.bundles.integrations.github

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.utils.io.jvm.javaio.*
import java.io.File

class GithubClient(
    private val client: HttpClient,
    private val githubToken: String
) {
    private val authHeader get() = "Bearer $githubToken"
    suspend fun getReleases(
        owner: String,
        repo: String
    ): List<GithubReleaseDto> {
        val releases = mutableListOf<GithubReleaseDto>()

        var nextUrl: String? = "https://api.github.com/repos/$owner/$repo/releases?per_page=100"

        while (nextUrl != null) {
            val response = client.get(nextUrl) {
                header("Authorization", authHeader)
                header("Accept", "application/vnd.github+json")
            }

            releases += response.body<List<GithubReleaseDto>>()

            val linkHeader = response.headers["Link"]
            nextUrl = parseNextLink(linkHeader)
        }

        return releases
    }

    suspend fun getRepo(
        owner: String,
        repo: String
    ) =
        client
            .get("https://api.github.com/repos/$owner/$repo") {
                header("Authorization", authHeader)
            }
            .body<GithubRepoDto>()

    suspend fun downloadFile(url: String, target: File) {
        target.outputStream().use { outputStream ->
            client.get(url) {
                header("Authorization", authHeader)
            }.bodyAsChannel().copyTo(outputStream)
        }
    }

    fun parseRepoUrl(url: String): Pair<String, String> {
        val parts = url.removeSuffix("/")
            .substringAfter("github.com/")
            .split("/")

        require(parts.size >= 2) { "Invalid GitHub repo URL" }

        return parts[0] to parts[1]
    }

    private fun parseNextLink(linkHeader: String?): String? {
        if (linkHeader == null) return null

        return linkHeader
            .split(",")
            .map { it.trim() }
            .firstOrNull { it.contains("""rel="next"""") }
            ?.substringAfter("<")
            ?.substringBefore(">")
    }
}
