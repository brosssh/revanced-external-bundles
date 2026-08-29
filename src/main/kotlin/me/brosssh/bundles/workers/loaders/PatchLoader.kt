package me.brosssh.bundles.workers.loaders

import me.brosssh.bundles.domain.models.Patch
import java.io.File

internal fun interface PatchLoader {
    fun load(bundleFile: File): Set<Patch>
}
