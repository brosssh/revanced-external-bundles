package me.brosssh.bundles.integrations.common

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.Headers
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import me.brosssh.bundles.integrations.gitea.GiteaHostClient
import me.brosssh.bundles.integrations.github.GithubClient
import me.brosssh.bundles.integrations.gitlab.GitlabHostClient
import java.time.OffsetDateTime
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class GitHostHttpStatusTest {
    private val now = OffsetDateTime.of(2026, 8, 31, 12, 0, 0, 0, ZoneOffset.UTC)
    private val httpClient = HttpClient(MockEngine { respond("{}") })
    private val github = GithubClient(httpClient)
    private val gitlab = GitlabHostClient(httpClient, "https://gitlab.test")
    private val gitea = GiteaHostClient(httpClient, "https://gitea.test")

    @Test
    fun `only source-level unavailable statuses produce a reason`() {
        assertEquals("404: Not Found", sourceUnavailableReason(HttpStatusCode.NotFound))
        assertEquals("410: Gone", sourceUnavailableReason(HttpStatusCode.Gone))
        assertEquals(
            "451: Unavailable For Legal Reasons",
            sourceUnavailableReason(UnavailableForLegalReasonsStatus)
        )
        assertNull(sourceUnavailableReason(HttpStatusCode.Forbidden))
        assertNull(sourceUnavailableReason(HttpStatusCode.TooManyRequests))
    }

    @Test
    fun `429 honors Retry-After`() {
        assertEquals(
            now.plusSeconds(90),
            github.rateLimitDeadline(
                HttpStatusCode.TooManyRequests,
                headersOf("Retry-After", "90"),
                now
            )
        )
    }

    @Test
    fun `GitLab RateLimit-Reset is interpreted as an epoch timestamp`() {
        val reset = now.plusMinutes(10)
        assertEquals(
            reset,
            gitlab.rateLimitDeadline(
                HttpStatusCode.TooManyRequests,
                headersOf("RateLimit-Reset", reset.toEpochSecond().toString()),
                now
            )
        )
    }

    @Test
    fun `GitHub rate-limit 403 honors its reset epoch`() {
        val reset = now.plusMinutes(15)
        assertEquals(
            reset,
            github.rateLimitDeadline(
                HttpStatusCode.Forbidden,
                headersOf(
                    "X-RateLimit-Remaining" to listOf("0"),
                    "X-RateLimit-Reset" to listOf(reset.toEpochSecond().toString())
                ),
                now
            )
        )
    }

    @Test
    fun `GitHub secondary-limit 403 honors Retry-After`() {
        assertEquals(
            now.plusSeconds(30),
            github.rateLimitDeadline(
                HttpStatusCode.Forbidden,
                headersOf("Retry-After", "30"),
                now
            )
        )
    }

    @Test
    fun `GitHub secondary-limit 403 without headers uses the fallback`() {
        assertEquals(
            now.plusSeconds(DEFAULT_RATE_LIMIT_RETRY_DELAY_SECONDS),
            github.rateLimitDeadline(
                HttpStatusCode.Forbidden,
                Headers.Empty,
                now,
                """{"message":"You have exceeded a secondary rate limit."}"""
            )
        )
    }

    @Test
    fun `GitLab remaining header can prove a rate-limit 403`() {
        assertEquals(
            now.plusSeconds(DEFAULT_RATE_LIMIT_RETRY_DELAY_SECONDS),
            gitlab.rateLimitDeadline(
                HttpStatusCode.Forbidden,
                headersOf("RateLimit-Remaining", "0"),
                now
            )
        )
    }

    @Test
    fun `ordinary 403 is not treated as rate limiting`() {
        listOf(github, gitlab, gitea).forEach { client ->
            assertNull(client.rateLimitDeadline(HttpStatusCode.Forbidden, Headers.Empty, now))
        }
        assertNull(
            github.rateLimitDeadline(
                HttpStatusCode.Forbidden,
                Headers.Empty,
                now,
                """{"message":"Resource not accessible by integration"}"""
            )
        )
    }

    @Test
    fun `missing or expired reset headers use a short fallback`() {
        assertEquals(
            now.plusSeconds(DEFAULT_RATE_LIMIT_RETRY_DELAY_SECONDS),
            gitea.rateLimitDeadline(
                HttpStatusCode.TooManyRequests,
                Headers.Empty,
                now
            )
        )
        assertEquals(
            now.plusSeconds(DEFAULT_RATE_LIMIT_RETRY_DELAY_SECONDS),
            github.rateLimitDeadline(
                HttpStatusCode.TooManyRequests,
                headersOf("X-RateLimit-Reset", now.minusMinutes(1).toEpochSecond().toString()),
                now
            )
        )
    }
}
