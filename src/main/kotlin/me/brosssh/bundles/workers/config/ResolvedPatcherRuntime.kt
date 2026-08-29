package me.brosssh.bundles.workers.config

import me.brosssh.bundles.domain.models.BundleType
import org.semver4j.range.RangeList

data class ResolvedPatcherRuntime(
    val bundleType: BundleType,
    val adapter: String,
    val mavenCoordinate: MavenCoordinate,
    val acceptedBundleVersions: RangeList
) {
    val coordinate: String
        get() = mavenCoordinate.value

    val versionText: String
        get() = mavenCoordinate.version

    val directoryName: String
        get() = "${runtimeDirectorySegment(bundleType.value)}/${runtimeDirectorySegment(versionText)}"
}

internal fun runtimeDirectorySegment(value: String) =
    value.replace(Regex("[^A-Za-z0-9._-]"), "_")
