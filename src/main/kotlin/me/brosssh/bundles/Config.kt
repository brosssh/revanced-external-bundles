package me.brosssh.bundles

import io.github.cdimascio.dotenv.dotenv

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

    // Database
    val databaseUrl: String = getEnv("DATABASE_URL")
    val databaseUser: String = getEnv("DATABASE_USER")
    val databasePassword: String = getEnv("DATABASE_PSSW")
    
    // Authentication
    val authenticationSecret: String = getEnv("AUTHENTICATION_SECRET")
    
    // GitHub
    val githubRepoToken: String = getEnv("GITHUB_REPO_TOKEN")

    // Server
    val port: Int = getEnv("PORT", "8080").toInt()

}
