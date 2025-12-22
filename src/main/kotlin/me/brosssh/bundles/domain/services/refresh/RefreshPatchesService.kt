package me.brosssh.bundles.domain.services.refresh

import app.revanced.patcher.patch.PatchBundle
import me.brosssh.bundles.db.entities.BundleEntity
import me.brosssh.bundles.db.entities.SourceEntity
import me.brosssh.bundles.db.tables.BundleTable
import me.brosssh.bundles.domain.services.CacheService
import me.brosssh.bundles.integrations.github.GithubClient
import me.brosssh.bundles.repositories.*
import me.brosssh.bundles.util.sha256
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.transactions.suspendTransaction
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.File

class RefreshPatchesService(
    cacheService: CacheService,
    refreshJobRepository: RefreshJobRepository,
    private val githubClient: GithubClient,
    private val sourceRepository: SourceRepository,
    private val patchRepository: PatchRepository,
    private val packageRepository: PackageRepository,
    private val patchPackageRepository: PatchPackageRepository
) : BaseRefreshJobService(cacheService, refreshJobRepository) {

    override val logger: Logger = LoggerFactory.getLogger(RefreshPatchesService::class.java)
    override val jobType: String = "PATCHES"

    override suspend fun processRefresh(jobId: String) {
        val processDir = File(System.getProperty("java.io.tmpdir"))
            .resolve("bundles")
            .resolve(jobId)
            .apply { mkdirs() }

        try {
            sourceRepository.getAll().forEach { source ->
                val (owner, repo) = githubClient.parseRepoUrl(source.url)
                logger.info("Processing refresh for url ${source.url}")

                RELEASE_TYPES.forEach { isPrerelease ->
                    val context = RefreshContext(
                        processDir = processDir,
                        owner = owner,
                        repo = repo,
                        isPrerelease = isPrerelease
                    )

                    suspendTransaction {
                        processRelease(
                            context,
                            source
                        )
                    }
                }
            }
        } finally {
            processDir.deleteRecursively()
        }
    }

    private suspend fun processRelease(context: RefreshContext, source: SourceEntity) {
        val bundleEntity = BundleEntity.find {
            (BundleTable.sourceFk eq source.id) and
                    (BundleTable.isPrerelease eq context.isPrerelease)
        }.single()

        val bundleFile = File(context.processDir, "${context.owner}-${context.repo}").apply {
            githubClient.downloadFile(bundleEntity.downloadUrl, this)
            logger.info("Downloaded RVP: $absolutePath")
        }

        try {
            val bundleFileSha = bundleFile.sha256()
            if (bundleEntity.fileSha == bundleFileSha) {
                logger.info("Bundle did not change, skipping")
                bundleFile.delete()
                return
            }

            logger.info("Bundle has changed, reprocessing patches")
            bundleEntity.fileSha = bundleFileSha

            // Remove old patches before creating new ones
            patchRepository.deleteByBundle(bundleEntity)

            PatchBundle(bundleFile.absolutePath).patches?.forEach { patch ->
                val patchEntity = patchRepository.create(
                    bundleEntity = bundleEntity,
                    name = patch.name,
                    description = patch.description
                )

                patch.compatiblePackages?.forEach { (packageName, versions) ->
                    val resolvedPackages = versions
                        ?.map { version ->
                            packageRepository.findOrCreate(packageName, version)
                        }
                        ?: listOf(
                            packageRepository.findOrCreate(packageName, null)
                        )

                    resolvedPackages.forEach { pkg ->
                        patchPackageRepository.link(
                            patch = patchEntity,
                            pkg = pkg
                        )
                    }
                }
            }

            logger.info("Success")
        }
        finally {
            bundleFile.delete()
        }
    }

    companion object {
        private val RELEASE_TYPES = setOf(false, true)
    }

    data class RefreshContext(
        val processDir: File,
        val owner: String,
        val repo: String,
        val isPrerelease: Boolean
    )

}
