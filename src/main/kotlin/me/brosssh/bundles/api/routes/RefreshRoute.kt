package me.brosssh.bundles.api.routes

import io.github.smiley4.ktoropenapi.post
import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.authenticate
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.route
import me.brosssh.bundles.domain.services.refresh.RefreshBundlesMetadataService
import me.brosssh.bundles.domain.services.refresh.RefreshBundlesService
import org.koin.ktor.ext.get

fun Route.refreshRoute() {

    route("/refresh") {
        authenticate("hmacAuth") {
            post("bundles", {
                description = "Trigger async refresh job"

                securitySchemeNames("hmacAuth")

                response {
                    HttpStatusCode.Created to {
                        description = "Refresh job created"
                    }
                    code(HttpStatusCode.Created) {
                        body<Map<String, String>>()
                    }
                }
            }) {
                val refreshService = call.get<RefreshBundlesService>()
                val jobId = refreshService.refresh()
                call.respond(HttpStatusCode.Created, mapOf("job_id" to jobId))
            }
        }

        post("bundlesMetadata", {
            description = "Trigger async refresh job"

            securitySchemeNames("hmacAuth")

            response {
                HttpStatusCode.Created to {
                    description = "Refresh job created"
                }
                code(HttpStatusCode.Created) {
                    body<Map<String, String>>()
                }
            }
        }) {
            val refreshBundlesService = call.get<RefreshBundlesMetadataService>()
            val jobId = refreshBundlesService.refresh()
            call.respond(HttpStatusCode.Created, mapOf("job_id" to jobId))
        }
    }
}
