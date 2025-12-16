package me.brosssh.bundles.db.repositories

import kotlinx.serialization.Serializable
import me.brosssh.bundles.db.entities.BundleEntity
import org.jetbrains.exposed.v1.jdbc.transactions.transaction

class BundleRepository {
    fun findAll(): List<BundleEntity> =
        transaction { BundleEntity.all().toList() }

    fun findById(id: Int): BundleEntity? =
        transaction { BundleEntity.findById(id) }
}
