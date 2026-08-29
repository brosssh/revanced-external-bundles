package me.brosssh.bundles.workers.loaders.revanced.v3

import me.brosssh.bundles.domain.models.CompatiblePackage
import me.brosssh.bundles.domain.models.Patch
import me.brosssh.bundles.workers.loaders.PatchLoader
import me.brosssh.bundles.workers.loaders.common.invokeNoArg
import me.brosssh.bundles.workers.loaders.common.invokeUnwrapped
import me.brosssh.bundles.workers.loaders.common.toObjectPatchSnapshot
import java.io.File

internal object ReVancedV3PatchLoader : PatchLoader {
    override fun load(bundleFile: File): Set<Patch> {
        val patches = loadPatches(bundleFile) as? Iterable<*>
            ?: error("ReVanced V3 patcher loader did not return an Iterable")

        return patches.mapNotNull { patch ->
            when (patch) {
                null -> null
                is Class<*> -> patch.annotationsToSnapshot()
                else -> patch.toObjectPatchSnapshot()
            }
        }.toSet()
    }

    /**
     * Supports the ReVanced V3 loader APIs from Patcher 6.x through 19.x:
     * - 14.x-19.x: `app.revanced.patcher.PatchBundleLoader.Jar`
     * - 6.x-13.x: `app.revanced.patcher.util.patch.PatchBundle.Jar`
     *
     * Patcher 5.x and older are intentionally unsupported; Patcher 20+ uses the V4 adapter.
     */
    private fun loadPatches(bundleFile: File): Any? {
        val directLoader = runCatching {
            Class.forName("app.revanced.patcher.PatchBundleLoader\$Jar")
        }.getOrNull()
        if (directLoader != null) {
            return directLoader
                .getConstructor(Array<File>::class.java)
                .newInstance(arrayOf(bundleFile))
        }

        // Patcher 6.x-13.x requires explicitly loading annotation-bearing patch classes.
        return Class.forName("app.revanced.patcher.util.patch.PatchBundle\$Jar")
            .getConstructor(String::class.java)
            .newInstance(bundleFile.absolutePath)
            .invokeNoArg("loadPatches")
    }

    private fun Class<*>.annotationsToSnapshot(): Patch {
        val compatibility = findAnnotationRecursively("app.revanced.patcher.annotation.Compatibility")
        val compatiblePackages = compatibility
            ?.annotationValue("compatiblePackages")
            ?.let { it as? Array<*> }
            ?.mapNotNull { it as? Annotation }
            ?.map { it.toAnnotatedCompatiblePackage() }
            ?.toSet()

        return Patch(
            name = findAnnotationRecursively("app.revanced.patcher.annotation.Name")
                ?.annotationValue("name") as? String,
            description = findAnnotationRecursively("app.revanced.patcher.annotation.Description")
                ?.annotationValue("description") as? String,
            compatiblePackages = compatiblePackages
        )
    }

    private fun Class<*>.findAnnotationRecursively(typeName: String): Annotation? {
        val pending = ArrayDeque<Annotation>().apply { addAll(annotations) }
        val visited = mutableSetOf<Class<out Annotation>>()
        while (pending.isNotEmpty()) {
            val annotation = pending.removeFirst()
            val annotationType = annotation.annotationClass.java
            if (!visited.add(annotationType)) continue
            if (annotationType.name == typeName) return annotation
            annotationType.annotations.forEach(pending::addLast)
        }
        return null
    }

    private fun Annotation.toAnnotatedCompatiblePackage(): CompatiblePackage {
        val name = annotationValue("name") as? String
            ?: error("${annotationClass.java.name} returned an invalid package name")
        val versions = (annotationValue("versions") as? Array<*>)
            ?.mapNotNull { it as? String }
            ?.toSet()
            ?.takeUnless { it.isEmpty() }
        return CompatiblePackage(name, versions)
    }

    private fun Annotation.annotationValue(name: String): Any? =
        annotationClass.java.getMethod(name).invokeUnwrapped(this)
}
