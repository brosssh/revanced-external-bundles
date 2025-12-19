package me.brosssh.bundles.db.repositories

import me.brosssh.bundles.db.entities.BundleEntity
import me.brosssh.bundles.db.entities.SourceEntity
import me.brosssh.bundles.db.tables.BundleTable
import me.brosssh.bundles.db.tables.PatchTable
import me.brosssh.bundles.db.tables.SourceTable
import me.brosssh.bundles.models.BundleDto
import me.brosssh.bundles.models.GithubReleaseDto
import me.brosssh.bundles.models.frontend.SearchResponseDto
import me.brosssh.bundles.models.frontend.SearchResponsePatchDto
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.like
import org.jetbrains.exposed.v1.core.lowerCase
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction

class BundleRepository {
    fun findById(bundleId: Int) =
        transaction {
            (BundleTable innerJoin SourceTable)
                .selectAll()
                .where { BundleTable.id eq bundleId }
                .limit(1)
                .map {
                    BundleDto(
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
    ): BundleEntity = transaction {

        val existing = BundleEntity.find {
            (BundleTable.sourceFk eq source.id) and (BundleTable.isPrerelease eq isPrereleaseFlag)
        }.singleOrNull()

        existing?.apply {
            fromGithubRelease(releaseDto, isPrereleaseFlag)
        } ?: BundleEntity.new {
            sourceEntity = source.id
            fromGithubRelease(releaseDto, isPrereleaseFlag)
        }
    }

    fun search(query: String) = transaction {
        (BundleTable innerJoin SourceTable)
            .selectAll()
            .where { SourceTable.url.lowerCase() like "%${query.lowercase()}%" }
            .limit(20)
            .map { bundleRow ->
                val bundleId = bundleRow[BundleTable.id].value
                val patches = PatchTable
                    .selectAll()
                    .where { PatchTable.bundleFk eq bundleId }
                    .map { patchRow ->
                        // Map PatchRow to your PatchDto
                        SearchResponsePatchDto(
                            name = patchRow[PatchTable.name],
                            description = patchRow[PatchTable.description]
                        )
                    }

                SearchResponseDto(
                    sourceUrl = bundleRow[SourceTable.url],
                    bundleId = bundleId,
                    createdAt = bundleRow[BundleTable.createdAt].substringBefore("Z"),
                    description = bundleRow[BundleTable.description] ?: "",
                    version = bundleRow[BundleTable.version],
                    downloadUrl = bundleRow[BundleTable.downloadUrl],
                    signatureDownloadUrl = bundleRow[BundleTable.signatureDownloadUrl] ?: "",
                    patches = patches
                )
            }
    }


}
