package me.brosssh.bundles.workers.config

import me.brosssh.bundles.domain.models.BundleType
import org.semver4j.Semver
import org.semver4j.range.RangeList
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.HexFormat

class PatcherRuntimeRegistry private constructor(
    val workerSettings: WorkerSettings,
    private val definitions: Map<BundleType, Definition>
) {
    private data class Definition(
        val adapter: String,
        val fallbackRuntimes: List<ResolvedPatcherRuntime>,
        val rangedRuntimes: List<ResolvedPatcherRuntime>
    )

    val runtimes: List<ResolvedPatcherRuntime>
        get() = definitions.values.flatMap { definition ->
            definition.fallbackRuntimes + definition.rangedRuntimes
        }.distinctBy { it.bundleType to it.coordinate }

    fun runtimeSelectionFingerprint(bundleType: BundleType): String {
        val definition = definitions.getValue(bundleType)
        val normalizedSelection = buildString {
            appendLine("patcher-runtime-selection-v1")
            appendLine(bundleType.value)
            appendLine("adapter=${definition.adapter}")
            if (bundleType == BundleType.MORPHE_V1) {
                // Morphe runtime selection depends on how Patcher-Version is discovered in the
                // bundle. Changing this marker intentionally requeues terminal failures created by
                // the older manifest-first reader after switching to an any-entry manifest scan.
                appendLine("declared-version-reader=manifest-any-entry-v2")
            }
            definition.fallbackRuntimes.forEach { runtime ->
                appendLine("fallback=${runtime.coordinate}")
            }
            definition.rangedRuntimes
                .sortedBy(ResolvedPatcherRuntime::coordinate)
                .forEach { runtime ->
                    appendLine("runtime=${runtime.coordinate}:${runtime.acceptedBundleVersions}")
                }
        }
        return HexFormat.of().formatHex(
            MessageDigest.getInstance("SHA-256")
                .digest(normalizedSelection.toByteArray(StandardCharsets.UTF_8))
        )
    }

    /**
     * Declared Patcher-Version metadata selects exactly one ranged runtime. Without metadata, the
     * configured fallback order is used, with a still-configured cached success moved to the front.
     */
    fun resolveCandidates(
        bundleType: BundleType,
        declaredPatcherVersion: String?,
        cachedRuntime: String? = null
    ): List<ResolvedPatcherRuntime> {
        val definition = definitions.getValue(bundleType)
        val declaredVersion = declaredPatcherVersion?.let(::Semver)
        if (declaredVersion != null) {
            return listOf(
                definition.rangedRuntimes.singleOrNull {
                    it.acceptedBundleVersions.isSatisfiedBy(declaredVersion)
                } ?: throw IllegalStateException(
                    "No ${bundleType.value} patcher runtime accepts bundle Patcher-Version $declaredVersion"
                )
            )
        }

        val cached = definition.fallbackRuntimes.singleOrNull { it.coordinate == cachedRuntime }
        return if (cached == null) {
            definition.fallbackRuntimes
        } else {
            listOf(cached) + definition.fallbackRuntimes.filterNot { it === cached }
        }
    }

    fun resolve(bundleType: BundleType, declaredPatcherVersion: String?): ResolvedPatcherRuntime =
        resolveCandidates(bundleType, declaredPatcherVersion).first()

    companion object {
        private val supportedAdapters = setOf("morphe-v1", "revanced-v3", "revanced-v4")
        private val heapPattern = Regex("^[1-9]\\d*[kKmMgG]$")

        fun from(config: PatcherRuntimeConfig): PatcherRuntimeRegistry {
            validateWorkerSettings(config.worker)

            val definitions = config.bundleTypes.mapKeys { (type, _) -> BundleType.from(type) }
                .mapValues { (bundleType, raw) -> resolveDefinition(bundleType, raw) }

            val missingTypes = BundleType.entries.toSet() - definitions.keys
            require(missingTypes.isEmpty()) {
                "Missing isolated patcher runtimes for: ${missingTypes.joinToString { it.value }}"
            }

            return PatcherRuntimeRegistry(config.worker, definitions)
        }

        private fun validateWorkerSettings(settings: WorkerSettings) {
            require(settings.timeoutSeconds > 0) { "worker.timeoutSeconds must be positive" }
            require(settings.restartAttempts >= 0) { "worker.restartAttempts cannot be negative" }
            require(heapPattern.matches(settings.maxHeap)) {
                "worker.maxHeap must look like 512m, 1g etc."
            }
        }

        private fun resolveDefinition(
            bundleType: BundleType,
            raw: BundleRuntimeConfig
        ): Definition {
            require(raw.adapter in supportedAdapters) {
                "Unsupported patcher worker adapter '${raw.adapter}' for ${bundleType.value}"
            }
            require(raw.fallbackRuntimes.isNotEmpty()) {
                "${bundleType.value} must define at least one fallback runtime"
            }
            require(raw.fallbackRuntimes.distinct().size == raw.fallbackRuntimes.size) {
                "${bundleType.value} fallback runtimes must not contain duplicates"
            }

            val fallbackRuntimes = raw.fallbackRuntimes.mapIndexed { index, coordinate ->
                resolveRuntime(
                    bundleType = bundleType,
                    adapter = raw.adapter,
                    coordinate = coordinate,
                    range = parseVersionRange("*"),
                    path = "${bundleType.value}.fallback-runtimes[$index]"
                )
            }
            val rangedRuntimes = resolveRangedRuntimes(bundleType, raw)
            validateDisjointRanges(bundleType, rangedRuntimes)
            return Definition(raw.adapter, fallbackRuntimes, rangedRuntimes)
        }

        private fun resolveRangedRuntimes(
            bundleType: BundleType,
            raw: BundleRuntimeConfig
        ): List<ResolvedPatcherRuntime> = when (bundleType) {
            BundleType.MORPHE_V1 -> {
                require(raw.runtimes.isNotEmpty()) {
                    "At least one runtime is required for ${bundleType.value}"
                }
                raw.runtimes.map { (coordinate, rangeText) ->
                    resolveRuntime(
                        bundleType = bundleType,
                        adapter = raw.adapter,
                        coordinate = coordinate,
                        range = parseVersionRange(rangeText),
                        path = "${bundleType.value} runtime '$coordinate'"
                    )
                }
            }

            BundleType.REVANCED_V3, BundleType.REVANCED_V4 -> {
                require(raw.runtimes.isEmpty()) {
                    "${bundleType.value} cannot define runtime ranges because its bundles do not declare Patcher-Version"
                }
                emptyList()
            }
        }

        private fun resolveRuntime(
            bundleType: BundleType,
            adapter: String,
            coordinate: String,
            range: RangeList,
            path: String
        ): ResolvedPatcherRuntime {
            val mavenCoordinate = MavenCoordinate.parse(coordinate, path)
            require(Semver.isValid(mavenCoordinate.version)) {
                "$path runtime coordinate version '${mavenCoordinate.version}' must use semantic versioning"
            }
            return ResolvedPatcherRuntime(
                bundleType = bundleType,
                adapter = adapter,
                mavenCoordinate = mavenCoordinate,
                acceptedBundleVersions = range
            )
        }

        private fun validateDisjointRanges(
            bundleType: BundleType,
            runtimes: List<ResolvedPatcherRuntime>
        ) {
            runtimes.forEachIndexed { index, runtime ->
                runtimes.drop(index + 1).forEach { other ->
                    require(!runtime.acceptedBundleVersions.overlaps(other.acceptedBundleVersions)) {
                        "Overlapping ${bundleType.value} runtime ranges are not allowed: " +
                            "${runtime.versionText}=${runtime.acceptedBundleVersions} overlaps " +
                            "${other.versionText}=${other.acceptedBundleVersions}"
                    }
                }
            }
        }
    }
}
