package me.brosssh.bundles.api.v3.routes

import io.github.smiley4.ktoropenapi.get
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.route
import me.brosssh.bundles.api.v3.dto.BundleResponseDto
import me.brosssh.bundles.api.v3.dto.toResponseDto
import me.brosssh.bundles.domain.models.ReleaseChannel
import me.brosssh.bundles.domain.services.BundleQuery
import me.brosssh.bundles.domain.services.BundleService
import me.brosssh.bundles.integrations.common.parseRepoUrl
import org.koin.ktor.ext.get

internal sealed interface BundleVersionSelector {
    val channel: ReleaseChannel

    data class Latest(override val channel: ReleaseChannel) : BundleVersionSelector

    data class Exact(
        val version: String,
        override val channel: ReleaseChannel
    ) : BundleVersionSelector
}

internal fun parseBundleVersionSelector(
    version: String?,
    channel: String?
): BundleVersionSelector {
    val normalizedVersion = version?.trim().orEmpty()
    require(normalizedVersion.isNotEmpty()) { "Missing 'version' query parameter." }

    val normalizedChannel = channel?.trim().orEmpty()
    val releaseChannel = if (normalizedChannel.isEmpty()) {
        null
    } else {
        runCatching { ReleaseChannel.valueOf(normalizedChannel.uppercase()) }
            .getOrNull()
            ?: throw IllegalArgumentException(
                "Invalid 'channel' query parameter; expected 'any', 'stable', or 'prerelease'."
            )
    }

    return if (normalizedVersion.equals("latest", ignoreCase = true)) {
        BundleVersionSelector.Latest(
            releaseChannel
                ?: throw IllegalArgumentException(
                    "Missing 'channel' query parameter when version is 'latest'."
                )
        )
    } else {
        BundleVersionSelector.Exact(normalizedVersion, releaseChannel ?: ReleaseChannel.ANY)
    }
}

fun Route.bundleRoutes() {
    route("/bundle") {
        get("", {
            description = "Get a cached bundle for a registered source URL and version. " +
                "Use version=latest with a channel to select the latest cached release. " +
                "This endpoint never fetches from the upstream git host."
            tags = listOf("Bundle v3")

            request {
                queryParameter<String>("source_url") {
                    description = "Registered repository source URL"
                    required = true
                }
                queryParameter<String>("version") {
                    description = "Exact cached bundle version, or 'latest'"
                    required = true
                }
                queryParameter<String>("channel") {
                    description = "Release channel: any, stable, or prerelease; required for version=latest"
                    required = false
                }
            }

            response {
                HttpStatusCode.OK to {
                    description = "Cached bundle found"
                    body<BundleResponseDto>()
                }
                HttpStatusCode.BadRequest to {
                    description = "Invalid or missing query parameter"
                }
                HttpStatusCode.NotFound to {
                    description = "Source or cached bundle not found"
                }
            }
        }) {
            val sourceUrl = call.request.queryParameters["source_url"]
                ?: return@get call.respond(
                    HttpStatusCode.BadRequest,
                    mapOf("error" to "Missing 'source_url' query parameter.")
                )

            val source = try {
                parseRepoUrl(sourceUrl)
            } catch (error: IllegalArgumentException) {
                return@get call.respond(
                    HttpStatusCode.BadRequest,
                    mapOf("error" to (error.message ?: "Invalid 'source_url' query parameter."))
                )
            }

            val selector = try {
                parseBundleVersionSelector(
                    version = call.request.queryParameters["version"],
                    channel = call.request.queryParameters["channel"]
                )
            } catch (error: IllegalArgumentException) {
                return@get call.respond(
                    HttpStatusCode.BadRequest,
                    mapOf("error" to (error.message ?: "Invalid bundle selector."))
                )
            }

            val query = when (selector) {
                is BundleVersionSelector.Latest -> BundleQuery.BySourceAndChannel(
                    sourceUrl = source.canonicalUrl,
                    channel = selector.channel
                )
                is BundleVersionSelector.Exact -> BundleQuery.BySourceAndVersion(
                    sourceUrl = source.canonicalUrl,
                    version = selector.version,
                    channel = selector.channel
                )
            }
            val bundle = call.get<BundleService>().getBundleByQuery(query)
                ?: return@get call.respond(
                    HttpStatusCode.NotFound,
                    mapOf("error" to "No cached bundle matches the requested source and version.")
                )

            call.respond(HttpStatusCode.OK, bundle.toResponseDto(source))
        }
    }
}
