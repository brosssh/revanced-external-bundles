package me.brosssh.bundles.workers.loaders

import me.brosssh.bundles.domain.models.Patch
import me.brosssh.bundles.workers.loaders.morphe.v1.MorpheV1PatchLoader
import me.brosssh.bundles.workers.loaders.revanced.v3.ReVancedV3PatchLoader
import me.brosssh.bundles.workers.loaders.revanced.v4.ReVancedV4PatchLoader
import java.io.File

internal object PatchLoaderRegistry {
    private val loaders = mapOf(
        "morphe-v1" to MorpheV1PatchLoader,
        "revanced-v3" to ReVancedV3PatchLoader,
        "revanced-v4" to ReVancedV4PatchLoader
    )

    fun load(adapter: String, bundleFile: File): Set<Patch> =
        requireNotNull(loaders[adapter]) {
            "Unknown patch worker adapter '$adapter'"
        }.load(bundleFile)
}
