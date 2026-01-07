package me.brosssh.bundles

import io.github.smiley4.ktoropenapi.openApi
import io.github.smiley4.ktorswaggerui.swaggerUI
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.routing.*
import me.brosssh.bundles.plugins.*
import me.brosssh.bundles.api.routes.bundleRoutes
import me.brosssh.bundles.api.routes.graphQLRoute
import me.brosssh.bundles.api.routes.refreshRoute
import me.brosssh.bundles.api.routes.snapshotRoutes

fun Application.module() {
    configureSerialization()
    configureDatabase()
    configureKoin()
    configureOpenApi()
    configureStatic()
    configureAuthentication(Config.authenticationSecret)

    routing {
        route("api.json") {
            openApi()
        }
        route("swagger") {
            swaggerUI("/api.json")
        }

        refreshRoute()
        snapshotRoutes()
        bundleRoutes()
        graphQLRoute()
    }
}

fun main(args: Array<String>) {
    embeddedServer(
        Netty,
        port = Config.port,
        host = "0.0.0.0",
        module = Application::module
    ).start(wait = true)
}
