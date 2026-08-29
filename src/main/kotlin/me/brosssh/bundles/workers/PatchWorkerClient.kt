package me.brosssh.bundles.workers

import me.brosssh.bundles.domain.models.Patch
import me.brosssh.bundles.workers.config.ResolvedPatcherRuntime
import me.brosssh.bundles.workers.config.WorkerSettings
import me.brosssh.bundles.workers.config.runtimeDirectorySegment
import org.slf4j.LoggerFactory
import java.io.Closeable
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import kotlin.concurrent.thread

internal interface PatchWorkerTransport : Closeable {
    fun request(bundleBytes: ByteArray): Set<Patch>
}

internal class PatchWorkerClient(
    private val runtime: ResolvedPatcherRuntime,
    private val settings: WorkerSettings,
    private val runtimeDirectory: Path,
    private val workerMainClass: String = "me.brosssh.bundles.workers.PatchWorkerMainKt"
) : PatchWorkerTransport {
    private val logger = LoggerFactory.getLogger(PatchWorkerClient::class.java)

    // The protocol has no response multiplexing, so one client must complete a frame exchange before
    // another starts. Timeout termination deliberately does not take this lock: it must unblock I/O.
    private val requestLock = Any()
    private val requestIds = AtomicLong()

    @Volatile
    private var process: Process? = null

    @Volatile
    private var input: DataInputStream? = null

    @Volatile
    private var output: DataOutputStream? = null

    override fun request(bundleBytes: ByteArray): Set<Patch> = synchronized(requestLock) {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(settings.timeoutSeconds)
        var lastError: Throwable? = null
        for (attempt in 0..settings.restartAttempts) {
            val remainingNanos = deadline - System.nanoTime()
            if (remainingNanos <= 0) throw timeout(lastError)

            val timedOut = AtomicBoolean(false)
            val timeoutTask = timeoutExecutor.schedule(
                {
                    timedOut.set(true)
                    terminate()
                },
                remainingNanos,
                TimeUnit.NANOSECONDS
            )
            try {
                ensureStarted()
                val requestId = requestIds.incrementAndGet()
                PatchWorkerProtocol.writeRequest(requireNotNull(output), requestId, bundleBytes)
                return PatchWorkerProtocol.readResponse(requireNotNull(input), requestId)
            } catch (error: PatchBundleRejectedException) {
                if (timedOut.get()) throw timeout(error)
                throw error
            } catch (error: Exception) {
                lastError = error
                terminate()
                if (timedOut.get() || System.nanoTime() >= deadline) throw timeout(error)
                if (Thread.currentThread().isInterrupted || attempt == settings.restartAttempts) break
                logger.warn(
                    "Restarting {} patcher runtime {} after worker transport failure",
                    runtime.bundleType.value,
                    runtime.versionText,
                    error
                )
            } finally {
                timeoutTask.cancel(false)
            }
        }
        throw PatchWorkerTransportException(
            "${runtime.bundleType.value} patcher worker ${runtime.versionText} failed",
            lastError
        )
    }

    fun terminate() {
        val current = process
        val currentOutput = output
        val currentInput = input
        process = null
        output = null
        input = null

        // On Windows, closing a Process input stream while another thread is blocked reading it can
        // block until the child exits. Kill the worker first so the blocked protocol read is released.
        if (current != null) {
            runCatching { current.destroy() }
            if (!runCatching { current.waitFor(2, TimeUnit.SECONDS) }.getOrDefault(false)) {
                runCatching { current.destroyForcibly() }
                runCatching { current.waitFor(2, TimeUnit.SECONDS) }
            }
        }

        runCatching { currentOutput?.close() }
        runCatching { currentInput?.close() }
    }

    override fun close() = terminate()

    private fun timeout(cause: Throwable?) = PatchWorkerTimeoutException(
        "${runtime.bundleType.value} patch extraction exceeded ${settings.timeoutSeconds} seconds",
        cause
    )

    private fun ensureStarted() {
        process?.takeIf(Process::isAlive)?.let { return }

        require(Files.isDirectory(runtimeDirectory)) {
            "Patcher runtime directory does not exist: $runtimeDirectory"
        }
        val hasJars = Files.list(runtimeDirectory).use { files ->
            files.anyMatch { it.fileName.toString().endsWith(".jar") }
        }
        require(hasJars) { "Patcher runtime directory contains no jars: $runtimeDirectory" }

        val javaExecutable = Path.of(
            System.getProperty("java.home"),
            "bin",
            if (System.getProperty("os.name").startsWith("Windows", ignoreCase = true)) "java.exe" else "java"
        )
        val classpath = listOf(
            System.getProperty("java.class.path"),
            runtimeDirectory.toAbsolutePath().normalize().toString() + File.separator + "*"
        ).joinToString(System.getProperty("path.separator"))

        val started = ProcessBuilder(
            javaExecutable.toString(),
            "-Xmx${settings.maxHeap}",
            "-cp",
            classpath,
            workerMainClass,
            runtime.adapter
        )
            .redirectErrorStream(false)
            .start()

        process = started
        input = DataInputStream(started.inputStream.buffered())
        output = DataOutputStream(started.outputStream.buffered())
        thread(
            name = "patch-worker-${runtimeDirectorySegment(runtime.bundleType.value)}-${runtime.versionText}-stderr",
            isDaemon = true
        ) {
            try {
                started.errorStream.bufferedReader().useLines { lines ->
                    lines.forEach { line ->
                        logger.info(
                            "[patch-worker {} {}] {}",
                            runtime.bundleType.value,
                            runtime.versionText,
                            line
                        )
                    }
                }
            } catch (_: IOException) {
                // Expected when a timed out or failed worker is terminated.
            }
        }
        logger.info(
            "Started isolated patcher worker for {} runtime {} (pid {})",
            runtime.bundleType.value,
            runtime.versionText,
            started.pid()
        )
    }

    private companion object {
        val timeoutExecutor = Executors.newSingleThreadScheduledExecutor { task ->
            Thread(task, "patch-worker-timeouts").apply { isDaemon = true }
        }
    }
}

class PatchWorkerTransportException(message: String, cause: Throwable?) : IOException(message, cause)
