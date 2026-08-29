package me.brosssh.bundles.workers.config

import org.tomlj.Toml
import org.tomlj.TomlArray
import org.tomlj.TomlTable
import java.io.InputStream
import java.lang.Math.toIntExact

internal object PatcherRuntimeConfigParser {
    private const val RESOURCE_NAME = "patcher-runtimes.toml"

    fun parse(input: InputStream): PatcherRuntimeConfig {
        val document = input.bufferedReader().use { Toml.parse(it.readText()) }
        require(!document.hasErrors()) { document.errors().joinToString("\n") }

        val worker = requireNotNull(document.getTable("worker")) {
            "$RESOURCE_NAME must contain a [worker] table"
        }
        val typeDefinitions = requireNotNull(document.getTable("bundle-types")) {
            "$RESOURCE_NAME must contain [bundle-types] definitions"
        }
        val bundleTypes = typeDefinitions.keySet().associateWith { bundleType ->
            val path = "bundle-types.$bundleType"
            val definition = typeDefinitions.requiredTable(bundleType, path)
            val runtimes = definition.getTable("runtimes")
            BundleRuntimeConfig(
                adapter = definition.requiredString("adapter", "$path.adapter"),
                fallbackRuntimes = definition.requiredStringArray(
                    "fallback-runtimes",
                    "$path.fallback-runtimes"
                ),
                runtimes = runtimes?.keySet()?.associateWith { coordinate ->
                    runtimes.requiredString(coordinate, "$path.runtimes.$coordinate")
                }.orEmpty()
            )
        }

        return PatcherRuntimeConfig(
            worker = WorkerSettings(
                timeoutSeconds = worker.requiredLong("timeout-seconds", "worker.timeout-seconds"),
                maxHeap = worker.requiredString("max-heap", "worker.max-heap"),
                restartAttempts = toIntExact(
                    worker.requiredLong("restart-attempts", "worker.restart-attempts")
                )
            ),
            bundleTypes = bundleTypes
        )
    }

}

private fun tomlKey(value: String) =
    "\"${value.replace("\\", "\\\\").replace("\"", "\\\"")}\""

private fun TomlTable.requiredTable(key: String, path: String): TomlTable =
    requireNotNull(getTable(tomlKey(key))) { "$path must be a TOML table" }

private fun TomlTable.requiredString(key: String, path: String): String =
    requireNotNull(getString(tomlKey(key))) { "$path must be a string" }

private fun TomlTable.requiredLong(key: String, path: String): Long =
    requireNotNull(getLong(key)) { "$path must be an integer" }

private fun TomlTable.requiredStringArray(key: String, path: String): List<String> {
    val array: TomlArray = requireNotNull(getArray(key)) { "$path must be an array" }
    return List(array.size()) { index ->
        requireNotNull(array.getString(index)) { "$path[$index] must be a string" }
    }.also { require(it.isNotEmpty()) { "$path must not be empty" } }
}
