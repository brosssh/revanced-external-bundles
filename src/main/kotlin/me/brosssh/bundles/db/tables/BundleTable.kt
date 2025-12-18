package me.brosssh.bundles.db.tables

import me.brosssh.bundles.db.tables.PackageTable.name
import org.jetbrains.exposed.v1.core.dao.id.IntIdTable

object BundleTable : IntIdTable("bundle") {
    val version = varchar("version", 255)
    val createdAt = varchar("created_at", 255)
    val description = text("description").nullable()
    val downloadUrl = varchar("download_url", 255)
    val signatureDownloadUrl = varchar("signature_download_url", 255).nullable()
    val isPrerelease = bool("is_prerelease")
    val fileSha = varchar("file_sha", 64)
    val sourceFk = reference("source_fk", SourceTable.id)

    init {
        uniqueIndex("bundle_source_prerelease_uq", sourceFk, isPrerelease)
    }
}
