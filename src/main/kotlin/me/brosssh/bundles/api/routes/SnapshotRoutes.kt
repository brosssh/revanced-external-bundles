package me.brosssh.bundles.api.routes

import io.github.smiley4.ktoropenapi.get
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.route
import me.brosssh.bundles.api.dto.SnapshotResponseDto
import me.brosssh.bundles.domain.services.BundleService
import me.brosssh.bundles.domain.services.CacheService
import org.koin.ktor.ext.get

fun Route.snapshotRoutes() {

    route("/snapshot") {
        get({
            description = "Get a snapshot of the bundles and their patches"

            response {
                HttpStatusCode.OK
                code(HttpStatusCode.OK) {
                    body<List<SnapshotResponseDto>>()
                }
            }
        }) {
            val cacheService = call.get<CacheService>()
            val bundleService = call.get<BundleService>()

            val results = cacheService.getCachedSnapshot {
                bundleService.getSnapshot()
            }
            call.respond(HttpStatusCode.OK, results)
        }
    }
}
