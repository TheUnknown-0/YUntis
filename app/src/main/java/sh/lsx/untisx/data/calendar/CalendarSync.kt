package sh.lsx.untisx.data.calendar

import android.Manifest
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.provider.CalendarContract
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset

/** A calendar entry the app wrote earlier. */
@Serializable
data class MappedEvent(
    val eventId: Long,
    val fingerprint: String,
    val startMillis: Long,
    val calendarId: Long,
)

/** What one export run has to do in the calendar. Pure, so it can be tested without a device. */
data class CalendarPlan(
    val inserts: List<CalendarItem>,
    val updates: List<Pair<Long, CalendarItem>>,
    val deletes: List<Long>,
    /** Keys whose stored mapping is dropped (deleted events, or old ones left in the calendar). */
    val dropKeys: List<String>,
)

object CalendarPlanner {
    fun plan(
        accountId: String,
        items: List<CalendarItem>,
        mapping: Map<String, MappedEvent>,
        calendarId: Long,
        todayStartMillis: Long,
        startMillis: (CalendarItem) -> Long,
        exists: (Long) -> Boolean = { true },
    ): CalendarPlan {
        val own = mapping.filterKeys { it.startsWith("$accountId:") }
        val wanted = items.associateBy { it.key }

        val inserts = ArrayList<CalendarItem>()
        val updates = ArrayList<Pair<Long, CalendarItem>>()
        val deletes = ArrayList<Long>()
        val drop = ArrayList<String>()

        // a past entry is kept in the calendar; its mapping is forgotten once the builder no longer sees it
        val forgetBefore = todayStartMillis - (CalendarItems.DAYS_BACK + 7) * 24 * 60 * 60 * 1000L

        own.forEach { (key, mapped) ->
            val item = wanted[key]

            when {
                mapped.calendarId != calendarId -> {
                    deletes += mapped.eventId
                    drop += key
                }
                item == null && mapped.startMillis >= todayStartMillis -> {
                    deletes += mapped.eventId
                    drop += key
                }
                item == null && mapped.startMillis < forgetBefore -> drop += key
                item == null -> Unit
                !exists(mapped.eventId) -> drop += key
                mapped.fingerprint != item.fingerprint -> updates += mapped.eventId to item
            }
        }

        items.forEach { item ->
            val mapped = own[item.key]
            if (mapped == null || item.key in drop) inserts += item
        }

        return CalendarPlan(inserts, updates, deletes, drop)
    }
}

/** A calendar on the device the user can choose. */
data class DeviceCalendar(
    val id: Long,
    val name: String,
    val account: String,
    val color: Int,
)

data class CalendarSyncResult(val inserted: Int, val updated: Int, val deleted: Int, val total: Int)

/** Reads and writes the device calendar through the calendar provider. */
object CalendarSync {
    private const val TAG = "CalendarSync"
    private const val MAP_PREFS = "calendar_export_map"
    private const val KEY_MAP = "map"
    const val OWN_ACCOUNT_NAME = "YUntis"
    private const val OWN_CALENDAR_NAME = "yuntis_export"

    private val json = Json { ignoreUnknownKeys = true }

    fun hasPermission(context: Context): Boolean =
        listOf(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR).all {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        }

