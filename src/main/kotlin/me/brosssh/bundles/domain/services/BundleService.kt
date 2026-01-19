package me.brosssh.bundles.domain.services

import me.brosssh.bundles.repositories.BundleRepository

sealed class BundleQuery {
    data class ById(val id: Int) : BundleQuery()
    data class ByRepositoryLatest(
        val owner: String,
        val repo: String,
        val isPrerelease: Boolean = false
    ) : BundleQuery()

    data class ByRepositoryAndVersion(
        val owner: String,
        val repo: String,
        val version: String
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
        }
}
