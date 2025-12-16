package me.brosssh.bundles.util

import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest

fun File.sha256(): String {
    val digest = MessageDigest.getInstance("SHA-256")
    FileInputStream(this).use { fis ->
        val buffer = ByteArray(8 * 1024) // 8 KB buffer
        var bytesRead: Int
        while (fis.read(buffer).also { bytesRead = it } != -1) {
            digest.update(buffer, 0, bytesRead)
        }
    }
    return digest.digest().joinToString("") { "%02x".format(it) }
}
