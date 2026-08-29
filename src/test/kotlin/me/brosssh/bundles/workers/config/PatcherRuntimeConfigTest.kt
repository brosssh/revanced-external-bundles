package me.brosssh.bundles.workers.config

import me.brosssh.bundles.domain.models.BundleType
import org.semver4j.Semver
import java.io.ByteArrayInputStream
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.HexFormat
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class MavenCoordinateTest {
    @Test
    fun `parses artifact and semantic version components`() {
        val coordinate = MavenCoordinate.parse(
            "app.revanced:revanced-patcher:19.3.1",
            "test runtime"
        )

        assertEquals("app.revanced:revanced-patcher", coordinate.artifact)
        assertEquals("19.3.1", coordinate.version)
        assertEquals("app.revanced:revanced-patcher:19.3.1", coordinate.value)
    }

    @Test
    fun `rejects malformed coordinates`() {
        assertFailsWith<IllegalArgumentException> {
            MavenCoordinate.parse("app.revanced:revanced-patcher", "test runtime")
        }
    }
}

class PatcherRuntimeConfigParserTest {
    @Test
    fun `requires worker settings instead of applying hidden defaults`() {
        val input = ByteArrayInputStream(
            """
            [worker]
            max-heap = "512m"
            restart-attempts = 1

            [bundle-types]
            """.trimIndent().toByteArray()
        )

        val error = assertFailsWith<IllegalArgumentException> {
            PatcherRuntimeConfigParser.parse(input)
        }
        assertTrue(error.message.orEmpty().contains("worker.timeout-seconds must be an integer"))
    }
}

class SemverRangeTest {
    @Test
    fun `uses semver4j range semantics including prereleases`() {
        val majorOne = parseVersionRange(">=1 <2")
        assertTrue(majorOne.isSatisfiedBy(Semver("1.0.0")))
        assertTrue(majorOne.isSatisfiedBy(Semver("1.9.9")))
        assertFalse(majorOne.isSatisfiedBy(Semver("2.0.0")))
        assertTrue(parseVersionRange("*").isSatisfiedBy(Semver("999.0.0")))
        assertTrue(parseVersionRange("<=1.5.0-dev.10").isSatisfiedBy(Semver("1.5.0-dev.2")))
    }

    @Test
    fun `detects overlap including inclusive boundaries and disjunctions`() {
        assertTrue(parseVersionRange(">=1 <2").overlaps(parseVersionRange(">=1.5 <3")))
        assertFalse(parseVersionRange(">=1 <2").overlaps(parseVersionRange(">=2 <3")))
        assertTrue(parseVersionRange(">=1 <=2").overlaps(parseVersionRange(">=2")))
        assertTrue(parseVersionRange("1.x || >=3").overlaps(parseVersionRange(">=3.5 <4")))
    }

    @Test
    fun `rejects unsatisfiable ranges`() {
        assertFailsWith<IllegalArgumentException> { parseVersionRange(">=2 <1") }
        assertFailsWith<IllegalArgumentException> { parseVersionRange(">2 <=2") }
    }
}

class PatcherRuntimeRegistryTest {
    @Test
    fun `uses the configured fallback runtime when metadata is absent`() {
        val registry = PatcherRuntimeRegistry.from(testConfig())

        assertEquals("1.1.1", registry.resolve(BundleType.MORPHE_V1, null).versionText)
        assertEquals("19.3.1", registry.resolve(BundleType.REVANCED_V3, null).versionText)
        assertEquals("21.1.0-dev.5", registry.resolve(BundleType.REVANCED_V4, null).versionText)
        assertEquals("1.11.0", registry.resolve(BundleType.MORPHE_V1, "2.0.0").versionText)
    }

    @Test
    fun `moves a configured cached fallback to the front`() {
        val registry = PatcherRuntimeRegistry.from(testConfig())
        val cached = "app.revanced:revanced-patcher:11.0.4"

        assertEquals(
            listOf(
                cached,
                "app.revanced:revanced-patcher:19.3.1"
            ),
            registry.resolveCandidates(BundleType.REVANCED_V3, null, cached).map { it.coordinate }
        )
        assertEquals(
            listOf(
                "app.revanced:revanced-patcher:19.3.1",
                cached
            ),
            registry.resolveCandidates(BundleType.REVANCED_V3, null, "not:configured:1.0").map { it.coordinate }
        )
    }

