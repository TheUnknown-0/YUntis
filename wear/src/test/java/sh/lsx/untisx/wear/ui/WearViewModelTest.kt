package sh.lsx.untisx.wear.ui

import android.app.Application
import androidx.lifecycle.viewModelScope
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import sh.lsx.untisx.data.AppClock
import sh.lsx.untisx.data.wear.WearRefreshResult
import sh.lsx.untisx.data.wear.WearTimetablePayload
import java.time.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class WearViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val payloads = MutableStateFlow<WearTimetablePayload?>(null)
    private val requests = mutableListOf<String>()
    private var time = 1000000L
    private var connected = true
    private lateinit var vm: WearViewModel

    @Before fun setup() {
        Dispatchers.setMain(dispatcher)
        AppClock.set(today = { LocalDate.of(2026, 10, 5) })
        vm = WearViewModel(ApplicationProvider.getApplicationContext<Application>(), payloads,
            { payloads.value }, { requests += it; connected }, { time })
    }

    @After fun cleanup() {
        vm.viewModelScope.cancel()
        Dispatchers.resetMain()
        AppClock.reset()
    }

    @Test fun `unrelated cache transmission cannot finish a school refresh`() = runTest(dispatcher) {
        runCurrent()
        vm.refresh()
        runCurrent()
        payloads.value = WearTimetablePayload(sentAt = 100)
        runCurrent()
        assertTrue(vm.refreshing.value)
        payloads.value = WearTimetablePayload(requestId = requests.single(), refreshResult = WearRefreshResult.FAILED)
        runCurrent()
        assertFalse(vm.refreshing.value)
        assertEquals(WearSyncState.FAILED, vm.syncState.value)
    }

    @Test fun `cache acknowledgement is not reported as updated school data`() = runTest(dispatcher) {
        runCurrent()
        vm.refresh()
        runCurrent()
        payloads.value = WearTimetablePayload(requestId = requests.single())
        runCurrent()
        assertEquals(WearSyncState.CACHE, vm.syncState.value)
    }

    @Test fun `delayed work is not a failure and its late response is accepted`() = runTest(dispatcher) {
        runCurrent()
        vm.refresh()
        runCurrent()
        advanceTimeBy(120001)
        runCurrent()
        assertEquals(WearSyncState.DELAYED, vm.syncState.value)
        payloads.value = WearTimetablePayload(requestId = requests.single(), refreshResult = WearRefreshResult.UPDATED)
        runCurrent()
        assertEquals(WearSyncState.UPDATED, vm.syncState.value)
    }

    @Test fun `resume reuses fresh weeks and requests stale ones without repeated immediate requests`() = runTest(dispatcher) {
        payloads.value = WearTimetablePayload(accountId = "a",
            weeks = mapOf("20261005" to emptyList(), "20261012" to emptyList()),
            weekFetchedAt = mapOf("20261005" to time, "20261012" to time))
        runCurrent()
        vm.onForegroundTick()
        runCurrent()
        assertTrue(requests.isEmpty())
        time += WearViewModel.FRESH_WINDOW_MS + 1
        vm.onForegroundTick()
        vm.onForegroundTick()
        runCurrent()
        assertEquals(1, requests.size)
    }

    @Test fun `foreground tick updates the date even without new payload`() = runTest(dispatcher) {
        runCurrent()
        AppClock.set(today = { LocalDate.of(2026, 10, 6) })
        vm.onForegroundTick()
        assertEquals(LocalDate.of(2026, 10, 6), vm.today.value)
    }

    @Test fun `disconnected phone fails without waiting for workmanager`() = runTest(dispatcher) {
        runCurrent()
        connected = false
        vm.refresh()
        runCurrent()
        assertFalse(vm.refreshing.value)
        assertEquals(WearSyncState.FAILED, vm.syncState.value)
    }

    @Test fun `a retry result can replace failure for the same request`() = runTest(dispatcher) {
        runCurrent()
        vm.refresh()
        runCurrent()

        val id = requests.single()
        payloads.value = WearTimetablePayload(requestId = id, refreshResult = WearRefreshResult.FAILED)
        runCurrent()
        payloads.value = WearTimetablePayload(requestId = id, refreshResult = WearRefreshResult.UPDATED)
        runCurrent()
        assertEquals(WearSyncState.UPDATED, vm.syncState.value)
    }
}
