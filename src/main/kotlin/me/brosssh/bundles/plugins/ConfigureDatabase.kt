package me.brosssh.bundles.plugins

import io.ktor.server.application.*
import org.jetbrains.exposed.sql.Database

fun Application.configureDatabase() {
    val dbConfig = environment.config.config("app.db")
    val jdbcUrl = dbConfig.property("jdbcUrl").getString()
    val dbUser = dbConfig.property("user").getString()
    val dbPassword = dbConfig.property("password").getString()

    Database.connect(jdbcUrl, driver = "org.postgresql.Driver", user = dbUser, password = dbPassword)
}
