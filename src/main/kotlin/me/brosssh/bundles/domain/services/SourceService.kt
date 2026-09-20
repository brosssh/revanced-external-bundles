package me.brosssh.bundles.domain.services

import me.brosssh.bundles.repositories.SourceRepository

class SourceService(
    private val sourceRepository: SourceRepository
) {
    fun hardDelete(sourceUrl: String) = sourceRepository.hardDelete(sourceUrl)
}
