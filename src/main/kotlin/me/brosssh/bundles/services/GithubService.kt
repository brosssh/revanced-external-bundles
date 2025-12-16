package me.brosssh.bundles.services

import me.brosssh.bundles.clients.GithubClient
import java.io.File

class GithubService(
    private val githubClient: GithubClient
) {

    suspend fun downloadLatestRvp(owner: String, repo: String, targetDir: File): File {
        val release = githubClient.getLatestRelease(owner, repo)

        val asset = release.assets
            .firstOrNull { it.name.endsWith(".rvp", ignoreCase = true) }
            ?: error("No .rvp file found in latest release")

        return File(targetDir, asset.name).also {
            githubClient.downloadFile(asset.browser_download_url, it)
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
