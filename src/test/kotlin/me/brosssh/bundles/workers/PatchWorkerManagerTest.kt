package me.brosssh.bundles.workers

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import me.brosssh.bundles.domain.models.BundleType
import me.brosssh.bundles.domain.models.Patch
import me.brosssh.bundles.workers.config.BundleRuntimeConfig
import me.brosssh.bundles.workers.config.PatchWorkerPoolSettings
import me.brosssh.bundles.workers.config.PatcherRuntimeConfig
import me.brosssh.bundles.workers.config.PatcherRuntimeRegistry
import me.brosssh.bundles.workers.config.WorkerSettings
import java.io.ByteArrayOutputStream
import java.nio.file.Path
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.jar.Attributes
import java.util.jar.JarEntry
import java.util.jar.JarFile
import java.util.jar.JarOutputStream
import java.util.jar.Manifest
import kotlin.system.measureTimeMillis
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds

class PatchWorkerManagerTest {
    private val defaultPoolSettings = PatchWorkerPoolSettings(
        maxProcesses = 4,
        maxPerRuntime = 2,
        idleTimeoutSeconds = 300
    )

    @Test
    fun `loads repeated requests through an isolated long-lived worker`() = runBlocking {
        val bundle = emptyMorpheBundle("1.3.3")
        PatchWorkerManager(
            config = testConfig(),
            runtimeRoot = Path.of("build/patcher-runtimes"),
            poolSettings = defaultPoolSettings
        ).use { manager ->
            assertTrue(manager.loadPatches(BundleType.MORPHE_V1, bundle).patches.isEmpty())
            assertTrue(manager.loadPatches(BundleType.MORPHE_V1, bundle).patches.isEmpty())
        }
    }

    @Test
    fun `reads Morphe patcher version when the manifest is not first`() = runBlocking {
        val attempted = mutableListOf<String>()
        PatchWorkerManager(
            config = testConfig(),
            runtimeRoot = Path.of("build/patcher-runtimes"),
            poolSettings = defaultPoolSettings,
            clientFactory = { runtime, _, _ ->
                object : PatchWorkerTransport {
                    override fun request(bundleBytes: ByteArray): Set<Patch> {
                        attempted += runtime.coordinate
                        return emptySet()
                    }

                    override fun close() = Unit
                }
            }
        ).use { manager ->
            manager.loadPatches(
                BundleType.MORPHE_V1,
                morpheBundleWithDelayedManifest("1.3.3")
            )
        }

        assertEquals(listOf("app.morphe:morphe-patcher:1.11.0"), attempted)
    }

    @Test
    fun `tries ordered fallbacks and prefers a cached successful runtime`() = runBlocking {
        val attempted = mutableListOf<String>()
        PatchWorkerManager(
            config = testConfig(),
            runtimeRoot = Path.of("build/patcher-runtimes"),
            poolSettings = defaultPoolSettings,
            clientFactory = { runtime, _, _ ->
                object : PatchWorkerTransport {
                    override fun request(bundleBytes: ByteArray): Set<Patch> {
                        attempted += runtime.coordinate
                        if (runtime.versionText == "19.3.1") error("incompatible runtime")
                        return emptySet()
                    }

                    override fun close() = Unit
                }
            }
        ).use { manager ->
            val first = manager.loadPatches(BundleType.REVANCED_V3, byteArrayOf(1))
            assertEquals("app.revanced:revanced-patcher:15.0.3", first.patcherRuntime)
            assertEquals(
                listOf(
                    "app.revanced:revanced-patcher:19.3.1",
                    "app.revanced:revanced-patcher:15.0.3"
                ),
                attempted
            )

            attempted.clear()
            val cached = manager.loadPatches(
                BundleType.REVANCED_V3,
                byteArrayOf(1),
                cachedRuntime = first.patcherRuntime
            )
            assertEquals(first.patcherRuntime, cached.patcherRuntime)
            assertEquals(listOf("app.revanced:revanced-patcher:15.0.3"), attempted)
        }
    }

