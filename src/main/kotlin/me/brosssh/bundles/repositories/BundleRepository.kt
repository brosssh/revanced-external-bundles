package me.brosssh.bundles.repositories

import me.brosssh.bundles.db.tables.BundleTable
import me.brosssh.bundles.db.tables.SourceMetadataTable
import me.brosssh.bundles.db.tables.SourceTable
import me.brosssh.bundles.domain.models.Bundle
import me.brosssh.bundles.domain.models.BundleMetadata
import me.brosssh.bundles.domain.models.BundleType
import me.brosssh.bundles.domain.models.ReleaseChannel
import org.jetbrains.exposed.v1.core.*
import org.jetbrains.exposed.v1.core.statements.UpdateBuilder
import org.jetbrains.exposed.v1.datetime.timestampWithTimeZoneParam
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import org.jetbrains.exposed.v1.jdbc.upsert
import java.time.OffsetDateTime

data class BundlePatchCandidate(
    val id: Int,
    val bundle: Bundle,
    val patcherRuntime: String?,
    val fileHash: String?
)

private val NEVER_ATTEMPTED_PATCH_REFRESH: OffsetDateTime =
    OffsetDateTime.parse("1970-01-01T00:00:00Z")

class BundleRepository {
    fun findById(bundleId: Int) = transaction {
        (BundleTable innerJoin SourceTable)
            .selectAll()
            .where {
                (BundleTable.id eq bundleId) and enabledSourceFilter
            }
            .limit(1)
            .map(::rowToDomain)
            .singleOrNull()
    }

    fun upsert(bundleMetadata: BundleMetadata) = transaction {
        val hashChanged = bundleMetadata.fileHash?.let { hash ->
            BundleTable.fileHash.isNull() or (BundleTable.fileHash neq hash)
        } ?: BundleTable.fileHash.isNotNull()
        val artifactChanged =
            hashChanged or
                (BundleTable.downloadUrl neq bundleMetadata.bundle.downloadUrl) or
                (BundleTable.bundleType neq bundleMetadata.bundle.bundleType.value)
        val pendingAfterUpdate =
            BundleTable.needPatchesUpdate or artifactChanged

        val commonFields: (UpdateBuilder<*>) -> Unit = {
            it[BundleTable.version] = bundleMetadata.bundle.version
            it[BundleTable.description] = bundleMetadata.bundle.description
            it[BundleTable.createdAt] = bundleMetadata.bundle.createdAt
            it[BundleTable.downloadUrl] = bundleMetadata.bundle.downloadUrl
            it[BundleTable.signatureDownloadUrl] = bundleMetadata.bundle.signatureDownloadUrl
            it[BundleTable.fileHash] = bundleMetadata.fileHash
            it[BundleTable.bundleType] = bundleMetadata.bundle.bundleType.value
        }

        BundleTable.upsert(
            BundleTable.sourceFk,
            BundleTable.isPrerelease,
            BundleTable.version,
            onUpdate = {
                // Any provable artifact identity change invalidates the stored patch rows,
                // including bundles whose provider did not previously supply a digest.
                it[BundleTable.needPatchesUpdate] = pendingAfterUpdate
                it[BundleTable.patchRefreshAttemptedAt] = Case()
                    .When(
                        artifactChanged and pendingAfterUpdate,
                        timestampWithTimeZoneParam(NEVER_ATTEMPTED_PATCH_REFRESH)
                    )
                    .Else(BundleTable.patchRefreshAttemptedAt)

                commonFields(it)
            }
        ) { bundle ->
            bundle[sourceFk] = bundleMetadata.bundle.sourceFk
            bundle[isPrerelease] = bundleMetadata.isPrerelease
            // Queue every newly imported bundle for patch extraction; isolated workers support
            // all recognized bundle types, including legacy ReVanced V3.
            bundle[needPatchesUpdate] = true

            commonFields(bundle)
        }
    }

    fun getBundlesNeedPatchesUpdate(
        latestOnly: Boolean? = null,
        limit: Int? = null
    ) = transaction {
        require(limit == null || limit >= 0) { "Patch refresh limit cannot be negative" }

        val query = (BundleTable innerJoin SourceTable)
            .selectAll()
            .where {
                var filter =
                    (BundleTable.needPatchesUpdate eq true) and
                        enabledSourceFilter and
                        availableSourceFilter
                latestOnly?.let { filter = filter and (BundleTable.isLatest eq it) }
                filter
            }

        if (latestOnly == false) {
            // Rotate retrying historical failures behind bundles that have not been attempted
            // recently, so a bounded batch cannot starve the older backlog indefinitely.
            query.orderBy(
                BundleTable.patchRefreshAttemptedAt to SortOrder.ASC,
                BundleTable.createdAt to SortOrder.DESC
            )
        } else {
            // Populate visible releases before the backlog when querying the combined queue.
            query.orderBy(
                BundleTable.isLatest to SortOrder.DESC,
                BundleTable.createdAt to SortOrder.DESC
            )
        }

        limit?.let(query::limit)

        query.map { row ->
            BundlePatchCandidate(
                id = row[BundleTable.id].value,
                bundle = rowToDomain(row),
                patcherRuntime = row[BundleTable.patcherRuntime],
                fileHash = row[BundleTable.fileHash]
            )
        }
    }

    fun getLatestBundlesNeedPatchesUpdate() =
        getBundlesNeedPatchesUpdate(latestOnly = true)

