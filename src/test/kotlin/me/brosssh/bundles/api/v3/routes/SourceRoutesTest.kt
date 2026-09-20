package me.brosssh.bundles.api.v3.routes

import io.ktor.client.request.delete
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import me.brosssh.bundles.domain.models.SourceDeletionResult
import me.brosssh.bundles.plugins.configureSerialization
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SourceRoutesTest {
    @Test
    fun `missing or invalid admin secret is rejected before deletion`() = testApplication {
        var invoked = false
        application {
            configureSerialization()
            routing {
                route("/api/v3") {
                    sourceRoutes(ADMIN_SECRET) {
                        invoked = true
                        SourceDeletionResult.NotFound
                    }
                }
            }
        }

        val missing = client.delete("/api/v3/source") {
            parameter("source_url", SOURCE_URL)
        }
        val invalid = client.delete("/api/v3/source") {
            parameter("source_url", SOURCE_URL)
            header(HASURA_ADMIN_SECRET_HEADER, "invalid")
        }

        assertEquals(HttpStatusCode.Unauthorized, missing.status)
        assertEquals(HttpStatusCode.Unauthorized, invalid.status)
        assertFalse(invoked)
    }

    @Test
    fun `valid admin secret deletes the canonical source and reports counts`() = testApplication {
        var deletedSourceUrl: String? = null
        application {
            configureSerialization()
            routing {
                route("/api/v3") {
                    sourceRoutes(ADMIN_SECRET) { sourceUrl ->
                        deletedSourceUrl = sourceUrl
                        SourceDeletionResult.Deleted(sources = 1, bundles = 3, patches = 7)
                    }
                }
            }
        }

        val response = client.delete("/api/v3/source") {
            parameter("source_url", "$SOURCE_URL/")
            header(HASURA_ADMIN_SECRET_HEADER, ADMIN_SECRET)
        }

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals(SOURCE_URL, deletedSourceUrl)
        assertEquals(
            """{"deleted_sources":1,"deleted_bundles":3,"deleted_patches":7}""",
            response.bodyAsText()
        )
    }

    @Test
    fun `enabled source is rejected`() = testApplication {
        var invoked = false
        application {
            configureSerialization()
            routing {
                route("/api/v3") {
                    sourceRoutes(ADMIN_SECRET) {
                        invoked = true
                        SourceDeletionResult.Enabled
                    }
                }
            }
        }

        val response = client.delete("/api/v3/source") {
            parameter("source_url", SOURCE_URL)
            header(HASURA_ADMIN_SECRET_HEADER, ADMIN_SECRET)
        }

        assertEquals(HttpStatusCode.Conflict, response.status)
        assertTrue(invoked)
    }

    @Test
    fun `missing source URL is rejected before deletion`() = testApplication {
        var invoked = false
        application {
            configureSerialization()
            routing {
                route("/api/v3") {
                    sourceRoutes(ADMIN_SECRET) {
                        invoked = true
                        SourceDeletionResult.NotFound
                    }
                }
            }
        }

        val response = client.delete("/api/v3/source") {
            header(HASURA_ADMIN_SECRET_HEADER, ADMIN_SECRET)
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
        assertFalse(invoked)
    }

    private companion object {
        const val ADMIN_SECRET = "hasura-admin-secret"
        const val SOURCE_URL = "https://github.com/example/patches"
    }
}
