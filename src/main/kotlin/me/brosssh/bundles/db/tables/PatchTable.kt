package me.brosssh.bundles.db.tables

import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.core.dao.id.IntIdTable

object PatchTable : IntIdTable("patch") {
    val bundleFk = reference("bundle_fk", BundleTable, onDelete = ReferenceOption.CASCADE)
    val name = varchar("name", 255).nullable()
    val description = text("description").nullable()
}
