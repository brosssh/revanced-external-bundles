package me.brosssh.bundles.services

import app.revanced.patcher.patch.PatchBundle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import me.brosssh.bundles.db.entities.PackageEntity
import me.brosssh.bundles.db.entities.PatchEntity
import me.brosssh.bundles.db.entities.RefreshJobEntity
import me.brosssh.bundles.db.repositories.BundleRepository
import me.brosssh.bundles.db.repositories.PackageRepository
import me.brosssh.bundles.db.repositories.RefreshJobRepository
import me.brosssh.bundles.db.repositories.SourceRepository
import me.brosssh.bundles.db.tables.PackageTable
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.transactions.suspendTransaction
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.slf4j.LoggerFactory
import java.io.File
import java.util.UUID


class RefreshService (
    private val githubServiceFactory: (String) -> GithubService,
    private val refreshJobRepository: RefreshJobRepository,
    private val sourceRepository: SourceRepository,
    private val bundleRepository: BundleRepository,
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
        val processDir = File(System.getProperty("java.io.tmpdir"), "bundles").also { it.mkdirs() }
        sourceRepository.getAll().forEach { source ->
            logger.info("Processing refresh for url ${source.url}")
            val (owner, repo) = githubService.parseRepoUrl(source.url)

            for (isPrerelease in setOf(false, true)) {
                val releaseDto = githubService.getRelease(owner, repo, isPrerelease)
                    ?: error("No release found for owner $owner, repo $repo and flag prelease $isPrerelease")

                val bundleEntity = bundleRepository.create(releaseDto, source, isPrerelease)

                val file = File(processDir, jobId)
                    .also { githubService.downloadFile(bundleEntity.downloadUrl, it) }

                PatchBundle(file.absolutePath).also { thisBundle ->
                    thisBundle.patches?.forEach { patch ->
                        transaction {
                            val compatiblePackageIds = patch.compatiblePackages
                                ?.flatMap { (name, versions) ->
                                    versions?.map { name to it } ?: listOf(name to null)
                                }
                                ?.map { (name, version) ->
                                    (PackageEntity.find {
                                        (PackageTable.name eq name) and (PackageTable.version eq version)
                                    }.firstOrNull() ?: PackageEntity.new {
                                        this.name = name
                                        this.version = version
                                    }).id.value
                                }
                                ?: emptyList()

                            PatchEntity.new {
                                bundle = bundleEntity
                                name = patch.name
                                description = patch.description
                                compatiblePackageFk = compatiblePackageIds
                            }
                        }
                    }
                }

                logger.info("Downloaded RVP: ${file.absolutePath}")
            }
        }
    }
}
