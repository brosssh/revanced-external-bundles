package me.brosssh.bundles.workers

import me.brosssh.bundles.domain.models.BundleType

data class PatcherRuntimeRejection(
    val runtimeCoordinate: String,
    val reason: String
)

sealed class PatcherBundleTerminalException(
    val bundleType: BundleType,
    val runtimeFingerprint: String,
    message: String,
    cause: Throwable? = null
) : RuntimeException(message, cause)

class PatcherRuntimeExhaustedException internal constructor(
    bundleType: BundleType,
    runtimeFingerprint: String,
    val rejections: List<PatcherRuntimeRejection>
) : PatcherBundleTerminalException(
    bundleType,
    runtimeFingerprint,
    buildString {
        append("All ${bundleType.value} patcher runtimes rejected the bundle: ")
        append(rejections.joinToString { rejection ->
            "${rejection.runtimeCoordinate} (${rejection.reason})"
        })
    }
) {
    init {
        require(rejections.isNotEmpty()) { "Runtime exhaustion requires at least one rejection" }
    }
}

class PatcherRuntimeSelectionException internal constructor(
    bundleType: BundleType,
    runtimeFingerprint: String,
    reason: String,
    cause: Throwable
) : PatcherBundleTerminalException(
    bundleType,
    runtimeFingerprint,
    "Cannot select a ${bundleType.value} patcher runtime: $reason",
    cause
)
