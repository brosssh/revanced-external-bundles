package me.brosssh.bundles.repositories

import me.brosssh.bundles.db.entities.BundleEntity
import me.brosssh.bundles.db.tables.*
import me.brosssh.bundles.domain.models.Bundle
import me.brosssh.bundles.domain.models.BundleMetadata
import me.brosssh.bundles.domain.models.BundleType
import org.jetbrains.exposed.v1.core.*
import org.jetbrains.exposed.v1.core.statements.UpdateBuilder
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.upsert

class BundleRepository {
    fun findById(bundleId: Int) = transaction {
        BundleTable
            .selectAll()
            .where { BundleTable.id eq bundleId }
            .limit(1)
            .map(::rowToDomain)
            .singleOrNull()
    }

    fun upsert(bundleMetadata: BundleMetadata) = transaction {
        val commonFields: (UpdateBuilder<*>) -> Unit = {
            it[BundleTable.version] = bundleMetadata.bundle.version
            it[BundleTable.description] = bundleMetadata.bundle.description
            it[BundleTable.createdAt] = bundleMetadata.bundle.createdAt
            it[BundleTable.downloadUrl] = bundleMetadata.bundle.downloadUrl
            it[BundleTable.signatureDownloadUrl] = bundleMetadata.bundle.signatureDownloadUrl
            it[BundleTable.fileHash] = bundleMetadata.fileHash
            it[BundleTable.bundleType] = bundleMetadata.bundle.bundleType.value
        }

        BundleTable.upsert(
            BundleTable.sourceFk,
            BundleTable.isPrerelease,
            onUpdate = {
                it[BundleTable.needPatchesUpdate] = BundleTable.needPatchesUpdate or
                        BundleTable.fileHash.neq(bundleMetadata.fileHash)

                commonFields(it)
            }
        ) { bundle ->
            bundle[sourceFk] = bundleMetadata.bundle.sourceFk
            bundle[isPrerelease] = bundleMetadata.isPrerelease
            bundle[needPatchesUpdate] = bundleMetadata.bundle.bundleType != BundleType.REVANCED_V3

            commonFields(bundle)
        }
    }

    fun getBundlesNeedPatchesUpdate() = transaction {
        BundleEntity.find { BundleTable.needPatchesUpdate eq true }.toList()
    }

    fun findByPk(owner: String, repo: String, prerelease: Boolean) = transaction {
        (BundleTable innerJoin SourceTable innerJoin SourceMetadataTable)
            .selectAll()
            .where {
                (SourceMetadataTable.ownerName eq owner) and
                        (SourceMetadataTable.repoName eq repo) and
                        (BundleTable.isPrerelease eq prerelease)
            }
            .limit(1)
            .map(::rowToDomain)
            .singleOrNull()
    }

    private fun rowToDomain(row: ResultRow) =
        Bundle.create(
            row[BundleTable.bundleType],
            row[BundleTable.version],
            row[BundleTable.description],
            row[BundleTable.createdAt],
            row[BundleTable.downloadUrl],
            row[BundleTable.signatureDownloadUrl],
            row[BundleTable.sourceFk].value
        )
}
