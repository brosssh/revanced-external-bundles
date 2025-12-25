package me.brosssh.bundles.db.entities

import me.brosssh.bundles.db.tables.BundleTable
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.dao.IntEntity
import org.jetbrains.exposed.v1.dao.IntEntityClass

class BundleEntity(id: EntityID<Int>) : IntEntity(id) {
    companion object : IntEntityClass<BundleEntity>(BundleTable)

    var version by BundleTable.version
    var createdAt by BundleTable.createdAt
    var description by BundleTable.description
    var downloadUrl by BundleTable.downloadUrl
    var signatureDownloadUrl by BundleTable.signatureDownloadUrl
    var isPrerelease by BundleTable.isPrerelease
    var fileHash by BundleTable.fileHash
    var needPatchesUpdate by BundleTable.needPatchesUpdate
    var sourceEntity by SourceEntity referencedOn BundleTable.sourceFk
}
