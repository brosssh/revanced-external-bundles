package me.brosssh.bundles.domain.services

import me.brosssh.bundles.repositories.BundleRepository


class BundleService (
    private val bundleRepository: BundleRepository
) {
    fun getById(id: Int) = bundleRepository.findById(id)
    fun search(query: String) = bundleRepository.search(query)

}