    @Test
    fun `reports deterministic rejection after every fallback runtime fails`() = runBlocking {
        val attempted = mutableListOf<String>()
        PatchWorkerManager(
            config = testConfig(),
            runtimeRoot = Path.of("build/patcher-runtimes"),
            poolSettings = defaultPoolSettings,
            clientFactory = { runtime, _, _ ->
                object : PatchWorkerTransport {
                    override fun request(bundleBytes: ByteArray): Set<Patch> {
                        attempted += runtime.coordinate
                        throw PatchBundleRejectedException(
                            "java.lang.NoClassDefFoundError\nCaused by: java.lang.ClassNotFoundException: missing.Type"
                        )
                    }

                    override fun close() = Unit
                }
            }
        ).use { manager ->
            val error = assertFailsWith<PatcherRuntimeExhaustedException> {
                manager.loadPatches(BundleType.REVANCED_V3, byteArrayOf(1))
            }

            val expectedRuntimes = listOf(
                "app.revanced:revanced-patcher:19.3.1",
                "app.revanced:revanced-patcher:15.0.3",
                "app.revanced:revanced-patcher:11.0.4"
            )
            assertEquals(expectedRuntimes, attempted)
            assertEquals(
                expectedRuntimes.map { runtime ->
                    PatcherRuntimeRejection(runtime, "java.lang.ClassNotFoundException: missing.Type")
                },
                error.rejections
            )
            assertEquals(manager.runtimeSelectionFingerprint(BundleType.REVANCED_V3), error.runtimeFingerprint)
        }
    }

    @Test
    fun `reports deterministic rejection for every bundle family`() = runBlocking {
        val bundles = mapOf(
            BundleType.MORPHE_V1 to Pair(
                emptyMorpheBundle("1.3.3"),
                listOf("app.morphe:morphe-patcher:1.11.0")
            ),
            BundleType.REVANCED_V4 to Pair(
                byteArrayOf(1),
                listOf(
                    "app.revanced:patcher-jvm:22.1.0-dev.1",
                    "app.revanced:revanced-patcher:21.1.0-dev.5",
                    "app.revanced:revanced-patcher:20.0.2"
                )
            )
        )

        bundles.forEach { (bundleType, testCase) ->
            val (bundleBytes, expectedRuntimes) = testCase
            PatchWorkerManager(
                config = testConfig(),
                runtimeRoot = Path.of("build/patcher-runtimes"),
                poolSettings = defaultPoolSettings,
                clientFactory = { _, _, _ ->
                    object : PatchWorkerTransport {
                        override fun request(bundleBytes: ByteArray): Set<Patch> =
                            throw PatchBundleRejectedException("unsupported bundle")

                        override fun close() = Unit
                    }
                }
            ).use { manager ->
                val error = assertFailsWith<PatcherRuntimeExhaustedException> {
                    manager.loadPatches(bundleType, bundleBytes)
                }
                assertEquals(bundleType, error.bundleType)
                assertEquals(manager.runtimeSelectionFingerprint(bundleType), error.runtimeFingerprint)
                assertEquals(expectedRuntimes, error.rejections.map(PatcherRuntimeRejection::runtimeCoordinate))
            }
        }
    }

    @Test
    fun `does not terminalize when any runtime failure is transient`() = runBlocking {
        val transientFailures = listOf(
            PatchWorkerFailureException("temporary file write failed"),
            PatchWorkerTransportException("worker pipe closed", null)
        )

        transientFailures.forEach { transientFailure ->
            PatchWorkerManager(
                config = testConfig(),
                runtimeRoot = Path.of("build/patcher-runtimes"),
                poolSettings = defaultPoolSettings,
                clientFactory = { runtime, _, _ ->
                    object : PatchWorkerTransport {
                        override fun request(bundleBytes: ByteArray): Set<Patch> {
                            if (runtime.versionText == "19.3.1") {
                                throw PatchBundleRejectedException("unsupported bundle")
                            }
                            throw transientFailure
                        }

                        override fun close() = Unit
                    }
                }
            ).use { manager ->
                val error = assertFailsWith<IllegalStateException> {
                    manager.loadPatches(BundleType.REVANCED_V3, byteArrayOf(1))
                }
                assertTrue(error.suppressed.any { it::class == transientFailure::class })
            }
        }
    }

