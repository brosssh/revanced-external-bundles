package me.brosssh.bundles.services

import app.revanced.patcher.patch.PatchBundle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import me.brosssh.bundles.db.entities.RefreshJobEntity
import me.brosssh.bundles.db.entities.SourceEntity
import me.brosssh.bundles.db.repositories.BundleRepository
import me.brosssh.bundles.db.repositories.PackageRepository
import me.brosssh.bundles.db.repositories.PatchRepository
import me.brosssh.bundles.db.repositories.RefreshJobRepository
import me.brosssh.bundles.db.repositories.SourceRepository
import me.brosssh.bundles.util.intId
import me.brosssh.bundles.util.sha256
import org.jetbrains.exposed.v1.jdbc.transactions.suspendTransaction
import org.slf4j.LoggerFactory
import java.io.File
import java.util.*

data class RefreshContext(
    val jobId: String,
    val githubService: GithubService,
    val processDir: File,
    val owner: String,
    val repo: String,
    val isPrerelease: Boolean
)

class RefreshService (
    private val githubServiceFactory: (String) -> GithubService,
    private val refreshJobRepository: RefreshJobRepository,
    private val sourceRepository: SourceRepository,
    private val bundleRepository: BundleRepository,
    private val patchRepository: PatchRepository,
    private val packageRepository: PackageRepository
) {
    private val logger = LoggerFactory.getLogger(RefreshService::class.java)

    fun refreshAsync(githubToken: String): String {
        val jobId = UUID.randomUUID().toString()
        val jobEntityId = refreshJobRepository.create(jobId).id.value

        CoroutineScope(Dispatchers.Default).launch {
            try {
                suspendTransaction {
                    processRefresh(jobId, githubServiceFactory(githubToken))

                    val job = RefreshJobEntity[jobEntityId]
                    job.setCompleted()
                }
            } catch (e: Exception) {
                suspendTransaction {
                    logger.error("Error during refresh", e)
                    val job = RefreshJobEntity[jobEntityId]
                    job.setFailed("${e.message} - ${e.cause} - ${e.stackTrace}")
                }
            }
        }

        return jobId
    }

    private suspend fun processRefresh(jobId: String, githubService: GithubService) {
        val processDir = File(System.getProperty("java.io.tmpdir"), "bundles").apply { mkdirs() }

        sourceRepository.getAll().forEach { source ->
            val (owner, repo) = githubService.parseRepoUrl(source.url)
            logger.info("Processing refresh for url ${source.url}")

            RELEASE_TYPES.forEach { isPrerelease ->
                val context = RefreshContext(
                    jobId = jobId,
                    githubService = githubService,
                    processDir = processDir,
                    owner = owner,
                    repo = repo,
                    isPrerelease = isPrerelease
                )
                processRelease(context, source)
            }
        }
    }

    private suspend fun processRelease(context: RefreshContext, source: SourceEntity) {
        val releaseDto = context.githubService.getRelease(context.owner, context.repo, context.isPrerelease)
            ?: error("No release found for owner=${context.owner}, repo=${context.repo}, prerelease=${context.isPrerelease}")

        val bundleEntity = bundleRepository.upsert(releaseDto, source, context.isPrerelease)

        val bundleFile = File(context.processDir, context.jobId).apply {
            context.githubService.downloadFile(bundleEntity.downloadUrl, this)
            logger.info("Downloaded RVP: $absolutePath")
        }

        val bundleFileSha = bundleFile.sha256()
        if (bundleEntity.fileSha == bundleFileSha) {
            logger.info("Bundle did not change, skipping")
            return
        }

        logger.info("Bundle has changed, reprocessing patches")
        bundleEntity.fileSha = bundleFileSha

        // Remove old patches before creating new ones
        patchRepository.deleteByBundle(bundleEntity)

        PatchBundle(bundleFile.absolutePath).patches?.forEach { patch ->
            val compatiblePackageIds = buildList {
                patch.compatiblePackages?.forEach { (packageName, versions) ->
                    if (versions == null) {
                        add(packageRepository.findOrCreate(packageName, null).intId)
                    } else {
                        versions.forEach { version ->
                            add(packageRepository.findOrCreate(packageName, version).intId)
                        }
                    }
                }
            }

            patchRepository.create(
                bundleEntity = bundleEntity,
                name = patch.name,
                description = patch.description,
                compatiblePackageIds = compatiblePackageIds
            )
        }

        logger.info("Success")
    }

    companion object {
        private val RELEASE_TYPES = setOf(false, true)
    }
}
