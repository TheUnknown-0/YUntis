package sh.lsx.untisx.ui.timetable

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import sh.lsx.untisx.UntisXApp
import sh.lsx.untisx.data.AppClock
import sh.lsx.untisx.data.prefs.AccountStore
import java.time.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class TimetableAnchorTest {

    private val friday = LocalDate.of(2026, 10, 2)
    private val saturday = LocalDate.of(2026, 10, 3)
    private val thursday = LocalDate.of(2026, 10, 1)

    private lateinit var dispatcher: TestDispatcher
    private val viewModels = mutableListOf<TimetableViewModel>()

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        context.getSharedPreferences(UntisXApp.PREFS_NAME, Context.MODE_PRIVATE)
            .edit().clear().commit()

        AccountStore.resetForTests()

        dispatcher = UnconfinedTestDispatcher()
        Dispatchers.setMain(dispatcher)
        AppClock.set(today = { friday })
    }

    @After
    fun tearDown() {
        viewModels.forEach { it.cancelBackgroundWork() }
        viewModels.clear()
        dispatcher.scheduler.advanceUntilIdle()

        AccountStore.resetForTests()
        AppClock.reset()
        Dispatchers.resetMain()
    }

    private fun createViewModel(): TimetableViewModel {
        val app = ApplicationProvider.getApplicationContext<Application>()

        return TimetableViewModel(app)
            .also { it.cancelBackgroundWork() }
            .also(viewModels::add)
    }

    @Test
    fun `reanchoring on saturday keeps the previously visible monday`() {
        val vm = createViewModel()

        assertEquals(LocalDate.of(2026, 9, 28), vm.anchorMonday)

        val visibleMonday = vm.mondayForPage(TimetableViewModel.CENTER)
        assertEquals(LocalDate.of(2026, 9, 28), visibleMonday)

        AppClock.set(today = { saturday })
        vm.reanchorIfNeeded()

        assertEquals(LocalDate.of(2026, 10, 5), vm.anchorMonday)
        assertEquals(TimetableViewModel.CENTER - 1, vm.pageForMonday(visibleMonday))
        assertEquals(visibleMonday, vm.mondayForPage(TimetableViewModel.CENTER - 1))
    }

    @Test
    fun `same week does not reanchor`() {
        val vm = createViewModel()

        AppClock.set(today = { thursday })
        vm.reanchorIfNeeded()

        assertEquals(LocalDate.of(2026, 9, 28), vm.anchorMonday)
    }

    @Test
    fun `pageForMonday clamps to the valid week window`() {
        val vm = createViewModel()

        assertEquals(0, vm.pageForMonday(LocalDate.of(2000, 1, 3)))
        assertEquals(
            TimetableViewModel.WEEK_SPAN - 1,
            vm.pageForMonday(LocalDate.of(2030, 12, 30)),
        )
    }
}
