package me.brosssh.bundles.domain.models

sealed interface SourceDeletionResult {
    data object NotFound : SourceDeletionResult

    data object Enabled : SourceDeletionResult

    data class Deleted(
        val sources: Int,
        val bundles: Int,
        val patches: Int
    ) : SourceDeletionResult
}