    @Test
    fun `does not try another runtime after a timeout`() = runBlocking {
        val attempted = mutableListOf<String>()
        PatchWorkerManager(
            config = testConfig(),
            runtimeRoot = Path.of("build/patcher-runtimes"),
            poolSettings = defaultPoolSettings,
            clientFactory = { runtime, _, _ ->
                object : PatchWorkerTransport {
                    override fun request(bundleBytes: ByteArray): Set<Patch> {
                        attempted += runtime.coordinate
                        throw PatchWorkerTimeoutException("request timed out")
                    }

                    override fun close() = Unit
                }
            }
        ).use { manager ->
            assertFailsWith<PatchWorkerTimeoutException> {
                manager.loadPatches(BundleType.REVANCED_V3, byteArrayOf(1))
            }
        }

        assertEquals(listOf("app.revanced:revanced-patcher:19.3.1"), attempted)
    }

    @Test
    fun `terminalizes invalid and unsupported Morphe patcher versions`() = runBlocking {
        val clientsCreated = AtomicInteger()
        PatchWorkerManager(
            config = testConfigWithUnsupportedMorpheVersion(),
            runtimeRoot = Path.of("build/patcher-runtimes"),
            poolSettings = defaultPoolSettings,
            clientFactory = { _, _, _ ->
                clientsCreated.incrementAndGet()
                error("Runtime selection failures must not allocate a worker")
            }
        ).use { manager ->
            listOf("not-a-version", "999.0.0").forEach { version ->
                val error = assertFailsWith<PatcherRuntimeSelectionException> {
                    manager.loadPatches(BundleType.MORPHE_V1, emptyMorpheBundle(version))
                }
                assertEquals(BundleType.MORPHE_V1, error.bundleType)
                assertEquals(
                    manager.runtimeSelectionFingerprint(BundleType.MORPHE_V1),
                    error.runtimeFingerprint
                )
            }
        }

        assertEquals(0, clientsCreated.get())
    }

    @Test
    fun `limits concurrent workers per runtime`() = runBlocking {
        val active = AtomicInteger()
        val peak = AtomicInteger()
        val created = AtomicInteger()
        val concurrentRequests = CountDownLatch(2)
        val bundle = emptyMorpheBundle("1.3.3")

        PatchWorkerManager(
            config = testConfig(),
            runtimeRoot = Path.of("build/patcher-runtimes"),
            poolSettings = PatchWorkerPoolSettings(
                maxProcesses = 4,
                maxPerRuntime = 2,
                idleTimeoutSeconds = 60
            ),
            clientFactory = { _, _, _ ->
                created.incrementAndGet()
                object : PatchWorkerTransport {
                    override fun request(bundleBytes: ByteArray): Set<Patch> {
                        val current = active.incrementAndGet()
                        peak.updateAndGet { previous -> maxOf(previous, current) }
                        return try {
                            concurrentRequests.countDown()
                            check(concurrentRequests.await(5, TimeUnit.SECONDS)) {
                                "Two worker requests did not execute concurrently"
                            }
                            emptySet()
                        } finally {
                            active.decrementAndGet()
                        }
                    }

                    override fun close() = Unit
                }
            }
        ).use { manager ->
            withTimeout(5_000.milliseconds) {
                coroutineScope {
                    List(8) {
                        async { manager.loadPatches(BundleType.MORPHE_V1, bundle) }
                    }.awaitAll()
                }
            }

            assertEquals(2, peak.get())
            assertEquals(2, created.get())
            assertEquals(2, manager.poolSnapshot().allocated)
            assertEquals(0, manager.poolSnapshot().busy)
        }
    }

    @Test
    fun `reclaims an idle runtime slot when the global pool is full`() = runBlocking {
        val created = AtomicInteger()
        val closed = AtomicInteger()

        PatchWorkerManager(
            config = testConfig(),
            runtimeRoot = Path.of("build/patcher-runtimes"),
            poolSettings = PatchWorkerPoolSettings(
                maxProcesses = 1,
                maxPerRuntime = 1,
                idleTimeoutSeconds = 60
            ),
            clientFactory = { _, _, _ ->
                created.incrementAndGet()
                object : PatchWorkerTransport {
                    override fun request(bundleBytes: ByteArray) = emptySet<Patch>()
                    override fun close() {
                        closed.incrementAndGet()
                    }
                }
            }
        ).use { manager ->
            manager.loadPatches(BundleType.MORPHE_V1, emptyMorpheBundle("1.3.3"))
            manager.loadPatches(BundleType.MORPHE_V1, emptyMorpheBundle("1.2.0"))

            assertEquals(2, created.get())
            assertEquals(1, closed.get())
            assertEquals(1, manager.poolSnapshot().allocated)
        }
    }

