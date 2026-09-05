package me.brosssh.bundles.plugins

import io.ktor.server.application.*
import kotlinx.coroutines.delay
import me.brosssh.bundles.Config
import me.brosssh.bundles.db.tables.*
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.sql.SQLException

private const val DATABASE_INIT_MAX_ATTEMPTS = 5
private const val DATABASE_INIT_INITIAL_DELAY_MS = 1_000L
private const val DATABASE_INIT_MAX_DELAY_MS = 8_000L
private val RETRYABLE_DATABASE_SQL_STATES = setOf(
    "57P01", // admin_shutdown
    "57P02", // crash_shutdown
    "57P03", // cannot_connect_now
    "53300", // too_many_connections
)

internal fun Throwable.isRetryableDatabaseAvailabilityFailure(): Boolean {
    var current: Throwable? = this
    while (current != null) {
        if (current is SQLException) {
            val sqlState = current.sqlState
            if (sqlState?.startsWith("08") == true || sqlState in RETRYABLE_DATABASE_SQL_STATES) return true
        }
        current = current.cause
    }
    return false
}

internal suspend fun retryDatabaseInitialization(
    maxAttempts: Int = DATABASE_INIT_MAX_ATTEMPTS,
    initialDelayMillis: Long = DATABASE_INIT_INITIAL_DELAY_MS,
    maxDelayMillis: Long = DATABASE_INIT_MAX_DELAY_MS,
    wait: suspend (Long) -> Unit = { delay(it) },
    onRetry: (attempt: Int, delayMillis: Long, error: Throwable) -> Unit = { _, _, _ -> },
    initialize: () -> Unit,
) {
    require(maxAttempts > 0) { "maxAttempts must be greater than zero" }
    require(initialDelayMillis >= 0) { "initialDelayMillis must not be negative" }
    require(maxDelayMillis >= 0) { "maxDelayMillis must not be negative" }

    var retryDelayMillis = initialDelayMillis.coerceAtMost(maxDelayMillis)
    repeat(maxAttempts) { attemptIndex ->
        try {
            initialize()
            return
        } catch (error: Exception) {
            val attempt = attemptIndex + 1
            if (attempt == maxAttempts || !error.isRetryableDatabaseAvailabilityFailure()) throw error

            onRetry(attempt, retryDelayMillis, error)
            wait(retryDelayMillis)
            retryDelayMillis = if (retryDelayMillis > maxDelayMillis / 2) {
                maxDelayMillis
            } else {
                retryDelayMillis * 2
            }
        }
    }
}

suspend fun Application.configureDatabase() {
    val db = Database.connect(
        Config.databaseJdbcUrl,
        driver = "org.postgresql.Driver",
        user = Config.databaseUser,
        password = Config.databasePassword
    )

    retryDatabaseInitialization(
        onRetry = { attempt, retryDelayMillis, error ->
            log.warn(
                "Database initialization failed on attempt $attempt/$DATABASE_INIT_MAX_ATTEMPTS; " +
                    "retrying in ${retryDelayMillis}ms",
                error,
            )
        }
    ) {
        transaction(db) {
            SchemaUtils.create(
                BundleTable,
                PackageTable,
                PatchTable,
                RefreshJobTable,
                SourceTable,
                SourceMetadataTable,
                PatchPackageTable,
                GitHostRateLimitTable,
            )
        }
    }
}
