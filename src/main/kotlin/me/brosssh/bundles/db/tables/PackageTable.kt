package me.brosssh.bundles.db.tables

import org.jetbrains.exposed.v1.core.dao.id.IntIdTable

object PackageTable : IntIdTable("package") {
    val name = varchar("name", 255)
    val version = varchar("version", 255).nullable()

    init {
        uniqueIndex("package_unique", version, name)
    }
}
