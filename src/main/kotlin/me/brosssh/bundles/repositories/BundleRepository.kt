package me.brosssh.bundles.repositories

import me.brosssh.bundles.db.entities.BundleEntity
import me.brosssh.bundles.db.tables.BundleTable
import me.brosssh.bundles.db.tables.SourceMetadataTable
import me.brosssh.bundles.db.tables.SourceTable
import me.brosssh.bundles.domain.models.Bundle
import me.brosssh.bundles.domain.models.BundleMetadata
import me.brosssh.bundles.domain.models.BundleType
import me.brosssh.bundles.domain.models.ReleaseChannel
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
            BundleTable.version,
            onUpdate = {
                it[BundleTable.needPatchesUpdate] =
                    BundleTable.needPatchesUpdate or // If already need update, keep it to true
                        (BundleTable.fileHash.isNotNull() and // If hash is null, do not reprocess it. It's old anyway
                                BundleTable.fileHash.neq(bundleMetadata.fileHash)) // If hash is different, needs to be reprocessed

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

    fun findLatestByRepo(owner: String, repo: String, prerelease: Boolean) = transaction {
        (BundleTable innerJoin SourceTable innerJoin SourceMetadataTable)
            .selectAll()
            .where {
                (SourceMetadataTable.ownerName eq owner) and
                        (SourceMetadataTable.repoName eq repo) and
                        (BundleTable.isPrerelease eq prerelease) and
                        (BundleTable.isLatest eq true)
            }
            .limit(1)
            .map(::rowToDomain)
            .singleOrNull()
    }

    fun findByRepoAndVersion(owner: String, repo: String, version: String) = transaction {
        (BundleTable innerJoin SourceTable innerJoin SourceMetadataTable)
            .selectAll()
            .where {
                (SourceMetadataTable.ownerName eq owner) and
                        (SourceMetadataTable.repoName eq repo) and
                        (BundleTable.version eq version)
            }
            .limit(1)
            .map(::rowToDomain)
            .singleOrNull()
    }

    fun findByRepoAndChannel(owner: String, repo: String, channel: ReleaseChannel) = transaction {
        (BundleTable innerJoin SourceTable innerJoin SourceMetadataTable)
            .selectAll()
            .where {
                (SourceMetadataTable.ownerName eq owner) and
                        (SourceMetadataTable.repoName eq repo) and
                        (BundleTable.isLatest eq true) and
                        (channel.predicate())
            }
            .orderBy(BundleTable.createdAt, SortOrder.DESC)
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
