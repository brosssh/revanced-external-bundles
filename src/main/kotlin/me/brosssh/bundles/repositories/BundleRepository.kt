package me.brosssh.bundles.repositories

import me.brosssh.bundles.api.dto.BundleResponseDto
import me.brosssh.bundles.api.dto.SnapshotPackageResponseDto
import me.brosssh.bundles.api.dto.SnapshotPatchResponseDto
import me.brosssh.bundles.api.dto.SnapshotResponseDto
import me.brosssh.bundles.db.entities.BundleEntity
import me.brosssh.bundles.db.tables.*
import me.brosssh.bundles.domain.models.BundleMetadata
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.neq
import org.jetbrains.exposed.v1.core.or
import org.jetbrains.exposed.v1.core.statements.UpdateBuilder
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.upsert

class BundleRepository {
    fun findById(bundleId: Int) =
        transaction {
            (BundleTable innerJoin SourceTable)
                .selectAll()
                .where { BundleTable.id eq bundleId }
                .limit(1)
                .map {
                    BundleResponseDto(
                        createdAt = it[BundleTable.createdAt].substringBefore("Z"),
                        description = it[BundleTable.description] ?: "",
                        version = it[BundleTable.version],
                        downloadUrl = it[BundleTable.downloadUrl],
                        signatureDownloadUrl = it[BundleTable.signatureDownloadUrl] ?: "",
                    )
                }
                .singleOrNull()
        }

    fun upsert(bundleMetadata: BundleMetadata) = transaction {
        val commonFields: (UpdateBuilder<*>) -> Unit = {
            it[BundleTable.version] = bundleMetadata.version
            it[BundleTable.description] = bundleMetadata.description
            it[BundleTable.createdAt] = bundleMetadata.createdAt
            it[BundleTable.downloadUrl] = bundleMetadata.downloadUrl
            it[BundleTable.signatureDownloadUrl] = bundleMetadata.signatureDownloadUrl
            it[BundleTable.fileHash] = bundleMetadata.fileHash
            it[BundleTable.isBundleV3] = bundleMetadata.isBundleV3
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
            bundle[sourceFk] = bundleMetadata.sourceFk
            bundle[isPrerelease] = bundleMetadata.isPrerelease
            bundle[needPatchesUpdate] = !bundleMetadata.isBundleV3

            commonFields(bundle)
        }
    }

    fun getBundlesNeedPatchesUpdate() = transaction {
        BundleEntity.find { BundleTable.needPatchesUpdate eq true }.toList()
    }

    fun getSnapshot() = transaction {
        (BundleTable
                innerJoin SourceTable
                leftJoin SourceMetadataTable
                leftJoin PatchTable
                leftJoin PatchPackageTable
                leftJoin PackageTable)
            .selectAll()
            .orderBy(SourceMetadataTable.repoStars, SortOrder.DESC_NULLS_LAST)
            .groupBy { it[BundleTable.id].value }
            .map { (bundleId, rows) ->
                val firstRow = rows.first()

                val patches = rows
                    .filter { it[PatchTable.id] != null }
                    .groupBy { it[PatchTable.id].value }
                    .map { (_, patchRows) ->
                        SnapshotPatchResponseDto(
                            name = patchRows.first()[PatchTable.name],
                            description = patchRows.first()[PatchTable.description],
                            compatiblePackages = patchRows
                                .filter { it[PackageTable.id] != null }
                                .groupBy { it[PackageTable.name] }
                                .map { (name, pkgRows) ->
                                    SnapshotPackageResponseDto(
                                        name,
                                        pkgRows.map { it[PackageTable.version] }
                                    )
                                }
                        )
                    }

                SnapshotResponseDto(
                    ownerName = firstRow[SourceMetadataTable.ownerName],
                    ownerAvatarUrl = firstRow[SourceMetadataTable.ownerAvatarUrl],
                    repoName = firstRow[SourceMetadataTable.repoName],
                    repoDescription = firstRow[SourceMetadataTable.repoDescription],
                    repoStars = firstRow[SourceMetadataTable.repoStars],
                    sourceUrl = firstRow[SourceTable.url],
                    bundleId = bundleId,
                    createdAt = firstRow[BundleTable.createdAt].substringBefore("Z"),
                    description = firstRow[BundleTable.description].orEmpty(),
                    version = firstRow[BundleTable.version],
                    downloadUrl = firstRow[BundleTable.downloadUrl],
                    signatureDownloadUrl = firstRow[BundleTable.signatureDownloadUrl].orEmpty(),
                    isPrerelease = firstRow[BundleTable.isPrerelease],
                    isBundleV3 = firstRow[BundleTable.isBundleV3],
                    patches = patches
                )
            }
    }


}
