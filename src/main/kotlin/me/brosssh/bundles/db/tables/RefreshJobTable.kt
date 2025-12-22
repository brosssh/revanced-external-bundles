package me.brosssh.bundles.db.tables

import org.jetbrains.exposed.v1.core.dao.id.IntIdTable


object RefreshJobTable : IntIdTable("refresh_jobs") {
    val jobId = varchar("job_id", 36).uniqueIndex()
    val jobType = varchar("job_type", 31)
    val status = enumerationByName("status", 20, JobStatus::class)
    val error = text("error").nullable()
    val createdAt = long("created_at")
    val updatedAt = long("updated_at")

    enum class JobStatus {
        PENDING,
        COMPLETED,
        FAILED
    }
}
