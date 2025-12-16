package me.brosssh.bundles.plugins

import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.auth.*

fun Application.configureAuthentication(expectedToken: String) {
    install(Authentication) {
        bearer("apiTokenAuth") {
            authenticate { tokenCredential ->
                if (tokenCredential.token == expectedToken) {
                    UserIdPrincipal("authorizedUser")
                } else {
                    null
                }
            }
        }
    }
}
