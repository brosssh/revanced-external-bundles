package me.brosssh.bundles.repositories

import me.brosssh.bundles.db.tables.GitHostRateLimitTable
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import java.time.OffsetDateTime

class GitHostRateLimitRepository {
    fun activeUntil(
        authority: String,
        credentialFingerprint: String,
        now: OffsetDateTime
    ): OffsetDateTime? = transaction {
        GitHostRateLimitTable
            .selectAll()
            .where { keyMatches(authority, credentialFingerprint) }
            .limit(1)
            .singleOrNull()
            ?.get(GitHostRateLimitTable.rateLimitedUntil)
            ?.takeIf { it.isAfter(now) }
    }

    fun record(
        authority: String,
        credentialFingerprint: String,
        rateLimitedUntil: OffsetDateTime
    ) = transaction {
        val existing = GitHostRateLimitTable
            .selectAll()
            .where { keyMatches(authority, credentialFingerprint) }
            .forUpdate()
            .limit(1)
            .singleOrNull()

        if (existing == null) {
            GitHostRateLimitTable.insert {
                it[GitHostRateLimitTable.authority] = authority.lowercase()
                it[GitHostRateLimitTable.credentialFingerprint] = credentialFingerprint
                it[GitHostRateLimitTable.rateLimitedUntil] = rateLimitedUntil
            }
        } else if (rateLimitedUntil.isAfter(existing[GitHostRateLimitTable.rateLimitedUntil])) {
            GitHostRateLimitTable.update({ keyMatches(authority, credentialFingerprint) }) {
                it[GitHostRateLimitTable.rateLimitedUntil] = rateLimitedUntil
            }
        }
    }

    fun clear(authority: String, credentialFingerprint: String) = transaction {
        GitHostRateLimitTable.deleteWhere { keyMatches(authority, credentialFingerprint) }
    }

    private fun keyMatches(authority: String, credentialFingerprint: String) =
        (GitHostRateLimitTable.authority eq authority.lowercase()) and
            (GitHostRateLimitTable.credentialFingerprint eq credentialFingerprint)
}
