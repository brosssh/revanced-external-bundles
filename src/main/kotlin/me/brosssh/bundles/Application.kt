package me.brosssh.bundles

import io.ktor.server.application.Application
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.openapi.openAPI
import io.ktor.server.plugins.swagger.swaggerUI
import io.ktor.server.routing.routing
import me.brosssh.bundles.plugins.*
import me.brosssh.bundles.routes.refreshRoute

fun Application.module() {
    configureSerialization()
    configureDatabase()
    configureKoin()
    configureAuthentication(Config.authenticationSecret)

    routing {
        openAPI(path = "openapi", swaggerFile = "openapi.yaml")
        swaggerUI(path = "swagger", swaggerFile = "openapi.yaml")

        refreshRoute()
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
