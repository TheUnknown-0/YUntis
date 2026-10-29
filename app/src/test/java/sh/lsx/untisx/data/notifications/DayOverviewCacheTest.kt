package sh.lsx.untisx.data.notifications

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.testing.TestListenableWorkerBuilder
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import sh.lsx.untisx.data.TimetableCache
import sh.lsx.untisx.data.model.TimetableEntry
import sh.lsx.untisx.data.prefs.AccountStore
import sh.lsx.untisx.data.prefs.FakeCryptor
import sh.lsx.untisx.data.security.Crypto
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class DayOverviewCacheTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    @Before fun setup() { AccountStore.cryptor = FakeCryptor() }
    @After fun cleanup() { AccountStore.cryptor = Crypto }

    @Test fun `offline overview only includes the requested day and retains week timestamps`() {
        val monday = TimetableEntry(id = 1, date = 20261005, startTime = 800, endTime = 845)
        val tuesday = monday.copy(id = 2, date = 20261006)

        TimetableCache.save(context, "offline-test", TimetableCache.Data(
            weeks = mapOf("20261005" to listOf(monday, tuesday)),
            weekFetchedAt = mapOf("20261005" to 123L),
        ))

        val worker = TestListenableWorkerBuilder<DayOverviewWorker>(context).build()
        val method = DayOverviewWorker::class.java.getDeclaredMethod("cachedEntries", Context::class.java, String::class.java, LocalDate::class.java)

        method.isAccessible = true
        assertEquals(listOf(tuesday), method.invoke(worker, context, "offline-test", LocalDate.of(2026, 10, 6)))
        assertEquals(123L, TimetableCache.load(context, "offline-test")?.weekFetchedAt?.get("20261005"))
    }
}
