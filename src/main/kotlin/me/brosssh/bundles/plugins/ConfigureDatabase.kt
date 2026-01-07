package me.brosssh.bundles.plugins

import io.ktor.server.application.*
import me.brosssh.bundles.Config
import me.brosssh.bundles.db.tables.*
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.transactions.transaction

fun Application.configureDatabase() {
    val db = Database.connect(
        Config.databaseJdbcUrl,
        driver = "org.postgresql.Driver",
        user = Config.databaseUser,
        password = Config.databasePassword
    )

    transaction(db) {
        SchemaUtils.create(BundleTable,
            PackageTable,
            PatchTable,
            RefreshJobTable,
            SourceTable,
            SourceMetadataTable,
            PatchPackageTable
        )
    }
}
