package me.brosssh.bundles.db.tables

import me.brosssh.bundles.domain.models.RefreshJob
import org.jetbrains.exposed.v1.core.dao.id.IntIdTable
import org.jetbrains.exposed.v1.datetime.timestampWithTimeZone


object RefreshJobTable : IntIdTable("refresh_jobs") {
    val jobId = varchar("job_id", 36).uniqueIndex()
    val jobType = enumerationByName("job_type", 31, RefreshJob.RefreshJobType::class)
    val status = enumerationByName("status", 31, RefreshJob.RefreshJobStatus::class)
    val error = text("error").nullable()
    val startedAt = timestampWithTimeZone("started_at")
    val completedAt = timestampWithTimeZone("completed_at").nullable()
}
