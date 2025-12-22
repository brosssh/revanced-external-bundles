package me.brosssh.bundles.db.tables

import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.core.Table

object PatchPackageTable : Table("patch_package") {
    val patchFk = reference("patch_fk", PatchTable, onDelete = ReferenceOption.CASCADE)
    val packageFk = reference("package_fk", PackageTable, onDelete = ReferenceOption.CASCADE)

    override val primaryKey = PrimaryKey(patchFk, packageFk)
}
