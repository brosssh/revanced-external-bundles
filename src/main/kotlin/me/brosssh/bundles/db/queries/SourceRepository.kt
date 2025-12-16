package me.brosssh.bundles.db.queries

import kotlinx.serialization.Serializable
import me.brosssh.bundles.db.tables.Source
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction

@Serializable
data class SourceDto(
    val id: Int,
    val url: String
)

class SourceRepository {

    fun getAll(): List<SourceDto> =
        transaction {
            Source.selectAll().map {
                SourceDto(
                    id = it[Source.id].value,
                    url = it[Source.url]
                )
            }
        }
}
