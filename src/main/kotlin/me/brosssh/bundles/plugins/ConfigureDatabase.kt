package me.brosssh.bundles.plugins

import io.ktor.server.application.*
import me.brosssh.bundles.db.tables.*
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.transactions.transaction

fun Application.configureDatabase() {
    val dbConfig = environment.config.config("app.db")
    val jdbcUrl = dbConfig.property("jdbcUrl").getString()
    val dbUser = dbConfig.property("user").getString()
    val dbPassword = dbConfig.property("password").getString()

    val db = Database.connect(jdbcUrl, driver = "org.postgresql.Driver", user = dbUser, password = dbPassword)

    transaction(db) {
        SchemaUtils.create(BundleTable, PackageTable, PatchTable, RefreshJobTable, SourceTable)
    }
}
