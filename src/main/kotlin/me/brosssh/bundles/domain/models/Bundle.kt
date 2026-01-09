package me.brosssh.bundles.domain.models

import app.revanced.patcher.patch.loadPatchesFromJar as loadReVancedBundle
import app.morphe.patcher.patch.loadPatchesFromJar as loadMorpheBundle
import me.brosssh.bundles.api.dto.BundleResponseDto
import me.brosssh.bundles.integrations.github.GithubClient
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.io.File
import java.util.UUID
import kotlin.getValue

sealed class Bundle(
    val version: String,
    val description: String?,
    val createdAt: String,
    val downloadUrl: String,
    val signatureDownloadUrl: String?,
    val sourceFk: Int
) : KoinComponent {
    protected val githubClient: GithubClient by inject()
    protected val processDir =
        File(System.getProperty("java.io.tmpdir"))
            .resolve("bundles")
            .apply { mkdirs() }

    protected suspend fun downloadBundleFile() =
        File(processDir, UUID.randomUUID().toString()).apply {
            githubClient.downloadFile(downloadUrl, this)
        }

    private var _patches: Set<Patch>? = null

    suspend fun patches(): Set<Patch> {
        _patches?.let { return it }

        with(downloadBundleFile()) {
            val loaded = try {
                loadPatchesFromBundle(this)
            } finally {
                runCatching { delete() }
            }
            _patches = loaded
            return loaded
        }
    }

    abstract val bundleType: BundleType
    protected abstract suspend fun loadPatchesFromBundle(bundleFile: File): Set<Patch>

    companion object {
        fun create(
            type: BundleType,
            version: String,
            description: String?,
            createdAt: String,
            downloadUrl: String,
            signatureDownloadUrl: String?,
            sourceFk: Int
        ): Bundle = when (type) {
            BundleType.REVANCED_V3 -> ReVancedV3Bundle(
                version, description, createdAt, downloadUrl,
                signatureDownloadUrl, sourceFk
            )

            BundleType.REVANCED_V4 -> ReVancedV4Bundle(
                version, description, createdAt, downloadUrl,
                signatureDownloadUrl, sourceFk
            )

            BundleType.MORPHE_V1 -> MorpheV1Bundle(
                version, description, createdAt, downloadUrl,
                signatureDownloadUrl, sourceFk
            )
        }

        fun create(
            type: String,
            version: String,
            description: String?,
            createdAt: String,
            downloadUrl: String,
            signatureDownloadUrl: String?,
            sourceFk: Int
        ): Bundle =
            create(
                type = type,
                version = version,
                description = description,
                createdAt = createdAt,
                downloadUrl = downloadUrl,
                signatureDownloadUrl = signatureDownloadUrl,
                sourceFk = sourceFk
            )
    }
}

class ReVancedV3Bundle(
    version: String,
    description: String?,
    createdAt: String,
    downloadUrl: String,
    signatureDownloadUrl: String?,
    sourceFk: Int
) : Bundle(
    version,
    description,
    createdAt,
    downloadUrl,
    signatureDownloadUrl,
    sourceFk
) {
    override val bundleType = BundleType.REVANCED_V3

    override suspend fun loadPatchesFromBundle(bundleFile: File) =
        emptySet<Patch>()
}

class ReVancedV4Bundle(
    version: String,
    description: String?,
    createdAt: String,
    downloadUrl: String,
    signatureDownloadUrl: String?,
    sourceFk: Int
) : Bundle(
    version,
    description,
    createdAt,
    downloadUrl,
    signatureDownloadUrl,
    sourceFk
) {
    override val bundleType = BundleType.REVANCED_V4

    override suspend fun loadPatchesFromBundle(bundleFile: File) =
        loadReVancedBundle(setOf(bundleFile))
            .map { ReVancedPatchAdapter(it) }
            .toSet()
}

class MorpheV1Bundle(
    version: String,
    description: String?,
    createdAt: String,
    downloadUrl: String,
    signatureDownloadUrl: String?,
    sourceFk: Int
) : Bundle(
    version,
    description,
    createdAt,
    downloadUrl,
    signatureDownloadUrl,
    sourceFk
) {
    override val bundleType = BundleType.MORPHE_V1

    override suspend fun loadPatchesFromBundle(bundleFile: File) =
        loadMorpheBundle(setOf(bundleFile))
            .map { MorphePatchAdapter(it) }
            .toSet()
}

data class BundleMetadata(
    val bundle: Bundle,
    val isPrerelease: Boolean,
    val fileHash: String?
)

sealed class BundleImportError : Exception() {
    class ReleaseFileNotFoundError : BundleImportError()
}

fun Bundle.toResponseDto() = BundleResponseDto(
    createdAt = createdAt.substringBefore("Z"),
    description = description ?: "",
    version = version,
    downloadUrl = downloadUrl,
    signatureDownloadUrl = signatureDownloadUrl ?: ""
)

enum class BundleType(val value: String) {
    REVANCED_V3("ReVanced:V3"),
    REVANCED_V4("ReVanced:V4"),
    MORPHE_V1("Morphe:V1");

    companion object {
        fun from(value: String) =
            entries.firstOrNull { it.value == value }
                ?: throw IllegalArgumentException("Unknown bundle type: $value")
    }
}
