package me.brosssh.bundles.plugins

import io.github.smiley4.ktoropenapi.OpenApi
import io.github.smiley4.ktoropenapi.config.OutputFormat
import io.ktor.server.application.*
import kotlinx.serialization.ExperimentalSerializationApi
import me.brosssh.bundles.Config

@OptIn(ExperimentalSerializationApi::class)
fun Application.configureOpenApi() {
    install(OpenApi) {
        info {
            title = "ReVanced external bundles API"
            version = Config.version
        }
        /**
         * https://github.com/SMILEY4/ktor-openapi-tools/issues/227
        schemas {
            generator = SchemaGenerator.kotlinx {
                namingStrategy = JsonNamingStrategy.SnakeCase
            }
        }
        **/
        outputFormat = OutputFormat.JSON
    }
}
