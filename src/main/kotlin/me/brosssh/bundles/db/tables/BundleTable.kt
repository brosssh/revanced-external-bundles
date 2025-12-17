package me.brosssh.bundles.db.tables

import org.jetbrains.exposed.v1.core.dao.id.IntIdTable

object BundleTable : IntIdTable("bundle") {
    val version = varchar("version", 255).nullable()
    val createdAt = varchar("created_at", 255).nullable()
    val description = text("description").nullable()
    val downloadUrl = varchar("download_url", 255)
    val signatureDownloadUrl = varchar("signature_download_url", 255).nullable()
    val isPrerelease = bool("is_rerelease")
    val sourceFk = reference("source_fk", SourceTable.id)
}
