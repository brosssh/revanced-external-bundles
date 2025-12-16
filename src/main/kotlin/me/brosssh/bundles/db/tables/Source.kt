package me.brosssh.bundles.db.tables

import org.jetbrains.exposed.dao.id.IntIdTable

object Source : IntIdTable("source") {
    val url = varchar("url", 255)
}
