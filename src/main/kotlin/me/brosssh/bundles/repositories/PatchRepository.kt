package me.brosssh.bundles.repositories

import me.brosssh.bundles.db.entities.BundleEntity
import me.brosssh.bundles.db.entities.PatchEntity
import me.brosssh.bundles.db.tables.PatchTable
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.transactions.transaction

class PatchRepository {
    fun create(
        bundleEntity: BundleEntity,
        name: String?,
        description: String?
    ) = transaction {
        PatchEntity.new {
            this.bundle = bundleEntity
            this.name = name
            this.description = description
        }
    }

    fun deleteByBundle(bundleEntity: BundleEntity) = transaction {
        PatchTable.deleteWhere { PatchTable.bundleFk eq bundleEntity.id }
    }
}
