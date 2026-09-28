package me.brosssh.bundles.repositories

import me.brosssh.bundles.db.entities.RefreshJobEntity
import me.brosssh.bundles.db.tables.RefreshJobTable
import me.brosssh.bundles.domain.models.RefreshJob
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.transactions.TransactionManager
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.util.UUID
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class RefreshJobRepositoryTest {
    private lateinit var database: Database

    @BeforeTest
    fun setUp() {
        database = Database.connect(
            url = "jdbc:h2:mem:${UUID.randomUUID()};MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
            driver = "org.h2.Driver"
        )
        TransactionManager.defaultDatabase = database
        transaction(database) {
            SchemaUtils.create(RefreshJobTable)
        }
    }

    @Test
    fun `startup recovery fails interrupted jobs and preserves completed jobs`() {
        val repository = RefreshJobRepository()
        val firstInterruptedJobId = "interrupted-patches"
        val secondInterruptedJobId = "interrupted-all"
        val completedJobId = "completed"

        repository.create(firstInterruptedJobId, RefreshJob.RefreshJobType.PATCHES)
        repository.create(secondInterruptedJobId, RefreshJob.RefreshJobType.ALL)
        val completedId = repository.create(
            completedJobId,
            RefreshJob.RefreshJobType.BUNDLES
        ).id.value
        transaction(database) {
            RefreshJobEntity[completedId].setCompleted()
        }

        assertEquals(
            2,
            repository.failStartedJobs("Application restarted before the refresh job completed")
        )
        val firstInterrupted = assertNotNull(repository.findByJobId(firstInterruptedJobId))
        val secondInterrupted = assertNotNull(repository.findByJobId(secondInterruptedJobId))
        val completed = assertNotNull(repository.findByJobId(completedJobId))
        assertEquals(RefreshJob.RefreshJobStatus.FAILED, firstInterrupted.status)
        assertEquals(RefreshJob.RefreshJobStatus.FAILED, secondInterrupted.status)
        assertEquals(RefreshJob.RefreshJobStatus.COMPLETED, completed.status)
        assertNotNull(firstInterrupted.completedAt)
        assertNotNull(secondInterrupted.completedAt)
    }
}
