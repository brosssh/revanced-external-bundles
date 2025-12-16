package me.brosssh.bundles.clients

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.utils.io.jvm.javaio.copyTo
import me.brosssh.bundles.models.GithubReleaseDto
import me.brosssh.bundles.models.ReleaseType
import java.io.File

class GithubClient(
    private val client: HttpClient,
    private val githubToken: String? = null
) {

    suspend fun getLatestRelease(owner: String, repo: String): GithubReleaseDto {
        return client.get("https://api.github.com/repos/$owner/$repo/releases/latest") {
            header(HttpHeaders.Accept, "application/vnd.github+json")
            githubToken?.let {
                header(HttpHeaders.Authorization, "Bearer $it")
            }
        }.body()
    }

    suspend fun getRelease(
        owner: String,
        repo: String,
        type: ReleaseType
    ): GithubReleaseDto =
        when (type) {
            ReleaseType.STABLE ->
                client.get("https://api.github.com/repos/$owner/$repo/releases/latest").body()

            ReleaseType.PRERELEASE ->
                client.get("https://api.github.com/repos/$owner/$repo/releases")
                    .body<List<GithubReleaseDto>>()
                    .firstOrNull { it.prerelease }
                    ?: error("No prerelease found")
        }

    suspend fun downloadFile(url: String, target: File) {
        target.outputStream().use { outputStream ->
            client.get(url).bodyAsChannel().copyTo(outputStream)
        }
    }
}
