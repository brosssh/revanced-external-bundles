package me.brosssh.bundles.db.migration

import org.jetbrains.exposed.v1.jdbc.transactions.transaction

fun migrationScript() {
    transaction {
        exec("""
            ALTER TABLE bundle
            ADD COLUMN IF NOT EXISTS bundle_type VARCHAR(255) NOT NULL DEFAULT 'V4'
        """)

        exec("""
            ALTER TABLE bundle
            DROP COLUMN IF EXISTS is_bundle_v3
        """)
    }
}
