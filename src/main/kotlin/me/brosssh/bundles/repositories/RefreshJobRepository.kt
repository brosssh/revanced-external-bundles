package me.brosssh.bundles.repositories

import me.brosssh.bundles.db.entities.RefreshJobEntity
import me.brosssh.bundles.db.tables.RefreshJobTable
import org.jetbrains.exposed.v1.jdbc.transactions.transaction


class RefreshJobRepository {

    fun create(jobId: String, jobType: String) = transaction {
        RefreshJobEntity.new {
            this.jobId = jobId
            this.jobType = jobType
            this.status = RefreshJobTable.JobStatus.PENDING
            this.error = null
            val now = System.currentTimeMillis()
            this.createdAt = now
            this.updatedAt = now
        }
    }
}