    /** Calendars the app may write to, without its own export calendar. */
    fun writableCalendars(context: Context): List<DeviceCalendar> {
        if (!hasPermission(context)) return emptyList()

        val out = ArrayList<DeviceCalendar>()
        val projection = arrayOf(
            CalendarContract.Calendars._ID,
            CalendarContract.Calendars.CALENDAR_DISPLAY_NAME,
            CalendarContract.Calendars.ACCOUNT_NAME,
            CalendarContract.Calendars.ACCOUNT_TYPE,
            CalendarContract.Calendars.CALENDAR_COLOR,
            CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL,
        )

        context.contentResolver.query(CalendarContract.Calendars.CONTENT_URI, projection, null, null, null)?.use { c ->
            while (c.moveToNext()) {
                val access = c.getInt(5)
                val type = c.getString(3).orEmpty()
                val accountName = c.getString(2).orEmpty()
                if (access < CalendarContract.Calendars.CAL_ACCESS_CONTRIBUTOR) continue
                if (type == CalendarContract.ACCOUNT_TYPE_LOCAL && accountName == OWN_ACCOUNT_NAME) continue

                out += DeviceCalendar(
                    id = c.getLong(0),
                    name = c.getString(1).orEmpty().ifBlank { accountName },
                    account = accountName,
                    color = c.getInt(4),
                )
            }
        }

        return out.sortedWith(compareBy({ it.account.lowercase() }, { it.name.lowercase() }))
    }

    private fun asSyncAdapter(uri: Uri): Uri = uri.buildUpon()
        .appendQueryParameter(CalendarContract.CALLER_IS_SYNCADAPTER, "true")
        .appendQueryParameter(CalendarContract.Calendars.ACCOUNT_NAME, OWN_ACCOUNT_NAME)
        .appendQueryParameter(CalendarContract.Calendars.ACCOUNT_TYPE, CalendarContract.ACCOUNT_TYPE_LOCAL)
        .build()

    private fun findOwnCalendar(context: Context): Long? {
        context.contentResolver.query(
            CalendarContract.Calendars.CONTENT_URI,
            arrayOf(CalendarContract.Calendars._ID),
            "${CalendarContract.Calendars.ACCOUNT_TYPE} = ? AND ${CalendarContract.Calendars.ACCOUNT_NAME} = ?",
            arrayOf(CalendarContract.ACCOUNT_TYPE_LOCAL, OWN_ACCOUNT_NAME),
            null,
        )?.use { c -> if (c.moveToFirst()) return c.getLong(0) }

        return null
    }

    /** The app's own calendar that only lives on this device; created on first use. */
    fun ensureOwnCalendar(context: Context, displayName: String): Long? {
        findOwnCalendar(context)?.let { return it }

        val values = ContentValues().apply {
            put(CalendarContract.Calendars.ACCOUNT_NAME, OWN_ACCOUNT_NAME)
            put(CalendarContract.Calendars.ACCOUNT_TYPE, CalendarContract.ACCOUNT_TYPE_LOCAL)
            put(CalendarContract.Calendars.NAME, OWN_CALENDAR_NAME)
            put(CalendarContract.Calendars.CALENDAR_DISPLAY_NAME, displayName)
            put(CalendarContract.Calendars.CALENDAR_COLOR, Color.rgb(0xF6, 0x5D, 0x31))
            put(CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL, CalendarContract.Calendars.CAL_ACCESS_OWNER)
            put(CalendarContract.Calendars.OWNER_ACCOUNT, OWN_ACCOUNT_NAME)
            put(CalendarContract.Calendars.VISIBLE, 1)
            put(CalendarContract.Calendars.SYNC_EVENTS, 1)
            put(CalendarContract.Calendars.CALENDAR_TIME_ZONE, ZoneId.systemDefault().id)
        }

        return context.contentResolver.insert(asSyncAdapter(CalendarContract.Calendars.CONTENT_URI), values)
            ?.let(ContentUris::parseId)
    }

    private fun loadMap(context: Context): MutableMap<String, MappedEvent> {
        val text = context.getSharedPreferences(MAP_PREFS, Context.MODE_PRIVATE).getString(KEY_MAP, null)
            ?: return mutableMapOf()
        return runCatching { json.decodeFromString<Map<String, MappedEvent>>(text).toMutableMap() }
            .getOrElse { mutableMapOf() }
    }

    private fun saveMap(context: Context, map: Map<String, MappedEvent>) {
        context.getSharedPreferences(MAP_PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY_MAP, json.encodeToString(map))
            .apply()
    }

