package me.brosssh.bundles.workers.config

data class PatcherRuntimeConfig(
    val worker: WorkerSettings,
    val bundleTypes: Map<String, BundleRuntimeConfig>
)

data class WorkerSettings(
    val timeoutSeconds: Long,
    val maxHeap: String,
    val restartAttempts: Int
)

data class BundleRuntimeConfig(
    val adapter: String,
    val fallbackRuntimes: List<String>,
    val runtimes: Map<String, String>
)
