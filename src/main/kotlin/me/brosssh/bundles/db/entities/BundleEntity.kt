package me.brosssh.bundles.db.entities

import me.brosssh.bundles.db.tables.BundleTable
import me.brosssh.bundles.domain.models.*
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
    var bundleType by BundleTable.bundleType
    var needPatchesUpdate by BundleTable.needPatchesUpdate
    var sourceFk by BundleTable.sourceFk
    var sourceEntity by SourceEntity referencedOn BundleTable.sourceFk
}


private fun createBundleDomain(
    constructor: (String, String?, String, String, String?, Int, BundleType) -> Bundle,
    entity: BundleEntity
): Bundle = constructor(
    entity.version,
    entity.description,
    entity.createdAt,
    entity.downloadUrl,
    entity.signatureDownloadUrl,
    entity.sourceFk.value,
    BundleType.from(entity.bundleType)
)

fun BundleEntity.toBundleDomain() =  when (BundleType.from(bundleType)) {
    BundleType.REVANCED_V3 -> createBundleDomain(::ReVancedV3Bundle, this)
    BundleType.REVANCED_V4 -> createBundleDomain(::ReVancedV4Bundle, this)
    BundleType.MORPHE_V1 -> createBundleDomain(::MorpheV1Bundle, this)
}
