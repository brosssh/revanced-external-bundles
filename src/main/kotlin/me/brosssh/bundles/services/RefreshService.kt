package me.brosssh.bundles.services

import app.revanced.patcher.patch.PatchBundle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import me.brosssh.bundles.db.queries.RefreshJobRepository
import me.brosssh.bundles.db.queries.SourceRepository
import org.slf4j.LoggerFactory
import java.io.File
import java.util.*


class RefreshService (
    private val githubService: GithubService,
    private val refreshJobRepository: RefreshJobRepository,
    private val sourceRepository: SourceRepository
) {
    private val logger = LoggerFactory.getLogger(RefreshService::class.java)

    fun refreshAsync(): String {
        val jobId = UUID.randomUUID().toString()
        refreshJobRepository.create(jobId)

        CoroutineScope(Dispatchers.Default).launch {
            try {
                processRefresh()
                refreshJobRepository.complete(jobId)
            } catch (e: Exception) {
                logger.error("Error during refresh", e)
                //TODO
            }
        }

        return jobId
    }

    private suspend fun processRefresh() {
        sourceRepository.getAll().map { it.url }.forEach { url ->
            logger.info("Processing refresh for url $url")
            val (owner, repo) = githubService.parseRepoUrl(url)

            val tempDir = File(System.getProperty("java.io.tmpdir"), owner).also { it.mkdirs() }

            val file = githubService.downloadLatestRvp(
                owner = owner,
                repo = repo,
                targetDir = tempDir
            )

            val bundle = PatchBundle(file.absolutePath)

            logger.info("Downloaded RVP: ${file.absolutePath}")
        }
    }
}