    @Test
    fun `terminates a worker that exceeds its hard deadline`() {
        val runtime = PatcherRuntimeRegistry.from(testConfig())
            .resolve(BundleType.MORPHE_V1, "1.3.3")
        PatchWorkerClient(
            runtime = runtime,
            settings = WorkerSettings(timeoutSeconds = 1, maxHeap = "64m", restartAttempts = 1),
            runtimeDirectory = Path.of("build/patcher-runtimes").resolve(runtime.directoryName),
            workerMainClass = HangingPatchWorker::class.java.name
        ).use { client ->
            val elapsed = measureTimeMillis {
                assertFailsWith<PatchWorkerTimeoutException> { client.request(byteArrayOf(1)) }
            }
            assertTrue(elapsed < 5_000, "Hard timeout took ${elapsed}ms")
        }
    }

    private fun testConfig() = PatcherRuntimeConfig(
        worker = WorkerSettings(timeoutSeconds = 60, maxHeap = "512m", restartAttempts = 1),
        bundleTypes = mapOf(
            "Morphe:V1" to BundleRuntimeConfig(
                adapter = "morphe-v1",
                fallbackRuntimes = listOf("app.morphe:morphe-patcher:1.1.1"),
                runtimes = mapOf(
                    "app.morphe:morphe-patcher:1.2.0" to "<=1.2.0",
                    "app.morphe:morphe-patcher:1.11.0" to ">1.2.0"
                )
            ),
            "ReVanced:V3" to BundleRuntimeConfig(
                adapter = "revanced-v3",
                fallbackRuntimes = listOf(
                    "app.revanced:revanced-patcher:19.3.1",
                    "app.revanced:revanced-patcher:15.0.3",
                    "app.revanced:revanced-patcher:11.0.4"
                ),
                runtimes = emptyMap()
            ),
            "ReVanced:V4" to BundleRuntimeConfig(
                adapter = "revanced-v4",
                fallbackRuntimes = listOf(
                    "app.revanced:patcher-jvm:22.1.0-dev.1",
                    "app.revanced:revanced-patcher:21.1.0-dev.5",
                    "app.revanced:revanced-patcher:20.0.2"
                ),
                runtimes = emptyMap()
            )
        )
    )

    private fun testConfigWithUnsupportedMorpheVersion(): PatcherRuntimeConfig {
        val config = testConfig()
        val morphe = config.bundleTypes.getValue("Morphe:V1")
        return config.copy(
            bundleTypes = config.bundleTypes + (
                "Morphe:V1" to morphe.copy(
                    runtimes = mapOf(
                        "app.morphe:morphe-patcher:1.2.0" to "<=1.2.0"
                    )
                )
            )
        )
    }

    private fun emptyMorpheBundle(patcherVersion: String): ByteArray {
        val manifest = morpheManifest(patcherVersion)
        return ByteArrayOutputStream().also { bytes ->
            JarOutputStream(bytes, manifest).use { }
        }.toByteArray()
    }

    private fun morpheBundleWithDelayedManifest(patcherVersion: String): ByteArray =
        ByteArrayOutputStream().also { bytes ->
            JarOutputStream(bytes).use { jar ->
                jar.putNextEntry(JarEntry("example/First.class"))
                jar.write(byteArrayOf(0))
                jar.closeEntry()
                jar.putNextEntry(JarEntry(JarFile.MANIFEST_NAME))
                morpheManifest(patcherVersion).write(jar)
                jar.closeEntry()
            }
        }.toByteArray()

    private fun morpheManifest(patcherVersion: String) = Manifest().apply {
        mainAttributes[Attributes.Name.MANIFEST_VERSION] = "1.0"
        mainAttributes.putValue("Patcher-Version", patcherVersion)
    }
}

private object HangingPatchWorker {
    @JvmStatic
    @Suppress("UNUSED_PARAMETER")
    fun main(args: Array<String>) {
        Thread.sleep(30_000)
    }
}
