package me.brosssh.bundles.db.entities

import me.brosssh.bundles.db.tables.SourceMetadataTable
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.dao.IntEntity
import org.jetbrains.exposed.v1.dao.IntEntityClass

class SourceMetadataEntity(id: EntityID<Int>) : IntEntity(id) {
    companion object : IntEntityClass<SourceMetadataEntity>(SourceMetadataTable)

    var sourceEntity by SourceMetadataTable.sourceFk
    var ownerName by SourceMetadataTable.ownerName
    var ownerAvatarUrl by SourceMetadataTable.ownerAvatarUrl
    var repoName by SourceMetadataTable.repoName
    var repoDescription by SourceMetadataTable.repoDescription
    var repoStars by SourceMetadataTable.repoStars
}
