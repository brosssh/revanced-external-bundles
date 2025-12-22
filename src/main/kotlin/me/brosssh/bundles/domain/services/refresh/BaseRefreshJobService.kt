package me.brosssh.bundles.domain.services.refresh

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import me.brosssh.bundles.db.entities.RefreshJobEntity
import me.brosssh.bundles.domain.services.CacheService
import me.brosssh.bundles.repositories.RefreshJobRepository
import org.jetbrains.exposed.v1.jdbc.transactions.suspendTransaction
import org.slf4j.Logger
import java.util.*

abstract class BaseRefreshJobService (
    private val cacheService: CacheService,
    private val refreshJobRepository: RefreshJobRepository
) {
    abstract val logger: Logger
    abstract val jobType: String

    fun refresh(): String {
        val jobId = UUID.randomUUID().toString()
        val jobEntityId = refreshJobRepository.create(jobId, jobType).id.value

        CoroutineScope(Dispatchers.Default).launch {
            try {
                processRefresh(jobId)
                cacheService.invalidateCache()

                suspendTransaction {
                    RefreshJobEntity[jobEntityId]
                        .setCompleted()
                }
            } catch (e: Exception) {
                suspendTransaction {
                    logger.error("Error during refresh", e)
                    RefreshJobEntity[jobEntityId]
                        .setFailed("${e.message} - ${e.cause} - ${e.stackTrace}")
                }
            }
        }

        return jobId
    }

    protected abstract suspend fun processRefresh(jobId: String)
}