    @Test
    fun `fingerprint changes only with runtime selection`() {
        val base = testConfig()
        val reorderedV3 = base.withBundleType("ReVanced:V3") { definition ->
            definition.copy(fallbackRuntimes = definition.fallbackRuntimes.reversed())
        }
        val changedMorpheRanges = base.withBundleType("Morphe:V1") { definition ->
            definition.copy(
                runtimes = mapOf(
                    "app.morphe:morphe-patcher:1.2.0" to "<=1.3.0",
                    "app.morphe:morphe-patcher:1.11.0" to ">1.3.0"
                )
            )
        }

        assertNotEquals(
            base.fingerprint(BundleType.REVANCED_V3),
            reorderedV3.fingerprint(BundleType.REVANCED_V3)
        )
        assertNotEquals(
            base.fingerprint(BundleType.MORPHE_V1),
            changedMorpheRanges.fingerprint(BundleType.MORPHE_V1)
        )
        assertEquals(
            base.fingerprint(BundleType.REVANCED_V4),
            base.copy(worker = base.worker.copy(maxHeap = "1g")).fingerprint(BundleType.REVANCED_V4)
        )
    }

    @Test
    fun `Morphe fingerprint invalidates failures from the manifest-first selector`() {
        val config = testConfig()

        assertNotEquals(
            legacySelectionFingerprint(config, BundleType.MORPHE_V1),
            config.fingerprint(BundleType.MORPHE_V1)
        )
        assertEquals(
            legacySelectionFingerprint(config, BundleType.REVANCED_V3),
            config.fingerprint(BundleType.REVANCED_V3)
        )
        assertEquals(
            legacySelectionFingerprint(config, BundleType.REVANCED_V4),
            config.fingerprint(BundleType.REVANCED_V4)
        )
    }

    @Test
    fun `selects one of multiple Morphe runtimes by declared version`() {
        val config = runtimeConfig(
            bundleTypes = mapOf(
                "Morphe:V1" to BundleRuntimeConfig(
                    adapter = "morphe-v1",
                    fallbackRuntimes = listOf("app.morphe:morphe-patcher:1.2.0"),
                    runtimes = mapOf(
                        "app.morphe:morphe-patcher:1.3.3" to ">=1 <1.5",
                        "app.morphe:morphe-patcher:1.5.0" to ">=1.5 <2"
                    )
                ),
                "ReVanced:V3" to fallback("revanced-v3", "11.0.4"),
                "ReVanced:V4" to fallback("revanced-v4", "21.1.0-dev.5")
            )
        )
        val registry = PatcherRuntimeRegistry.from(config)

        assertEquals("1.3.3", registry.resolve(BundleType.MORPHE_V1, "1.4.9").versionText)
        assertEquals("1.2.0", registry.resolve(BundleType.MORPHE_V1, null).versionText)
        assertEquals("1.5.0", registry.resolve(BundleType.MORPHE_V1, "1.5.0").versionText)
        assertEquals(
            "1.5.0",
            registry.resolveCandidates(
                BundleType.MORPHE_V1,
                declaredPatcherVersion = "1.5.0",
                cachedRuntime = "app.morphe:morphe-patcher:1.2.0"
            ).single().versionText
        )
        assertFailsWith<IllegalArgumentException> {
            registry.resolve(BundleType.MORPHE_V1, "not-a-version")
        }
    }

    @Test
    fun `rejects a non-coordinate ReVanced fallback`() {
        val config = runtimeConfig(
            bundleTypes = mapOf(
                "Morphe:V1" to BundleRuntimeConfig(
                    adapter = "morphe-v1",
                    fallbackRuntimes = listOf("app.morphe:morphe-patcher:1.2.0"),
                    runtimes = mapOf("app.morphe:morphe-patcher:1.3.3" to "*")
                ),
                "ReVanced:V3" to BundleRuntimeConfig(
                    adapter = "revanced-v3",
                    fallbackRuntimes = listOf("11.0.4"),
                    runtimes = emptyMap()
                ),
                "ReVanced:V4" to fallback("revanced-v4", "21.1.0-dev.5")
            )
        )

        assertFailsWith<IllegalArgumentException> { PatcherRuntimeRegistry.from(config) }
    }

