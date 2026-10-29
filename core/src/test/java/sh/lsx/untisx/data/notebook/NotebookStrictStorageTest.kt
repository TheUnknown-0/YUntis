package sh.lsx.untisx.data.notebook

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import java.io.File
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import sh.lsx.untisx.data.AtomicJsonFile
import sh.lsx.untisx.data.EncryptedReadFailure
import sh.lsx.untisx.data.EncryptedReadResult
import sh.lsx.untisx.data.EncryptedWriteFailure
import sh.lsx.untisx.data.EncryptedWriteResult
import sh.lsx.untisx.data.prefs.AccountStore
import sh.lsx.untisx.data.prefs.FakeCryptor
import sh.lsx.untisx.data.security.Cryptor
import sh.lsx.untisx.data.security.DecryptResult

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class NotebookStrictStorageTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val file get() = File(context.filesDir, "strict_storage_test.json")

    @Before
    fun setUp() {
        AccountStore.resetForTests()
        AccountStore.cryptor = FakeCryptor()
        clear()
    }

    @After
    fun tearDown() {
        clear()
        AccountStore.resetForTests()
    }

    @Test
    fun `missing base and backup is missing`() {
        assertEquals(EncryptedReadResult.Missing, AtomicJsonFile.readEncryptedStrict(file))
        assertFalse(file.exists())
    }

    @Test
    fun `strict write and read round trip`() {
        assertEquals(EncryptedWriteResult.Success, AtomicJsonFile.writeEncryptedStrict(file, "private"))

        assertEquals(EncryptedReadResult.Text("private"), AtomicJsonFile.readEncryptedStrict(file))
        assertFalse(file.readText().contains("private"))
    }

    @Test
    fun `plaintext is rejected and never migrated`() {
        file.writeText("plain")

        assertEquals(
            EncryptedReadResult.Failed(EncryptedReadFailure.NOT_ENCRYPTED),
            AtomicJsonFile.readEncryptedStrict(file),
        )
        assertEquals("plain", file.readText())
    }

    @Test
    fun `transient and key loss are distinct failures`() {
        assertEquals(EncryptedWriteResult.Success, AtomicJsonFile.writeEncryptedStrict(file, "private"))

        AccountStore.cryptor = object : Cryptor {
            override fun encrypt(plain: String): String = "unused"
            override fun decrypt(encoded: String): DecryptResult = DecryptResult.Transient
        }
        assertEquals(
            EncryptedReadResult.Failed(EncryptedReadFailure.TRANSIENT),
            AtomicJsonFile.readEncryptedStrict(file),
        )

        AccountStore.cryptor = object : Cryptor {
            override fun encrypt(plain: String): String = "unused"
            override fun decrypt(encoded: String): DecryptResult = DecryptResult.KeyLost
        }
        assertEquals(
            EncryptedReadResult.Failed(EncryptedReadFailure.KEY_LOST),
            AtomicJsonFile.readEncryptedStrict(file),
        )
    }

    @Test
    fun `encryption failure does not touch the previous file`() {
        assertEquals(EncryptedWriteResult.Success, AtomicJsonFile.writeEncryptedStrict(file, "old"))
        val before = file.readText()

        AccountStore.cryptor = object : Cryptor {
            override fun encrypt(plain: String): String = error("no key")
            override fun decrypt(encoded: String): DecryptResult = DecryptResult.Value("unused")
        }

        assertEquals(
            EncryptedWriteResult.Failed(EncryptedWriteFailure.KEY_UNAVAILABLE),
            AtomicJsonFile.writeEncryptedStrict(file, "new"),
        )
        assertEquals(before, file.readText())
    }

    @Test
    fun `base missing with backup is an IO failure not missing`() {
        File(file.path + ".bak").writeText("recoverable")

        assertEquals(
            EncryptedReadResult.Failed(EncryptedReadFailure.IO),
            AtomicJsonFile.readEncryptedStrict(file),
        )
        assertTrue(File(file.path + ".bak").exists())
    }

    private fun clear() {
        file.delete()
        File(file.path + ".bak").delete()
    }
}
