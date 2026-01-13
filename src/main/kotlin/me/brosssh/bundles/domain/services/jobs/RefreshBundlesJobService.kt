package me.brosssh.bundles.domain.services.jobs

import me.brosssh.bundles.domain.models.BundleImportError
import me.brosssh.bundles.domain.models.RefreshJob
import me.brosssh.bundles.integrations.github.GithubClient
import me.brosssh.bundles.integrations.github.toDomainModel
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
    private val githubClient: GithubClient,
    private val sourceRepository: SourceRepository,
    private val sourceMetadataRepository: SourceMetadataRepository,
    private val bundleRepository: BundleRepository
) : BaseRefreshJobService(refreshJobRepository) {

    override val logger: Logger = LoggerFactory.getLogger(RefreshBundlesJobService::class.java)
    override val jobType = RefreshJob.RefreshJobType.BUNDLES

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
                        getReleases(owner, repo).forEach { releaseDto ->
                            try {
                                bundleRepository.upsert(
                                    releaseDto.toDomainModel(source.intId)
                                )
                            } catch (_: BundleImportError) {
                                logger.warn("No rvp found for owner=${owner}, repo=${repo}, version${releaseDto.tagName}")
                                return@forEach
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
}
