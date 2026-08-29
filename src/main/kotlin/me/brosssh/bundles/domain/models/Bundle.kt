package me.brosssh.bundles.domain.models

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.expectSuccess
import io.ktor.client.request.get
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import me.brosssh.bundles.workers.PatchExtractionResult
import me.brosssh.bundles.workers.PatchWorkerManager

sealed class Bundle(
    val version: String,
    val description: String?,
    val createdAt: String,
    val downloadUrl: String,
    val signatureDownloadUrl: String?,
    val sourceFk: Int
) : KoinComponent {
    protected val httpClient: HttpClient by inject()
    private val patchWorkerManager: PatchWorkerManager by inject()

    private suspend fun downloadBundleBytes() =
        httpClient.get(downloadUrl) {
            expectSuccess = true
        }.body<ByteArray>()

    private var _patchExtraction: PatchExtractionResult? = null

    suspend fun patches(cachedRuntime: String? = null): PatchExtractionResult {
        _patchExtraction?.let { return it }

        val extraction = patchWorkerManager.loadPatches(
            bundleType = bundleType,
            bundleBytes = downloadBundleBytes(),
            cachedRuntime = cachedRuntime
        )
        _patchExtraction = extraction
        return extraction
    }

    abstract val bundleType: BundleType

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
                type = BundleType.from(type),
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
}

data class BundleMetadata(
    val bundle: Bundle,
    val isPrerelease: Boolean,
    val fileHash: String?
)

sealed class BundleImportError : Exception() {
    class ReleaseFileNotFoundError : BundleImportError()
}

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
