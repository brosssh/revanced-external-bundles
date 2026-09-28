package me.brosssh.bundles.db

import me.brosssh.bundles.db.SourceManifestSync.Companion.ManifestEntry
import me.brosssh.bundles.db.entities.SourceEntity
import me.brosssh.bundles.db.tables.SourceTable
import me.brosssh.bundles.integrations.HostResolver
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.transactions.TransactionManager
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.util.UUID
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SourceManifestReconciliationTest {
    private val sync = SourceManifestSync(HostResolver(factories = emptyMap()))

    @BeforeTest
    fun setUp() {
        val database = Database.connect(
            "jdbc:h2:mem:${UUID.randomUUID()};MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
            driver = "org.h2.Driver"
        )
        TransactionManager.defaultDatabase = database
        transaction { SchemaUtils.create(SourceTable) }
    }

    @Test
    fun `database-only sources recover while explicit disabled sources stay hidden`() {
        val hushfeed = insert("https://github.com/SysAdminDoc/hushfeed", false)
        val newSource = insert("https://github.com/example/new", true)
        val retired = insert("https://github.com/example/retired", false)
        val entries = listOf(ManifestEntry("https://github.com/example/retired", false))

        val result = sync.sync(entries)

        assertEquals(1, result.reenabled)
        assertTrue(enabled(hushfeed))
        assertTrue(enabled(newSource))
        assertFalse(enabled(retired))
        assertEquals(SourceManifestSync.SyncResult(), sync.sync(entries))
    }

    @Test
    fun `database-only duplicates retain one enabled source`() {
        val first = insert("https://github.com/example/patches", false)
        val duplicate = insert("https://github.com/example/patches", true)

        sync.sync(emptyList())

        assertTrue(enabled(first))
        assertFalse(enabled(duplicate))
    }

    @Test
    fun `manifest can insert disable and reenable a source`() {
        val url = "https://github.com/example/patches"
        assertEquals(1, sync.sync(listOf(ManifestEntry(url))).inserted)
        assertEquals(1, sync.sync(listOf(ManifestEntry(url, false))).disabled)
        assertEquals(1, sync.sync(listOf(ManifestEntry(url))).reenabled)
    }

    @Test
    fun `unsupported database URLs are not reactivated`() {
        val invalid = insert("https://github.com/example/patches/releases", false)
        val unknown = insert("https://unknown.test/example/patches", false)

        sync.sync(emptyList())

        assertFalse(enabled(invalid))
        assertFalse(enabled(unknown))
    }

    @Test
    fun `tracked manifest restores hushfeed and preserves retired sources`() {
        val entries = SourceManifestSync.loadManifest().associateBy { it.url }
        assertTrue(entries.getValue("https://github.com/SysAdminDoc/hushfeed").enabled)
        assertFalse(entries.getValue("https://github.com/crimera/revanced-integrations").enabled)
        assertFalse(entries.getValue("https://github.com/IMXEren/mix-patches").enabled)
    }

    private fun insert(sourceUrl: String, isEnabled: Boolean): Int = transaction {
        SourceEntity.new {
            url = sourceUrl
            enabled = isEnabled
        }.id.value
    }

    private fun enabled(id: Int): Boolean = transaction {
        SourceEntity.findById(id)!!.enabled
    }
}
