package me.brosssh.bundles.domain.services

import me.brosssh.bundles.api.dto.SearchResponseDto
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

class CacheService {
    private var cachedSearchResults = ConcurrentHashMap<String, List<SearchResponseDto>>()
    private var lastUpdate = Instant.now()

    fun invalidateCache() {
        cachedSearchResults.clear()
        lastUpdate = Instant.now()
    }

    fun getCachedSearch(query: String, fetch: () -> List<SearchResponseDto>): List<SearchResponseDto> =
        cachedSearchResults.getOrPut(query.lowercase()) {
            fetch()
        }
}
