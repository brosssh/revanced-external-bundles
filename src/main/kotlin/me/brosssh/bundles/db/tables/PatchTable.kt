package me.brosssh.bundles.db.tables

import org.jetbrains.exposed.v1.core.dao.id.IntIdTable

object PatchTable : IntIdTable("patch") {
    val bundleFk = reference("bundle_fk", BundleTable.id)
    val name = varchar("name", 255).nullable()
    val description = text("description").nullable()
    val compatiblePackageFk = array<Int>("compatible_package_fk").nullable()
}
