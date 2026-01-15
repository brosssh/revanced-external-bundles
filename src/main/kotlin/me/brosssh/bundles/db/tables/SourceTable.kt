package me.brosssh.bundles.db.tables

import org.jetbrains.exposed.v1.core.dao.id.IntIdTable

object SourceTable : IntIdTable("source") {
    val url = varchar("url", 255)
}
