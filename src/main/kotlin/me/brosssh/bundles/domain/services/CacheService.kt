package me.brosssh.bundles.domain.services

import me.brosssh.bundles.api.dto.SearchResponseDto
import java.time.Instant
import java.util.concurrent.atomic.AtomicReference

class CacheService {
    private val cachedSnapshot = AtomicReference<List<SearchResponseDto>>(emptyList())
    private var lastUpdate = Instant.now()

    fun invalidateCache() {
        cachedSnapshot.set(null)
        lastUpdate = Instant.now()
    }

    fun getCachedSnapshot(fetch: () -> List<SearchResponseDto>): List<SearchResponseDto> {
        val current = cachedSnapshot.get()
        if (current.isNotEmpty()) return current

        val snapshot = fetch()
        cachedSnapshot.set(snapshot)
        lastUpdate = Instant.now()
        return snapshot
    }
}
