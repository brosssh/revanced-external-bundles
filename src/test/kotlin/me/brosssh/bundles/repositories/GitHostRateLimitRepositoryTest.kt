package me.brosssh.bundles.repositories

import me.brosssh.bundles.db.tables.GitHostRateLimitTable
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.transactions.TransactionManager
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class GitHostRateLimitRepositoryTest {
    private lateinit var repository: GitHostRateLimitRepository

    @BeforeTest
    fun setUp() {
        val database = Database.connect(
            url = "jdbc:h2:mem:${UUID.randomUUID()};MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
            driver = "org.h2.Driver"
        )
        TransactionManager.defaultDatabase = database
        transaction(database) {
            SchemaUtils.create(GitHostRateLimitTable)
        }
        repository = GitHostRateLimitRepository()
    }

    @Test
    fun `active limit is keyed by normalized authority and credential fingerprint`() {
        val now = OffsetDateTime.of(2026, 8, 31, 12, 0, 0, 0, ZoneOffset.UTC)
        val deadline = now.plusMinutes(15)

        repository.record("GitHub.COM", "credential-a", deadline)

        assertEquals(deadline, repository.activeUntil("github.com", "credential-a", now))
        assertNull(repository.activeUntil("github.com", "credential-b", now))
        assertNull(repository.activeUntil("gitlab.com", "credential-a", now))
        assertNull(repository.activeUntil("github.com", "credential-a", deadline))
    }

    @Test
    fun `shorter observation cannot replace a later deadline`() {
        val now = OffsetDateTime.of(2026, 8, 31, 12, 0, 0, 0, ZoneOffset.UTC)
        repository.record("github.com", "credential", now.plusMinutes(30))
        repository.record("github.com", "credential", now.plusMinutes(5))

        assertEquals(
            now.plusMinutes(30),
            repository.activeUntil("github.com", "credential", now)
        )
    }

    @Test
    fun `successful request clears the stored limit`() {
        val now = OffsetDateTime.of(2026, 8, 31, 12, 0, 0, 0, ZoneOffset.UTC)
        repository.record("github.com", "credential", now.plusMinutes(15))

        repository.clear("github.com", "credential")

        assertNull(repository.activeUntil("github.com", "credential", now))
    }
}
