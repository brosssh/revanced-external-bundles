package me.brosssh.bundles.db.entities

import me.brosssh.bundles.db.tables.PatchPackageTable
import me.brosssh.bundles.db.tables.PatchTable
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.dao.IntEntity
import org.jetbrains.exposed.v1.dao.IntEntityClass

class PatchEntity(id: EntityID<Int>) : IntEntity(id) {
    companion object : IntEntityClass<PatchEntity>(PatchTable)

    var bundle by BundleEntity referencedOn PatchTable.bundleFk
    var name by PatchTable.name
    var description by PatchTable.description
}
