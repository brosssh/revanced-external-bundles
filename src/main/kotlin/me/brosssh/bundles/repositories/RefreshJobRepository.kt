package me.brosssh.bundles.repositories

import me.brosssh.bundles.db.entities.RefreshJobEntity
import me.brosssh.bundles.db.tables.RefreshJobTable
import me.brosssh.bundles.domain.models.RefreshJob
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.time.OffsetDateTime
import java.time.ZoneOffset


class RefreshJobRepository {

    fun create(jobId: String, jobType: RefreshJob.RefreshJobType) = transaction {
        RefreshJobEntity.new {
            this.jobId = jobId
            this.jobType = jobType
            this.status = RefreshJob.RefreshJobStatus.STARTED
            this.error = null
            this.startedAt = OffsetDateTime.now(ZoneOffset.UTC)
            this.completedAt = null
        }
    }

    fun findByJobId(jobId: String) = transaction {
        RefreshJobTable
            .selectAll()
            .where { RefreshJobTable.jobId eq jobId }
            .limit(1)
            .map(::rowToDomain)
            .singleOrNull()
    }

    private fun rowToDomain(row: ResultRow) =
        RefreshJob(
            row[RefreshJobTable.jobId],
            row[RefreshJobTable.jobType],
            row[RefreshJobTable.status],
            row[RefreshJobTable.startedAt],
            row[RefreshJobTable.completedAt],
            row[RefreshJobTable.error],
        )
}
