package me.brosssh.bundles.workers.loaders.revanced.v4

import me.brosssh.bundles.domain.models.Patch
import me.brosssh.bundles.workers.loaders.PatchLoader
import me.brosssh.bundles.workers.loaders.common.PatchKtBundleLoader
import me.brosssh.bundles.workers.loaders.common.invokeUnwrapped
import me.brosssh.bundles.workers.loaders.common.toObjectPatchSnapshots
import java.io.File
import java.net.URLClassLoader
import java.util.zip.ZipFile

internal object ReVancedV4PatchLoader : PatchLoader {
    private const val PATCH_FACADE = "app.revanced.patcher.patch.PatchKt"

    override fun load(bundleFile: File): Set<Patch> =
        load(Class.forName(PATCH_FACADE), bundleFile)

    internal fun load(facade: Class<*>, bundleFile: File): Set<Patch> {
        val usesLegacyFacade = facade.methods.any { method ->
            method.name == "loadPatchesFromJar" &&
                method.parameterTypes.contentEquals(arrayOf(Set::class.java))
        }
        return if (usesLegacyFacade) {
            PatchKtBundleLoader.load(facade, bundleFile)
        } else {
            loadV22(facade, bundleFile)
        }
    }

    // Patcher 22 (app.revanced:patcher-jvm) expects the caller to enumerate the patch class names
    // and provide a classloader over the bundle archive.
    private fun loadV22(facade: Class<*>, bundleFile: File): Set<Patch> {
        val classNames = ZipFile(bundleFile).use { zip ->
            zip.entries().asSequence()
                .filter { !it.isDirectory && it.name.endsWith(".class") }
                .map { it.name.removeSuffix(".class").replace('/', '.') }
                .toList()
                .also { names ->
                    require(names.isNotEmpty()) {
                        "${facade.name} (22 DSL): bundle contains no class entries"
                    }
                }
        }

        return URLClassLoader(
            arrayOf(bundleFile.toURI().toURL()),
            ReVancedV4PatchLoader::class.java.classLoader
        ).use { classLoader ->
            val patches = facade.getMethod("getPatches", List::class.java, ClassLoader::class.java)
                .invokeUnwrapped(null, classNames, classLoader) as? Iterable<*>
                ?: error("${facade.name} (22 DSL) did not return an Iterable")

            patches.toObjectPatchSnapshots()
        }
    }
}
