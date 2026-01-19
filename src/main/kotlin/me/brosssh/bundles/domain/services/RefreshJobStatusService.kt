package me.brosssh.bundles.domain.services

import me.brosssh.bundles.repositories.RefreshJobRepository

class RefreshJobStatusService (
    private val refreshJobRepository: RefreshJobRepository
) {
    fun getByJobId(jobId: String) = refreshJobRepository.findByJobId(jobId)
}
