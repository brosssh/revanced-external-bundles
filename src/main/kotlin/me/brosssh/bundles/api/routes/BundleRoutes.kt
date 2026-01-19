package me.brosssh.bundles.api.routes

import io.github.smiley4.ktoropenapi.get
import io.ktor.http.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import me.brosssh.bundles.api.dto.BundleResponseDto
import me.brosssh.bundles.api.dto.toResponseDto
import me.brosssh.bundles.domain.services.BundleQuery
import me.brosssh.bundles.domain.services.BundleService
import org.koin.ktor.ext.get

fun Route.bundleRoutes() {
    route("/bundle") {
        get("/{id}", {
            description = "Get bundle by internal ID"
            tags = listOf("Bundle")

            request {
                pathParameter<Int>("id") {
                    description = "Internal ID of the bundle"
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

            val id = call.parameters["id"]?.toIntOrNull()
                ?: return@get call.respond(
                    HttpStatusCode.BadRequest,
                    mapOf("error" to "Invalid id")
                )

            val bundle = bundleService.getById(id)
                ?: return@get call.respond(
                    HttpStatusCode.NotFound,
                    mapOf("error" to "Bundle not found")
                )

            call.respond(HttpStatusCode.OK, bundle.toResponseDto())
        }

        //region Latest
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
                queryParameter<Boolean>("prerelease") {
                    description = "Whether to fetch prerelease version"
                    required = false
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
            val isPrerelease = call.request.queryParameters["prerelease"]?.toBooleanStrictOrNull() ?: false

            val bundle = bundleService.getBundleByQuery(
                BundleQuery.ByRepositoryLatest(owner, repo, isPrerelease)
            )
                ?: return@get call.respond(
                    HttpStatusCode.NotFound,
                    mapOf("error" to "Bundle not found")
                )

            call.respond(HttpStatusCode.OK, bundle.toResponseDto())
        }
        //endregion

        //region By version
        get("/{owner}/{repo}/{version}", {
            description = "Get bundle by repository owner, name and version"
            tags = listOf("Bundle")

            request {
                pathParameter<String>("owner") {
                    description = "Repository owner"
                }
                pathParameter<String>("repo") {
                    description = "Repository name"
                }
                pathParameter<String>("version") {
                    description = "Bundle version"
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
            val version = call.parameters["version"]!!

            val bundle = bundleService.getBundleByQuery(
                BundleQuery.ByRepositoryAndVersion(owner, repo, version)
            )
                ?: return@get call.respond(
                    HttpStatusCode.NotFound,
                    mapOf("error" to "Bundle not found")
                )

            call.respond(HttpStatusCode.OK, bundle.toResponseDto())
        }
        //endregion
    }
}
