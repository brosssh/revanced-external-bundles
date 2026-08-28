package me.brosssh.bundles.db

import me.brosssh.bundles.db.entities.SourceEntity
import me.brosssh.bundles.db.tables.SourceTable
import me.brosssh.bundles.integrations.HostResolver
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.slf4j.LoggerFactory
import org.tomlj.Toml
import org.tomlj.TomlTable

/**
 * Reconciles the canonical tracked-source manifest with the database at startup.
 *
 * Missing rows are inserted, entries absent from the manifest are soft-disabled, and re-added
 * entries are enabled again. Cached bundles and patches are never deleted.
 */
class SourceManifestSync(
    private val hostResolver: HostResolver
) {
    fun sync(): SyncResult {
        val entries = loadManifest()
        validate(entries, hostResolver)

        val result = SyncResult()
        transaction {
            entries.forEach { entry ->
                val existing = SourceEntity.find { SourceTable.url eq entry.url }
                    .orderBy(SourceTable.id to SortOrder.ASC)
                    .toList()
                val canonical = existing.firstOrNull()

                if (canonical == null) {
                    SourceEntity.new {
                        url = entry.url
                        enabled = entry.enabled
                    }
                    result.inserted++
                } else if (canonical.enabled != entry.enabled) {
                    canonical.enabled = entry.enabled
                    if (entry.enabled) result.reenabled++ else result.disabled++
                }

                // Historical duplicate URL rows retain their cached data but must not refresh twice.
                existing.drop(1)
                    .filter { it.enabled }
                    .forEach {
                        it.enabled = false
                        result.disabled++
                    }
            }

            val manifestUrls = entries.mapTo(mutableSetOf()) { it.url }
            SourceEntity.all()
                .filter { it.enabled && it.url !in manifestUrls }
                .forEach {
                    it.enabled = false
                    result.disabled++
                }
        }
        return result
    }

    data class SyncResult(
        var inserted: Int = 0,
        var disabled: Int = 0,
        var reenabled: Int = 0
    ) {
        val summary: String
            get() = "sources.toml sync: $inserted inserted, $disabled disabled, $reenabled re-enabled"
    }

    companion object {
        private val logger = LoggerFactory.getLogger(SourceManifestSync::class.java)

        data class ManifestEntry(
            val url: String,
            val enabled: Boolean = true
        )

        fun parseManifest(content: String): List<ManifestEntry> {
            val document = Toml.parse(content)
            require(!document.hasErrors()) {
                "sources.toml is invalid:\n${document.errors().joinToString("\n")}"
            }

            val sources = document.getArray("sources")
                ?: throw IllegalArgumentException("sources.toml must contain [[sources]] entries")
            val entries = buildList {
                for (index in 0 until sources.size()) {
                    val table = sources.get(index) as? TomlTable
                        ?: throw IllegalArgumentException(
                            "sources.toml entry ${index + 1} must be a table"
                        )
                    val url = table.getString("url")
                        ?: throw IllegalArgumentException(
                            "sources.toml entry ${index + 1} is missing a url"
                        )
                    add(ManifestEntry(url, table.getBoolean("enabled") ?: true))
                }
            }

            require(entries.isNotEmpty()) {
                "sources.toml must contain at least one [[sources]] entry"
            }
            return entries
        }

        fun loadManifest(): List<ManifestEntry> {
            val resource = requireNotNull(
                SourceManifestSync::class.java.getResourceAsStream("/sources.toml")
            ) {
                "Tracked sources file /sources.toml was not found"
            }
            return resource.bufferedReader().use { parseManifest(it.readText()) }
        }

        /** Validates URLs using the same parser and authority registry as runtime refreshes. */
        fun validate(entries: List<ManifestEntry>, hostResolver: HostResolver) {
            val problems = mutableListOf<String>()
            val seen = mutableMapOf<String, Int>()

            entries.forEachIndexed { index, entry ->
                val entryNumber = index + 1
                val parsed = try {
                    hostResolver.requireSupported(entry.url)
                } catch (exception: IllegalArgumentException) {
                    problems += "entry $entryNumber: ${exception.message}"
                    return@forEachIndexed
                }

                if (entry.url != parsed.canonicalUrl) {
                    problems +=
                        "entry $entryNumber is not canonical; use '${parsed.canonicalUrl}'"
                }

                val previous = seen.putIfAbsent(parsed.canonicalUrl, entryNumber)
                if (previous != null) {
                    problems += "entry $entryNumber duplicates entry $previous"
                }
            }

            require(problems.isEmpty()) {
                "sources.toml validation failed (${problems.size} problem(s)):\n" +
                    problems.joinToString("\n")
            }
        }

        /** Entry point for the Gradle `validateSources` task; no database is required. */
        @JvmStatic
        fun main(args: Array<String>) {
            val hostResolver = HostResolver(
                factories = emptyMap(),
                authorities = HostResolver.fromEnv(
                    System.getenv("BACKEND_GIT_HOSTS").orEmpty()
                )
            )
            val entries = loadManifest()
            validate(entries, hostResolver)
            logger.info("sources.toml: validated {} source(s) OK", entries.size)
        }
    }
}
