package me.brosssh.bundles.workers

import me.brosssh.bundles.domain.models.CompatiblePackage
import me.brosssh.bundles.domain.models.Patch
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.EOFException
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class PatchWorkerProtocolTest {
    @Test
    fun `round trips bundle request bytes`() {
        val bytes = byteArrayOf(0, 1, 2, 127, -1)
        val encoded = ByteArrayOutputStream().also { output ->
            PatchWorkerProtocol.writeRequest(DataOutputStream(output), 42, bytes)
        }.toByteArray()

        val request = PatchWorkerProtocol.readRequest(DataInputStream(ByteArrayInputStream(encoded)))
        requireNotNull(request)
        assertEquals(42, request.id)
        assertContentEquals(bytes, request.bundleBytes)
    }

    @Test
    fun `round trips patch snapshots`() {
        val patches = setOf(
            Patch(
                name = "Hide ads",
                description = "Removes ads",
                compatiblePackages = setOf(
                    CompatiblePackage("com.example", setOf("1.0", "1.1")),
                    CompatiblePackage("com.other", null)
                )
            ),
            Patch(name = null, description = null, compatiblePackages = null)
        )
        val encoded = ByteArrayOutputStream().also { output ->
            PatchWorkerProtocol.writeSuccess(DataOutputStream(output), 7, patches)
        }.toByteArray()

        val decoded = PatchWorkerProtocol.readResponse(
            DataInputStream(ByteArrayInputStream(encoded)),
            expectedRequestId = 7
        )
        assertEquals(patches, decoded)
    }

    @Test
    fun `propagates bundle rejection separately from worker failure`() {
        val rejected = ByteArrayOutputStream().also { output ->
            PatchWorkerProtocol.writeBundleRejected(
                DataOutputStream(output),
                9,
                NoClassDefFoundError("missing.Type")
            )
        }.toByteArray()
        val failed = ByteArrayOutputStream().also { output ->
            PatchWorkerProtocol.writeWorkerFailure(
                DataOutputStream(output),
                10,
                IllegalStateException("disk unavailable")
            )
        }.toByteArray()

        val rejection = assertFailsWith<PatchBundleRejectedException> {
            PatchWorkerProtocol.readResponse(
                DataInputStream(ByteArrayInputStream(rejected)),
                expectedRequestId = 9
            )
        }
        assertContains(rejection.message.orEmpty(), "missing.Type")

        val workerFailure = assertFailsWith<PatchWorkerFailureException> {
            PatchWorkerProtocol.readResponse(
                DataInputStream(ByteArrayInputStream(failed)),
                expectedRequestId = 10
            )
        }
        assertContains(workerFailure.message.orEmpty(), "disk unavailable")
    }

    @Test
    fun `rejects a response for another request`() {
        val encoded = ByteArrayOutputStream().also { output ->
            PatchWorkerProtocol.writeSuccess(DataOutputStream(output), 7, emptySet())
        }.toByteArray()

        assertFailsWith<IllegalArgumentException> {
            PatchWorkerProtocol.readResponse(
                DataInputStream(ByteArrayInputStream(encoded)),
                expectedRequestId = 8
            )
        }
    }

    @Test
    fun `rejects a truncated request payload`() {
        val encoded = ByteArrayOutputStream().also { output ->
            PatchWorkerProtocol.writeRequest(DataOutputStream(output), 42, byteArrayOf(1, 2, 3))
        }.toByteArray()

        assertFailsWith<EOFException> {
            PatchWorkerProtocol.readRequest(
                DataInputStream(ByteArrayInputStream(encoded.copyOf(encoded.size - 1)))
            )
        }
    }
}
