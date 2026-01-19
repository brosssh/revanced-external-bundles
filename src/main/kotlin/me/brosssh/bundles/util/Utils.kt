package me.brosssh.bundles.util

import org.jetbrains.exposed.v1.dao.IntEntity
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

fun hmacSha256Hex(secret: String, data: String): String {
    val hmac = Mac.getInstance("HmacSHA256")
    val keySpec = SecretKeySpec(secret.toByteArray(), "HmacSHA256")
    hmac.init(keySpec)
    return hmac.doFinal(data.toByteArray()).joinToString("") { "%02x".format(it) }
}

val IntEntity.intId: Int
    get() = this.id.value
