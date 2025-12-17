package me.brosssh.bundles.db.repositories

import me.brosssh.bundles.db.entities.BundleEntity
import me.brosssh.bundles.db.entities.SourceEntity
import me.brosssh.bundles.models.GithubReleaseDto
import org.jetbrains.exposed.v1.jdbc.transactions.transaction

class BundleRepository {
    fun findAll(): List<BundleEntity> =
        transaction { BundleEntity.all().toList() }

    fun findById(id: Int): BundleEntity? =
        transaction { BundleEntity.findById(id) }

    fun create(
        releaseDto: GithubReleaseDto,
        source: SourceEntity,
        isPrereleaseFlag: Boolean
    ) = transaction {
        BundleEntity.new {
            version = releaseDto.tag_name
            description = releaseDto.body
            createdAt = releaseDto.created_at
            downloadUrl =
                releaseDto.assets.firstOrNull { it.name.endsWith(".rvp") }?.browser_download_url
                    ?: error("No rvp file found")
            signatureDownloadUrl =
                releaseDto.assets.firstOrNull { it.name.endsWith(".rvp.asc") }?.browser_download_url
            isPrerelease = isPrereleaseFlag
            sourceEntity = source.id
        }
    }
}
