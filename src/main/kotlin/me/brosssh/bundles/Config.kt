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

    // Self-hosted git authorities, format: "host[:port]=type,..." (type = github|gitlab|gitea)
    val gitHosts: String = getEnv("BACKEND_GIT_HOSTS", "")

    // Optional authority-specific PATs, format: "host[:port]=pat,..."
    val gitHostsPat: String = getEnv("BACKEND_GIT_HOSTS_PAT", "")

    // Deprecated GitHub-only PAT retained so existing deployments keep authenticating.
    val legacyGithubPatToken: String = getEnv("BACKEND_GITHUB_PAT_TOKEN", "")

    // Isolated patcher workers
    val patcherRuntimeDir: String = getEnv("BACKEND_PATCHER_RUNTIME_DIR", "build/patcher-runtimes")
    val patcherWorkerMaxProcesses: Int = getEnv("BACKEND_PATCHER_WORKER_MAX_PROCESSES", "4").toInt()
    val patcherWorkerMaxPerRuntime: Int = getEnv("BACKEND_PATCHER_WORKER_MAX_PER_RUNTIME", "2").toInt()
    val patcherWorkerIdleSeconds: Long = getEnv("BACKEND_PATCHER_WORKER_IDLE_SECONDS", "300").toLong()
    val patcherRefreshConcurrency: Int = getEnv("BACKEND_PATCHER_REFRESH_CONCURRENCY", "4").toInt()

    // Server
    val port: Int = getEnv("BACKEND_PORT").toInt()

    val hasuraSecret: String = getEnv("HASURA_GRAPHQL_ADMIN_SECRET")
}
