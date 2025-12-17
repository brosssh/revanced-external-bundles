package me.brosssh.bundles

import io.ktor.server.application.Application
import io.ktor.server.netty.EngineMain
import io.ktor.server.plugins.openapi.openAPI
import io.ktor.server.plugins.swagger.swaggerUI
import io.ktor.server.routing.routing
import me.brosssh.bundles.plugins.*
import me.brosssh.bundles.routes.refreshRoute

fun Application.module() {
    configureSerialization()
    configureDatabase()
    configureKoin()
    configureAuthentication(
        environment.config.property("app.authentication_secret").getString()
    )

    routing {
        openAPI(path = "openapi", swaggerFile = "openapi.yaml")
        swaggerUI(path = "swagger", swaggerFile = "openapi.yaml")

        refreshRoute()
    }
}

fun main(args: Array<String>) {
    EngineMain.main(args)
}
