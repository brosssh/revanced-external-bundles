package me.brosssh.bundles.db.entities

import me.brosssh.bundles.db.tables.BundleTable
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.dao.IntEntity
import org.jetbrains.exposed.v1.dao.IntEntityClass

class BundleEntity(id: EntityID<Int>) : IntEntity(id) {
    companion object : IntEntityClass<BundleEntity>(BundleTable)

    var name by BundleTable.name
    var version by BundleTable.version
    var description by BundleTable.description
    var bundleSource by BundleTable.bundleSource
    var author by BundleTable.author
    var contact by BundleTable.contact
    var website by BundleTable.website
    var license by BundleTable.license
}
