package me.brosssh.bundles.routes

import io.ktor.http.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.routing.get
import me.brosssh.bundles.services.BundleService
import org.koin.ktor.ext.get

fun Route.bundleRoutes() {

    route("/bundles") {

        get {
            val sourceUrl = call.request.queryParameters["sourceUrl"]
                ?: return@get call.respond(
                    HttpStatusCode.BadRequest,
                    mapOf("error" to "sourceUrl query parameter is required")
                )
            val bundleService = call.get<BundleService>()

            val bundle = bundleService.getBySourceUrl(sourceUrl) ?: return@get call.respond(
                HttpStatusCode.NotFound,
                mapOf("error" to "Bundle not found")
            )

            call.respond(bundle)
        }
    }
}
