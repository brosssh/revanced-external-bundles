package me.brosssh.bundles.repositories

import me.brosssh.bundles.db.entities.PackageEntity
import me.brosssh.bundles.db.entities.PatchEntity
import me.brosssh.bundles.db.tables.PatchPackageTable
import org.jetbrains.exposed.v1.jdbc.insertIgnore
import org.jetbrains.exposed.v1.jdbc.transactions.transaction

class PatchPackageRepository {

    fun link(patch: PatchEntity, pkg: PackageEntity) = transaction {
        PatchPackageTable.insertIgnore {
            it[patchFk] = patch.id
            it[packageFk] = pkg.id
        }
    }
}
