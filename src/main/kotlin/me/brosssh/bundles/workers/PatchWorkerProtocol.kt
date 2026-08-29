package me.brosssh.bundles.workers

import me.brosssh.bundles.domain.models.CompatiblePackage
import me.brosssh.bundles.domain.models.Patch
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.EOFException
import java.nio.charset.StandardCharsets

internal object PatchWorkerProtocol {
    // ASCII "PTCH"
    private const val MAGIC = 0x50544348
    private const val VERSION = 1
    private const val SUCCESS = 0
    private const val BUNDLE_REJECTED = 1
    private const val WORKER_FAILURE = 2

    // Validate all peer-controlled lengths before allocation to bound malformed-frame memory use.
    private const val MAX_BUNDLE_SIZE = 256 * 1024 * 1024
    private const val MAX_COLLECTION_SIZE = 1_000_000
    private const val MAX_STRING_SIZE = 16 * 1024 * 1024

    class Request(val id: Long, val bundleBytes: ByteArray)

    fun writeRequest(output: DataOutputStream, id: Long, bundleBytes: ByteArray) {
        require(bundleBytes.size <= MAX_BUNDLE_SIZE) {
            "Bundle is too large for patch worker IPC: ${bundleBytes.size} bytes"
        }
        output.writeInt(MAGIC)
        output.writeByte(VERSION)
        output.writeLong(id)
        output.writeInt(bundleBytes.size)
        output.write(bundleBytes)
        output.flush()
    }

    fun readRequest(input: DataInputStream): Request? {
        val magic = try {
            input.readInt()
        } catch (_: EOFException) {
            return null
        }
        require(magic == MAGIC) { "Invalid patch worker request magic" }
        require(input.readUnsignedByte() == VERSION) { "Unsupported patch worker protocol version" }
        val id = input.readLong()
        val size = input.readBoundedSize(MAX_BUNDLE_SIZE, "bundle")
        return Request(id, input.readExactly(size))
    }

    fun writeSuccess(output: DataOutputStream, requestId: Long, patches: Set<Patch>) {
        output.writeResponseHeader(requestId, SUCCESS)
        output.writeInt(patches.size)
        patches.forEach { patch -> output.writePatch(patch) }
        output.flush()
    }

    fun writeBundleRejected(output: DataOutputStream, requestId: Long, error: Throwable) =
        writeError(output, requestId, BUNDLE_REJECTED, error)

    fun writeWorkerFailure(output: DataOutputStream, requestId: Long, error: Throwable) =
        writeError(output, requestId, WORKER_FAILURE, error)

    fun readResponse(input: DataInputStream, expectedRequestId: Long): Set<Patch> {
        require(input.readInt() == MAGIC) { "Invalid patch worker response magic" }
        require(input.readUnsignedByte() == VERSION) { "Unsupported patch worker protocol version" }
        val requestId = input.readLong()
        require(requestId == expectedRequestId) {
            "Patch worker response id $requestId does not match request $expectedRequestId"
        }
        return when (input.readUnsignedByte()) {
            SUCCESS -> buildSet {
                repeat(input.readBoundedSize(MAX_COLLECTION_SIZE, "patch count")) {
                    add(input.readPatch())
                }
            }

            BUNDLE_REJECTED -> throw PatchBundleRejectedException(input.readError("rejected the bundle"))
            WORKER_FAILURE -> throw PatchWorkerFailureException(input.readError("failed"))
            else -> error("Invalid patch worker response status")
        }
    }

    private fun writeError(
        output: DataOutputStream,
        requestId: Long,
        status: Int,
        error: Throwable
    ) {
        output.writeResponseHeader(requestId, status)
        output.writeNullableString(error.stackTraceToString().take(MAX_STRING_SIZE))
        output.flush()
    }

    private fun DataInputStream.readError(action: String): String =
        readNullableString() ?: "Patch worker $action without an error message"

    private fun DataOutputStream.writeResponseHeader(requestId: Long, status: Int) {
        writeInt(MAGIC)
        writeByte(VERSION)
        writeLong(requestId)
        writeByte(status)
    }

    private fun DataOutputStream.writePatch(patch: Patch) {
        writeNullableString(patch.name)
        writeNullableString(patch.description)
        val packages = patch.compatiblePackages
        if (packages == null) {
            writeInt(-1)
            return
        }
        writeInt(packages.size)
        packages.forEach { compatiblePackage ->
            writeNullableString(compatiblePackage.name)
            val versions = compatiblePackage.versions
            if (versions == null) {
                writeInt(-1)
            } else {
                writeInt(versions.size)
                versions.forEach { version -> writeNullableString(version) }
            }
        }
    }

    private fun DataInputStream.readPatch(): Patch {
        val name = readNullableString()
        val description = readNullableString()
        val packageCount = readNullableCollectionSize("compatible package count")
        val packages = packageCount?.let { count ->
            buildSet {
                repeat(count) {
                    val packageName = requireNotNull(readNullableString()) {
                        "Patch worker returned a null package name"
                    }
                    val versionCount = readNullableCollectionSize("compatible version count")
                    val versions = versionCount?.let { size ->
                        buildSet {
                            repeat(size) {
                                add(requireNotNull(readNullableString()) {
                                    "Patch worker returned a null compatible version"
                                })
                            }
                        }
                    }
                    add(CompatiblePackage(packageName, versions))
                }
            }
        }
        return Patch(name, description, packages)
    }

    private fun DataOutputStream.writeNullableString(value: String?) {
        if (value == null) {
            writeInt(-1)
            return
        }
        val bytes = value.toByteArray(StandardCharsets.UTF_8)
        require(bytes.size <= MAX_STRING_SIZE) { "IPC string is too large" }
        writeInt(bytes.size)
        write(bytes)
    }

    private fun DataInputStream.readNullableString(): String? {
        val size = readInt()
        if (size == -1) return null
        require(size in 0..MAX_STRING_SIZE) { "Invalid IPC string size $size" }
        return String(readExactly(size), StandardCharsets.UTF_8)
    }

    private fun DataInputStream.readNullableCollectionSize(label: String): Int? {
        val size = readInt()
        if (size == -1) return null
        require(size in 0..MAX_COLLECTION_SIZE) { "Invalid $label $size" }
        return size
    }

    private fun DataInputStream.readBoundedSize(maximum: Int, label: String): Int {
        val size = readInt()
        require(size in 0..maximum) { "Invalid $label size $size" }
        return size
    }

    private fun DataInputStream.readExactly(size: Int): ByteArray =
        readNBytes(size).also { bytes ->
            if (bytes.size != size) throw EOFException("Expected $size bytes, got ${bytes.size}")
        }
}

internal class PatchBundleRejectedException(message: String) : RuntimeException(message)

internal class PatchWorkerFailureException(message: String) : RuntimeException(message)
