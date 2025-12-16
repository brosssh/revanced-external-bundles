package me.brosssh.bundles.db.tables

import org.jetbrains.exposed.dao.id.IntIdTable

object RefreshJobs : IntIdTable("refresh_jobs") {
    val jobId = varchar("job_id", 36).uniqueIndex()
    val status = enumerationByName("status", 20, JobStatus::class)
    val resultJson = text("result_json").nullable()
    val createdAt = long("created_at")
    val updatedAt = long("updated_at")
}

enum class JobStatus {
    PENDING,
    COMPLETED,
    FAILED
}
