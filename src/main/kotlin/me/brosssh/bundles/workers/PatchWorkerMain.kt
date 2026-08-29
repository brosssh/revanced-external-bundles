package me.brosssh.bundles.workers

import me.brosssh.bundles.workers.loaders.PatchLoaderRegistry
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.FileDescriptor
import java.io.FileOutputStream
import java.io.PrintStream
import java.nio.charset.StandardCharsets
import java.nio.file.Files

fun main(args: Array<String>) {
    require(args.size == 1) { "Patch worker requires exactly one adapter argument" }
    val adapter = args.single()

    // Reserve the original stdout descriptor exclusively for framed IPC. Any println calls made by
    // patch bundles or patcher libraries are redirected to stderr so they cannot corrupt responses.
    val protocolOutput = DataOutputStream(BufferedOutputStream(FileOutputStream(FileDescriptor.out)))
    System.setOut(PrintStream(FileOutputStream(FileDescriptor.err), true, StandardCharsets.UTF_8))
    val protocolInput = DataInputStream(BufferedInputStream(System.`in`))

    System.err.println("Patch worker started with adapter $adapter")
    while (true) {
        val request = PatchWorkerProtocol.readRequest(protocolInput) ?: break
        // The worker owns the temporary path; upstream asset names never cross the IPC boundary.
        val bundleFile = try {
            Files.createTempFile("patch-worker-", ".jar").toFile()
        } catch (error: Exception) {
            error.printStackTrace(System.err)
            PatchWorkerProtocol.writeWorkerFailure(protocolOutput, request.id, error)
            continue
        }

        try {
            try {
                bundleFile.writeBytes(request.bundleBytes)
            } catch (error: Exception) {
                error.printStackTrace(System.err)
                PatchWorkerProtocol.writeWorkerFailure(protocolOutput, request.id, error)
                continue
            }

            val patches = try {
                PatchLoaderRegistry.load(adapter, bundleFile)
            } catch (error: Exception) {
                error.printStackTrace(System.err)
                PatchWorkerProtocol.writeBundleRejected(protocolOutput, request.id, error)
                continue
            } catch (error: LinkageError) {
                // Missing or binary-incompatible classes are deterministic for this runtime/bundle pair.
                error.printStackTrace(System.err)
                PatchWorkerProtocol.writeBundleRejected(protocolOutput, request.id, error)
                continue
            }

            System.err.println(
                "Patch worker loaded ${patches.size} patches from ${bundleFile.name} " +
                    "(${bundleFile.length()} bytes)"
            )
            // A response-write failure leaves a partial frame, so let the worker exit instead of
            // attempting another response on a desynchronized transport.
            PatchWorkerProtocol.writeSuccess(protocolOutput, request.id, patches)
        } finally {
            runCatching { bundleFile.delete() }
        }
    }
}