    fun millis(time: LocalDateTime, allDay: Boolean): Long =
        if (allDay) {
            time.toLocalDate().atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli()
        } else {
            time.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        }

    private fun values(calendarId: Long, item: CalendarItem) = ContentValues().apply {
        put(CalendarContract.Events.CALENDAR_ID, calendarId)
        put(CalendarContract.Events.TITLE, item.title)
        put(CalendarContract.Events.DESCRIPTION, item.description)
        put(CalendarContract.Events.EVENT_LOCATION, item.location)
        put(CalendarContract.Events.DTSTART, millis(item.start, item.allDay))
        put(CalendarContract.Events.DTEND, millis(item.end, item.allDay))
        put(CalendarContract.Events.ALL_DAY, if (item.allDay) 1 else 0)
        put(CalendarContract.Events.EVENT_TIMEZONE, if (item.allDay) "UTC" else ZoneId.systemDefault().id)
        put(
            CalendarContract.Events.AVAILABILITY,
            if (item.allDay || item.category == CalendarCategory.CANCELLED) {
                CalendarContract.Events.AVAILABILITY_FREE
            } else {
                CalendarContract.Events.AVAILABILITY_BUSY
            },
        )
    }

    private fun eventExists(context: Context, id: Long): Boolean =
        context.contentResolver.query(
            ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, id),
            arrayOf(CalendarContract.Events._ID, CalendarContract.Events.DELETED),
            null,
            null,
            null,
        )?.use { c -> c.moveToFirst() && c.getInt(1) == 0 } ?: false

    private fun deleteEvent(context: Context, id: Long) {
        runCatching {
            context.contentResolver.delete(ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, id), null, null)
        }.onFailure { Log.d(TAG, "delete failed: ${it.javaClass.simpleName}") }
    }

    /** Brings the calendar in line with [items] for one account. */
    fun sync(context: Context, accountId: String, calendarId: Long, items: List<CalendarItem>, today: LocalDate): CalendarSyncResult {
        val map = loadMap(context)
        val plan = CalendarPlanner.plan(
            accountId = accountId,
            items = items,
            mapping = map,
            calendarId = calendarId,
            todayStartMillis = today.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli(),
            startMillis = { millis(it.start, it.allDay) },
            exists = { eventExists(context, it) },
        )

        plan.deletes.forEach { deleteEvent(context, it) }
        plan.dropKeys.forEach { map.remove(it) }

        var updated = 0
        plan.updates.forEach { (eventId, item) ->
            val rows = runCatching {
                context.contentResolver.update(
                    ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, eventId),
                    values(calendarId, item),
                    null,
                    null,
                )
            }.getOrDefault(0)

            if (rows > 0) {
                updated++
                map[item.key] = MappedEvent(eventId, item.fingerprint, millis(item.start, item.allDay), calendarId)
            }
        }

        var inserted = 0
        plan.inserts.forEach { item ->
            val uri = runCatching {
                context.contentResolver.insert(CalendarContract.Events.CONTENT_URI, values(calendarId, item))
            }.onFailure { Log.d(TAG, "insert failed: ${it.javaClass.simpleName}") }.getOrNull()

            if (uri != null) {
                inserted++
                map[item.key] = MappedEvent(ContentUris.parseId(uri), item.fingerprint, millis(item.start, item.allDay), calendarId)
            }
        }

        saveMap(context, map)

        return CalendarSyncResult(inserted, updated, plan.deletes.size, items.size)
    }

    /** Removes every entry the app wrote, for one account or for all. Returns how many were removed. */
    fun removeAll(context: Context, accountId: String? = null): Int {
        val map = loadMap(context)
        val keys = map.keys.filter { accountId == null || it.startsWith("$accountId:") }

        keys.forEach { key ->
            map[key]?.let { deleteEvent(context, it.eventId) }
            map.remove(key)
        }

        saveMap(context, map)

        return keys.size
    }
}
