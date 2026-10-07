package me.brosssh.bundles.db.tables

import org.jetbrains.exposed.v1.core.dao.id.IntIdTable
import org.jetbrains.exposed.v1.datetime.timestampWithTimeZone
import java.time.OffsetDateTime

object BundleTable : IntIdTable("bundle") {
    val version = varchar("version", 255)
    val createdAt = varchar("created_at", 255)
    val description = text("description").nullable()
    val downloadUrl = varchar("download_url", 255)
    val signatureDownloadUrl = varchar("signature_download_url", 255).nullable()
    val isPrerelease = bool("is_prerelease")
    val isLatest = bool("is_latest")
    val fileHash = varchar("file_hash", 255).nullable()
    val needPatchesUpdate = bool("need_patches_update")
    val patchRefreshAttemptedAt = timestampWithTimeZone("patch_refresh_attempted_at")
        .default(OffsetDateTime.parse("1970-01-01T00:00:00Z"))
    val patcherRuntime = varchar("patcher_runtime", 255).nullable()
    val patcherFailure = text("patcher_failure").nullable()
    val patcherFailureFingerprint = varchar("patcher_failure_fingerprint", 64).nullable()
    val bundleType = varchar("bundle_type", 255)
    val sourceFk = reference("source_fk", SourceTable.id)

    init {
        uniqueIndex("bundle_source_prerelease_uq", sourceFk, isPrerelease)
    }
}
