package me.brosssh.bundles.repositories

import me.brosssh.bundles.api.dto.SearchResponseDto
import me.brosssh.bundles.api.dto.SearchResponsePatchDto
import me.brosssh.bundles.api.dto.SearchResponsePatchPackageDto
import me.brosssh.bundles.db.entities.SourceEntity
import me.brosssh.bundles.db.tables.BundleTable
import me.brosssh.bundles.db.tables.PackageTable
import me.brosssh.bundles.db.tables.PatchPackageTable
import me.brosssh.bundles.db.tables.PatchTable
import me.brosssh.bundles.db.tables.SourceMetadataTable
import me.brosssh.bundles.db.tables.SourceTable
import me.brosssh.bundles.domain.models.Bundle
import me.brosssh.bundles.integrations.github.GithubReleaseDto
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.like
import org.jetbrains.exposed.v1.core.lowerCase
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
                    Bundle(
                        createdAt = it[BundleTable.createdAt].substringBefore("Z"),
                        description = it[BundleTable.description] ?: "",
                        version = it[BundleTable.version],
                        downloadUrl = it[BundleTable.downloadUrl],
                        signatureDownloadUrl = it[BundleTable.signatureDownloadUrl] ?: "",
                    )
                }
                .singleOrNull()
        }

    fun upsert(
        releaseDto: GithubReleaseDto,
        source: SourceEntity,
        isPrereleaseFlag: Boolean
    ) = transaction {
        BundleTable.upsert(
            BundleTable.sourceFk, BundleTable.isPrerelease
        ) { bundle ->
            bundle[sourceFk] = source.id
            bundle[version] = releaseDto.tagName
            bundle[description] = releaseDto.body
            bundle[createdAt] = releaseDto.createdAt
            bundle[downloadUrl] = releaseDto.assets.firstOrNull { it.name.endsWith(".rvp") }
                    ?.browserDownloadUrl
                    ?: error("No rvp file found")
            bundle[signatureDownloadUrl] = releaseDto.assets.firstOrNull { it.name.endsWith(".rvp.asc") }
                    ?.browserDownloadUrl
            bundle[isPrerelease] = isPrereleaseFlag
        }
    }

    fun search(query: String) = transaction {
        (BundleTable
                innerJoin SourceTable
                leftJoin SourceMetadataTable
                innerJoin PatchTable
                leftJoin PatchPackageTable
                leftJoin PackageTable)
            .selectAll()
            .where {
                SourceTable.url.lowerCase() like "%${query.lowercase()}%"
            }
            .orderBy(SourceMetadataTable.repoStars, SortOrder.DESC_NULLS_LAST)
            .groupBy { it[BundleTable.id].value }
            .map { (bundleId, rows) ->
                val firstRow = rows.first()

                val patches = rows
                    .groupBy { it[PatchTable.id].value }
                    .map { (_, patchRows) ->
                        SearchResponsePatchDto(
                            name = patchRows.first()[PatchTable.name],
                            description = patchRows.first()[PatchTable.description],
                            compatiblePackages = patchRows
                                .filter { it[PackageTable.id] != null }
                                .groupBy { it[PackageTable.name] }
                                .map { (name, pkgRows) ->
                                    SearchResponsePatchPackageDto(
                                        name,
                                        pkgRows.map { it[PackageTable.version] }
                                    )
                                }
                        )
                    }

                SearchResponseDto(
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
                    patches = patches
                )
            }
    }


}
