package me.brosssh.bundles.api.v3.routes

import io.github.smiley4.ktoropenapi.delete
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.header
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.route
import me.brosssh.bundles.api.v3.dto.SourceDeletionResponseDto
import me.brosssh.bundles.api.v3.dto.toResponseDto
import me.brosssh.bundles.domain.models.SourceDeletionResult
import me.brosssh.bundles.integrations.common.parseRepoUrl
import java.nio.charset.StandardCharsets
import java.security.MessageDigest

internal const val HASURA_ADMIN_SECRET_HEADER = "X-Hasura-Admin-Secret"

internal fun matchesAdminSecret(provided: String?, expected: String): Boolean =
    provided != null &&
            expected.isNotEmpty() &&
            MessageDigest.isEqual(
                provided.toByteArray(StandardCharsets.UTF_8),
                expected.toByteArray(StandardCharsets.UTF_8)
            )

fun Route.sourceRoutes(
    hasuraAdminSecret: String,
    hardDeleteSource: (String) -> SourceDeletionResult
) {
    route("/source") {
        delete("", {
            summary = "Hard-delete a disabled source"
            description = "Deletes a disabled source and its cached bundles and patches. " +
                    "Requires the configured Hasura admin secret."
            tags = listOf("Source v3")

            request {
                queryParameter<String>("source_url") {
                    description = "Canonical URL of the disabled source"
                    required = true
                }
                headerParameter<String>(HASURA_ADMIN_SECRET_HEADER) {
                    description = "Hasura admin secret"
                    required = true
                }
            }

            response {
                HttpStatusCode.OK to {
                    description = "Source and cached data deleted"
                    body<SourceDeletionResponseDto>()
                }
                HttpStatusCode.BadRequest to {
                    description = "Invalid or missing source URL"
                    body<Map<String, String>>()
                }
                HttpStatusCode.Unauthorized to {
                    description = "Missing or invalid admin secret"
                    body<Map<String, String>>()
                }
                HttpStatusCode.NotFound to {
                    description = "Source not found"
                    body<Map<String, String>>()
                }
                HttpStatusCode.Conflict to {
                    description = "Source is still enabled"
                    body<Map<String, String>>()
                }
            }
        }) {
            if (!matchesAdminSecret(call.request.header(HASURA_ADMIN_SECRET_HEADER), hasuraAdminSecret)) {
                return@delete call.respond(
                    HttpStatusCode.Unauthorized,
                    mapOf("error" to "Missing or invalid admin secret.")
                )
            }

            val sourceUrl = call.request.queryParameters["source_url"]
                ?: return@delete call.respond(
                    HttpStatusCode.BadRequest,
                    mapOf("error" to "Missing 'source_url' query parameter.")
                )
            val source = try {
                parseRepoUrl(sourceUrl)
            } catch (error: IllegalArgumentException) {
                return@delete call.respond(
                    HttpStatusCode.BadRequest,
                    mapOf("error" to (error.message ?: "Invalid 'source_url' query parameter."))
                )
            }

            when (val result = hardDeleteSource(source.canonicalUrl)) {
                SourceDeletionResult.NotFound -> call.respond(
                    HttpStatusCode.NotFound,
                    mapOf("error" to "Source not found.")
                )

                SourceDeletionResult.Enabled -> call.respond(
                    HttpStatusCode.Conflict,
                    mapOf("error" to "Source must be disabled before hard deletion.")
                )

                is SourceDeletionResult.Deleted -> call.respond(
                    HttpStatusCode.OK,
                    result.toResponseDto()
                )
            }
        }
    }
}
