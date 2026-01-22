package me.brosssh.bundles.domain.models

import me.brosssh.bundles.db.tables.BundleTable
import org.jetbrains.exposed.v1.core.Op
import org.jetbrains.exposed.v1.core.eq

private sealed interface ReleaseSelection {
    val releaseFilter: Op<Boolean>
}

enum class ReleaseChannel : ReleaseSelection {
    STABLE {
        override val releaseFilter = BundleTable.isPrerelease eq false
    },
    PRERELEASE {
        override val releaseFilter = BundleTable.isPrerelease eq true
    },
    ANY {
        override val releaseFilter = Op.TRUE
    }
}
