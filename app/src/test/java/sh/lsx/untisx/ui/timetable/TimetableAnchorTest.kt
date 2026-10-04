package sh.lsx.untisx.ui.timetable

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
import sh.lsx.untisx.data.AppClock
import java.time.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class TimetableAnchorTest {

    private val friday = LocalDate.of(2026, 10, 2)
    private val saturday = LocalDate.of(2026, 10, 3)
    private val thursday = LocalDate.of(2026, 10, 1)

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        AppClock.set(today = { friday })
    }

    @After
    fun tearDown() {
        AppClock.reset()
        Dispatchers.resetMain()
    }

    @Test
    fun `reanchoring on saturday keeps the previously visible monday`() {
        val app = ApplicationProvider.getApplicationContext<Application>()

        val vm = TimetableViewModel(app)
        vm.cancelBackgroundWork()

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
        val app = ApplicationProvider.getApplicationContext<Application>()

        val vm = TimetableViewModel(app)
        vm.cancelBackgroundWork()

        AppClock.set(today = { thursday })
        vm.reanchorIfNeeded()

        assertEquals(LocalDate.of(2026, 9, 28), vm.anchorMonday)
    }

    @Test
    fun `pageForMonday clamps to the valid week window`() {
        val app = ApplicationProvider.getApplicationContext<Application>()

        val vm = TimetableViewModel(app)
        vm.cancelBackgroundWork()

        assertEquals(0, vm.pageForMonday(LocalDate.of(2000, 1, 3)))
        assertEquals(
            TimetableViewModel.WEEK_SPAN - 1,
            vm.pageForMonday(LocalDate.of(2030, 12, 30)),
        )
    }
}
