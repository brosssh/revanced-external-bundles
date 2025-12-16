package me.brosssh.bundles.db.tables

import org.jetbrains.exposed.dao.id.IntIdTable

object Bundles : IntIdTable("bundle") {
    val name = varchar("name", 255)
}
