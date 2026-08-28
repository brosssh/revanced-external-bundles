package me.brosssh.bundles.integrations.common

import java.net.URI

/** Authority-keyed personal access tokens for git hosts. */
class GitHostCredentials private constructor(
    private val pats: Map<String, String>
) {
    /** Returns the PAT configured for [authority]. */
    fun patFor(authority: String): String? = pats[authority.lowercase()]

    companion object {
        /**
         * Parses comma-separated `host[:port]=pat` entries. The first `=` separates the authority,
         * so tokens may contain `=`; commas are reserved as entry separators. Validation errors
         * identify an entry by position and never include its token.
         */
        fun fromEnv(
            value: String,
            legacyGithubPatToken: String = ""
        ): GitHostCredentials {
            val pats = mutableMapOf<String, String>()
            val configuredAuthorities = mutableSetOf<String>()

            value.split(',')
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .forEachIndexed { index, entry ->
                    val separator = entry.indexOf('=')
                    require(separator > 0 && separator < entry.lastIndex) {
                        "Invalid BACKEND_GIT_HOSTS_PAT entry #${index + 1}. Expected host[:port]=pat."
                    }

                    val authority = entry.substring(0, separator).trim().lowercase()
                    val pat = entry.substring(separator + 1).trim()
                    require(isValidAuthority(authority) && pat.isNotEmpty()) {
                        "Invalid BACKEND_GIT_HOSTS_PAT entry #${index + 1}. Expected host[:port]=pat."
                    }
                    require(configuredAuthorities.add(authority)) {
                        "Duplicate BACKEND_GIT_HOSTS_PAT authority '$authority'."
                    }
                    pats[authority] = pat
                }

            if ("github.com" !in pats && legacyGithubPatToken.isNotBlank()) {
                pats["github.com"] = legacyGithubPatToken.trim()
            }

            return GitHostCredentials(pats)
        }

        private fun isValidAuthority(authority: String): Boolean {
            val uri = runCatching { URI("https://$authority") }.getOrNull() ?: return false
            return uri.rawAuthority?.lowercase() == authority &&
                uri.host != null &&
                uri.userInfo == null &&
                uri.rawPath.isNullOrEmpty() &&
                uri.rawQuery == null &&
                uri.rawFragment == null
        }
    }
}
