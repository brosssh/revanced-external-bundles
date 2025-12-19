package me.brosssh.bundles.db.tables

import org.jetbrains.exposed.v1.core.dao.id.IntIdTable

object SourceMetadataTable : IntIdTable("source_metadata") {
    val sourceFk = reference("source_fk", SourceTable)
    val ownerName = varchar("owner_name", 255)
    val ownerAvatarUrl = varchar("owner_avatar_url", 255)
    val repoName = varchar("repo_name", 255)
    val repoDescription = varchar("repo_description", 1047).nullable()
    val repoStars = integer("repo_stars")

    init {
        uniqueIndex("source_fk_unique", sourceFk)
    }
}
