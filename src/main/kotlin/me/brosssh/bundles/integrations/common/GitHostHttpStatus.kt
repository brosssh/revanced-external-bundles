package me.brosssh.bundles.integrations.common

import io.ktor.http.HttpStatusCode
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

internal const val DEFAULT_RATE_LIMIT_RETRY_DELAY_SECONDS = 60L

internal val UnavailableForLegalReasonsStatus =
    HttpStatusCode(451, "Unavailable For Legal Reasons")

private val sourceUnavailableStatuses = mapOf(
    HttpStatusCode.NotFound.value to "Not Found",
    HttpStatusCode.Gone.value to "Gone",
    UnavailableForLegalReasonsStatus.value to "Unavailable For Legal Reasons"
)

internal fun sourceUnavailableReason(status: HttpStatusCode): String? =
    sourceUnavailableStatuses[status.value]?.let { reason -> "${status.value}: $reason" }

internal fun resolvedRateLimitDeadline(
    retryAfter: String?,
    reset: OffsetDateTime?,
    now: OffsetDateTime
): OffsetDateTime =
    (parseRetryAfter(retryAfter, now) ?: reset)
        ?.takeIf { it.isAfter(now) }
        ?: now.plusSeconds(DEFAULT_RATE_LIMIT_RETRY_DELAY_SECONDS)

internal fun parseEpochSeconds(value: String?): OffsetDateTime? =
    value?.trim()?.toLongOrNull()
        ?.let { runCatching { Instant.ofEpochSecond(it).atOffset(ZoneOffset.UTC) }.getOrNull() }

internal fun parseHttpDate(value: String?): OffsetDateTime? =
    value?.let {
        runCatching {
            ZonedDateTime.parse(it.trim(), DateTimeFormatter.RFC_1123_DATE_TIME).toOffsetDateTime()
        }.getOrNull()
    }

private fun parseRetryAfter(value: String?, now: OffsetDateTime): OffsetDateTime? {
    if (value.isNullOrBlank()) return null
    return value.trim().toLongOrNull()
        ?.takeIf { it >= 0 }
        ?.let(now::plusSeconds)
        ?: parseHttpDate(value)
}
