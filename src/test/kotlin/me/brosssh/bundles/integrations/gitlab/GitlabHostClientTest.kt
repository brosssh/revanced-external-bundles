package me.brosssh.bundles.integrations.gitlab

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GitlabHostClientTest {
    @Test
    fun `semantic version prerelease tags are classified as prereleases`() {
        assertTrue(isGitlabPrereleaseTag("v1.2.3-rc.1"))
        assertTrue(isGitlabPrereleaseTag("1.2.3-beta+build.4"))
    }

    @Test
    fun `stable and non-semantic tags are not classified as prereleases`() {
        assertFalse(isGitlabPrereleaseTag("v1.2.3"))
        assertFalse(isGitlabPrereleaseTag("release-1.2.3"))
    }

    @Test
    fun `GitLab timestamps are normalized to UTC whole seconds`() {
        assertEquals(
            "2019-01-03T01:56:19Z",
            normalizeGitlabTimestamp("2019-01-03T03:56:19.539+02:00")
        )
    }

    @Test
    fun `missing GitLab timestamps remain empty`() {
        assertEquals("", normalizeGitlabTimestamp(null))
    }

    @Test
    fun `release mapping uses tag-based prerelease classification`() {
        assertTrue(GitlabReleaseDto(tagName = "v2.0.0-alpha.1").toReleaseInfo().prerelease)
        assertFalse(GitlabReleaseDto(tagName = "v2.0.0").toReleaseInfo().prerelease)
    }
}
