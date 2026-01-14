package me.brosssh.bundles.db.migration

import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import me.brosssh.bundles.Config

suspend fun applyHasuraMetadata() {
    with(HttpClient()) {
        val metadataJson = javaClass
                .classLoader
                .getResourceAsStream("hasura/metadata.json")
                ?.bufferedReader()
                ?.readText()
                ?: error("hasura/metadata.json not found on classpath")

        val response = post(Config.hostUrl.resolve("/hasura/v1/metadata").toURL()) {
            header("X-Hasura-Admin-Secret", Config.hasuraSecret)
            contentType(ContentType.Application.Json)
            setBody("""
                {
                    "type": "replace_metadata",
                    "args": $metadataJson
                }
            """.trimIndent()
            )
        }

        val body = response.bodyAsText()
        if (response.status.value !in 200..299)
            throw RuntimeException("Failed to apply Hasura metadata: $body. Dump again the metadata.json and try again")

        close()
    }
}
