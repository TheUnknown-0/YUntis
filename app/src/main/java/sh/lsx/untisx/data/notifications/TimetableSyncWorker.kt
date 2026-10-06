package sh.lsx.untisx.data.notifications

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import sh.lsx.untisx.data.SubjectColorsPrefs
import sh.lsx.untisx.data.TimetableCache
import kotlinx.coroutines.CancellationException
import sh.lsx.untisx.data.api.UntisClient
import sh.lsx.untisx.data.api.UntisException
import sh.lsx.untisx.data.model.DayMessage
import sh.lsx.untisx.data.model.MasterDataItem
import sh.lsx.untisx.data.model.TimetableEntry
import sh.lsx.untisx.data.model.toUntisDate
import sh.lsx.untisx.data.model.toUntisWeekStart
import sh.lsx.untisx.data.prefs.AccountPrefs
import sh.lsx.untisx.data.prefs.AccountStore
import sh.lsx.untisx.data.prefs.AuthData
import sh.lsx.untisx.data.prefs.CredentialsPrefs
import sh.lsx.untisx.data.prefs.PasswordVault
import java.io.IOException
import java.time.LocalDate

class TimetableSyncWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val context = applicationContext

        Notifications.ensureChannels(context)

        val changesEnabled = NotificationPrefs.areChangesEnabled(context)
        val newsEnabled = NotificationPrefs.areAnyNewsEnabled(context)

        if (!NotificationPrefs.isEnabled(context) || (!changesEnabled && !newsEnabled)) {
            return Result.success()
        }

        if (!Notifications.canPost(context)) return Result.success()

        AccountStore.ensureLoaded(context)

        val auth = AccountStore.active ?: return Result.success()
        val accountId = AccountPrefs.accountId(auth)

        if (CredentialsPrefs.isInvalid(context, accountId)) return Result.success()

        return try {
            val cache = TimetableCache.load(context, accountId)

            val subjects = cache?.subjects?.associateBy { it.id }.orEmpty()
            val teachers = cache?.teachers?.associateBy { it.id }.orEmpty()
            val rooms = cache?.rooms?.associateBy { it.id }.orEmpty()

            var fresh: AuthData = auth

            if (changesEnabled) {
                fresh = syncChanges(context, accountId, fresh, subjects, teachers, rooms)
            }

            if (newsEnabled) {
                syncNews(context, fresh, accountId, rooms)
            }

            NotificationPrefs.setLastSyncAt(context, System.currentTimeMillis())

            Result.success()
        } catch (e: CancellationException) {
            throw e
        } catch (e: UntisException.BadCredentials) {
            Log.d(TAG, "sync stopped: bad credentials")
            markCredentialsInvalid(context, accountId)
            Result.success()
        } catch (e: UntisException.Api) {
            Log.d(TAG, "sync stopped: api error ${e.code}")
            Result.success()
        } catch (e: UntisException.Network) {
            Log.d(TAG, "sync failed: network")
            if (runAttemptCount < 3) Result.retry() else Result.success()
        } catch (e: IOException) {
            Log.d(TAG, "sync failed: io")
            if (runAttemptCount < 3) Result.retry() else Result.success()
        } catch (e: Exception) {
            Log.d(TAG, "sync failed: ${e.javaClass.simpleName}")
            Result.success()
        }
    }

    override suspend fun getForegroundInfo(): ForegroundInfo =
        ForegroundInfo(
            Notifications.FOREGROUND_SYNC_ID,
            Notifications.foregroundNotification(applicationContext),
        )

    private fun markCredentialsInvalid(context: Context, accountId: String) {
        if (CredentialsPrefs.markInvalid(context, accountId)) {
            Notifications.notifyLoginRequired(context)
        }
    }

    private suspend fun syncChanges(
        context: Context,
        accountId: String,
        auth: AuthData,
        subjects: Map<Int, MasterDataItem>,
        teachers: Map<Int, MasterDataItem>,
        rooms: Map<Int, MasterDataItem>,
    ): AuthData {
        val baseline = ChangeBaseline.load(context, accountId)
        val monday = LocalDate.now().toUntisWeekStart()

        val filter = ChangeFilter(
            cancelled = NotificationPrefs.notifyCancelled(context),
            room = NotificationPrefs.notifyRoomChange(context),
            teacher = NotificationPrefs.notifyTeacherChange(context),
        )

        val changes = ArrayList<ScheduleChange>()
        val observed = HashMap<String, List<TimetableEntry>>()
        var fresh = auth

        for (week in listOf(monday, monday.plusWeeks(1))) {
            val fetched = TimetableSource.fetchWeek(context, fresh, week)
            fetched.fresh?.let { fresh = it }

            val key = week.toUntisDate().toString()
            val previous = baseline?.weeks?.get(key)

            if (previous != null) {
                changes += ScheduleChangeDetector.detect(
                    previous = previous,
                    fresh = fetched.entries,
                    subjects = subjects,
                    teachers = teachers,
                    rooms = rooms,
                    subjectAliases = SubjectColorsPrefs.getAliases(context),
                    filter = filter,
                )
            }

            observed[key] = fetched.entries
        }

        val currentKey = monday.toUntisDate()
        val pruned = observed.filterKeys { key ->
            key.toIntOrNull()?.let { it >= currentKey } ?: true
        }

        if (changes.isNotEmpty()) {
            Log.d(TAG, "changes -> ${changes.size}")

            val silent = NotificationPrefs.changesSilent(context) ||
                NotificationRules.isDuringLessons(context, observed.values.flatten())

            Notifications.notifyChanges(context, changes, silent)
        }

        ChangeBaseline.save(context, accountId, pruned)

        return fresh
    }

    private suspend fun syncNews(
        context: Context,
        auth: AuthData,
        accountId: String,
        rooms: Map<Int, MasterDataItem>,
    ) {
        val password = PasswordVault.retrieve(context, accountId) ?: return

        val messagesEnabled = NotificationPrefs.notifyNewMessages(context)
        val eventsEnabled = NotificationPrefs.notifyNewEvents(context)
        val announcementsEnabled = NotificationPrefs.notifyNewAnnouncements(context)

        val baseline = NewsBaseline.load(context, accountId)
        val now = System.currentTimeMillis()

        var messages = if (messagesEnabled) baseline?.messages else null
        var events = if (eventsEnabled) baseline?.events else null
        var announcements = if (announcementsEnabled) baseline?.announcements else null
        var messagesAt = if (messagesEnabled) baseline?.messagesUpdatedAt ?: 0L else 0L
        var eventsAt = if (eventsEnabled) baseline?.eventsUpdatedAt ?: 0L else 0L
        var announcementsAt = if (announcementsEnabled) baseline?.announcementsUpdatedAt ?: 0L else 0L

        val silent = NotificationRules.isDuringLessons(context)

        if (messagesEnabled) {
            val data = try {
                UntisClient.fetchMessages(auth, password)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.d(TAG, "messages failed: ${e.javaClass.simpleName}")
                null
            }

            if (data != null) {
                val incoming = data.incoming
                val previous = baseline?.messages

                if (previous != null && !NewsBaseline.isStale(messagesAt, now)) {
                    val freshItems = NewsDiff.newItems(incoming, previous, NewsSignatures::message)

                    if (freshItems.isNotEmpty()) {
                        Notifications.notifyNewMessages(context, freshItems, silent)
                    }
                }

                messages = incoming.mapTo(LinkedHashSet()) { NewsSignatures.message(it) }
                messagesAt = now
            }
        }

        if (eventsEnabled) {
            val today = LocalDate.now()
            val end = today.plusMonths(NEWS_EVENTS_AHEAD_MONTHS)
            val items = ArrayList<TimetableEntry>()
            var anySuccess = false
            var freshAuth = auth
            var cursor = today

            while (!cursor.isAfter(end)) {
                val chunkEnd = minOf(cursor.plusMonths(1).minusDays(1), end)

                try {
                    val fetched = TimetableSource.fetchRange(context, freshAuth, cursor, chunkEnd)
                    fetched.fresh?.let { freshAuth = it }

                    items += fetched.entries.filter { it.isEvent }
                    anySuccess = true
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.d(TAG, "events failed: ${e.javaClass.simpleName}")
                }

                cursor = chunkEnd.plusDays(1)
            }

            if (anySuccess) {
                val previous = baseline?.events

                if (previous != null && !NewsBaseline.isStale(eventsAt, now)) {
                    val freshItems = NewsDiff.newItems(items, previous, NewsSignatures::event)

                    if (freshItems.isNotEmpty()) {
                        Notifications.notifyNewEvents(
                            context = context,
                            events = freshItems.map { NewsEvent(event = it, room = eventRoom(it, rooms)) },
                            silent = silent,
                        )
                    }
                }

                events = items.mapTo(LinkedHashSet()) { NewsSignatures.event(it) }
                eventsAt = now
            }
        }

        if (announcementsEnabled) {
            val today = LocalDate.now()
            val found = LinkedHashMap<Int, DayMessage>()
            var anySuccess = false

            for (daysBack in 0..2) {
                val date = today.minusDays(daysBack.toLong())

                try {
                    val list = UntisClient.fetchMessagesOfDay(auth, password, date)
                    anySuccess = true

                    list.forEach { message ->
                        val key = if (message.id != 0) message.id else message.body.hashCode()
                        found.putIfAbsent(key, message)
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.d(TAG, "announcements failed: ${e.javaClass.simpleName}")
                }
            }

            if (anySuccess) {
                val items = found.values.toList()
                val previous = baseline?.announcements

                if (previous != null && !NewsBaseline.isStale(announcementsAt, now)) {
                    val freshItems = NewsDiff.newItems(items, previous, NewsSignatures::announcement)

                    if (freshItems.isNotEmpty()) {
                        Notifications.notifyNewAnnouncements(context, freshItems, silent)
                    }
                }

                announcements = items.mapTo(LinkedHashSet()) { NewsSignatures.announcement(it) }
                announcementsAt = now
            }
        }

        NewsBaseline.save(
            context = context,
            accountId = accountId,
            data = NewsBaseline.Data(
                messages = messages,
                events = events,
                announcements = announcements,
                messagesUpdatedAt = messagesAt,
                eventsUpdatedAt = eventsAt,
                announcementsUpdatedAt = announcementsAt,
            ),
        )
    }

    private fun eventRoom(event: TimetableEntry, rooms: Map<Int, MasterDataItem>): String =
        event.room
            .mapNotNull { element ->
                rooms[element.id]?.name?.takeIf { it.isNotBlank() }
                    ?: element.name.takeIf { it.isNotBlank() }
            }
            .distinct()
            .joinToString(", ")

    companion object {
        private const val TAG = "TimetableSyncWorker"
        private const val NEWS_EVENTS_AHEAD_MONTHS = 3L
    }
}
