package me.brosssh.bundles.workers.loaders.morphe.v1

import me.brosssh.bundles.domain.models.Patch
import me.brosssh.bundles.workers.loaders.PatchLoader
import me.brosssh.bundles.workers.loaders.common.PatchKtBundleLoader
import java.io.File

internal object MorpheV1PatchLoader : PatchLoader {
    override fun load(bundleFile: File): Set<Patch> =
        PatchKtBundleLoader.load("app.morphe.patcher.patch.PatchKt", bundleFile)
}
