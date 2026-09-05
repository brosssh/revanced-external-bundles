package me.brosssh.bundles.plugins

import kotlinx.coroutines.runBlocking
import java.sql.SQLException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class ConfigureDatabaseTest {
    @Test
    fun `database initialization retries connection failures with bounded backoff`() = runBlocking {
        var attempts = 0
        val delays = mutableListOf<Long>()
        val retries = mutableListOf<Int>()

        retryDatabaseInitialization(
            maxAttempts = 5,
            initialDelayMillis = 100,
            maxDelayMillis = 150,
            wait = { delays += it },
            onRetry = { attempt, _, _ -> retries += attempt },
        ) {
            attempts++
            if (attempts < 4) throw SQLException("connection unavailable", "08001")
        }

        assertEquals(4, attempts)
        assertEquals(listOf(100L, 150L, 150L), delays)
        assertEquals(listOf(1, 2, 3), retries)
    }

    @Test
    fun `database initialization caps the initial delay`() = runBlocking {
        val delays = mutableListOf<Long>()

        assertFailsWith<SQLException> {
            retryDatabaseInitialization(
                maxAttempts = 2,
                initialDelayMillis = 50,
                maxDelayMillis = 10,
                wait = { delays += it },
            ) {
                throw SQLException("connection unavailable", "08001")
            }
        }

        assertEquals(listOf(10L), delays)
    }

    @Test
    fun `database initialization backoff does not overflow`() = runBlocking {
        val delays = mutableListOf<Long>()

        assertFailsWith<SQLException> {
            retryDatabaseInitialization(
                maxAttempts = 3,
                initialDelayMillis = Long.MAX_VALUE / 2 + 1,
                maxDelayMillis = Long.MAX_VALUE,
                wait = { delays += it },
            ) {
                throw SQLException("connection unavailable", "08001")
            }
        }

        assertEquals(listOf(Long.MAX_VALUE / 2 + 1, Long.MAX_VALUE), delays)
    }

    @Test
    fun `wrapped SQL connection failures are recognized`() {
        val error = IllegalStateException(
            "wrapped",
            SQLException("connection lost", "08006"),
        )

        assertTrue(error.isRetryableDatabaseAvailabilityFailure())
    }

    @Test
    fun `transient postgres availability failures are recognized`() {
        assertTrue(SQLException("database is shutting down", "57P01").isRetryableDatabaseAvailabilityFailure())
        assertTrue(SQLException("database crashed", "57P02").isRetryableDatabaseAvailabilityFailure())
        assertTrue(SQLException("database is starting", "57P03").isRetryableDatabaseAvailabilityFailure())
        assertTrue(SQLException("connection limit reached", "53300").isRetryableDatabaseAvailabilityFailure())
    }

    @Test
    fun `database initialization does not retry non-connection failures`() = runBlocking {
        var attempts = 0
        val delays = mutableListOf<Long>()

        assertFailsWith<SQLException> {
            retryDatabaseInitialization(
                wait = { delays += it },
            ) {
                attempts++
                throw SQLException("constraint failure", "23505")
            }
        }

        assertEquals(1, attempts)
        assertTrue(delays.isEmpty())
    }

    @Test
    fun `database initialization stops after the configured attempt limit`() = runBlocking {
        var attempts = 0
        val delays = mutableListOf<Long>()

        assertFailsWith<SQLException> {
            retryDatabaseInitialization(
                maxAttempts = 3,
                initialDelayMillis = 10,
                maxDelayMillis = 20,
                wait = { delays += it },
            ) {
                attempts++
                throw SQLException("connection unavailable", "08001")
            }
        }

        assertEquals(3, attempts)
        assertEquals(listOf(10L, 20L), delays)
    }
}

