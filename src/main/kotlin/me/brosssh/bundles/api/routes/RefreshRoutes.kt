package me.brosssh.bundles.api.routes

import io.github.smiley4.ktoropenapi.get
import io.github.smiley4.ktoropenapi.post
import io.ktor.http.*
import io.ktor.server.auth.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import me.brosssh.bundles.api.dto.JobStatusResponseDto
import me.brosssh.bundles.api.dto.toResponseDto
import me.brosssh.bundles.domain.models.RefreshJob
import me.brosssh.bundles.domain.services.RefreshJobStatusService
import me.brosssh.bundles.domain.services.jobs.RefreshBundlesJobService
import me.brosssh.bundles.domain.services.jobs.RefreshPatchesJobService
import org.koin.ktor.ext.get

fun Route.refreshRoutes() {

    route("/refresh") {
        authenticate("hmacAuth") {
            post("bundles", {
                description = "Trigger async bundles refresh job"
                tags = listOf("Refresh")

                securitySchemeNames("hmacAuth")

                response {
                    HttpStatusCode.Accepted to {
                        description = "Refresh job Accepted"
                    }
                    code(HttpStatusCode.Accepted) {
                        body<Map<String, String>>()
                    }
                }
            }) {
                with(call.get<RefreshBundlesJobService>()) {
                    val jobId = refresh()
                    call.respond(HttpStatusCode.Accepted, mapOf("job_id" to jobId))
                }
            }

            post("patches", {
                description = "Trigger async patches refresh job"
                tags = listOf("Refresh")

                securitySchemeNames("hmacAuth")

                response {
                    HttpStatusCode.Accepted to {
                        description = "Refresh job Accepted"
                    }
                    code(HttpStatusCode.Accepted) {
                        body<Map<String, String>>()
                    }
                }
            }) {
                with(call.get<RefreshPatchesJobService>()) {
                    val jobId = refresh()
                    call.respond(HttpStatusCode.Accepted, mapOf("job_id" to jobId))
                }
            }
        }

        get("status/{jobId}", {
            description = "Get status of any refresh job (bundles or patches)"
            tags = listOf("Refresh")
            securitySchemeNames("hmacAuth")

            request {
                pathParameter<String>("jobId") {
                    description = "Job ID returned from refresh endpoint"
                    required = true
                }
            }

            response {
                HttpStatusCode.OK to {
                    description = "Job completed successfully or failed"
                    body<JobStatusResponseDto>()
                }
                HttpStatusCode.Accepted to {
                    description = "Job still running or pending"
                    body<JobStatusResponseDto>()
                }
                HttpStatusCode.NotFound to {
                    description = "Job not found"
                    body<Map<String, String>>()
                }
            }
        }) {
            val jobId = call.parameters["jobId"] ?: return@get call.respond(
                HttpStatusCode.BadRequest,
                mapOf("error" to "Missing jobId parameter")
            )

            val statusResponse = call.get<RefreshJobStatusService>().getByJobId(jobId)
                ?: return@get call.respond(
                    HttpStatusCode.BadRequest,
                    mapOf("error" to "Job with jobId $jobId doesn't exists")
                )

            when (statusResponse.status) {
                RefreshJob.RefreshJobStatus.COMPLETED, RefreshJob.RefreshJobStatus.FAILED ->
                    call.respond(HttpStatusCode.OK, statusResponse.toResponseDto())

                else ->
                    call.respond(HttpStatusCode.Accepted, statusResponse.toResponseDto())
            }

        }
    }
}
