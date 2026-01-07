package me.brosssh.bundles.domain.services.refresh

import me.brosssh.bundles.domain.models.BundleImportError
import me.brosssh.bundles.domain.services.CacheService
import me.brosssh.bundles.integrations.github.GithubClient
import me.brosssh.bundles.integrations.github.toDomainModel
import me.brosssh.bundles.repositories.*
import me.brosssh.bundles.util.intId
import org.jetbrains.exposed.v1.jdbc.transactions.suspendTransaction
import org.slf4j.Logger
import org.slf4j.LoggerFactory

class RefreshBundlesService(
    cacheService: CacheService,
    refreshJobRepository: RefreshJobRepository,
    private val githubClient: GithubClient,
    private val sourceRepository: SourceRepository,
    private val sourceMetadataRepository: SourceMetadataRepository,
    private val bundleRepository: BundleRepository
) : BaseRefreshJobService(cacheService, refreshJobRepository) {

    override val logger: Logger = LoggerFactory.getLogger(RefreshBundlesService::class.java)
    override val jobType: String = "BUNDLES"

    override suspend fun processRefresh(jobId: String) {
        logger.info("Processing bundles refresh")
        sourceRepository.getAll().forEach { source ->
            logger.info("Processing source ${source.url}")
            try {
                suspendTransaction {
                    with(githubClient) {
                        val (owner, repo) = parseRepoUrl(source.url)

                        // Update metatable
                        getRepo(owner, repo).also { repoDto ->
                            sourceMetadataRepository.upsert(
                                repoDto.toDomainModel(source.intId)
                            )
                        }

                        // Update bundle table
                        RELEASE_TYPES.forEach { releaseType ->
                            getRelease(owner, repo, releaseType).also { releaseDto ->
                                if (releaseDto == null) {
                                    logger.warn("No release found for owner=${owner}, repo=${repo}, prerelease=${releaseType}")
                                    return@forEach
                                }

                                try {
                                    bundleRepository.upsert(
                                        releaseDto.toDomainModel(source.intId)
                                    )
                                } catch (_: BundleImportError) {
                                    logger.warn("No rvp found for owner=${owner}, repo=${repo}, prerelease=${releaseType}")
                                    return@forEach
                                }
                            }
                        }
                    }
                }
            }
            catch (e: Exception) {
                logger.warn("Something went wrong while processing, error: ${e.cause}, ${e.message}, ${e.stackTrace}")
            }

            logger.info("Source process completed")
        }
        logger.info("Process completed")
    }

    companion object {
        private val RELEASE_TYPES = setOf(false, true)
    }
}
