package me.brosssh.bundles.domain.services.refresh

import me.brosssh.bundles.db.entities.BundleEntity
import me.brosssh.bundles.db.entities.toBundleDomain
import me.brosssh.bundles.domain.services.CacheService
import me.brosssh.bundles.repositories.*
import org.jetbrains.exposed.v1.jdbc.transactions.suspendTransaction
import org.slf4j.Logger
import org.slf4j.LoggerFactory

class RefreshPatchesService(
    cacheService: CacheService,
    refreshJobRepository: RefreshJobRepository,
    private val bundleRepository: BundleRepository,
    private val patchRepository: PatchRepository,
    private val packageRepository: PackageRepository,
    private val patchPackageRepository: PatchPackageRepository
) : BaseRefreshJobService(cacheService, refreshJobRepository) {

    override val logger: Logger = LoggerFactory.getLogger(RefreshPatchesService::class.java)
    override val jobType: String = "PATCHES"

    override suspend fun processRefresh(jobId: String) {
        logger.info("Processing patches refresh")

        try {
            bundleRepository.getBundlesNeedPatchesUpdate().forEach { bundle ->
                logger.info("Processing refresh for bundle ${bundle.id}")

                suspendTransaction {
                    processRelease(bundle)
                }
            }
        } catch (e: Exception) {
            logger.warn("Something went wrong while processing, error: ${e.cause}, ${e.message}, ${e.stackTrace}")
        }

        logger.info("Process completed")
    }

    private suspend fun processRelease(bundleEntity: BundleEntity) {
        // Remove old patches before creating new ones
        patchRepository.deleteByBundle(bundleEntity)

        bundleEntity.toBundleDomain().patches().forEach { patch ->
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
    }
}
