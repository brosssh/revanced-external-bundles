package me.brosssh.bundles.db.tables

import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.datetime.timestampWithTimeZone

object GitHostRateLimitTable : Table("git_host_rate_limit") {
    val authority = varchar("authority", 255)
    val credentialFingerprint = varchar("credential_fingerprint", 64)
    val rateLimitedUntil = timestampWithTimeZone("rate_limited_until")

    override val primaryKey = PrimaryKey(authority, credentialFingerprint)
}
