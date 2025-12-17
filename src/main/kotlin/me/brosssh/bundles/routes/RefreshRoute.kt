package me.brosssh.bundles.routes

import io.ktor.http.*
import io.ktor.server.auth.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import me.brosssh.bundles.Config
import me.brosssh.bundles.services.RefreshService
import org.koin.ktor.ext.get


fun Route.refreshRoute() {

    route("/refresh") {
        authenticate("hmacAuth") {
            post {
                val githubToken =
                    call.request.headers["X-Github-Token"]
                        ?: Config.githubToken

                val refreshService = call.get<RefreshService>()
                val jobId = refreshService.refreshAsync(githubToken)
                call.respond(HttpStatusCode.Created, mapOf("job_id" to jobId))
            }
        }
    }
}
