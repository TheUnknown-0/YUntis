package sh.lsx.untisx.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsBackupNotebookIsolationTest {

    @Test
    fun `settings backup codec contains no notebook fields`() {
        val encoded = SettingsBackupCodec.encode(
            SettingsBackup(
                appVersion = "test",
                exportedAt = 1,
                general = GeneralSettingsBackup(language = "EN"),
                timetable = TimetableSettingsBackup(knownSubjects = listOf("Math")),
            ),
        ).lowercase()

        assertFalse(encoded.contains("notebook"))
        assertFalse(encoded.contains("drafts"))
        assertFalse(encoded.contains("notebookitem"))
    }

    @Test
    fun `settings backup roundtrip stays settings-only`() {
        val original = SettingsBackup(
            appVersion = "test",
            exportedAt = 1,
            general = GeneralSettingsBackup(language = "DE", haptics = true),
        )

        val decoded = SettingsBackupCodec.decode(SettingsBackupCodec.encode(original))

        assertTrue(decoded is SettingsBackupCodec.DecodeResult.Valid)
        assertTrue((decoded as SettingsBackupCodec.DecodeResult.Valid).backup.general?.haptics == true)
    }
}
