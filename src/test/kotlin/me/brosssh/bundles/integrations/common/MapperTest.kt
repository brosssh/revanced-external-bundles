package me.brosssh.bundles.integrations.common

import me.brosssh.bundles.domain.models.BundleImportError
import me.brosssh.bundles.domain.models.BundleType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class MapperTest {
    @Test
    fun `choosePatchBundle prefers explicit assets and returns null without a bundle`() {
        val jar = AssetInfo("legacy.jar", "https://example.com/legacy.jar", null)
        val rvp = AssetInfo("patches.rvp", "https://example.com/patches.rvp", null)

        assertEquals(rvp to BundleType.REVANCED_V4, listOf(jar, rvp).choosePatchBundle())
        assertEquals(jar to BundleType.REVANCED_V3, listOf(jar).choosePatchBundle())
        assertNull(listOf(AssetInfo("notes.txt", "https://example.com/notes.txt", null)).choosePatchBundle())
    }

    private fun release(vararg assets: AssetInfo) = ReleaseInfo(
        tagName = "v1.0",
        body = "Release notes",
        prerelease = false,
        createdAt = "2025-01-01T00:00:00Z",
        assets = assets.toList()
    )

    @Test
    fun `explicit rvp asset takes priority over an earlier jar`() {
        val metadata = release(
            AssetInfo("legacy.jar", "https://example.com/legacy.jar", "jar-digest"),
            AssetInfo("patches.rvp", "https://example.com/patches.rvp", "rvp-digest")
        ).toDomainModel(7)

        assertEquals(BundleType.REVANCED_V4, metadata.bundle.bundleType)
        assertEquals("https://example.com/patches.rvp", metadata.bundle.downloadUrl)
        assertEquals("rvp-digest", metadata.fileHash)
        assertEquals(7, metadata.bundle.sourceFk)
    }

    @Test
    fun `explicit mpp asset in URL takes priority over an earlier jar`() {
        val metadata = release(
            AssetInfo("legacy.jar", "https://example.com/legacy.jar", null),
            AssetInfo("Patches", "https://example.com/patches.MPP?download=1#file", "mpp-digest")
        ).toDomainModel(7)

        assertEquals(BundleType.MORPHE_V1, metadata.bundle.bundleType)
        assertEquals("https://example.com/patches.MPP?download=1#file", metadata.bundle.downloadUrl)
        assertEquals("mpp-digest", metadata.fileHash)
    }

    @Test
    fun `download URL bundle type overrides a conflicting human readable name`() {
        val metadata = release(
            AssetInfo("legacy.jar", "https://example.com/legacy.jar", null),
            AssetInfo("patches.rvp", "https://example.com/patches.mpp", null)
        ).toDomainModel(7)

        assertEquals(BundleType.MORPHE_V1, metadata.bundle.bundleType)
        assertEquals("https://example.com/patches.mpp", metadata.bundle.downloadUrl)
    }

    @Test
    fun `signature URL is not classified as a bundle from its human readable name`() {
        val metadata = release(
            AssetInfo("legacy.jar", "https://example.com/legacy.jar", null),
            AssetInfo("patches.mpp", "https://example.com/patches.mpp.asc", null)
        ).toDomainModel(7)

        assertEquals(BundleType.REVANCED_V3, metadata.bundle.bundleType)
        assertEquals("https://example.com/legacy.jar", metadata.bundle.downloadUrl)
    }

    @Test
    fun `non bundle URL extension blocks a bundle looking human readable name`() {
        val metadata = release(
            AssetInfo("legacy.jar", "https://example.com/legacy.jar", null),
            AssetInfo("patches.mpp", "https://example.com/release-notes.txt", null)
        ).toDomainModel(7)

        assertEquals(BundleType.REVANCED_V3, metadata.bundle.bundleType)
        assertEquals("https://example.com/legacy.jar", metadata.bundle.downloadUrl)
    }

    @Test
    fun `opaque download URL can still use the bundle name`() {
        val metadata = release(
            AssetInfo("legacy.jar", "https://example.com/legacy.jar", null),
            AssetInfo("patches.mpp", "https://example.com/download?id=42", null)
        ).toDomainModel(7)

        assertEquals(BundleType.MORPHE_V1, metadata.bundle.bundleType)
        assertEquals("https://example.com/download?id=42", metadata.bundle.downloadUrl)
    }

    @Test
    fun `first explicit asset wins when multiple explicit types are present`() {
        val metadata = release(
            AssetInfo("legacy.jar", "https://example.com/legacy.jar", null),
            AssetInfo("patches.mpp", "https://example.com/patches.mpp", null),
            AssetInfo("patches.rvp", "https://example.com/patches.rvp", null)
        ).toDomainModel(7)

        assertEquals(BundleType.MORPHE_V1, metadata.bundle.bundleType)
        assertEquals("https://example.com/patches.mpp", metadata.bundle.downloadUrl)
    }

    @Test
    fun `jar is used when no explicit bundle type is present`() {
        val metadata = release(
            AssetInfo("notes.txt", "https://example.com/notes.txt", null),
            AssetInfo("Patches", "https://example.com/patches.JAR?download=1", "jar-digest")
        ).toDomainModel(7)

        assertEquals(BundleType.REVANCED_V3, metadata.bundle.bundleType)
        assertEquals("https://example.com/patches.JAR?download=1", metadata.bundle.downloadUrl)
        assertEquals("jar-digest", metadata.fileHash)
    }

    @Test
    fun `release without a bundle asset is rejected`() {
        assertFailsWith<BundleImportError.ReleaseFileNotFoundError> {
            release(AssetInfo("signature.asc", "https://example.com/signature.asc", null))
                .toDomainModel(7)
        }
    }

    @Test
    fun `signature follows the selected explicit bundle asset`() {
        val metadata = release(
            AssetInfo("legacy.jar", "https://example.com/legacy.jar", null),
            AssetInfo("legacy.jar.asc", "https://example.com/legacy.jar.asc", null),
            AssetInfo("patches.rvp", "https://example.com/patches.rvp", null),
            AssetInfo("patches.rvp.asc", "https://example.com/patches.rvp.asc", null)
        ).toDomainModel(7)

        assertEquals("https://example.com/patches.rvp.asc", metadata.bundle.signatureDownloadUrl)
        assertNull(metadata.fileHash)
    }

    @Test
    fun `unmatched signature is not reused when multiple bundle candidates are present`() {
        val metadata = release(
            AssetInfo("legacy.jar", "https://example.com/legacy.jar", null),
            AssetInfo("legacy.jar.asc", "https://example.com/legacy.jar.asc", null),
            AssetInfo("patches.rvp", "https://example.com/patches.rvp", null)
        ).toDomainModel(7)

        assertNull(metadata.bundle.signatureDownloadUrl)
    }

    @Test
    fun `human readable link title does not override signature filename matching`() {
        val metadata = release(
            AssetInfo("legacy.jar", "https://example.com/legacy.jar", null),
            AssetInfo("Patches", "https://example.com/patches.mpp", null),
            AssetInfo("Patches.asc", "https://example.com/legacy.jar.asc", null),
            AssetInfo("Signature", "https://example.com/patches.mpp.asc", null)
        ).toDomainModel(7)

        assertEquals("https://example.com/patches.mpp.asc", metadata.bundle.signatureDownloadUrl)
    }

    @Test
    fun `bundle URL is not reused as a signature from its human readable name`() {
        val metadata = release(
            AssetInfo("Patches", "https://example.com/patches.mpp", null),
            AssetInfo("patches.mpp.asc", "https://example.com/helper.jar", null)
        ).toDomainModel(7)

        assertNull(metadata.bundle.signatureDownloadUrl)
    }

    @Test
    fun `signature URL follows selected bundle when filenames repeat in different paths`() {
        val metadata = release(
            AssetInfo("Stable", "https://example.com/stable/patches.mpp", null),
            AssetInfo("Nightly", "https://example.com/nightly/patches.mpp", null),
            AssetInfo("Nightly signature", "https://example.com/nightly/patches.mpp.asc", null),
            AssetInfo("Stable signature", "https://example.com/stable/patches.mpp.asc", null)
        ).toDomainModel(7)

        assertEquals("https://example.com/stable/patches.mpp.asc", metadata.bundle.signatureDownloadUrl)
    }

    @Test
    fun `ambiguous repeated filename does not borrow another bundle signature`() {
        val metadata = release(
            AssetInfo("Stable", "https://example.com/stable/patches.mpp", null),
            AssetInfo("Nightly", "https://example.com/nightly/patches.mpp", null),
            AssetInfo("Nightly signature", "https://example.com/nightly/patches.mpp.asc", null)
        ).toDomainModel(7)

        assertNull(metadata.bundle.signatureDownloadUrl)
    }

    @Test
    fun `signature URL query must match the selected bundle URL query`() {
        val metadata = release(
            AssetInfo("Stable", "https://example.com/patches.mpp?variant=1", null),
            AssetInfo("Nightly", "https://example.com/patches.mpp?variant=2", null),
            AssetInfo("Nightly signature", "https://example.com/patches.mpp.asc?variant=2", null),
            AssetInfo("Stable signature", "https://example.com/patches.mpp.asc?variant=1", null)
        ).toDomainModel(7)

        assertEquals(
            "https://example.com/patches.mpp.asc?variant=1",
            metadata.bundle.signatureDownloadUrl
        )
    }

    @Test
    fun `signature from another query variant is not borrowed when it is the only signature`() {
        val metadata = release(
            AssetInfo("Stable", "https://example.com/patches.mpp?variant=1", null),
            AssetInfo("Nightly", "https://example.com/patches.mpp?variant=2", null),
            AssetInfo("Nightly signature", "https://example.com/patches.mpp.asc?variant=2", null)
        ).toDomainModel(7)

        assertNull(metadata.bundle.signatureDownloadUrl)
    }

    @Test
    fun `signature path can match when bundle and signature query tokens differ`() {
        val metadata = release(
            AssetInfo("Stable", "https://example.com/stable/patches.mpp?token=bundleA", null),
            AssetInfo("Nightly", "https://example.com/nightly/patches.mpp?token=bundleB", null),
            AssetInfo("Nightly signature", "https://example.com/nightly/patches.mpp.asc?token=sigB", null),
            AssetInfo("Stable signature", "https://example.com/stable/patches.mpp.asc?token=sigA", null)
        ).toDomainModel(7)

        assertEquals(
            "https://example.com/stable/patches.mpp.asc?token=sigA",
            metadata.bundle.signatureDownloadUrl
        )
    }

    @Test
    fun `single bundle keeps a generic signature`() {
        val metadata = release(
            AssetInfo("patches.rvp", "https://example.com/patches.rvp", null),
            AssetInfo("signature.asc", "https://example.com/signature.asc", null)
        ).toDomainModel(7)

        assertEquals("https://example.com/signature.asc", metadata.bundle.signatureDownloadUrl)
    }
}
