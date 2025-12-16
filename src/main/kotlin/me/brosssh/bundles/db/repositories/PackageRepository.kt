package me.brosssh.bundles.db.repositories

import me.brosssh.bundles.db.entities.PackageEntity
import me.brosssh.bundles.db.entities.SourceEntity
import org.jetbrains.exposed.v1.jdbc.transactions.transaction

class PackageRepository {
    fun buildPackageLookup() = transaction {
        PackageEntity.all().associate { pkg ->
            Pair(pkg.name, pkg.version) to pkg.id.value
        }
    }
}
