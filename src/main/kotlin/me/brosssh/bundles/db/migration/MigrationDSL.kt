package me.brosssh.bundles.db.migration

import org.jetbrains.exposed.v1.jdbc.transactions.transaction

fun migrationScript() {
    transaction {
        exec("""
            ALTER TABLE bundle
            ADD COLUMN IF NOT EXISTS bundle_type VARCHAR(255) NOT NULL DEFAULT 'ReVanced:V4'
        """)

        exec("""
            ALTER TABLE bundle
            DROP COLUMN IF EXISTS is_bundle_v3
        """)

        exec("""
            ALTER TABLE source_metadata
            ADD COLUMN IF NOT EXISTS repo_pushed_at varchar(20) NOT NULL DEFAULT '2000-01-01T00:00:00Z'
        """)

        exec("""
            ALTER TABLE source_metadata
            ADD COLUMN IF NOT EXISTS is_repo_archived BOOLEAN NOT NULL DEFAULT false
        """)
    }
}
