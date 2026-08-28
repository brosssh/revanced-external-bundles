package me.brosssh.bundles.domain.services

import me.brosssh.bundles.domain.models.ReleaseChannel
import me.brosssh.bundles.repositories.BundleRepository

sealed class BundleQuery {
    data class ById(val id: Int) : BundleQuery()
    data class ByRepositoryLatest(
        val owner: String,
        val repo: String,
        val isPrerelease: Boolean = false
    ) : BundleQuery()

    data class ByRepositoryAndChannel(
        val owner: String,
        val repo: String,
        val channel: ReleaseChannel
    ) : BundleQuery()

    data class ByRepositoryAndVersion(
        val owner: String,
        val repo: String,
        val version: String
    ) : BundleQuery()

    data class BySourceAndChannel(
        val sourceUrl: String,
        val channel: ReleaseChannel
    ) : BundleQuery()

    data class BySourceAndVersion(
        val sourceUrl: String,
        val version: String,
        val channel: ReleaseChannel
    ) : BundleQuery()
}

class BundleService (
    private val bundleRepository: BundleRepository
) {
    fun getById(id: Int) = bundleRepository.findById(id)
    fun getBundleByQuery(query: BundleQuery) =
        when (query) {
            is BundleQuery.ById -> bundleRepository.findById(query.id)
            is BundleQuery.ByRepositoryLatest -> bundleRepository.findLatestByRepo(
                query.owner,
                query.repo,
                query.isPrerelease
            )
            is BundleQuery.ByRepositoryAndVersion -> bundleRepository.findByRepoAndVersion(
                query.owner,
                query.repo,
                query.version
            )
            is BundleQuery.ByRepositoryAndChannel -> bundleRepository.findByRepoAndChannel(
                query.owner,
                query.repo,
                query.channel
            )
            is BundleQuery.BySourceAndChannel -> bundleRepository.findBySourceAndChannel(
                query.sourceUrl,
                query.channel
            )
            is BundleQuery.BySourceAndVersion -> bundleRepository.findBySourceAndVersion(
                query.sourceUrl,
                query.version,
                query.channel
            )
        }
}
