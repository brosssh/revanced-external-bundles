package me.brosssh.bundles.db.entities

import me.brosssh.bundles.db.tables.RefreshJobTable
import me.brosssh.bundles.db.tables.RefreshJobTable.JobStatus
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.dao.IntEntity
import org.jetbrains.exposed.v1.dao.IntEntityClass

class RefreshJobEntity(id: EntityID<Int>) : IntEntity(id) {
    companion object : IntEntityClass<RefreshJobEntity>(RefreshJobTable)

    var jobId by RefreshJobTable.jobId
    var jobType by RefreshJobTable.jobType
    var status by RefreshJobTable.status
    var error by RefreshJobTable.error
    var createdAt by RefreshJobTable.createdAt
    var updatedAt by RefreshJobTable.updatedAt

    fun setCompleted() {
        status = JobStatus.COMPLETED
        updatedAt = System.currentTimeMillis()
    }

    fun setFailed(e: String) {
        status = JobStatus.FAILED
        updatedAt = System.currentTimeMillis()
        error = e
    }

}
