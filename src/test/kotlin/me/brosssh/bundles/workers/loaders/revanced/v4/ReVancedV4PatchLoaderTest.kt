package me.brosssh.bundles.workers.loaders.revanced.v4

import me.brosssh.bundles.domain.models.CompatiblePackage
import me.brosssh.bundles.domain.models.Patch
import java.io.File
import java.nio.file.Files
import java.util.jar.JarEntry
import java.util.jar.JarOutputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ReVancedV4PatchLoaderTest {
    @Test
    fun `loads patches through the legacy facade`() = withBundleJar { bundleFile ->
        val patches = ReVancedV4PatchLoader.load(FakeLegacyPatchFacade::class.java, bundleFile)

        assertEquals(setOf(expectedPatch), patches)
    }

    @Test
    fun `loads patches through the version 22 facade`() =
        withBundleJar(classEntry(BundleMarker::class.java)) { bundleFile ->
            val patches = ReVancedV4PatchLoader.load(FakeV22PatchFacade::class.java, bundleFile)

            assertEquals(setOf(expectedPatch), patches)
        }

    @Test
    fun `rejects the entire version 22 bundle when any class cannot load`() =
        withBundleJar(
            classEntry(BundleMarker::class.java),
            "missing/Broken.class" to byteArrayOf(0)
        ) { bundleFile ->
            assertFailsWith<ClassFormatError> {
                ReVancedV4PatchLoader.load(FakeV22PatchFacade::class.java, bundleFile)
            }
        }

    private fun withBundleJar(
        vararg entries: Pair<String, ByteArray>,
        block: (File) -> Unit
    ) {
        val bundleFile = Files.createTempFile("revanced-v4-loader-test-", ".jar").toFile()
        try {
            JarOutputStream(bundleFile.outputStream()).use { jar ->
                entries.forEach { (name, bytes) ->
                    jar.putNextEntry(JarEntry(name))
                    jar.write(bytes)
                    jar.closeEntry()
                }
            }
            block(bundleFile)
        } finally {
            bundleFile.delete()
        }
    }

    private fun classEntry(type: Class<*>): Pair<String, ByteArray> {
        val name = type.name.replace('.', '/') + ".class"
        val bytes = checkNotNull(type.classLoader.getResourceAsStream(name)) {
            "Missing test class resource $name"
        }.use { it.readBytes() }
        return name to bytes
    }

    private companion object {
        val expectedPatch = Patch(
            name = "Example patch",
            description = "Example description",
            compatiblePackages = setOf(CompatiblePackage("com.example", setOf("1.0")))
        )
    }
}

object FakeLegacyPatchFacade {
    @JvmStatic
    @Suppress("UNUSED_PARAMETER")
    fun loadPatchesFromJar(bundleFiles: Set<File>): Set<FakeV4Patch> = setOf(FakeV4Patch())
}

object FakeV22PatchFacade {
    @JvmStatic
    @Suppress("unused") // Invoked reflectively by the loader.
    fun getPatches(classNames: List<String>, classLoader: ClassLoader): Set<FakeV4Patch> {
        classNames.forEach(classLoader::loadClass)
        return setOf(FakeV4Patch())
    }
}

class BundleMarker

data class FakeV4Patch(
    val name: String? = "Example patch",
    val description: String? = "Example description",
    val compatiblePackages: Set<Pair<String, Set<String>?>>? = setOf("com.example" to setOf("1.0"))
)
