package me.brosssh.bundles.db.entities

import me.brosssh.bundles.db.tables.PackageTable
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.dao.IntEntity
import org.jetbrains.exposed.v1.dao.IntEntityClass

class PackageEntity(id: EntityID<Int>) : IntEntity(id) {
    companion object : IntEntityClass<PackageEntity>(PackageTable)

    var name by PackageTable.name
    var version by PackageTable.version
}
