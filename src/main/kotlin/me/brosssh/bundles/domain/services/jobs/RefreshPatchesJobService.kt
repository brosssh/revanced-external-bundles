package me.brosssh.bundles.domain.services.jobs

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import me.brosssh.bundles.db.entities.BundleEntity
import me.brosssh.bundles.domain.models.BundleType
import me.brosssh.bundles.domain.models.Patch
import me.brosssh.bundles.domain.models.RefreshJob
import me.brosssh.bundles.repositories.BundlePatchCandidate
import me.brosssh.bundles.repositories.BundleRepository
import me.brosssh.bundles.repositories.PackageRepository
import me.brosssh.bundles.repositories.PatchPackageRepository
import me.brosssh.bundles.repositories.PatchRepository
import me.brosssh.bundles.repositories.RefreshJobRepository
import me.brosssh.bundles.workers.PatcherRuntimeExhaustedException
import me.brosssh.bundles.workers.PatchWorkerManager
import org.jetbrains.exposed.v1.jdbc.transactions.suspendTransaction
import org.slf4j.Logger
import org.slf4j.LoggerFactory

class RefreshPatchesJobService(
    refreshJobRepository: RefreshJobRepository,
    private val bundleRepository: BundleRepository,
    private val patchRepository: PatchRepository,
    private val packageRepository: PackageRepository,
    private val patchPackageRepository: PatchPackageRepository,
    private val patchWorkerManager: PatchWorkerManager,
    refreshConcurrency: Int
) : BaseRefreshJobService(refreshJobRepository) {

    override val logger: Logger = LoggerFactory.getLogger(RefreshPatchesJobService::class.java)
    override val jobType = RefreshJob.RefreshJobType.PATCHES

    private val refreshSemaphore: Semaphore
    // Fixed lock stripes serialize concurrent refreshes of the same bundle without retaining an
    // unbounded map of one mutex per historical bundle.
    private val bundleLocks = Array(256) { Mutex() }

    init {
        require(refreshConcurrency > 0) { "Patcher refresh concurrency must be positive" }
        refreshSemaphore = Semaphore(refreshConcurrency)
        logger.info("Patch refresh concurrency limited to {} bundle(s)", refreshConcurrency)
    }

    override suspend fun processRefresh(jobId: String) {
        BundleType.entries.forEach { bundleType ->
            val requeued = bundleRepository.requeuePatcherRuntimeFailures(
                bundleType,
                patchWorkerManager.runtimeSelectionFingerprint(bundleType)
            )
            if (requeued > 0) {
                logger.info(
                    "Requeued {} {} bundle(s) after the runtime-selection configuration changed",
                    requeued,
                    bundleType.value
                )
            }
        }

        val candidates = bundleRepository.getBundlesNeedPatchesUpdate()
        logger.info("Processing patches refresh for {} bundle(s)", candidates.size)

        coroutineScope {
            candidates.map { candidate ->
                async {
                    refreshSemaphore.withPermit {
                        try {
                            processCandidate(candidate)
                        } catch (error: CancellationException) {
                            throw error
                        } catch (error: PatcherRuntimeExhaustedException) {
                            bundleRepository.markPatcherRuntimeExhausted(
                                bundleId = candidate.id,
                                runtimeFingerprint = error.runtimeFingerprint,
                                failure = error.message.orEmpty()
                            )
                            logger.warn(
                                "All configured {} runtimes rejected bundle {}; " +
                                    "suppressing retries until the runtime configuration changes",
                                error.bundleType.value,
                                candidate.id,
                                error
                            )
                        } catch (error: Exception) {
                            // Download and worker infrastructure failures remain queued for retry.
                            logger.warn("Something went wrong while processing bundle ${candidate.id}", error)
                        }
                    }
                }
            }.awaitAll()
        }

        logger.info("Process completed")
    }

    /**
     * Downloads and parses outside the database transaction. Only the short replacement write holds
     * a database connection, so concurrent downloads/workers cannot exhaust the Hikari pool.
     */
    private suspend fun processCandidate(candidate: BundlePatchCandidate) {
        val lock = bundleLocks[candidate.id % bundleLocks.size]
        lock.withLock {
            val startedAt = System.nanoTime()
            logger.info("Processing refresh for bundle ${candidate.id}")

            val extraction = candidate.bundle.patches(candidate.patcherRuntime)

            suspendTransaction {
                val bundleEntity = requireNotNull(BundleEntity.findById(candidate.id)) {
                    "Bundle ${candidate.id} disappeared during patch extraction"
                }
                replacePatches(bundleEntity, extraction.patches)
                bundleEntity.patcherRuntime = extraction.patcherRuntime
                bundleEntity.patcherFailure = null
                bundleEntity.patcherFailureFingerprint = null
                bundleEntity.needPatchesUpdate = false
            }

            val elapsedMillis = (System.nanoTime() - startedAt) / 1_000_000
            logger.info(
                "Extracted and persisted {} patches for bundle {} with runtime {} in {} ms",
                extraction.patches.size,
                candidate.id,
                extraction.patcherRuntime,
                elapsedMillis
            )
        }
    }

    private fun replacePatches(bundleEntity: BundleEntity, patches: Set<Patch>) {
        patchRepository.deleteByBundle(bundleEntity)

        patches.forEach { patch ->
            val patchEntity = patchRepository.create(
                bundleEntity = bundleEntity,
                name = patch.name,
                description = patch.description
            )

            patch.compatiblePackages?.forEach { (packageName, versions) ->
                val resolvedPackages = versions
                    ?.map { version ->
                        packageRepository.findOrCreate(packageName, version)
                    }
                    ?: listOf(
                        packageRepository.findOrCreate(packageName, null)
                    )

                resolvedPackages.forEach { pkg ->
                    patchPackageRepository.link(
                        patch = patchEntity,
                        pkg = pkg
                    )
                }
            }
        }
    }
}
