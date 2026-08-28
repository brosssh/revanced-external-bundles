package me.brosssh.bundles.integrations

import me.brosssh.bundles.integrations.common.GitHostClient
import me.brosssh.bundles.integrations.common.GitHostClientFactory
import me.brosssh.bundles.integrations.common.RepoRef
import me.brosssh.bundles.integrations.common.parseRepoUrl
import org.slf4j.LoggerFactory

enum class GitHostType { GITHUB, GITLAB, GITEA }

data class ResolvedGitHost(
    val client: GitHostClient,
    val ref: RepoRef,
    val authority: String
)

/**
 * Resolves a repository URL to the appropriate [GitHostClient]. Provider factories own client
 * construction, authentication, and API base-URL conventions. Well-known SaaS hosts are recognised
 * by default; self-hosted instances can be registered via [authorities], e.g.
 * `gitlab.corp.com=gitlab,gitea.corp.com:3000=gitea`.
 */
class HostResolver(
    private val factories: Map<GitHostType, GitHostClientFactory>,
    private val authorities: Map<String, GitHostType> = emptyMap()
) {
    private val registry: Map<String, GitHostType> = defaultRegistry(authorities)

    fun resolve(url: String): ResolvedGitHost {
        val parsed = parseRepoUrl(url)
        val type = registry[parsed.authority]
            ?: throw IllegalArgumentException(
                "Unsupported git authority '${parsed.authority}'. Register it via BACKEND_GIT_HOSTS " +
                    "(e.g. '${parsed.authority}=gitea', '${parsed.authority}=gitlab', etc.)."
            )
        val factory = factories[type]
            ?: error("No GitHostClientFactory registered for $type")
        val hostClient = factory.create(parsed.scheme, parsed.authority)

        return ResolvedGitHost(
            client = hostClient,
            ref = parsed.ref,
            authority = parsed.authority
        )
    }

    companion object {
        private val logger = LoggerFactory.getLogger(HostResolver::class.java)

        /** Well-known SaaS authorities plus extra `host[:port]=type` entries from BACKEND_GIT_HOSTS. */
        fun defaultRegistry(extra: Map<String, GitHostType> = emptyMap()): Map<String, GitHostType> =
            buildMap {
                put("github.com", GitHostType.GITHUB)
                put("gitlab.com", GitHostType.GITLAB)
                put("codeberg.org", GitHostType.GITEA)
                put("gitea.com", GitHostType.GITEA)
                putAll(extra)
            }

        /**
         * Parses comma-separated `host[:port]=type` entries from `BACKEND_GIT_HOSTS` into a map.
         * Unknown types and malformed entries are ignored with a warning.
         */
        fun fromEnv(value: String): Map<String, GitHostType> {
            if (value.isBlank()) return emptyMap()

            return value.split(",")
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .mapNotNull { entry ->
                    val parts = entry.split("=", limit = 2)
                    val authority = parts[0].trim().lowercase()
                    val type = parts.getOrNull(1)?.trim()?.uppercase()
                    val resolved = type?.let { runCatching { GitHostType.valueOf(it) }.getOrNull() }
                    if (authority.isEmpty() || resolved == null) {
                        logger.warn("Ignoring invalid BACKEND_GIT_HOSTS entry: {}", entry)
                        null
                    } else authority to resolved
                }
                .toMap()
        }
    }
}