    fun getHistoricalBundlesNeedPatchesUpdate(limit: Int) =
        getBundlesNeedPatchesUpdate(latestOnly = false, limit = limit)

    fun markPatcherTerminalFailure(
        bundleId: Int,
        expectedBundleType: BundleType,
        runtimeFingerprint: String,
        failure: String,
        expectedFileHash: String?,
        expectedDownloadUrl: String
    ) = transaction {
        val hashMatches = expectedFileHash?.let { hash ->
            BundleTable.fileHash eq hash
        } ?: BundleTable.fileHash.isNull()
        BundleTable.update({
            (BundleTable.id eq bundleId) and
                (BundleTable.bundleType eq expectedBundleType.value) and
                (BundleTable.needPatchesUpdate eq true) and
                hashMatches and
                (BundleTable.downloadUrl eq expectedDownloadUrl)
        }) {
            it[BundleTable.needPatchesUpdate] = false
            it[BundleTable.patcherFailure] = failure
            it[BundleTable.patcherFailureFingerprint] = runtimeFingerprint
        } == 1
    }

    fun requeuePatcherRuntimeFailures(
        bundleType: BundleType,
        runtimeFingerprint: String
    ) = transaction {
        BundleTable.update({
            (BundleTable.bundleType eq bundleType.value) and
                (BundleTable.needPatchesUpdate eq false) and
                BundleTable.patcherFailureFingerprint.isNotNull() and
                (BundleTable.patcherFailureFingerprint neq runtimeFingerprint) and
                (BundleTable.sourceFk inSubQuery SourceTable
                    .select(SourceTable.id)
                    .where { availableSourceFilter })
        }) {
            it[BundleTable.needPatchesUpdate] = true
            it[BundleTable.patchRefreshAttemptedAt] = NEVER_ATTEMPTED_PATCH_REFRESH
        }
    }

    fun findLatestByRepo(owner: String, repo: String, prerelease: Boolean) = transaction {
        (BundleTable innerJoin SourceTable innerJoin SourceMetadataTable)
            .selectAll()
            .where {
                enabledSourceFilter and
                        (SourceMetadataTable.ownerName eq owner) and
                        (SourceMetadataTable.repoName eq repo) and
                        (BundleTable.isPrerelease eq prerelease) and
                        (BundleTable.isLatest eq true)
            }
            .limit(1)
            .map(::rowToDomain)
            .singleOrNull()
    }

    fun findByRepoAndVersion(owner: String, repo: String, version: String) = transaction {
        (BundleTable innerJoin SourceTable innerJoin SourceMetadataTable)
            .selectAll()
            .where {
                enabledSourceFilter and
                        (SourceMetadataTable.ownerName eq owner) and
                        (SourceMetadataTable.repoName eq repo) and
                        (BundleTable.version eq version)
            }
            .limit(1)
            .map(::rowToDomain)
            .singleOrNull()
    }

    fun findByRepoAndChannel(owner: String, repo: String, channel: ReleaseChannel) = transaction {
        (BundleTable innerJoin SourceTable innerJoin SourceMetadataTable)
            .selectAll()
            .where {
                enabledSourceFilter and
                        (SourceMetadataTable.ownerName eq owner) and
                        (SourceMetadataTable.repoName eq repo) and
                        (BundleTable.isLatest eq true) and
                        channel.releaseFilter
            }
            .orderBy(BundleTable.createdAt, SortOrder.DESC)
            .limit(1)
            .map(::rowToDomain)
            .singleOrNull()
    }

    fun findBySourceAndChannel(sourceUrl: String, channel: ReleaseChannel) = transaction {
        (BundleTable innerJoin SourceTable)
            .selectAll()
            .where {
                sourceUrlFilter(sourceUrl) and
                        (BundleTable.isLatest eq true) and
                        channel.releaseFilter
            }
            .orderBy(BundleTable.createdAt, SortOrder.DESC)
            .limit(1)
            .map(::rowToDomain)
            .singleOrNull()
    }

    fun findBySourceAndVersion(
        sourceUrl: String,
        version: String,
        channel: ReleaseChannel
    ) = transaction {
        (BundleTable innerJoin SourceTable)
            .selectAll()
            .where {
                sourceUrlFilter(sourceUrl) and
                        (BundleTable.version eq version) and
                        channel.releaseFilter
            }
            .orderBy(BundleTable.createdAt, SortOrder.DESC)
            .limit(1)
            .map(::rowToDomain)
            .singleOrNull()
    }

    // v1 and v2 expose only bundles from enabled sources; explicit v3 lookups do not use this filter.
    private val enabledSourceFilter: Op<Boolean>
        get() = SourceTable.enabled eq true

    // Temporarily unavailable sources remain served but do not enter the patch extraction queue.
    private val availableSourceFilter: Op<Boolean>
        get() = SourceTable.unavailableReason.isNull()

    private fun sourceUrlFilter(sourceUrl: String) =
        (SourceTable.url eq sourceUrl) or (SourceTable.url eq "$sourceUrl/")

    private fun rowToDomain(row: ResultRow) =
        Bundle.create(
            row[BundleTable.bundleType],
            row[BundleTable.version],
            row[BundleTable.description],
            row[BundleTable.createdAt],
            row[BundleTable.downloadUrl],
            row[BundleTable.signatureDownloadUrl],
            row[BundleTable.sourceFk].value
        )
}
