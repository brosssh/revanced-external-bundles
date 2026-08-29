package me.brosssh.bundles.workers

import me.brosssh.bundles.domain.models.BundleType

data class PatcherRuntimeRejection(
    val runtimeCoordinate: String,
    val reason: String
)

class PatcherRuntimeExhaustedException internal constructor(
    val bundleType: BundleType,
    val runtimeFingerprint: String,
    val rejections: List<PatcherRuntimeRejection>
) : RuntimeException(
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
