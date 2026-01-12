package me.brosssh.bundles.repositories

import me.brosssh.bundles.db.tables.SourceMetadataTable
import me.brosssh.bundles.domain.models.SourceMetadata
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.upsert


class SourceMetadataRepository {
    fun upsert(
        sourceMetadata: SourceMetadata
    ) = transaction {
        SourceMetadataTable.upsert(
            SourceMetadataTable.sourceFk
        ) { source ->
            source[sourceFk] = sourceMetadata.id
            source[ownerName] = sourceMetadata.ownerName
            source[ownerAvatarUrl] = sourceMetadata.ownerAvatarUrl
            source[repoName] = sourceMetadata.repoName
            source[repoDescription] = sourceMetadata.repoDescription
            source[repoStars] = sourceMetadata.repoStars
            source[isRepoArchived] = sourceMetadata.isRepoArchived
            source[repoPushedAt] = sourceMetadata.repoPushedAt
        }
    }
}
