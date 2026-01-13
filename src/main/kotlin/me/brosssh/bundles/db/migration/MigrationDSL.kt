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

        exec(
            """
            
            ALTER TABLE refresh_jobs
            DROP COLUMN IF EXISTS updated_at;
            
            ALTER TABLE refresh_jobs
            ADD COLUMN IF NOT EXISTS completed_at TIMESTAMPTZ;
            
            ALTER TABLE refresh_jobs
            ALTER COLUMN status TYPE varchar(31);

            """
        )

        exec("""
            DO $$
            BEGIN
              IF EXISTS (
                SELECT 1
                FROM information_schema.columns
                WHERE table_name = 'refresh_jobs'
                  AND column_name = 'created_at'
              ) THEN       
                
                ALTER TABLE refresh_jobs
                ALTER COLUMN created_at
                TYPE TIMESTAMPTZ
                USING to_timestamp(created_at / 1000.0);
                
                ALTER TABLE refresh_jobs
                RENAME COLUMN created_at TO started_at;
              END IF;
            END$$;
        """.trimIndent())
    }
}
