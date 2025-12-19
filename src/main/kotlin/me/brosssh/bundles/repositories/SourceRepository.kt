package me.brosssh.bundles.repositories

import me.brosssh.bundles.db.entities.SourceEntity
import org.jetbrains.exposed.v1.jdbc.transactions.transaction

class SourceRepository {
    fun getAll(): List<SourceEntity> = transaction { SourceEntity.all().toList() }
}
