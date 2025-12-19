package me.brosssh.bundles.services

import me.brosssh.bundles.db.repositories.BundleRepository


class BundleService (
    private val bundleRepository: BundleRepository
) {
    fun getById(id: Int) = bundleRepository.findById(id)
    fun search(query: String) = bundleRepository.search(query)

}
