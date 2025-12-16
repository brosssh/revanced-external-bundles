package me.brosssh.bundles.util

import org.jetbrains.exposed.v1.dao.IntEntity
import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

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

fun hmacSha256Hex(secret: String, data: String): String {
    val hmac = Mac.getInstance("HmacSHA256")
    val keySpec = SecretKeySpec(secret.toByteArray(), "HmacSHA256")
    hmac.init(keySpec)
    return hmac.doFinal(data.toByteArray()).joinToString("") { "%02x".format(it) }
}

val IntEntity.intId: Int
    get() = this.id.value
