package me.brosssh.bundles.api.routes

import io.github.smiley4.ktoropenapi.get
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.route
import me.brosssh.bundles.api.dto.BundleResponseDto
import me.brosssh.bundles.domain.services.BundleService
import org.koin.ktor.ext.get

fun Route.bundleRoutes() {

    route("/bundles") {
        get("id", {
            description = "Get bundle by ID"

            request {
                queryParameter<Int>("id") {
                    description = "Internal ID of the bundle"
                    required = true
                }
            }

            response {
                HttpStatusCode.OK to {
                    description = "Bundle found"
                }
                HttpStatusCode.BadRequest to {
                    description = "Missing id query parameter"
                }
                HttpStatusCode.NotFound to {
                    description = "Bundle not found"
                }
                code(HttpStatusCode.OK) {
                    body<BundleResponseDto>()
                }
            }
        }) {
            val bundleService = call.get<BundleService>()

            val id = call.request.queryParameters["id"]?.toIntOrNull()
                ?: return@get call.respond(
                    HttpStatusCode.BadRequest,
                    mapOf("error" to "id query parameter is required")
                )

            val bundle = bundleService.getById(id)
                ?: return@get call.respond(
                    HttpStatusCode.NotFound,
                    mapOf("error" to "Bundle not found")
                )

            call.respond(HttpStatusCode.OK, bundle)
        }
    }
}
