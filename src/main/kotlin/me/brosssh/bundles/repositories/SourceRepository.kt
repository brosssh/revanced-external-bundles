package me.brosssh.bundles.repositories

import me.brosssh.bundles.db.entities.SourceEntity
import me.brosssh.bundles.db.tables.BundleTable
import me.brosssh.bundles.db.tables.PatchTable
import me.brosssh.bundles.db.tables.SourceMetadataTable
import me.brosssh.bundles.db.tables.SourceTable
import me.brosssh.bundles.domain.models.SourceDeletionResult
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.or
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update

class SourceRepository {
    fun getEnabled(): List<SourceEntity> = transaction {
        SourceEntity.find { SourceTable.enabled eq true }.toList()
    }

    fun setUnavailableReason(sourceId: Int, reason: String?) = transaction {
        SourceTable.update({ SourceTable.id eq sourceId }) {
            it[unavailableReason] = reason
        }
    }

    fun hardDelete(sourceUrl: String): SourceDeletionResult = transaction {
        val sourceRows = SourceTable
            .selectAll()
            .where {
                (SourceTable.url eq sourceUrl) or (SourceTable.url eq "$sourceUrl/")
            }
            .forUpdate()
            .toList()

        if (sourceRows.isEmpty()) return@transaction SourceDeletionResult.NotFound
        if (sourceRows.any { it[SourceTable.enabled] }) {
            return@transaction SourceDeletionResult.Enabled
        }

        val sourceIds = sourceRows.map { it[SourceTable.id] }
        val bundleIds = BundleTable
            .selectAll()
            .where { BundleTable.sourceFk inList sourceIds }
            .map { it[BundleTable.id] }
        val patchCount = if (bundleIds.isEmpty()) {
            0
        } else {
            PatchTable
                .selectAll()
                .where { PatchTable.bundleFk inList bundleIds }
                .count()
                .toInt()
        }

        SourceMetadataTable.deleteWhere { SourceMetadataTable.sourceFk inList sourceIds }
        val bundleCount = BundleTable.deleteWhere { BundleTable.sourceFk inList sourceIds }
        val sourceCount = SourceTable.deleteWhere { SourceTable.id inList sourceIds }

        SourceDeletionResult.Deleted(
            sources = sourceCount,
            bundles = bundleCount,
            patches = patchCount
        )
    }
}
