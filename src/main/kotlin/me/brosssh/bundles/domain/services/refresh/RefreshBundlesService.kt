package me.brosssh.bundles.domain.services.refresh

import me.brosssh.bundles.domain.services.CacheService
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
        sourceRepository.getAll().forEach { source ->
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

                            bundleRepository.upsert(releaseDto, source, releaseType)
                        }
                    }
                }
            }
        }
    }

    companion object {
        private val RELEASE_TYPES = setOf(false, true)
    }
}
