package me.brosssh.bundles.workers.loaders.common

import me.brosssh.bundles.domain.models.Patch
import java.io.File

internal object PatchKtBundleLoader {
    fun load(patchClassName: String, bundleFile: File): Set<Patch> =
        load(Class.forName(patchClassName), bundleFile)

    fun load(facade: Class<*>, bundleFile: File): Set<Patch> {
        val patches = facade.getMethod("loadPatchesFromJar", Set::class.java)
            .invokeUnwrapped(null, setOf(bundleFile)) as? Iterable<*>
            ?: error("Patcher loader ${facade.name} did not return an Iterable")

        return patches.toObjectPatchSnapshots()
    }
}
