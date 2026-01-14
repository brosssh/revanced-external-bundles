package me.brosssh.bundles.db.functions

import org.jetbrains.exposed.v1.jdbc.transactions.transaction

fun refreshIsLatestFlag() = transaction {
    exec("""
        WITH ranked AS (
            SELECT
                id,
                ROW_NUMBER() OVER (
                    PARTITION BY source_fk, is_prerelease
                    ORDER BY created_at::timestamp DESC
                ) AS rn
            FROM public.bundle
        )
        UPDATE public.bundle b
        SET is_latest = (r.rn = 1)
        FROM ranked r
        WHERE b.id = r.id;
    """.trimIndent())
}
