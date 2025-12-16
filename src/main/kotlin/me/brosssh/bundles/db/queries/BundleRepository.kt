package me.brosssh.bundles.db.queries

import kotlinx.serialization.Serializable
import me.brosssh.bundles.db.tables.Bundles
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction

@Serializable
data class BundleDto(
    val id: Int,
    val name: String
)

class BundleRepository {

    fun getAll(): List<BundleDto> =
        transaction {
            Bundles.selectAll().map {
                BundleDto(
                    id = it[Bundles.id].value,
                    name = it[Bundles.name]
                )
            }
        }
}
