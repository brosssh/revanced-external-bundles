package me.brosssh.bundles.domain.services.jobs

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.expectSuccess
import io.ktor.client.request.get
import io.ktor.http.Headers
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.runBlocking
import me.brosssh.bundles.db.tables.BundleTable
import me.brosssh.bundles.db.tables.GitHostRateLimitTable
import me.brosssh.bundles.db.tables.RefreshJobTable
import me.brosssh.bundles.db.tables.SourceMetadataTable
import me.brosssh.bundles.db.tables.SourceTable
import me.brosssh.bundles.integrations.GitHostType
import me.brosssh.bundles.integrations.HostResolver
import me.brosssh.bundles.integrations.common.GitHostClient
import me.brosssh.bundles.integrations.common.GitHostClientFactory
import me.brosssh.bundles.integrations.common.GitHostCredentials
import me.brosssh.bundles.integrations.common.ReleaseInfo
import me.brosssh.bundles.integrations.common.RepoInfo
import me.brosssh.bundles.integrations.common.RepoRef
import me.brosssh.bundles.integrations.common.UnavailableForLegalReasonsStatus
import me.brosssh.bundles.integrations.github.GithubClient
import me.brosssh.bundles.repositories.BundleRepository
import me.brosssh.bundles.repositories.GitHostRateLimitRepository
import me.brosssh.bundles.repositories.RefreshJobRepository
import me.brosssh.bundles.repositories.SourceMetadataRepository
import me.brosssh.bundles.repositories.SourceRepository
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.TransactionManager
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class RefreshBundlesJobServiceTest {
    private lateinit var database: Database
    private lateinit var rateLimitRepository: GitHostRateLimitRepository

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
                RefreshJobTable,
                GitHostRateLimitTable
            )
        }
        rateLimitRepository = GitHostRateLimitRepository()
    }

    @Test
    fun `unavailable source is rechecked and cleared after a complete refresh`() = runBlocking {
        insertSource("https://github.com/example/patches")
        val client = MutableGitHostClient(UnavailableForLegalReasonsStatus)
        val service = service(client)

        service.refresh().job.join()

        assertEquals(
            "451: Unavailable For Legal Reasons",
            unavailableReason("https://github.com/example/patches")
        )
        assertEquals(1, client.repoRequests)
        assertEquals(0, client.releaseRequests)

        client.failureStatus = null
        service.refresh().job.join()

        assertNull(unavailableReason("https://github.com/example/patches"))
        assertEquals(2, client.repoRequests)
        assertEquals(1, client.releaseRequests)
    }

    @Test
    fun `source remains unavailable until repository and releases both succeed`() = runBlocking {
        val sourceUrl = "https://github.com/example/patches"
        insertSource(sourceUrl, "404: Not Found")
        val client = MutableGitHostClient(
            failureStatus = HttpStatusCode.InternalServerError,
            failReleases = true
        )
        val service = service(client)

        service.refresh().job.join()

        assertEquals("404: Not Found", unavailableReason(sourceUrl))
        assertEquals(0, sourceMetadataCount())

        client.failureStatus = null
        service.refresh().job.join()

        assertNull(unavailableReason(sourceUrl))
        assertEquals(1, sourceMetadataCount())
    }

    @Test
    fun `rate limit suppresses later sources sharing the host and credential`() = runBlocking {
        insertSource("https://github.com/example/one")
        insertSource("https://github.com/example/two")
        val client = MutableGitHostClient(
            failureStatus = HttpStatusCode.TooManyRequests,
            failureHeaders = headersOf("Retry-After", "120")
        )
        val credentials = GitHostCredentials.fromEnv("github.com=secret-token")
        val service = service(client, credentials)

        service.refresh().job.join()

        assertEquals(1, client.repoRequests)
        assertNull(unavailableReason("https://github.com/example/one"))
        assertNull(unavailableReason("https://github.com/example/two"))
        assertNotNull(
            rateLimitRepository.activeUntil(
                "github.com",
                credentials.fingerprintFor("github.com"),
                OffsetDateTime.now(ZoneOffset.UTC)
            )
        )

        client.failureStatus = null
        service.refresh().job.join()

        assertEquals(1, client.repoRequests)
    }

    @Test
    fun `GitHub secondary rate-limit body suppresses later sources`() = runBlocking {
        insertSource("https://github.com/example/one")
        insertSource("https://github.com/example/two")
        val client = MutableGitHostClient(
            failureStatus = HttpStatusCode.Forbidden,
            failureBody = """{"message":"You have exceeded a secondary rate limit."}"""
        )
        val service = service(client)

        service.refresh().job.join()

        assertEquals(1, client.repoRequests)
        assertNotNull(
            rateLimitRepository.activeUntil(
                "github.com",
                GitHostCredentials.ANONYMOUS_FINGERPRINT,
                OffsetDateTime.now(ZoneOffset.UTC)
            )
        )
    }

    private fun service(
        client: GitHostClient,
        credentials: GitHostCredentials = GitHostCredentials.fromEnv("")
    ) = RefreshBundlesJobService(
        RefreshJobRepository(),
        HostResolver(
            factories = mapOf(
                GitHostType.GITHUB to GitHostClientFactory { _, _ -> client }
            )
        ),
        SourceRepository(),
        SourceMetadataRepository(),
        BundleRepository(),
        credentials,
        rateLimitRepository
    )

    private fun insertSource(url: String, unavailableReason: String? = null) = transaction(database) {
        SourceTable.insert {
            it[SourceTable.url] = url
            it[SourceTable.enabled] = true
            it[SourceTable.unavailableReason] = unavailableReason
        }
    }

    private fun sourceMetadataCount(): Long = transaction(database) {
        SourceMetadataTable.selectAll().count()
    }

    private fun unavailableReason(url: String): String? = transaction(database) {
        SourceTable
            .selectAll()
            .where { SourceTable.url eq url }
            .single()[SourceTable.unavailableReason]
    }

    private class MutableGitHostClient(
        var failureStatus: HttpStatusCode?,
        private val failureHeaders: Headers = Headers.Empty,
        private val failureBody: String = "{}",
        private val failReleases: Boolean = false
    ) : GitHostClient {
        var repoRequests = 0
        var releaseRequests = 0

        private val errorClient = HttpClient(MockEngine {
            respond(
                content = failureBody,
                status = requireNotNull(failureStatus),
                headers = failureHeaders
            )
        })

        override fun rateLimitDeadline(
            status: HttpStatusCode,
            headers: Headers,
            now: OffsetDateTime
        ): OffsetDateTime? = GithubClient(errorClient).rateLimitDeadline(status, headers, now)

        override fun rateLimitDeadline(
            status: HttpStatusCode,
            headers: Headers,
            now: OffsetDateTime,
            responseBody: String?
        ): OffsetDateTime? =
            GithubClient(errorClient).rateLimitDeadline(status, headers, now, responseBody)

        override suspend fun getRepo(ref: RepoRef): RepoInfo {
            repoRequests++
            if (failureStatus != null && !failReleases) failRequest(ref)
            return RepoInfo(
                ownerName = ref.namespace,
                ownerAvatarUrl = "https://example.com/avatar.png",
                repoName = ref.repo,
                repoDescription = null,
                repoStars = 1,
                isRepoArchived = false,
                repoPushedAt = "2026-08-31T12:00:00Z"
            )
        }

        override suspend fun getReleases(ref: RepoRef): List<ReleaseInfo> {
            releaseRequests++
            if (failureStatus != null && failReleases) failRequest(ref)
            return emptyList()
        }

        private suspend fun failRequest(ref: RepoRef): Nothing {
            errorClient.get("https://api.github.test/repos/${ref.namespace}/${ref.repo}") {
                expectSuccess = true
            }
            error("Expected a failed git-host response")
        }
    }
}
