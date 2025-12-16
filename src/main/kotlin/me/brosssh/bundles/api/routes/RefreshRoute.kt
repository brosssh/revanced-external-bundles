package me.brosssh.bundles.api.routes

import io.github.smiley4.ktoropenapi.post
import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.authenticate
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.route
import me.brosssh.bundles.domain.services.refresh.RefreshBundlesService
import me.brosssh.bundles.domain.services.refresh.RefreshPatchesService
import org.koin.ktor.ext.get

fun Route.refreshRoute() {

    route("/refresh") {
        authenticate("hmacAuth") {
            post("bundles", {
                description = "Trigger async bundles refresh job"

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
                with(call.get<RefreshBundlesService>()) {
                    val jobId = refresh()
                    call.respond(HttpStatusCode.Created, mapOf("job_id" to jobId))
                }
            }

            post("patches", {
                description = "Trigger async patches refresh job"

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
                with(call.get<RefreshPatchesService>()) {
                    val jobId = refresh()
                    call.respond(HttpStatusCode.Created, mapOf("job_id" to jobId))
                }
            }
        }
    }
}
