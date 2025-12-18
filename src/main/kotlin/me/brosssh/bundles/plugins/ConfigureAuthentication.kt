package me.brosssh.bundles.plugins

import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.auth.*
import me.brosssh.bundles.Config.isDebug
import me.brosssh.bundles.util.hmacSha256Hex
import kotlin.math.abs

fun Application.configureAuthentication(authenticationSecret: String, validWindowSeconds: Long = 300) {
    install(Authentication) {
        bearer("hmacAuth") {
            authenticate { tokenCredential ->
                if (isDebug)
                    return@authenticate UserIdPrincipal("authorizedUser")

                val parts = tokenCredential.token.split("-")
                if (parts.size != 2) return@authenticate null

                val (timestampStr, signature) = parts
                val timestamp = timestampStr.toLongOrNull() ?: return@authenticate null

                // Check timestamp window
                val now = System.currentTimeMillis() / 1000
                if (abs(now - timestamp) > validWindowSeconds) return@authenticate null

                // Recompute HMAC
                val payload = "refresh:$timestamp"
                val expectedSignature = hmacSha256Hex(authenticationSecret, payload)

                if (signature == expectedSignature) {
                    UserIdPrincipal("authorizedUser")
                } else null
            }
        }
    }
}
