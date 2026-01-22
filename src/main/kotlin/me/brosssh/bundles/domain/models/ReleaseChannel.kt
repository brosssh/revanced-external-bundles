package me.brosssh.bundles.domain.models

import me.brosssh.bundles.db.tables.BundleTable
import org.jetbrains.exposed.v1.core.Op
import org.jetbrains.exposed.v1.core.eq

sealed interface ReleaseSelection {
    fun predicate(): Op<Boolean>
}

enum class ReleaseChannel : ReleaseSelection {
    STABLE {
        override fun predicate() = BundleTable.isPrerelease eq false
    },
    PRERELEASE {
        override fun predicate() = BundleTable.isPrerelease eq true
    },
    ANY {
        override fun predicate() = Op.TRUE
    }
}
