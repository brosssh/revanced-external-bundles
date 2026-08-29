package me.brosssh.bundles.domain.models

data class Patch(
    val name: String?,
    val description: String?,
    val compatiblePackages: Set<CompatiblePackage>?
)

data class CompatiblePackage(
    val name: String,
    val versions: Set<String>?
)
