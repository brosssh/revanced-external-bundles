package me.brosssh.bundles.workers

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.runInterruptible
import me.brosssh.bundles.domain.models.BundleType
import me.brosssh.bundles.domain.models.Patch
import me.brosssh.bundles.workers.config.PatchWorkerPoolSettings
import me.brosssh.bundles.workers.config.PatcherRuntimeConfig
import me.brosssh.bundles.workers.config.PatcherRuntimeRegistry
import me.brosssh.bundles.workers.config.ResolvedPatcherRuntime
import me.brosssh.bundles.workers.config.WorkerSettings
import org.slf4j.LoggerFactory
import java.io.ByteArrayInputStream
import java.io.Closeable
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.jar.JarFile
import java.util.jar.Manifest
import java.util.zip.ZipInputStream

data class PatchExtractionResult(
    val patches: Set<Patch>,
    val patcherRuntime: String
)

class PatchWorkerManager internal constructor(
    config: PatcherRuntimeConfig,
    private val runtimeRoot: Path,
    private val poolSettings: PatchWorkerPoolSettings,
    private val clientFactory: (
        ResolvedPatcherRuntime,
        WorkerSettings,
        Path
    ) -> PatchWorkerTransport = { runtime, settings, directory ->
        PatchWorkerClient(runtime, settings, directory)
    }
) : Closeable {
    private val logger = LoggerFactory.getLogger(PatchWorkerManager::class.java)
    private val registry = PatcherRuntimeRegistry.from(config)
    private val poolLock = Any()
    // Retain one wake-up per possible worker; a conflated signal can strand waiters when
    // several workers become idle at once.
    private val availability = Channel<Unit>(maxOf(1, poolSettings.maxProcesses))
    private val slots = mutableListOf<WorkerSlot>()
    private val closed = AtomicBoolean(false)
    private val idleTimeoutNanos = TimeUnit.SECONDS.toNanos(poolSettings.idleTimeoutSeconds)
    private val idleReaper = Executors.newSingleThreadScheduledExecutor { task ->
        Thread(task, "patch-worker-idle-reaper").apply { isDaemon = true }
    }

    init {
        require(poolSettings.maxProcesses > 0) { "Patcher worker maxProcesses must be positive" }
        require(poolSettings.maxPerRuntime > 0) { "Patcher worker maxPerRuntime must be positive" }
        require(poolSettings.maxPerRuntime <= poolSettings.maxProcesses) {
            "Patcher worker maxPerRuntime cannot exceed maxProcesses"
        }
        require(poolSettings.idleTimeoutSeconds > 0) {
            "Patcher worker idleTimeoutSeconds must be positive"
        }

        validateRuntimeInstallation()
        val reapInterval = minOf(poolSettings.idleTimeoutSeconds, 60L)
        idleReaper.scheduleWithFixedDelay(
            ::evictIdleWorkers,
            reapInterval,
            reapInterval,
            TimeUnit.SECONDS
        )
        Runtime.getRuntime().addShutdownHook(
            Thread({ close() }, "patch-worker-shutdown")
        )
        logger.info(
            "Patcher worker pool limited to {} process(es) globally, {} per runtime; idle timeout {}s",
            poolSettings.maxProcesses,
            poolSettings.maxPerRuntime,
            poolSettings.idleTimeoutSeconds
        )
    }

    suspend fun loadPatches(
        bundleType: BundleType,
        bundleBytes: ByteArray,
        cachedRuntime: String? = null
    ): PatchExtractionResult {
        val runtimeFingerprint = runtimeSelectionFingerprint(bundleType)
        val declaredPatcherVersion = try {
            when (bundleType) {
                BundleType.MORPHE_V1 -> readManifestPatcherVersion(bundleBytes)
                BundleType.REVANCED_V3, BundleType.REVANCED_V4 -> null
            }
        } catch (error: Exception) {
            throw PatcherRuntimeSelectionException(
                bundleType,
                runtimeFingerprint,
                error.message ?: "the bundle manifest is malformed",
                error
            )
        }
        val candidates = try {
            registry.resolveCandidates(bundleType, declaredPatcherVersion, cachedRuntime)
        } catch (error: RuntimeException) {
            throw PatcherRuntimeSelectionException(
                bundleType,
                runtimeFingerprint,
                error.message ?: "the declared patcher version is invalid",
                error
            )
        }
        val failures = mutableListOf<Pair<ResolvedPatcherRuntime, Exception>>()

        candidates.forEachIndexed { index, runtime ->
            val slot = borrow(runtime)
            try {
                val patches = runInterruptible(Dispatchers.IO) { slot.client.request(bundleBytes) }
                return PatchExtractionResult(patches, runtime.coordinate)
            } catch (error: CancellationException) {
                throw error
            } catch (error: PatchWorkerTimeoutException) {
                throw error
            } catch (error: Exception) {
                failures += runtime to error
                if (index < candidates.lastIndex) {
                    logger.warn(
                        "Patcher runtime {} failed for {}; trying the next fallback",
                        runtime.coordinate,
                        bundleType.value
                    )
                }
            } finally {
                release(slot)
            }
        }

        val rejections = failures.mapNotNull { (runtime, error) ->
            (error as? PatchBundleRejectedException)?.let { rejection ->
                PatcherRuntimeRejection(runtime.coordinate, rejection.conciseReason())
            }
        }
        if (failures.isNotEmpty() && rejections.size == failures.size) {
            throw PatcherRuntimeExhaustedException(
                bundleType = bundleType,
                runtimeFingerprint = runtimeFingerprint,
                rejections = rejections
            )
        }

        throw IllegalStateException(
            "All ${bundleType.value} patcher runtimes failed: " +
                failures.joinToString { it.first.coordinate }
        ).also { failure -> failures.forEach { (_, error) -> failure.addSuppressed(error) } }
    }

    fun runtimeSelectionFingerprint(bundleType: BundleType): String =
        registry.runtimeSelectionFingerprint(bundleType)

    internal fun poolSnapshot(): PatchWorkerPoolSnapshot = synchronized(poolLock) {
        PatchWorkerPoolSnapshot(
            allocated = slots.size,
            busy = slots.count(WorkerSlot::inUse),
            byRuntime = slots.groupingBy { it.runtime.directoryName }.eachCount()
        )
    }

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        idleReaper.shutdownNow()
        val clients = synchronized(poolLock) {
            slots.map(WorkerSlot::client).also { slots.clear() }
        }
        clients.forEach(PatchWorkerTransport::close)
        availability.close()
    }

    private suspend fun borrow(runtime: ResolvedPatcherRuntime): WorkerSlot {
        while (true) {
            var evicted: PatchWorkerTransport? = null
            val selected = synchronized(poolLock) {
                check(!closed.get()) { "Patcher worker manager is closed" }

                slots.firstOrNull {
                    !it.inUse && !it.retiring && it.runtime.directoryName == runtime.directoryName
                }
                    ?.also { it.inUse = true }
                    ?: allocateSlot(runtime)?.also { allocation ->
                        evicted = allocation.evicted
                    }?.slot
            }

            // If a global slot was reclaimed from another runtime, fully terminate that process
            // before the replacement starts so the configured process bound remains strict.
            evicted?.close()
            if (selected != null) return selected

            // A buffered release signal cannot be lost between the pool check and this wait.
            availability.receive()
        }
    }

    /** Called with [poolLock] held. */
    private fun allocateSlot(runtime: ResolvedPatcherRuntime): Allocation? {
        val runtimeCount = slots.count { it.runtime.directoryName == runtime.directoryName }
        if (runtimeCount >= poolSettings.maxPerRuntime) return null

        var evicted: PatchWorkerTransport? = null
        if (slots.size >= poolSettings.maxProcesses) {
            val idleVictim = slots
                .asSequence()
                .filter { !it.inUse && !it.retiring }
                .minByOrNull(WorkerSlot::lastUsedNanos)
                ?: return null
            slots.remove(idleVictim)
            evicted = idleVictim.client
        }

        val slot = WorkerSlot(
            runtime = runtime,
            client = clientFactory(
                runtime,
                registry.workerSettings,
                runtimeRoot.resolve(runtime.directoryName)
            ),
            inUse = true,
            lastUsedNanos = System.nanoTime(),
            retiring = false
        )
        slots += slot
        logger.info(
            "Allocated patcher worker slot {}/{} for {} runtime {}",
            slots.size,
            poolSettings.maxProcesses,
            runtime.bundleType.value,
            runtime.versionText
        )
        return Allocation(slot, evicted)
    }

    private fun release(slot: WorkerSlot) {
        synchronized(poolLock) {
            if (slots.any { it === slot }) {
                slot.inUse = false
                slot.lastUsedNanos = System.nanoTime()
            }
        }
        availability.trySend(Unit)
    }

    private fun evictIdleWorkers() {
        if (closed.get()) return
        val now = System.nanoTime()
        val expired = synchronized(poolLock) {
            slots.filter { slot ->
                !slot.inUse && !slot.retiring && now - slot.lastUsedNanos >= idleTimeoutNanos
            }.onEach { it.retiring = true }
        }
        // Keep retiring slots counted against the global limit until their processes have exited.
        expired.forEach { it.client.close() }
        synchronized(poolLock) { slots.removeAll(expired) }
        if (expired.isNotEmpty()) {
            logger.info("Terminated {} idle patcher worker process(es)", expired.size)
            repeat(expired.size) { availability.trySend(Unit) }
        }
    }

    private fun validateRuntimeInstallation() {
        registry.runtimes.forEach { runtime ->
            val directory = runtimeRoot.resolve(runtime.directoryName)
            require(Files.isDirectory(directory)) {
                "Missing isolated patcher runtime ${runtime.bundleType.value} ${runtime.versionText} at $directory. " +
                    "Run ./gradlew preparePatcherRuntimes or set BACKEND_PATCHER_RUNTIME_DIR."
            }
            val hasJars = Files.list(directory).use { files ->
                files.anyMatch { it.fileName.toString().endsWith(".jar") }
            }
            require(hasJars) { "Isolated patcher runtime directory contains no jars: $directory" }
        }
        logger.info("Validated {} isolated patcher runtimes in {}", registry.runtimes.size, runtimeRoot)
    }

    private fun readManifestPatcherVersion(bundleBytes: ByteArray): String? {
        ZipInputStream(ByteArrayInputStream(bundleBytes)).use { archive ->
            while (true) {
                val entry = archive.nextEntry ?: break
                if (!entry.isDirectory && entry.name.equals(JarFile.MANIFEST_NAME, ignoreCase = true)) {
                    return Manifest(archive).mainAttributes
                        .getValue("Patcher-Version")
                        ?.trim()
                        ?.takeIf(String::isNotEmpty)
                }
            }
        }
        return null
    }

    private class WorkerSlot(
        val runtime: ResolvedPatcherRuntime,
        val client: PatchWorkerTransport,
        var inUse: Boolean,
        var lastUsedNanos: Long,
        var retiring: Boolean
    )

    private data class Allocation(
        val slot: WorkerSlot,
        val evicted: PatchWorkerTransport?
    )
}

internal data class PatchWorkerPoolSnapshot(
    val allocated: Int,
    val busy: Int,
    val byRuntime: Map<String, Int>
)

class PatchWorkerTimeoutException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)

private fun PatchBundleRejectedException.conciseReason(): String {
    val lines = message.orEmpty()
        .lineSequence()
        .map(String::trim)
        .filter(String::isNotEmpty)
        .toList()
    return lines.lastOrNull { it.startsWith("Caused by: ") }
        ?.removePrefix("Caused by: ")
        ?: lines.firstOrNull()
        ?: "unknown bundle rejection"
}
