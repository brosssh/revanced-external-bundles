package me.brosssh.bundles.db.tables

import org.jetbrains.exposed.v1.core.dao.id.IntIdTable

object BundleTable : IntIdTable("bundle") {
    val version = varchar("version", 255)
    val createdAt = varchar("created_at", 255)
    val description = text("description").nullable()
    val downloadUrl = varchar("download_url", 255)
    val signatureDownloadUrl = varchar("signature_download_url", 255).nullable()
    val isPrerelease = bool("is_prerelease")
    val fileHash = varchar("file_hash", 255).nullable()
    val needPatchesUpdate = bool("need_patches_update")
    val isBundleV3 = bool("is_bundle_v3")
    val sourceFk = reference("source_fk", SourceTable.id)

    init {
        uniqueIndex("bundle_source_prerelease_uq", sourceFk, isPrerelease)
    }
}
