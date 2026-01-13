package me.brosssh.bundles.db.entities

import me.brosssh.bundles.db.tables.RefreshJobTable
import me.brosssh.bundles.domain.models.RefreshJob
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.dao.IntEntity
import org.jetbrains.exposed.v1.dao.IntEntityClass
import java.time.OffsetDateTime
import java.time.ZoneOffset

class RefreshJobEntity(id: EntityID<Int>) : IntEntity(id) {
    companion object : IntEntityClass<RefreshJobEntity>(RefreshJobTable)

    var jobId by RefreshJobTable.jobId
    var jobType by RefreshJobTable.jobType
    var status by RefreshJobTable.status
    var error by RefreshJobTable.error
    var startedAt by RefreshJobTable.startedAt
    var completedAt by RefreshJobTable.completedAt

    fun setCompleted() {
        status = RefreshJob.RefreshJobStatus.COMPLETED
        completedAt = OffsetDateTime.now(ZoneOffset.UTC)
    }

    fun setFailed(e: String) {
        status = RefreshJob.RefreshJobStatus.FAILED
        completedAt = OffsetDateTime.now(ZoneOffset.UTC)
        error = e
    }

}
