package sh.lsx.untisx.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import sh.lsx.untisx.data.prefs.AccountStore
import sh.lsx.untisx.data.prefs.FakeCryptor
import sh.lsx.untisx.data.security.Crypto
import sh.lsx.untisx.data.security.Cryptor
import sh.lsx.untisx.data.security.DecryptResult
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class EncryptedCacheTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val file get() = File(context.filesDir, "encrypted_cache_test.json")
    @Before fun setup() { AccountStore.cryptor = FakeCryptor(); AtomicJsonFile.delete(file) }
    @After fun cleanup() { AtomicJsonFile.delete(file); AccountStore.cryptor = Crypto }

    @Test fun `cache writes use the cryptor and remain readable`() {
        assertTrue(AtomicJsonFile.writeEncrypted(file, "private message"))
        assertFalse(file.readText().contains("private message"))
        assertEquals("private message", AtomicJsonFile.readEncrypted(file))
    }

    @Test fun `legacy JSON is atomically migrated on first read`() {
        file.writeText("""{"message":"private"}""")
        assertEquals("""{"message":"private"}""", AtomicJsonFile.readEncrypted(file))
        assertFalse(file.readText().contains("private"))
        assertEquals("""{"message":"private"}""", AtomicJsonFile.readEncrypted(file))
    }

    @Test fun `key failure never writes plaintext or destroys the previous cache`() {
        assertTrue(AtomicJsonFile.writeEncrypted(file, "old"))

        val before = file.readText()
        AccountStore.cryptor = object : Cryptor {
            override fun encrypt(plain: String): String = error("key unavailable")
            override fun decrypt(encoded: String): DecryptResult = DecryptResult.Transient
        }
        assertFalse(AtomicJsonFile.writeEncrypted(file, "new"))
        assertEquals(before, file.readText())
        assertNull(AtomicJsonFile.readEncrypted(file))
    }
}
