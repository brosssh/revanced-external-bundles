package me.brosssh.bundles.repositories

import me.brosssh.bundles.api.dto.SearchResponseDto
import me.brosssh.bundles.api.dto.SearchResponsePatchDto
import me.brosssh.bundles.db.entities.SourceEntity
import me.brosssh.bundles.db.tables.BundleTable
import me.brosssh.bundles.db.tables.PatchTable
import me.brosssh.bundles.db.tables.SourceMetadataTable
import me.brosssh.bundles.db.tables.SourceTable
import me.brosssh.bundles.domain.models.Bundle
import me.brosssh.bundles.integrations.github.GithubReleaseDto
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
        (BundleTable innerJoin SourceTable leftJoin SourceMetadataTable)
            .selectAll()
            .where { SourceTable.url.lowerCase() like "%${query.lowercase()}%" }
            .limit(20)
            .map { bundleRow ->
                val bundleId = bundleRow[BundleTable.id].value
                val patches = PatchTable
                    .selectAll()
                    .where { PatchTable.bundleFk eq bundleId }
                    .map { patchRow ->
                        SearchResponsePatchDto(
                            name = patchRow[PatchTable.name],
                            description = patchRow[PatchTable.description]
                        )
                    }

                SearchResponseDto(
                    ownerName = bundleRow[SourceMetadataTable.ownerName],
                    ownerAvatarUrl = bundleRow[SourceMetadataTable.ownerAvatarUrl],
                    repoName = bundleRow[SourceMetadataTable.repoName],
                    repoDescription = bundleRow[SourceMetadataTable.repoDescription],
                    repoStars = bundleRow[SourceMetadataTable.repoStars],
                    sourceUrl = bundleRow[SourceTable.url],

                    bundleId = bundleId,
                    createdAt = bundleRow[BundleTable.createdAt].substringBefore("Z"),
                    description = bundleRow[BundleTable.description] ?: "",
                    version = bundleRow[BundleTable.version],
                    downloadUrl = bundleRow[BundleTable.downloadUrl],
                    signatureDownloadUrl = bundleRow[BundleTable.signatureDownloadUrl] ?: "",
                    isPrerelease = bundleRow[BundleTable.isPrerelease],

                    patches = patches
                )
            }
    }


}
