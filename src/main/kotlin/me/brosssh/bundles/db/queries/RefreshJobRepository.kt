package me.brosssh.bundles.db.queries

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import me.brosssh.bundles.db.tables.JobStatus
import me.brosssh.bundles.db.tables.RefreshJobs
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction

@Serializable
data class RefreshJob(
    val jobId: String,
    val status: JobStatus,
    val result: List<BundleDto>? = null
)

class RefreshJobRepository {

    fun create(jobId: String) {
        transaction {
            RefreshJobs.insert {
                it[RefreshJobs.jobId] = jobId
                it[status] = JobStatus.PENDING
                it[resultJson] = null
                it[createdAt] = System.currentTimeMillis()
                it[updatedAt] = System.currentTimeMillis()
            }
        }
    }

    fun complete(jobId: String) {
        transaction {
            RefreshJobs.update({ RefreshJobs.jobId eq jobId }) {
                it[status] = JobStatus.COMPLETED
                it[updatedAt] = System.currentTimeMillis()
            }
        }
    }

    fun find(jobId: String): RefreshJob? = transaction {
        RefreshJobs.select { RefreshJobs.jobId eq jobId }
            .map {
                RefreshJob(
                    jobId = it[RefreshJobs.jobId],
                    status = it[RefreshJobs.status],
                    result = it[RefreshJobs.resultJson]?.let { json -> 
                        Json.decodeFromString<List<BundleDto>>(json)
                    }
                )
            }.singleOrNull()
    }
}
