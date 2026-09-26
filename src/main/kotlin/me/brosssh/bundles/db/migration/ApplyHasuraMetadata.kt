package me.brosssh.bundles.db.migration

import io.ktor.client.*
import io.ktor.client.plugins.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import me.brosssh.bundles.Config

suspend fun applyHasuraMetadata() {
    val metadataJson = object {}.javaClass
        .classLoader
        .getResourceAsStream("hasura/metadata.json")
        ?.bufferedReader()
        ?.use { it.readText() }
        ?: error("hasura/metadata.json not found on classpath")

    HttpClient {
        install(HttpRequestRetry) {
            retryOnServerErrors(maxRetries = 4)
            retryOnException(maxRetries = 4, retryOnTimeout = true)
            exponentialDelay()
        }
        // Must be installed after HttpRequestRetry for timeout exceptions to be retryable.
        install(HttpTimeout) {
            requestTimeoutMillis = 30_000
            connectTimeoutMillis = 10_000
        }
    }.use { client ->
        val response = client.post(Config.hasuraInternalUrl.resolve("/v1/metadata").toURL()) {
            header("X-Hasura-Admin-Secret", Config.hasuraSecret)
            contentType(ContentType.Application.Json)
            setBody("""
                {
                    "type": "replace_metadata",
                    "args": $metadataJson
                }
            """.trimIndent())
        }

        val body = response.bodyAsText()
        if (response.status.value !in 200..299) {
            throw RuntimeException("Failed to apply Hasura metadata (${response.status}): $body")
        }
    }
}
