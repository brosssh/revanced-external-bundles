package me.brosssh.bundles.db.tables

import org.jetbrains.exposed.v1.core.dao.id.IntIdTable
import kotlin.time.ExperimentalTime

@OptIn(ExperimentalTime::class)
object SourceMetadataTable : IntIdTable("source_metadata") {
    val sourceFk = reference("source_fk", SourceTable)
    val ownerName = varchar("owner_name", 255)
    val ownerAvatarUrl = varchar("owner_avatar_url", 255)
    val repoName = varchar("repo_name", 255)
    val repoDescription = varchar("repo_description", 1047).nullable()
    val repoStars = integer("repo_stars")
    val isRepoArchived = bool("is_repo_archived")
    val repoPushedAt = varchar("repo_pushed_at", 20)

    init {
        uniqueIndex("source_fk_unique", sourceFk)
    }
}
