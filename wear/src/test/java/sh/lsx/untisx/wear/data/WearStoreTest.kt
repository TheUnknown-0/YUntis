package sh.lsx.untisx.wear.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import sh.lsx.untisx.data.prefs.FakeCryptor
import sh.lsx.untisx.data.wear.WearTimetablePayload
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class WearStoreTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        WearStore.reset()
        WearStore.setCryptor(FakeCryptor())
    }

    @After
    fun tearDown() {
        WearStore.reset()
    }

    private fun payload(name: String) = WearTimetablePayload(displayName = name, fetchedAt = 123L)

    @Test
    fun `save and load round trip`() {
        WearStore.save(context, payload("Max"))

        WearStore.reset()
        WearStore.setCryptor(FakeCryptor())
        WearStore.load(context)

        assertEquals("Max", WearStore.payload.value?.displayName)
        assertEquals(123L, WearStore.payload.value?.fetchedAt)
    }

    @Test
    fun `load ignores missing file`() {
        WearStore.load(context)

        assertNull(WearStore.payload.value)
    }

    @Test
    fun `key lost clears stored payload`() {
        WearStore.save(context, payload("Old"))
        val file = File(context.filesDir, "wear_payload.enc")
        file.writeText(FakeCryptor.KEY_LOST)

        WearStore.load(context)

        assertNull(WearStore.payload.value)
        assertFalse(file.exists())
    }

    @Test
    fun `late account payload cannot replace a newer clear command even after restart`() {
        assertTrue(WearStore.save(context, payload("Old").copy(accountId = "old", sentAt = 10L)))
        assertTrue(WearStore.save(context, WearTimetablePayload(sentAt = 20L)))
        WearStore.reset()
        WearStore.setCryptor(FakeCryptor())
        assertFalse(WearStore.save(context, payload("Old").copy(accountId = "old", sentAt = 15L)))
        assertEquals("", WearStore.payload.value?.accountId)
    }

    @Test
    fun `new send of an older phone cache does not replace freshly fetched watch weeks`() {
        val fresh = payload("Max").copy(accountId = "a", sentAt = 10L,
            weeks = mapOf("20261005" to emptyList()), weekFetchedAt = mapOf("20261005" to 200L))

        assertTrue(WearStore.save(context, fresh))
        assertTrue(WearStore.save(context, fresh.copy(sentAt = 20L, weekFetchedAt = mapOf("20261005" to 100L))))
        assertEquals(200L, WearStore.payload.value?.weekFetchedAt?.get("20261005"))
    }

    @Test
    fun `account switch does not retain the previous accounts freshness`() {
        val first = payload("First").copy(accountId = "a", sentAt = 10L,
            weeks = mapOf("20261005" to emptyList()), weekFetchedAt = mapOf("20261005" to 200L))

        assertTrue(WearStore.save(context, first))
        assertTrue(WearStore.save(context, first.copy(accountId = "b", sentAt = 20L,
            weekFetchedAt = mapOf("20261005" to 100L))))
        assertEquals("b", WearStore.payload.value?.accountId)
        assertEquals(100L, WearStore.payload.value?.weekFetchedAt?.get("20261005"))
    }

    @Test fun `retained newer week keeps its matching access restrictions`() {
        val fresh = payload("Max").copy(accountId = "a", sentAt = 10L,
            weeks = mapOf("20261005" to emptyList()), weekFetchedAt = mapOf("20261005" to 200L),
            restrictedDays = setOf(20261006))

        assertTrue(WearStore.save(context, fresh))
        assertTrue(WearStore.save(context, fresh.copy(sentAt = 20L,
            weekFetchedAt = mapOf("20261005" to 100L), restrictedDays = emptySet())))
        assertEquals(setOf(20261006), WearStore.payload.value?.restrictedDays)
    }
}
