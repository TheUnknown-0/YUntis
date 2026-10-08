package sh.lsx.untisx.ui.timetable

import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import sh.lsx.untisx.data.AppClock
import sh.lsx.untisx.data.TimetableCache
import sh.lsx.untisx.data.model.DayMessage
import sh.lsx.untisx.data.model.TimetableEntry
import sh.lsx.untisx.data.prefs.AuthData
import sh.lsx.untisx.ui.util.UiText
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

internal class TimetableEventsLoader(
    private val scope: CoroutineScope,
    private val requireAuth: suspend () -> AuthData,
    private val fetchEvents: suspend (AuthData, LocalDate, LocalDate) -> List<TimetableEntry>,
    private val fetchMessages: suspend (AuthData, List<DayMessage>) -> List<DayMessage>,
    private val onPersist: suspend () -> Unit,
) {

    var events by mutableStateOf<List<TimetableEntry>>(emptyList())
        private set
    var loading by mutableStateOf(false)
        private set
    var error by mutableStateOf<UiText?>(null)
        private set
    var fetchedAt by mutableLongStateOf(0L)
        private set
    var dayMessages by mutableStateOf<List<DayMessage>>(emptyList())
        private set
    var messagesFetchedAt by mutableLongStateOf(0L)
        private set

    private var requested = false

    fun ensure(force: Boolean = false) {
        val messagesStale = messagesFetchedAt <= 0L ||
            Instant.ofEpochMilli(messagesFetchedAt).atZone(ZoneId.systemDefault()).toLocalDate() != AppClock.today()

        if (!force && (requested || fetchedAt > 0L) && !messagesStale) {
            error = null
            return
        }

        requested = true
        refresh()
    }

    fun refresh() {
        if (loading) return

        loading = true
        error = null

        scope.launch {
            try {
                val currentAuth = requireAuth()
                val from = AppClock.today()
                val to = from.plusMonths(EVENTS_AHEAD_MONTHS)

                val fetched = fetchEvents(currentAuth, from, to)
                Log.d(TAG, "events fetch -> ${fetched.size} periods, ${fetched.count { it.isEvent }} events")

                events = mergeConsecutiveEvents(
                    fetched
                        .filter { it.isEvent }
                        .sortedWith(compareBy({ it.date }, { it.startTime })),
                )

                fetchedAt = System.currentTimeMillis()

                dayMessages = fetchMessages(currentAuth, dayMessages)
                messagesFetchedAt = System.currentTimeMillis()

                onPersist()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.d(TAG, "events fetch failed: ${e.javaClass.simpleName}")
                error = friendlyError(e)
            } finally {
                loading = false
            }
        }
    }

    fun applyCached(cached: TimetableCache.Data) {
        events = cached.events
        fetchedAt = cached.eventsFetchedAt
        dayMessages = cached.messages
        messagesFetchedAt = cached.messagesFetchedAt
    }

    private companion object {
        const val TAG = "TimetableEventsLoader"
        const val EVENTS_AHEAD_MONTHS = 6L
    }
}
