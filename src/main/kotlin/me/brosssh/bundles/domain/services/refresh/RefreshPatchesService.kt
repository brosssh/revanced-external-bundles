package me.brosssh.bundles.domain.services.refresh

import app.revanced.patcher.patch.PatchBundle
import me.brosssh.bundles.db.entities.BundleEntity
import me.brosssh.bundles.domain.services.CacheService
import me.brosssh.bundles.integrations.github.GithubClient
import me.brosssh.bundles.repositories.*
import org.jetbrains.exposed.v1.jdbc.transactions.suspendTransaction
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.File

class RefreshPatchesService(
    cacheService: CacheService,
    refreshJobRepository: RefreshJobRepository,
    private val githubClient: GithubClient,
    private val bundleRepository: BundleRepository,
    private val patchRepository: PatchRepository,
    private val packageRepository: PackageRepository,
    private val patchPackageRepository: PatchPackageRepository
) : BaseRefreshJobService(cacheService, refreshJobRepository) {

    override val logger: Logger = LoggerFactory.getLogger(RefreshPatchesService::class.java)
    override val jobType: String = "PATCHES"

    override suspend fun processRefresh(jobId: String) {
        logger.info("Processing patches refresh")
        val processDir = File(System.getProperty("java.io.tmpdir"))
            .resolve("bundles")
            .resolve(jobId)
            .apply { mkdirs() }

        try {
            bundleRepository.getBundlesNeedPatchesUpdate().forEach { bundle ->
                logger.info("Processing refresh for bundle ${bundle.id}")

                suspendTransaction {
                    processRelease(bundle, processDir)
                }
            }
        } finally {
            processDir.deleteRecursively()
        }
        logger.info("Process completed")
    }

    private suspend fun processRelease(bundleEntity: BundleEntity, processDir: File) {
        val bundleFile = File(
            processDir,
            bundleEntity.id.toString()
        ).apply {
            githubClient.downloadFile(bundleEntity.downloadUrl, this)
            logger.info("Downloaded RVP: $absolutePath")
        }

        try {
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
            bundleEntity.needPatchesUpdate = false
            logger.info("Success")
        } finally {
            bundleFile.delete()
        }
    }
}
