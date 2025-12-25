package me.brosssh.bundles.db.entities

import me.brosssh.bundles.db.tables.SourceMetadataTable
import me.brosssh.bundles.db.tables.SourceTable
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.dao.IntEntity
import org.jetbrains.exposed.v1.dao.IntEntityClass

class SourceEntity(id: EntityID<Int>) : IntEntity(id) {
    companion object : IntEntityClass<SourceEntity>(SourceTable)

    var url by SourceTable.url
}
