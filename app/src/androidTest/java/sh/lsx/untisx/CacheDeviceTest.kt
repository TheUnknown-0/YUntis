package sh.lsx.untisx

import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import sh.lsx.untisx.data.AtomicJsonFile
import java.io.File

@RunWith(AndroidJUnit4::class)
class CacheDeviceTest {
    @Test fun encryptedCacheAndLegacyMigrationWorkWithDeviceKeystore() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val file = File(context.filesDir, "device_crypto_test.json")

        try {
            val message = "private-device-test-message"
            assertTrue(AtomicJsonFile.writeEncrypted(file, message))
            assertFalse(file.readText().contains(message))
            assertEquals(message, AtomicJsonFile.readEncrypted(file))
            file.writeText("""{"legacy":"$message"}""")
            assertEquals("""{"legacy":"$message"}""", AtomicJsonFile.readEncrypted(file))
            assertFalse(file.readText().contains(message))
        } finally {
            AtomicJsonFile.delete(file)
        }
    }
}
