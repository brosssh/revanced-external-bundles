package me.brosssh.bundles.domain.services.jobs

import me.brosssh.bundles.db.entities.BundleEntity
import me.brosssh.bundles.db.entities.toBundleDomain
import me.brosssh.bundles.domain.models.RefreshJob
import me.brosssh.bundles.repositories.*
import org.jetbrains.exposed.v1.jdbc.transactions.suspendTransaction
import org.slf4j.Logger
import org.slf4j.LoggerFactory

class RefreshPatchesJobService(
    refreshJobRepository: RefreshJobRepository,
    private val bundleRepository: BundleRepository,
    private val patchRepository: PatchRepository,
    private val packageRepository: PackageRepository,
    private val patchPackageRepository: PatchPackageRepository
) : BaseRefreshJobService(refreshJobRepository) {

    override val logger: Logger = LoggerFactory.getLogger(RefreshPatchesJobService::class.java)
    override val jobType = RefreshJob.RefreshJobType.PATCHES

    override suspend fun processRefresh(jobId: String) {
        logger.info("Processing patches refresh")

            bundleRepository.getBundlesNeedPatchesUpdate().forEach { bundle ->
                logger.info("Processing refresh for bundle ${bundle.id}")

                try {
                    suspendTransaction {
                        processRelease(bundle)
                    }
                } catch (e: Throwable) {
                    logger.warn("Something went wrong while processing, error: ${e.cause}, ${e.message}, ${e.stackTrace}")
                } finally {
                    suspendTransaction {
                        bundle.needPatchesUpdate = false
                    }
                }
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
        logger.info("Success")
    }
}
