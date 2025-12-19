package me.brosssh.bundles.domain.services.refresh

import app.revanced.patcher.patch.PatchBundle
import me.brosssh.bundles.db.entities.SourceEntity
import me.brosssh.bundles.integrations.github.GithubClient
import me.brosssh.bundles.repositories.*
import me.brosssh.bundles.util.intId
import me.brosssh.bundles.util.sha256
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.File

class RefreshBundlesService(
    refreshJobRepository: RefreshJobRepository,
    private val githubClient: GithubClient,
    private val sourceRepository: SourceRepository,
    private val bundleRepository: BundleRepository,
    private val patchRepository: PatchRepository,
    private val packageRepository: PackageRepository
) : BaseRefreshJobService(refreshJobRepository) {

    override val logger: Logger = LoggerFactory.getLogger(RefreshBundlesService::class.java)
    override val jobType: String = "BUNDLES"

    override suspend fun processRefresh(jobId: String) {
        val processDir = File(System.getProperty("java.io.tmpdir"), "bundles").apply { mkdirs() }

        sourceRepository.getAll().forEach { source ->
            val (owner, repo) = githubClient.parseRepoUrl(source.url)
            logger.info("Processing refresh for url ${source.url}")

            RELEASE_TYPES.forEach { isPrerelease ->
                val context = RefreshContext(
                    jobId = jobId,
                    processDir = processDir,
                    owner = owner,
                    repo = repo,
                    isPrerelease = isPrerelease
                )

                processRelease(
                    context,
                    source
                )
            }
        }
    }

    private suspend fun processRelease(context: RefreshContext, source: SourceEntity) {
        val releaseDto = githubClient.getRelease(context.owner, context.repo, context.isPrerelease)
            ?: error("No release found for owner=${context.owner}, repo=${context.repo}, prerelease=${context.isPrerelease}")

        val bundleEntity = bundleRepository.upsert(releaseDto, source, context.isPrerelease)

        val bundleFile = File(context.processDir, context.jobId).apply {
            githubClient.downloadFile(bundleEntity.downloadUrl, this)
            logger.info("Downloaded RVP: $absolutePath")
        }

        val bundleFileSha = bundleFile.sha256()
        if (bundleEntity.fileSha == bundleFileSha) {
            logger.info("Bundle did not change, skipping")
            return
        }

        logger.info("Bundle has changed, reprocessing patches")
        bundleEntity.fileSha = bundleFileSha

        // Remove old patches before creating new ones
        patchRepository.deleteByBundle(bundleEntity)

        PatchBundle(bundleFile.absolutePath).patches?.forEach { patch ->
            val compatiblePackageIds = buildList {
                patch.compatiblePackages?.forEach { (packageName, versions) ->
                    if (versions == null) {
                        add(packageRepository.findOrCreate(packageName, null).intId)
                    } else {
                        versions.forEach { version ->
                            add(packageRepository.findOrCreate(packageName, version).intId)
                        }
                    }
                }
            }

            patchRepository.create(
                bundleEntity = bundleEntity,
                name = patch.name,
                description = patch.description,
                compatiblePackageIds = compatiblePackageIds
            )
        }

        logger.info("Success")
    }

    companion object {
        private val RELEASE_TYPES = setOf(false, true)
    }

    data class RefreshContext(
        val jobId: String,
        val processDir: File,
        val owner: String,
        val repo: String,
        val isPrerelease: Boolean
    )

}
