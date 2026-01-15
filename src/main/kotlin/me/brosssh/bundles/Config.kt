package me.brosssh.bundles

import io.github.cdimascio.dotenv.dotenv
import java.net.URI

object Config {
    private val dotenv = dotenv { 
        ignoreIfMissing = true 
    }
    
    private fun getEnv(key: String, default: String? = null) =
        System.getenv(key)
            ?: dotenv[key] 
            ?: default 
            ?: throw IllegalStateException("$key is required")

    val env: String = getEnv("ENV", "production")
    val isDebug: Boolean = env.equals("debug", ignoreCase = true)
    val version: String = object {}.javaClass.`package`.implementationVersion ?: "dev"
    val host: String = getEnv("HOST")
    val hostUrl: URI = if (host == "localhost") URI("http://localhost:8080") else URI("https://$host")

    // Database
    val databaseHost: String = getEnv("DATABASE_HOST")
    val databaseName: String = getEnv("DATABASE_NAME")
    val databaseUser: String = getEnv("DATABASE_USER")
    val databasePassword: String = getEnv("DATABASE_PSSW")

    val databaseJdbcUrl = "jdbc:postgresql://$databaseHost:5432/$databaseName"

    
    // Authentication
    val authenticationSecret: String = getEnv("BACKEND_AUTHENTICATION_SECRET")

    // GitHub
    val githubPatToken: String = getEnv("BACKEND_GITHUB_PAT_TOKEN")

    // Server
    val port: Int = getEnv("BACKEND_PORT").toInt()

    val hasuraSecret: String = getEnv("HASURA_GRAPHQL_ADMIN_SECRET")
}
