package me.brosssh.bundles.repositories

import me.brosssh.bundles.db.entities.SourceEntity
import me.brosssh.bundles.db.tables.SourceTable
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.transactions.transaction

class SourceRepository {
    fun getEnabled(): List<SourceEntity> = transaction {
        SourceEntity.find { SourceTable.enabled eq true }.toList()
    }
}
