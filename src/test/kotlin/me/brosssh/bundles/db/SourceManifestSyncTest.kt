package me.brosssh.bundles.db

import me.brosssh.bundles.db.SourceManifestSync.Companion.ManifestEntry
import me.brosssh.bundles.integrations.HostResolver
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class SourceManifestSyncTest {
    private val resolver = HostResolver(factories = emptyMap())

    @Test
    fun `manifest entries default to enabled and support explicit disabling`() {
        val entries = SourceManifestSync.parseManifest(
            """
            [[sources]]
            url = "https://github.com/example/stable"

            [[sources]]
            url = "https://gitlab.com/example/paused"
            enabled = false
            """.trimIndent()
        )

        assertEquals(
            listOf(
                ManifestEntry("https://github.com/example/stable", enabled = true),
                ManifestEntry("https://gitlab.com/example/paused", enabled = false)
            ),
            entries
        )
    }

    @Test
    fun `validation rejects duplicate canonical sources`() {
        val exception = assertFailsWith<IllegalArgumentException> {
            SourceManifestSync.validate(
                listOf(
                    ManifestEntry("https://github.com/example/patches"),
                    ManifestEntry("https://github.com/example/patches")
                ),
                resolver
            )
        }

        assertTrue(exception.message.orEmpty().contains("entry 2 duplicates entry 1"))
    }

    @Test
    fun `validation rejects noncanonical source URLs`() {
        val exception = assertFailsWith<IllegalArgumentException> {
            SourceManifestSync.validate(
                listOf(ManifestEntry("https://github.com/example/patches/")),
                resolver
            )
        }

        assertTrue(exception.message.orEmpty().contains("is not canonical"))
    }

    @Test
    fun `validation errors do not expose URL credentials`() {
        val exception = assertFailsWith<IllegalArgumentException> {
            SourceManifestSync.validate(
                listOf(
                    ManifestEntry(
                        "https://alice:secret@github.com/example/patches?access_token=secret"
                    )
                ),
                resolver
            )
        }

        assertFalse(exception.message.orEmpty().contains("alice"))
        assertFalse(exception.message.orEmpty().contains("secret"))
        assertFalse(exception.message.orEmpty().contains("access_token"))
    }
}
