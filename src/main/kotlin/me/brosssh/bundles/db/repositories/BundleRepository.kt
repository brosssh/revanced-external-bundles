package me.brosssh.bundles.db.repositories

import me.brosssh.bundles.db.entities.BundleEntity
import me.brosssh.bundles.db.entities.SourceEntity
import me.brosssh.bundles.db.tables.BundleTable
import me.brosssh.bundles.db.tables.SourceTable
import me.brosssh.bundles.models.BundleDto
import me.brosssh.bundles.models.GithubReleaseDto
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction

class BundleRepository {
    fun findBySourceUrl(sourceUrl: String) =
        transaction {
            (BundleTable innerJoin SourceTable)
                .selectAll()
                .where { SourceTable.url eq sourceUrl }
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

}
