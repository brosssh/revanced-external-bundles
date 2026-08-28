package me.brosssh.bundles.domain.services.jobs

import me.brosssh.bundles.db.functions.refreshIsLatestFlag
import me.brosssh.bundles.domain.models.BundleImportError
import me.brosssh.bundles.domain.models.RefreshJob
import me.brosssh.bundles.integrations.HostResolver
import me.brosssh.bundles.integrations.common.toDomainModel
import me.brosssh.bundles.repositories.BundleRepository
import me.brosssh.bundles.repositories.RefreshJobRepository
import me.brosssh.bundles.repositories.SourceMetadataRepository
import me.brosssh.bundles.repositories.SourceRepository
import me.brosssh.bundles.util.intId
import org.jetbrains.exposed.v1.jdbc.transactions.suspendTransaction
import org.slf4j.Logger
import org.slf4j.LoggerFactory

class RefreshBundlesJobService(
    refreshJobRepository: RefreshJobRepository,
    private val hostResolver: HostResolver,
    private val sourceRepository: SourceRepository,
    private val sourceMetadataRepository: SourceMetadataRepository,
    private val bundleRepository: BundleRepository
) : BaseRefreshJobService(refreshJobRepository) {

    override val logger: Logger = LoggerFactory.getLogger(RefreshBundlesJobService::class.java)
    override val jobType = RefreshJob.RefreshJobType.BUNDLES

    override suspend fun processRefresh(jobId: String) {
        logger.info("Processing bundles refresh")

        sourceRepository.getEnabled().forEach { source ->
            logger.info("Processing source ${source.url}")
            try {
                suspendTransaction {
                    val resolved = hostResolver.resolve(source.url)

                    // Update metatable
                    sourceMetadataRepository.upsert(
                        resolved.client.getRepo(resolved.ref).toDomainModel(source.intId)
                    )

                    // Update bundle table
                    resolved.client.getReleases(resolved.ref).forEach { release ->
                        try {
                            bundleRepository.upsert(
                                release.toDomainModel(source.intId)
                            )
                        } catch (_: BundleImportError) {
                            logger.warn("No rvp/mpp/jar found for ${source.url}, version ${release.tagName}")
                            return@forEach
                        }
                    }
                }
            }
            catch (e: Exception) {
                logger.warn("Something went wrong while processing, error: ${e.cause}, ${e.message}, ${e.stackTrace}")
            }

            logger.info("Source process completed")
        }

        refreshIsLatestFlag()
        logger.info("Process completed")
    }

}
