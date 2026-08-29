package me.brosssh.bundles.workers.loaders.common

import me.brosssh.bundles.domain.models.CompatiblePackage
import me.brosssh.bundles.domain.models.Patch

internal fun Iterable<*>.toObjectPatchSnapshots(): Set<Patch> =
    mapNotNull { patch -> patch?.toObjectPatchSnapshot() }.toSet()

internal fun Any.toObjectPatchSnapshot(): Patch {
    val compatiblePackages = invokeNoArg("getCompatiblePackages")
        ?.let { packages ->
            require(packages is Iterable<*>) {
                "${javaClass.name}.compatiblePackages is not iterable"
            }
            packages.mapNotNull { it?.toCompatiblePackage() }.toSet()
        }

    return Patch(
        name = invokeNoArg("getName") as? String,
        description = invokeNoArg("getDescription") as? String,
        compatiblePackages = compatiblePackages
    )
}

private fun Any.toCompatiblePackage(): CompatiblePackage {
    // Older patchers expose compatibility as Pair<String, Set<String>?>; newer ones use a
    // CompatiblePackage object. Reflection keeps both binary-incompatible shapes isolated here.
    val pairGetter = javaClass.methods.firstOrNull {
        it.name == "getFirst" && it.parameterCount == 0
    }
    val (name, versionsValue) = if (pairGetter != null) {
        pairGetter.invokeUnwrapped(this) to invokeNoArg("getSecond")
    } else {
        invokeNoArg("getName") to invokeNoArg("getVersions")
    }

    require(name is String) {
        "${javaClass.name} returned an invalid compatible package name: $name"
    }
    val versions = versionsValue?.let {
        require(it is Iterable<*>) {
            "${javaClass.name} returned non-iterable compatible versions"
        }
        it.map { version ->
            require(version is String) {
                "${javaClass.name} returned a non-string compatible version"
            }
            version
        }.toSet()
    }
    return CompatiblePackage(name, versions)
}
