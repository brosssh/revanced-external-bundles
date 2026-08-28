package me.brosssh.bundles.integrations.common

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse

class GitHostClientTest {
    @Test
    fun `repository URL is canonicalized without query fragment or trailing slash`() {
        val parsed = parseRepoUrl(
            "HTTPS://GitLab.Example:8443/group/subgroup/my%20repo/?token=secret#fragment"
        )

        assertEquals("https", parsed.scheme)
        assertEquals("gitlab.example:8443", parsed.authority)
        assertEquals(RepoRef("group/subgroup", "my repo"), parsed.ref)
        assertEquals(
            "https://gitlab.example:8443/group/subgroup/my%20repo",
            parsed.canonicalUrl
        )
    }

    @Test
    fun `repository URL without a host is rejected`() {
        assertFailsWith<IllegalArgumentException> {
            parseRepoUrl("https://:8443/group/repo")
        }
    }

    @Test
    fun `repository URL containing user information is rejected without exposing it`() {
        val secret = "super-secret"
        val error = assertFailsWith<IllegalArgumentException> {
            parseRepoUrl("https://user:$secret@gitlab.example/group/repo")
        }

        assertFalse(error.message.orEmpty().contains(secret))
    }
}
