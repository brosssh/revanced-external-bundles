package me.brosssh.bundles.api.v3.routes

import me.brosssh.bundles.domain.models.ReleaseChannel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class BundleRoutesTest {
    @Test
    fun `latest version requires and parses a channel`() {
        assertEquals(
            BundleVersionSelector.Latest(ReleaseChannel.STABLE),
            parseBundleVersionSelector(" latest ", " stable ")
        )
    }

    @Test
    fun `exact version defaults to any channel`() {
        assertEquals(
            BundleVersionSelector.Exact("v1.2.3", ReleaseChannel.ANY),
            parseBundleVersionSelector(" v1.2.3 ", null)
        )
    }

    @Test
    fun `exact version accepts an explicit channel`() {
        assertEquals(
            BundleVersionSelector.Exact("v1.2.3", ReleaseChannel.PRERELEASE),
            parseBundleVersionSelector("v1.2.3", "prerelease")
        )
    }

    @Test
    fun `latest version without a channel is rejected`() {
        assertFailsWith<IllegalArgumentException> {
            parseBundleVersionSelector("latest", null)
        }
    }

    @Test
    fun `missing version is rejected`() {
        assertFailsWith<IllegalArgumentException> {
            parseBundleVersionSelector(null, "stable")
        }
    }

    @Test
    fun `unknown channel is rejected`() {
        assertFailsWith<IllegalArgumentException> {
            parseBundleVersionSelector("latest", "nightly")
        }
    }
}
