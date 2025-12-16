package me.brosssh.bundles.routes

import io.ktor.http.*
import io.ktor.server.auth.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import me.brosssh.bundles.services.RefreshService
import org.koin.ktor.ext.get


fun Route.refreshRoute() {

    route("/refresh") {
        authenticate("apiTokenAuth") {
            post {
                val refreshService = call.get<RefreshService>()
                val jobId = refreshService.refreshAsync()
                call.respond(HttpStatusCode.Created, mapOf("job_id" to jobId))
            }
        }
    }
}
