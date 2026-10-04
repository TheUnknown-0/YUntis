package sh.lsx.untisx.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateClientVersionTest {

    private fun version(tag: String) = requireNotNull(UpdateClient.parseTag(tag))

    @Test
    fun `final release is newer than the prerelease with the same numbers`() {
        assertTrue(UpdateClient.isNewer(version("v0.5.0"), "0.5.0-beta"))
    }

    @Test
    fun `prerelease of a higher version is newer`() {
        assertTrue(UpdateClient.isNewer(version("0.5.1-beta"), "0.5.0"))
    }

    @Test
    fun `numeric components compare numerically`() {
        assertTrue(UpdateClient.isNewer(version("0.10.0"), "0.9.9"))
        assertFalse(UpdateClient.isNewer(version("0.9.9"), "0.10.0"))
    }

    @Test
    fun `identical versions are not newer`() {
        assertFalse(UpdateClient.isNewer(version("0.5.0"), "0.5.0"))
    }

    @Test
    fun `unparseable installed version offers the update`() {
        assertTrue(UpdateClient.isNewer(version("0.5.0"), "not-a-version"))
    }

    @Test
    fun `forced marker is not treated as prerelease`() {
        val parsed = version("v0.5.0-forced")

        assertEquals("0.5.0", parsed.normalized)
        assertTrue(parsed.preRelease.isEmpty())
    }

    @Test
    fun `prerelease identifiers follow semver ranking`() {
        assertTrue(UpdateClient.compareVersions(version("1.0.0-beta.2"), version("1.0.0-beta.1")) > 0)
        assertTrue(UpdateClient.compareVersions(version("1.0.0-beta"), version("1.0.0-alpha")) > 0)
        assertTrue(UpdateClient.compareVersions(version("1.0.0"), version("1.0.0-rc.1")) > 0)
    }

    @Test
    fun `invalid tags parse to null`() {
        assertNull(UpdateClient.parseTag(""))
        assertNull(UpdateClient.parseTag("vNext"))
        assertNull(UpdateClient.parseTag("1.x.0"))
    }

    @Test
    fun `forced marker combines with a prerelease`() {
        val parsed = version("v0.5.1-beta-forced")

        assertEquals("0.5.1-beta", parsed.normalized)
        assertEquals(listOf("beta"), parsed.preRelease)
    }

    @Test
    fun `missing numeric components compare as zero`() {
        assertTrue(UpdateClient.compareVersions(version("1.0.1"), version("1.0")) > 0)
        assertEquals(0, UpdateClient.compareVersions(version("1.0"), version("1.0.0")))
    }

    @Test
    fun `numeric prerelease identifiers compare numerically`() {
        assertTrue(UpdateClient.compareVersions(version("1.0.0-alpha.10"), version("1.0.0-alpha.2")) > 0)
    }

    @Test
    fun `forced installed version is not newer`() {
        assertFalse(UpdateClient.isNewer(version("0.5.0"), "v0.5.0-forced"))
    }
}
