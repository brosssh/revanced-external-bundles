package me.brosssh.bundles.domain.services

import me.brosssh.bundles.repositories.BundleRepository

sealed class BundleQuery {
    data class ById(val id: Int) : BundleQuery()
    data class ByRepository(
        val owner: String,
        val repo: String,
        val isPrerelease: Boolean = false
    ) : BundleQuery()
}

class BundleService (
    private val bundleRepository: BundleRepository
) {
    fun getById(id: Int) = bundleRepository.findById(id)
    fun getSnapshot() = bundleRepository.getSnapshot()
    fun getBundleByQuery(query: BundleQuery) =
        when (query) {
            is BundleQuery.ById -> bundleRepository.findById(query.id)
            is BundleQuery.ByRepository -> bundleRepository.findByPk(
                query.owner,
                query.repo,
                query.isPrerelease
            )
        }
}
