package me.brosssh.bundles.domain.services.jobs

import me.brosssh.bundles.domain.models.RefreshJob
import me.brosssh.bundles.repositories.RefreshJobRepository
import org.slf4j.Logger
import org.slf4j.LoggerFactory

class RefreshAllJobService(
    refreshJobRepository: RefreshJobRepository,
    private val refreshBundlesJobService: RefreshBundlesJobService,
    private val refreshPatchesJobService: RefreshPatchesJobService
) : BaseRefreshJobService(refreshJobRepository) {

    override val logger: Logger = LoggerFactory.getLogger(RefreshAllJobService::class.java)
    override val jobType = RefreshJob.RefreshJobType.ALL

    override suspend fun processRefresh(jobId: String) {
        with(logger) {
            info("Starting full refresh")

            refreshBundlesJobService.refresh().also {
                it.job.join()
            }
            refreshPatchesJobService.refresh().also {
                it.job.join()
            }

            info("Full refresh completed")
        }
    }

}
