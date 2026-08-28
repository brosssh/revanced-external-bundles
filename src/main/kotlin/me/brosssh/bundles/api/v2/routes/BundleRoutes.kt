package me.brosssh.bundles.api.v2.routes

import io.github.smiley4.ktoropenapi.get
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.route
import me.brosssh.bundles.api.v2.dto.BundleResponseDto
import me.brosssh.bundles.api.v2.dto.toResponseDto
import me.brosssh.bundles.domain.models.ReleaseChannel
import me.brosssh.bundles.domain.services.BundleQuery
import me.brosssh.bundles.domain.services.BundleService
import org.koin.ktor.ext.get

fun Route.bundleRoutes() {
    route("/bundle") {
        get("/{owner}/{repo}/latest", {
            description = "Get bundle by repository owner and name"
            tags = listOf("Bundle")

            request {
                pathParameter<String>("owner") {
                    description = "Repository owner"
                }
                pathParameter<String>("repo") {
                    description = "Repository name"
                }
                queryParameter<String>("channel") {
                    description = "Release channel: any, stable, prerelease"
                    required = true
                }
            }

            response {
                HttpStatusCode.OK to {
                    description = "Bundle found"
                    body<BundleResponseDto>()
                }
                HttpStatusCode.NotFound to {
                    description = "Bundle not found"
                }
            }
        }) {
            val bundleService = call.get<BundleService>()

            val owner = call.parameters["owner"]!!
            val repo = call.parameters["repo"]!!
            val releaseChannelString = call.request.queryParameters["channel"]!!

            val releaseType = releaseChannelString.uppercase().let {
                runCatching { ReleaseChannel.valueOf(it) }.getOrNull()
            } ?: return@get call.respond(
                HttpStatusCode.BadRequest,
                mapOf("error" to "'channel' is not valid, must be 'any', 'stable' or 'prerelease'")
            )

            val bundle = bundleService.getBundleByQuery(
                BundleQuery.ByRepositoryAndChannel(owner, repo, releaseType)
            )
                ?: return@get call.respond(
                    HttpStatusCode.NotFound,
                    mapOf("error" to "Bundle not found")
                )

            call.respond(HttpStatusCode.OK, bundle.toResponseDto())
        }
    }
}
