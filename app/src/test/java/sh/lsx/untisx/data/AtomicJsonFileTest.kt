package sh.lsx.untisx.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.util.concurrent.atomic.AtomicInteger

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AtomicJsonFileTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun `parallel writes stay parseable and leave no temp files`() {
        val file = File(context.filesDir, "atomic_json_test.json")
        val failures = AtomicInteger()

        file.delete()
        File(context.filesDir, "atomic_json_test.json.tmp").delete()

        val threads = (0 until 50).map { index ->
            Thread {
                if (!AtomicJsonFile.write(file, """{"i":$index}""")) {
                    failures.incrementAndGet()
                }
            }.apply { start() }
        }

        threads.forEach { it.join() }

        val parsed = JSONObject(file.readText())
        assertTrue(parsed.getInt("i") in 0 until 50)
        assertEquals(0, failures.get())
        assertFalse(File(context.filesDir, "atomic_json_test.json.tmp").exists())
        assertFalse(File(context.filesDir, "atomic_json_test.json.new").exists())
    }

    @Test
    fun `read and write round trip plus delete`() {
        val file = File(context.filesDir, "atomic_json_roundtrip.json")

        assertNull(AtomicJsonFile.read(file))
        assertTrue(AtomicJsonFile.write(file, """{"a":1}"""))
        assertEquals("""{"a":1}""", AtomicJsonFile.read(file))
        assertTrue(AtomicJsonFile.delete(file))
        assertNull(AtomicJsonFile.read(file))
    }

    @Test
    fun `temp cleanup removes leftovers`() {
        val temp = File(context.filesDir, "stats_deadbeef.json.tmp").apply { writeText("x") }

        assertTrue(temp.exists())
        AtomicJsonFile.cleanupTempFiles(context.filesDir)
        assertFalse(temp.exists())
    }

    @Test
    fun `account files are stable and account specific`() {
        val first = accountFile(context, "stats_", "account-a")
        val again = accountFile(context, "stats_", "account-a")
        val other = accountFile(context, "stats_", "account-b")

        assertEquals(first, again)
        assertNotEquals(first, other)
        assertTrue(first.name.startsWith("stats_"))
        assertTrue(first.name.endsWith(".json"))
    }
}
