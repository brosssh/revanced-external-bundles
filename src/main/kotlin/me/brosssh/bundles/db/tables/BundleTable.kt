package me.brosssh.bundles.db.tables

import org.jetbrains.exposed.v1.core.dao.id.IntIdTable

object BundleTable : IntIdTable("bundle") {
    val name = varchar("name", 255).nullable()
    val version = varchar("version", 255).nullable()
    val description = text("description").nullable()
    val bundleSource = varchar("source", 255).nullable()
    val author = varchar("author", 255).nullable()
    val contact = varchar("contact", 255).nullable()
    val website = varchar("website", 255).nullable()
    val license = varchar("license", 255).nullable()
}