    @Test
    fun `rejects duplicate fallback runtimes`() {
        val duplicate = "app.revanced:revanced-patcher:19.3.1"
        val config = runtimeConfig(
            bundleTypes = mapOf(
                "Morphe:V1" to BundleRuntimeConfig(
                    adapter = "morphe-v1",
                    fallbackRuntimes = listOf("app.morphe:morphe-patcher:1.2.0"),
                    runtimes = mapOf("app.morphe:morphe-patcher:1.3.3" to "*")
                ),
                "ReVanced:V3" to BundleRuntimeConfig(
                    adapter = "revanced-v3",
                    fallbackRuntimes = listOf(duplicate, duplicate),
                    runtimes = emptyMap()
                ),
                "ReVanced:V4" to fallback("revanced-v4", "21.1.0-dev.5")
            )
        )

        assertFailsWith<IllegalArgumentException> { PatcherRuntimeRegistry.from(config) }
    }

    @Test
    fun `rejects overlapping runtime ranges`() {
        val config = runtimeConfig(
            bundleTypes = mapOf(
                "Morphe:V1" to BundleRuntimeConfig(
                    adapter = "morphe-v1",
                    fallbackRuntimes = listOf("app.morphe:morphe-patcher:1.2.0"),
                    runtimes = mapOf(
                        "app.morphe:morphe-patcher:1.3.3" to ">=1 <2",
                        "app.morphe:morphe-patcher:1.5.0" to ">=1.5 <3"
                    )
                ),
                "ReVanced:V3" to fallback("revanced-v3", "11.0.4"),
                "ReVanced:V4" to fallback("revanced-v4", "21.1.0-dev.5")
            )
        )

        assertFailsWith<IllegalArgumentException> { PatcherRuntimeRegistry.from(config) }
    }

    private fun testConfig() = runtimeConfig(
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
                    "app.revanced:revanced-patcher:11.0.4"
                ),
                runtimes = emptyMap()
            ),
            "ReVanced:V4" to BundleRuntimeConfig(
                adapter = "revanced-v4",
                fallbackRuntimes = listOf("app.revanced:revanced-patcher:21.1.0-dev.5"),
                runtimes = emptyMap()
            )
        )
    )

    private fun runtimeConfig(bundleTypes: Map<String, BundleRuntimeConfig>) = PatcherRuntimeConfig(
        worker = WorkerSettings(timeoutSeconds = 60, maxHeap = "512m", restartAttempts = 1),
        bundleTypes = bundleTypes
    )

    private fun fallback(adapter: String, version: String) = BundleRuntimeConfig(
        adapter = adapter,
        fallbackRuntimes = listOf("app.revanced:revanced-patcher:$version"),
        runtimes = emptyMap()
    )

    private fun PatcherRuntimeConfig.withBundleType(
        bundleType: String,
        transform: (BundleRuntimeConfig) -> BundleRuntimeConfig
    ) = copy(bundleTypes = bundleTypes + (bundleType to transform(bundleTypes.getValue(bundleType))))

    private fun legacySelectionFingerprint(config: PatcherRuntimeConfig, bundleType: BundleType): String {
        val definition = config.bundleTypes.getValue(bundleType.value)
        val normalizedSelection = buildString {
            appendLine("patcher-runtime-selection-v1")
            appendLine(bundleType.value)
            appendLine("adapter=${definition.adapter}")
            definition.fallbackRuntimes.forEach { coordinate ->
                appendLine("fallback=$coordinate")
            }
            definition.runtimes.toSortedMap().forEach { (coordinate, rangeText) ->
                appendLine("runtime=$coordinate:${parseVersionRange(rangeText)}")
            }
        }
        return HexFormat.of().formatHex(
            MessageDigest.getInstance("SHA-256")
                .digest(normalizedSelection.toByteArray(StandardCharsets.UTF_8))
        )
    }

    private fun PatcherRuntimeConfig.fingerprint(bundleType: BundleType) =
        PatcherRuntimeRegistry.from(this).runtimeSelectionFingerprint(bundleType)
}
