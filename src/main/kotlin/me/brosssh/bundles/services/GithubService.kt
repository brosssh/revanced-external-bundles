package me.brosssh.bundles.services

import me.brosssh.bundles.clients.GithubClient
import java.io.File

class GithubService(
    private val githubClient: GithubClient
) {
    suspend fun downloadFile(url: String, target: File) =
        githubClient.downloadFile(url, target)

    suspend fun getRelease(owner: String, repo: String, preRelease: Boolean) =
        githubClient.getRelease(owner, repo, preRelease)

    fun parseRepoUrl(url: String): Pair<String, String> {
        val parts = url.removeSuffix("/")
            .substringAfter("github.com/")
            .split("/")

        require(parts.size >= 2) { "Invalid GitHub repo URL" }

        return parts[0] to parts[1]
    }
}
