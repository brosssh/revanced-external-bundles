package me.brosssh.bundles.domain.services.jobs

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import me.brosssh.bundles.db.entities.RefreshJobEntity
import me.brosssh.bundles.domain.models.RefreshJob
import me.brosssh.bundles.repositories.RefreshJobRepository
import org.jetbrains.exposed.v1.jdbc.transactions.suspendTransaction
import org.slf4j.Logger
import java.util.*

abstract class BaseRefreshJobService (
    private val refreshJobRepository: RefreshJobRepository
) {
    abstract val logger: Logger
    abstract val jobType: RefreshJob.RefreshJobType

    private val refreshLock = Any()
    private var activeRefresh: RefreshJobHandle? = null

    fun refresh(): RefreshJobHandle = synchronized(refreshLock) {
        activeRefresh?.takeIf { !it.job.isCompleted }?.let { return@synchronized it }

        val jobId = UUID.randomUUID().toString()
        val jobEntityId = refreshJobRepository.create(jobId, jobType).id.value

        // Publish the handle before work can finish, so concurrent triggers share one job.
        val job = CoroutineScope(Dispatchers.Default).launch(start = CoroutineStart.LAZY) {
            try {
                processRefresh(jobId)

                suspendTransaction {
                    RefreshJobEntity[jobEntityId]
                        .setCompleted()
                }
            } catch (e: Exception) {
                suspendTransaction {
                    logger.error("Error during refresh", e)
                    RefreshJobEntity[jobEntityId]
                        .setFailed(e.message ?: e.cause?.message ?: e.javaClass.simpleName)
                }
            }
        }

        val handle = RefreshJobHandle(jobId, job)
        activeRefresh = handle
        job.invokeOnCompletion {
            synchronized(refreshLock) {
                if (activeRefresh === handle) activeRefresh = null
            }
        }
        job.start()
        handle
    }

    protected abstract suspend fun processRefresh(jobId: String)
}

data class RefreshJobHandle(
    val jobId: String,
    val job: Job
)
