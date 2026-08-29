package me.brosssh.bundles.workers.config

data class PatchWorkerPoolSettings(
    val maxProcesses: Int,
    val maxPerRuntime: Int,
    val idleTimeoutSeconds: Long
)
