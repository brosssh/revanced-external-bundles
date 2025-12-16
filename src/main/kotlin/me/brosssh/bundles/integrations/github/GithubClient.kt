package me.brosssh.bundles.integrations.github

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsChannel
import io.ktor.utils.io.jvm.javaio.copyTo
import java.io.File

class GithubClient(
    private val client: HttpClient,
    private val githubToken: String
) {
    private val authHeader get() = "Bearer $githubToken"

    suspend fun getRelease(
        owner: String,
        repo: String,
        preRelease: Boolean
    ) =
        client
            .get("https://api.github.com/repos/$owner/$repo/releases") {
                header("Authorization", authHeader)
            }
            .body<List<GithubReleaseDto>>()
            .firstOrNull { it.prerelease == preRelease }

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
}
