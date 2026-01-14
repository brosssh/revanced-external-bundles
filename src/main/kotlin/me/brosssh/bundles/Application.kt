package me.brosssh.bundles

import bundleRoutes
import io.github.smiley4.ktoropenapi.openApi
import io.github.smiley4.ktorswaggerui.swaggerUI
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.routing.*
import me.brosssh.bundles.api.routes.graphQLRoute
import me.brosssh.bundles.api.routes.refreshRoutes
import me.brosssh.bundles.db.migration.applyHasuraMetadata
import me.brosssh.bundles.db.migration.migrationScript
import me.brosssh.bundles.plugins.*

fun Route.apiV1(build: Route.() -> Unit) {
    route("/api/v1", build)
}

suspend fun Application.module() {
    configureSerialization()
    configureDatabase()
    configureKoin()
    configureOpenApi()
    configureStatic()
    configureAuthentication(Config.authenticationSecret)

    migrationScript()
    applyHasuraMetadata()

    routing {
        route("api.json") {
            openApi()
        }
        route("swagger") {
            swaggerUI("/api.json")
        }

        apiV1 {
            refreshRoutes()
            bundleRoutes()
        }

        graphQLRoute()
    }
}

fun main() {
    embeddedServer(
        Netty,
        port = Config.port,
        host = "0.0.0.0",
        module = Application::module
    ).start(wait = true)
}
