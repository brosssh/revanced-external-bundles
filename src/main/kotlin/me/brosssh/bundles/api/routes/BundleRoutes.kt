package me.brosssh.bundles.api.routes

import io.github.smiley4.ktoropenapi.get
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.route
import me.brosssh.bundles.domain.models.Bundle
import me.brosssh.bundles.api.dto.SearchResponseDto
import me.brosssh.bundles.domain.services.BundleService
import me.brosssh.bundles.domain.services.CacheService
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
                    body<Bundle>()
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

        get("search", {
            description = "Search bundle by query"

            request {
                queryParameter<String>("q") {
                    description = "Query string to search bundles"
                    required = true
                }
            }

            response {
                HttpStatusCode.OK to {
                    description = "Bundles matching the query"
                }
                HttpStatusCode.BadRequest to {
                    description = "Missing query parameter"
                }
                code(HttpStatusCode.OK) {
                    body<List<SearchResponseDto>>()
                }
            }
        }) {
            val cacheService = call.get<CacheService>()
            val bundleService = call.get<BundleService>()

            val query = call.request.queryParameters["q"]
                ?: return@get call.respond(
                    HttpStatusCode.BadRequest,
                    mapOf("error" to "Query parameter 'q' is required")
                )

            val results = cacheService.getCachedSearch(query) {
                bundleService.search(query)
            }
            call.respond(HttpStatusCode.OK, results)
        }
    }
}
