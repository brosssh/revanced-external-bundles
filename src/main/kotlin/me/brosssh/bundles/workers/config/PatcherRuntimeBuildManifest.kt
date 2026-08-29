package me.brosssh.bundles.workers.config

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.nio.file.Files
import java.nio.file.Path

@Serializable
internal data class PatcherRuntimeBuildManifest(
    val formatVersion: Int,
    val runtimes: List<PatcherRuntimeBuildEntry>
)

@Serializable
internal data class PatcherRuntimeBuildEntry(
    val coordinate: String,
    val directory: String
)

/**
 * Normalizes the application-owned runtime configuration for Gradle. The build plugin consumes this
 * manifest instead of independently parsing patcher-runtimes.toml and drifting from startup rules.
 */
internal object PatcherRuntimeBuildManifestGenerator {
    private const val FORMAT_VERSION = 1
    private val json = Json { prettyPrint = true }

    @JvmStatic
    fun main(args: Array<String>) {
        require(args.size == 1) { "Expected the output manifest path" }

        val registry = PatcherRuntimeRegistry.from(PatcherRuntimeConfigLoader.loadBundled())
        val manifest = PatcherRuntimeBuildManifest(
            formatVersion = FORMAT_VERSION,
            runtimes = registry.runtimes
                .map { PatcherRuntimeBuildEntry(it.coordinate, it.directoryName) }
                .sortedBy(PatcherRuntimeBuildEntry::directory)
        )
        val output = Path.of(args.single())
        output.parent?.let(Files::createDirectories)
        Files.writeString(output, json.encodeToString(manifest))
    }
}
