package me.brosssh.bundles.clients

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.utils.io.jvm.javaio.copyTo
import me.brosssh.bundles.models.GithubReleaseDto
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

    suspend fun downloadFile(url: String, target: File) {
        target.outputStream().use { outputStream ->
            client.get(url) {
                header("Authorization", authHeader)
            }.bodyAsChannel().copyTo(outputStream)
        }
    }
}
