package me.brosssh.bundles.services

import app.revanced.patcher.patch.PatchBundle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import me.brosssh.bundles.db.entities.BundleEntity
import me.brosssh.bundles.db.entities.PackageEntity
import me.brosssh.bundles.db.entities.PatchEntity
import me.brosssh.bundles.db.entities.RefreshJobEntity
import me.brosssh.bundles.db.repositories.PackageRepository
import me.brosssh.bundles.db.repositories.RefreshJobRepository
import me.brosssh.bundles.db.repositories.SourceRepository
import me.brosssh.bundles.db.tables.BundleTable
import me.brosssh.bundles.db.tables.PackageTable
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.transactions.experimental.newSuspendedTransaction
import org.jetbrains.exposed.v1.jdbc.transactions.suspendTransaction
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.slf4j.LoggerFactory
import java.io.File
import java.util.*


class RefreshService (
    private val githubService: GithubService,
    private val refreshJobRepository: RefreshJobRepository,
    private val sourceRepository: SourceRepository,
    private val packageRepository: PackageRepository
) {
    private val logger = LoggerFactory.getLogger(RefreshService::class.java)

    fun refreshAsync(): String {
        val jobId = UUID.randomUUID().toString()
        val jobEntityId = refreshJobRepository.create(jobId).id.value

        CoroutineScope(Dispatchers.Default).launch {
            try {
                suspendTransaction {
                    processRefresh()

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

            PatchBundle(file.absolutePath).also { thisBundle ->
                val bundleEntity = thisBundle.manifestAttributes.let {
                    BundleEntity.new {
                        name = it?.name
                        version = it?.version
                        description = it?.description
                        bundleSource = it?.source
                        author = it?.author
                        contact = it?.contact
                        website = it?.website
                        license = it?.license
                    }
                }

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
