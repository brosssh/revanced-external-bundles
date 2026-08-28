package me.brosssh.bundles.integrations.common

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull

class GitHostCredentialsTest {
    @Test
    fun `parses authority-specific PATs including ports`() {
        val credentials = GitHostCredentials.fromEnv(
            "gitlab.corp.com=glpat-abc,gitea.corp.com:3000=gtpat-xyz"
        )

        assertEquals("glpat-abc", credentials.patFor("gitlab.corp.com"))
        assertEquals("gtpat-xyz", credentials.patFor("gitea.corp.com:3000"))
        assertNull(credentials.patFor("unknown.example"))
    }

    @Test
    fun `GitHub PAT is configured through the common map and may contain equals`() {
        val credentials = GitHostCredentials.fromEnv("github.com=explicit=token")

        assertEquals("explicit=token", credentials.patFor("github.com"))
    }

    @Test
    fun `comma is reserved as the entry separator`() {
        assertFailsWith<IllegalArgumentException> {
            GitHostCredentials.fromEnv("github.com=token,fragment")
        }
    }

    @Test
    fun `invalid entry error does not expose its token`() {
        val secret = "super-secret-token"
        val error = assertFailsWith<IllegalArgumentException> {
            GitHostCredentials.fromEnv("=$secret")
        }

        assertFalse(error.message.orEmpty().contains(secret))
    }
}
