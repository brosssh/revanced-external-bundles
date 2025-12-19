package me.brosssh.bundles.domain.services.refresh

import me.brosssh.bundles.integrations.github.GithubClient
import me.brosssh.bundles.integrations.github.toDomainModel
import me.brosssh.bundles.repositories.RefreshJobRepository
import me.brosssh.bundles.repositories.SourceMetadataRepository
import me.brosssh.bundles.repositories.SourceRepository
import me.brosssh.bundles.util.intId
import org.slf4j.Logger
import org.slf4j.LoggerFactory

class RefreshBundlesMetadataService(
    refreshJobRepository: RefreshJobRepository,
    private val githubClient: GithubClient,
    private val sourceRepository: SourceRepository,
    private val sourceMetadataRepository: SourceMetadataRepository
) : BaseRefreshJobService(refreshJobRepository) {

    override val logger: Logger = LoggerFactory.getLogger(RefreshBundlesMetadataService::class.java)
    override val jobType: String = "METADATA"

    override suspend fun processRefresh(jobId: String) {
        sourceRepository.getAll().forEach { source ->
            with(githubClient) {
                parseRepoUrl(source.url).let { (owner, repo) ->
                    getRepo(owner, repo)
                }
            }.also { repoDto ->
                sourceMetadataRepository.upsert(
                    repoDto.toDomainModel(source.intId)
                )
            }
        }
    }
}
