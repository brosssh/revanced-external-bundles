package me.brosssh.bundles.integrations

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class HostResolverTest {
    private val resolver = HostResolver(factories = emptyMap())

    @Test
    fun `GitHub release page is rejected as a repository source`() {
        assertFailsWith<IllegalArgumentException> {
            resolver.requireSupported("https://github.com/example/patches/releases")
        }
    }

    @Test
    fun `GitLab release page is rejected as a repository source`() {
        assertFailsWith<IllegalArgumentException> {
            resolver.requireSupported("https://gitlab.com/example/patches/-/releases")
        }
    }

    @Test
    fun `nested GitLab repository root remains supported`() {
        val parsed = resolver.requireSupported(
            "https://gitlab.com/example/subgroup/patches"
        )

        assertEquals("example/subgroup", parsed.ref.namespace)
        assertEquals("patches", parsed.ref.repo)
    }
}
