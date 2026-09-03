package me.brosssh.bundles.repositories

import me.brosssh.bundles.db.tables.BundleTable
import me.brosssh.bundles.db.tables.PackageTable
import me.brosssh.bundles.db.tables.PatchPackageTable
import me.brosssh.bundles.db.tables.PatchTable
import me.brosssh.bundles.db.tables.SourceMetadataTable
import me.brosssh.bundles.db.tables.SourceTable
import me.brosssh.bundles.domain.models.BundleType
import me.brosssh.bundles.domain.models.ReleaseChannel
import me.brosssh.bundles.domain.models.SourceDeletionResult
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.insertAndGetId
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.TransactionManager
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import java.util.UUID
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class DisabledSourceRepositoryTest {
    private lateinit var database: Database

    @BeforeTest
    fun setUp() {
        database = Database.connect(
            url = "jdbc:h2:mem:${UUID.randomUUID()};MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
            driver = "org.h2.Driver"
        )
        TransactionManager.defaultDatabase = database
        transaction(database) {
            SchemaUtils.create(
                SourceTable,
                SourceMetadataTable,
                BundleTable,
                PatchTable,
                PackageTable,
                PatchPackageTable
            )
        }
    }

    @Test
    fun `legacy lookups hide disabled bundles while explicit source lookups retain access`() {
        val fixture = insertSource(enabled = false)
        val repository = BundleRepository()

        assertNull(repository.findById(fixture.bundleId))
        assertNull(repository.findLatestByRepo(OWNER, REPO, prerelease = false))
        assertNull(repository.findByRepoAndVersion(OWNER, REPO, VERSION))
        assertNull(repository.findByRepoAndChannel(OWNER, REPO, ReleaseChannel.STABLE))
        assertTrue(repository.getBundlesNeedPatchesUpdate().isEmpty())

        assertNotNull(repository.findBySourceAndChannel(SOURCE_URL, ReleaseChannel.STABLE))
        assertNotNull(repository.findBySourceAndVersion(SOURCE_URL, VERSION, ReleaseChannel.STABLE))

        transaction(database) {
            SourceTable.update({ SourceTable.id eq fixture.sourceId }) {
                it[enabled] = true
            }
        }

        assertNotNull(repository.findById(fixture.bundleId))
        assertNotNull(repository.findLatestByRepo(OWNER, REPO, prerelease = false))
        assertNotNull(repository.findByRepoAndVersion(OWNER, REPO, VERSION))
        assertNotNull(repository.findByRepoAndChannel(OWNER, REPO, ReleaseChannel.STABLE))
        assertEquals(1, repository.getBundlesNeedPatchesUpdate().size)
    }

    @Test
    fun `unavailable sources remain served but are filtered from patch extraction`() {
        val fixture = insertSource(enabled = true)
        val repository = BundleRepository()
        val sourceRepository = SourceRepository()

        sourceRepository.setUnavailableReason(fixture.sourceId, "451: Unavailable For Legal Reasons")

        assertNotNull(repository.findById(fixture.bundleId))
        assertNotNull(repository.findBySourceAndVersion(SOURCE_URL, VERSION, ReleaseChannel.STABLE))
        assertTrue(repository.getBundlesNeedPatchesUpdate().isEmpty())

        sourceRepository.setUnavailableReason(fixture.sourceId, null)

        assertEquals(1, repository.getBundlesNeedPatchesUpdate().size)
    }

    @Test
    fun `unavailable sources are excluded from runtime failure requeue`() {
        val fixture = insertSource(enabled = true)
        val repository = BundleRepository()
        val sourceRepository = SourceRepository()
        transaction(database) {
            BundleTable.update({ BundleTable.id eq fixture.bundleId }) {
                it[needPatchesUpdate] = false
                it[patcherFailure] = "terminal"
                it[patcherFailureFingerprint] = "old"
            }
        }
        sourceRepository.setUnavailableReason(fixture.sourceId, "404: Not Found")

        assertEquals(0, repository.requeuePatcherRuntimeFailures(BundleType.REVANCED_V4, "new"))
        transaction(database) {
            assertFalse(BundleTable.selectAll().single()[BundleTable.needPatchesUpdate])
        }

        sourceRepository.setUnavailableReason(fixture.sourceId, null)

        assertEquals(1, repository.requeuePatcherRuntimeFailures(BundleType.REVANCED_V4, "new"))
        transaction(database) {
            assertTrue(BundleTable.selectAll().single()[BundleTable.needPatchesUpdate])
        }
    }

    @Test
    fun `hard deletion removes a disabled source and its owned rows`() {
        insertSource(enabled = false, patchCount = 2)

        val result = assertIs<SourceDeletionResult.Deleted>(
            SourceRepository().hardDelete(SOURCE_URL)
        )

        assertEquals(1, result.sources)
        assertEquals(1, result.bundles)
        assertEquals(2, result.patches)
        transaction(database) {
            assertEquals(0, SourceTable.selectAll().count())
            assertEquals(0, SourceMetadataTable.selectAll().count())
            assertEquals(0, BundleTable.selectAll().count())
            assertEquals(0, PatchTable.selectAll().count())
            assertEquals(0, PatchPackageTable.selectAll().count())
            assertEquals(1, PackageTable.selectAll().count())
        }
    }

    @Test
    fun `hard deletion rejects an enabled source without changing it`() {
        insertSource(enabled = true)

        assertSame(SourceDeletionResult.Enabled, SourceRepository().hardDelete(SOURCE_URL))

        transaction(database) {
            assertEquals(1, SourceTable.selectAll().count())
            assertEquals(1, SourceMetadataTable.selectAll().count())
            assertEquals(1, BundleTable.selectAll().count())
        }
    }

    @Test
    fun `hard deletion reports an unknown source`() {
        assertSame(SourceDeletionResult.NotFound, SourceRepository().hardDelete(SOURCE_URL))
    }

    private fun insertSource(enabled: Boolean, patchCount: Int = 1): Fixture = transaction(database) {
        val sourceId = SourceTable.insertAndGetId {
            it[url] = SOURCE_URL
            it[SourceTable.enabled] = enabled
        }
        SourceMetadataTable.insert {
            it[sourceFk] = sourceId
            it[ownerName] = OWNER
            it[ownerAvatarUrl] = "https://example.com/avatar.png"
            it[repoName] = REPO
            it[repoDescription] = null
            it[repoStars] = 1
            it[isRepoArchived] = false
            it[repoPushedAt] = "2026-03-25T00:00:00Z"
        }
        val bundleId = BundleTable.insertAndGetId {
            it[version] = VERSION
            it[createdAt] = "2026-03-25T00:00:00Z"
            it[description] = null
            it[downloadUrl] = "https://example.com/bundle.rvp"
            it[signatureDownloadUrl] = null
            it[isPrerelease] = false
            it[isLatest] = true
            it[fileHash] = null
            it[needPatchesUpdate] = true
            it[bundleType] = BundleType.REVANCED_V4.value
            it[sourceFk] = sourceId
        }
        val packageId = PackageTable.insertAndGetId {
            it[name] = "com.example.app"
            it[version] = null
        }
        repeat(patchCount) { index ->
            val patchId = PatchTable.insertAndGetId {
                it[bundleFk] = bundleId
                it[name] = "Patch $index"
                it[description] = null
            }
            PatchPackageTable.insert {
                it[patchFk] = patchId
                it[packageFk] = packageId
            }
        }

        Fixture(sourceId.value, bundleId.value)
    }

    private data class Fixture(
        val sourceId: Int,
        val bundleId: Int
    )

    private companion object {
        const val SOURCE_URL = "https://github.com/example/patches"
        const val OWNER = "example"
        const val REPO = "patches"
        const val VERSION = "v1.0.0"
    }
}
