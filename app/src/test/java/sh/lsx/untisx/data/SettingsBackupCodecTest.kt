package sh.lsx.untisx.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import sh.lsx.untisx.data.SettingsBackupCodec.DecodeResult

class SettingsBackupCodecTest {

    @Test
    fun `encode and decode round trip`() {
        val backup = SettingsBackup(
            appVersion = "0.5.0",
            exportedAt = 1234L,
            general = GeneralSettingsBackup(language = "GERMAN", haptics = false),
            appearance = AppearanceSettingsBackup(themeMode = "DARK", animations = true),
            notifications = NotificationSettingsBackup(enabled = false, syncIntervalMinutes = 90),
            timetable = TimetableSettingsBackup(defaultSubjectColor = "#AABBCC"),
        )

        val decoded = SettingsBackupCodec.decode(SettingsBackupCodec.encode(backup))

        assertTrue(decoded is DecodeResult.Valid)
        assertEquals(backup, (decoded as DecodeResult.Valid).backup)
    }

    @Test
    fun `unknown fields are ignored`() {
        val text = """{"type":"untisx-settings","version":1,"unknown":"x"}"""

        assertTrue(SettingsBackupCodec.decode(text) is DecodeResult.Valid)
    }

    @Test
    fun `wrong type and broken json are invalid`() {
        assertTrue(SettingsBackupCodec.decode("not json") is DecodeResult.Invalid)
        assertTrue(SettingsBackupCodec.decode("""{"type":"other","version":1}""") is DecodeResult.Invalid)
    }

    @Test
    fun `future versions are unsupported`() {
        val text = """{"type":"untisx-settings","version":${SettingsBackupCodec.CURRENT_VERSION + 1}}"""

        assertTrue(SettingsBackupCodec.decode(text) is DecodeResult.UnsupportedVersion)
    }

    @Test
    fun `colors round trip through hex`() {
        assertEquals("#AABBCC", colorToHex(0xFFAABBCC.toInt()))
        assertEquals(0xFFAABBCC.toInt(), hexToColor("#AABBCC"))
        assertEquals("#80AABBCC", colorToHex(0x80AABBCC.toInt()))
        assertEquals(0x80AABBCC.toInt(), hexToColor("#80AABBCC"))
        assertEquals(0xFFAABBCC.toInt(), hexToColor("AABBCC"))
    }

    @Test
    fun `invalid hex values yield null`() {
        assertNull(hexToColor(""))
        assertNull(hexToColor("#12345"))
        assertNull(hexToColor("#GGGGGG"))
        assertNull(hexToColor("#123456789"))
    }
}
