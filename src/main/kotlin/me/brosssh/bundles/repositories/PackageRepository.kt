package me.brosssh.bundles.repositories

import me.brosssh.bundles.db.entities.PackageEntity
import me.brosssh.bundles.db.tables.PackageTable
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.transactions.transaction

class PackageRepository {
    fun findOrCreate(name: String, version: String?) = transaction {
        (PackageEntity.find {
            (PackageTable.name eq name) and (PackageTable.version eq version)
        }.firstOrNull() ?: PackageEntity.new {
            this.name = name
            this.version = version
        })
    }
}
