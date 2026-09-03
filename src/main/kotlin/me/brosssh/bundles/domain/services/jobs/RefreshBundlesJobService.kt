package me.brosssh.bundles.domain.services.jobs

import io.ktor.client.plugins.ClientRequestException
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.CancellationException
import me.brosssh.bundles.db.entities.SourceEntity
import me.brosssh.bundles.db.functions.refreshIsLatestFlag
import me.brosssh.bundles.domain.models.BundleImportError
import me.brosssh.bundles.domain.models.RefreshJob
import me.brosssh.bundles.integrations.HostResolver
import me.brosssh.bundles.integrations.common.GitHostClient
import me.brosssh.bundles.integrations.common.GitHostCredentials
import me.brosssh.bundles.integrations.common.sourceUnavailableReason
import me.brosssh.bundles.integrations.common.toDomainModel
import me.brosssh.bundles.repositories.BundleRepository
import me.brosssh.bundles.repositories.GitHostRateLimitRepository
import me.brosssh.bundles.repositories.RefreshJobRepository
import me.brosssh.bundles.repositories.SourceMetadataRepository
import me.brosssh.bundles.repositories.SourceRepository
import me.brosssh.bundles.util.intId
import org.jetbrains.exposed.v1.jdbc.transactions.suspendTransaction
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.time.OffsetDateTime
import java.time.ZoneOffset

class RefreshBundlesJobService(
    refreshJobRepository: RefreshJobRepository,
    private val hostResolver: HostResolver,
    private val sourceRepository: SourceRepository,
    private val sourceMetadataRepository: SourceMetadataRepository,
    private val bundleRepository: BundleRepository,
    private val credentials: GitHostCredentials,
    private val rateLimitRepository: GitHostRateLimitRepository
) : BaseRefreshJobService(refreshJobRepository) {

    override val logger: Logger = LoggerFactory.getLogger(RefreshBundlesJobService::class.java)
    override val jobType = RefreshJob.RefreshJobType.BUNDLES

    override suspend fun processRefresh(jobId: String) {
        logger.info("Processing bundles refresh")

        sourceRepository.getEnabled().forEach { source ->
            logger.info("Processing source ${source.url}")
            try {
                processSource(source)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                // Transient transport, server, and configuration failures preserve the source state.
                logger.warn("Something went wrong while processing source ${source.url}", error)
            }
            logger.info("Source process completed")
        }

        refreshIsLatestFlag()
        logger.info("Process completed")
    }

    private suspend fun processSource(source: SourceEntity) {
        val resolved = hostResolver.resolve(source.url)
        val credentialFingerprint = credentials.fingerprintFor(resolved.authority)
        val now = OffsetDateTime.now(ZoneOffset.UTC)
        val limitedUntil = rateLimitRepository.activeUntil(
            resolved.authority,
            credentialFingerprint,
            now
        )

        if (limitedUntil != null) {
            logger.info(
                "Skipping source {} because git host {} is rate limited until {}",
                source.url,
                resolved.authority,
                limitedUntil
            )
            return
        }

        try {
            val metadata = resolved.client.getRepo(resolved.ref).toDomainModel(source.intId)
            val bundles = resolved.client.getReleases(resolved.ref)
                .mapNotNull { release ->
                    try {
                        release.toDomainModel(source.intId)
                    } catch (_: BundleImportError) {
                        logger.warn(
                            "No rvp/mpp/jar found for ${source.url}, version ${release.tagName}"
                        )
                        null
                    }
                }

            suspendTransaction {
                // Update metatable
                sourceMetadataRepository.upsert(metadata)

                // Update bundle table
                bundles.forEach(bundleRepository::upsert)

                // Only a complete repository-and-releases refresh proves that the source recovered.
                sourceRepository.setUnavailableReason(source.intId, null)
                rateLimitRepository.clear(resolved.authority, credentialFingerprint)
            }
        } catch (error: ClientRequestException) {
            handleClientFailure(
                sourceId = source.intId,
                sourceUrl = source.url,
                authority = resolved.authority,
                client = resolved.client,
                credentialFingerprint = credentialFingerprint,
                error = error
            )
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            logger.warn("Failed to process source {}", source.url, error)
        }
    }

    private suspend fun handleClientFailure(
        sourceId: Int,
        sourceUrl: String,
        authority: String,
        client: GitHostClient,
        credentialFingerprint: String,
        error: ClientRequestException
    ) {
        val status = error.response.status
        val unavailableReason = sourceUnavailableReason(status)

        if (unavailableReason != null) {
            sourceRepository.setUnavailableReason(sourceId, unavailableReason)
            logger.warn("Source $sourceUrl is unavailable: $unavailableReason")
            return
        }

        val now = OffsetDateTime.now(ZoneOffset.UTC)
        val limitedUntil = client.rateLimitDeadline(
            status,
            error.response.headers,
            now,
            error.response.bodyAsText()
        )

        if (limitedUntil != null) {
            rateLimitRepository.record(
                authority,
                credentialFingerprint,
                limitedUntil
            )
            logger.warn("Git host $authority is rate limited until $limitedUntil")
            return
        }

        logger.warn("Git host request failed for source $sourceUrl with status $status")
    }
}