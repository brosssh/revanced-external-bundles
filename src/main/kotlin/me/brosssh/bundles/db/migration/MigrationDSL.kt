package me.brosssh.bundles.db.migration

import org.jetbrains.exposed.v1.jdbc.transactions.transaction

fun migrationScript() {
    transaction {
        exec("""
            ALTER TABLE bundle
            ADD COLUMN IF NOT EXISTS is_latest BOOL NOT NULL DEFAULT 'false';
        """)

        exec("""
            ALTER TABLE bundle DROP CONSTRAINT bundle_source_prerelease_uq;
        """)

        exec("""
            ALTER TABLE bundle ADD CONSTRAINT bundle_source_prerelease_uq UNIQUE ("version",source_fk,is_prerelease);
        """.trimIndent())
    }
}
