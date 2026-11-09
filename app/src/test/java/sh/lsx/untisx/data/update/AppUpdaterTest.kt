package sh.lsx.untisx.data.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AppUpdaterTest {
    @Test
    fun `versions are compared numerically`() {
        assertTrue(AppUpdater.isNewer("v0.10.0", "0.9.0"))
        assertTrue(AppUpdater.isNewer("1.0", "0.99.9"))
        assertFalse(AppUpdater.isNewer("v0.9.0", "0.9.0"))
        assertFalse(AppUpdater.isNewer("0.9.0", "0.10.0"))
        assertTrue(AppUpdater.isNewer("0.9.1", "0.9"))
    }

    @Test
    fun `release with an apk asset is read, others are ignored`() {
        val body = """
            {"tag_name":"v0.10.0","html_url":"https://github.com/x/y/releases/tag/v0.10.0","body":"Neu",
             "assets":[{"name":"notes.txt","browser_download_url":"https://x/notes.txt"},
                       {"name":"YUntis-0.10.0.apk","browser_download_url":"https://x/YUntis.apk"}]}
        """.trimIndent()

        val release = AppUpdater.parseRelease(body)!!
        assertEquals("0.10.0", release.version)
        assertEquals("https://x/YUntis.apk", release.apkUrl)
        assertEquals("Neu", release.notes)

        assertNull(AppUpdater.parseRelease("""{"tag_name":"v1","assets":[]}"""))
        assertNull(AppUpdater.parseRelease("""{"message":"Not Found"}"""))
    }
}
