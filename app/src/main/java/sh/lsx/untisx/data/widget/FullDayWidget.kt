package sh.lsx.untisx.data.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.RemoteViews
import sh.lsx.untisx.R
import sh.lsx.untisx.data.model.TimetableEntry
import sh.lsx.untisx.data.model.toUntisDate
import sh.lsx.untisx.ui.timetable.freeDurationText
import sh.lsx.untisx.ui.timetable.freeGaps
import java.time.LocalDate
import java.time.LocalDateTime

/** A large, resizable widget with a whole school day and arrows to browse days. */
class FullDayWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) = WidgetUpdater.updateAll(context)

    override fun onAppWidgetOptionsChanged(context: Context, manager: AppWidgetManager, id: Int, options: Bundle) =
        WidgetUpdater.updateAll(context)

    override fun onDeleted(context: Context, ids: IntArray) {
        ids.forEach { FullDayWidget.setChosenDate(context, it, null) }
    }

    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        val step = when (intent.action) {
            FullDayWidget.ACTION_PREV -> -1L
            FullDayWidget.ACTION_NEXT -> 1L
            FullDayWidget.ACTION_TODAY -> 0L
            else -> null
        }

        if (step != null && id != AppWidgetManager.INVALID_APPWIDGET_ID) {
            val current = FullDayWidget.chosenDate(context, id) ?: FullDayWidget.autoDate(context)
            FullDayWidget.setChosenDate(context, id, if (step == 0L) null else current.plusDays(step))
            WidgetUpdater.updateAll(context)
            return
        }

        super.onReceive(context, intent)
    }
}

/** One line of the full day: a lesson or a free period between lessons. */
sealed interface FullDayRow {
    data class Lesson(val lesson: WidgetLesson) : FullDayRow
    data class Free(val start: Int, val end: Int, val minutes: Int) : FullDayRow
}

object FullDayWidget {
    const val ACTION_PREV = "de.theunknown.yuntis.widget.PREV"
    const val ACTION_NEXT = "de.theunknown.yuntis.widget.NEXT"
    const val ACTION_TODAY = "de.theunknown.yuntis.widget.TODAY"

    private const val PREFS = "widget_full_day"
    private const val HEADER_DP = 96
    private const val ROW_DP = 50
    private const val COMPACT_ROW_DP = 28
    private const val FREE_ROW_DP = 20

    /** The day shown when the user has not browsed: today, or the next school day once today is over. */
    @Volatile
    private var lastAuto: LocalDate = LocalDate.now()

    fun autoDate(@Suppress("UNUSED_PARAMETER") context: Context): LocalDate = lastAuto

    fun chosenDate(context: Context, id: Int): LocalDate? {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val value = prefs.getLong("date_$id", 0L).takeIf { it > 0 } ?: return null
        val setOn = prefs.getLong("set_$id", 0L)
        // a browsed day only sticks for the day it was chosen on
        return if (setOn == LocalDate.now().toEpochDay()) LocalDate.ofEpochDay(value) else null
    }

    fun setChosenDate(context: Context, id: Int, date: LocalDate?) {
        val edit = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
        if (date == null) {
            edit.remove("date_$id").remove("set_$id")
        } else {
            edit.putLong("date_$id", date.toEpochDay()).putLong("set_$id", LocalDate.now().toEpochDay())
        }
        edit.apply()
    }

    /** Lessons and the free periods between them, in time order. */
    fun rows(lessons: List<WidgetLesson>, raw: List<TimetableEntry>): List<FullDayRow> {
        val free = freeGaps(raw).map { FullDayRow.Free(it.start, it.end, it.minutes) }
        val items: List<Pair<Int, FullDayRow>> = lessons.map { it.start to FullDayRow.Lesson(it) } + free.map { it.start to it }
        return items.sortedBy { it.first }.map { it.second }
    }

    private fun action(context: Context, id: Int, action: String, request: Int): PendingIntent = PendingIntent.getBroadcast(
        context,
        id * 10 + request,
        Intent(context, FullDayWidgetProvider::class.java)
            .setAction(action)
            .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    internal fun views(
        context: Context,
        id: Int,
        heightDp: Int,
        entries: List<TimetableEntry>,
        lessonsFor: (List<TimetableEntry>) -> List<WidgetLesson>,
        auto: LocalDate,
        now: LocalDateTime,
        loggedIn: Boolean,
        style: WidgetUpdater.Style,
    ): RemoteViews {
        lastAuto = auto
        val date = chosenDate(context, id) ?: auto
        val raw = entries.filter { it.date == date.toUntisDate() }
        val lessons = lessonsFor(raw)
        val rows = rows(lessons, raw)

        val views = RemoteViews(context.packageName, R.layout.widget_full)
        views.setOnClickPendingIntent(R.id.widget_root, WidgetUpdater.openApp(context, date))
        views.setOnClickPendingIntent(R.id.widget_prev, action(context, id, ACTION_PREV, 1))
        views.setOnClickPendingIntent(R.id.widget_next_day, action(context, id, ACTION_NEXT, 2))
        views.setOnClickPendingIntent(R.id.widget_header, action(context, id, ACTION_TODAY, 3))
        val look = style.look
        val held = lessons.filter { !it.cancelled }
        val span = if (held.isEmpty()) "" else " · " + WidgetUpdater.time(context, held.first().start) + " – " + WidgetUpdater.time(context, held.maxOf { it.end })
        WidgetUpdater.header(
            views,
            look,
            label = WidgetUpdater.dayLabel(context, date, now),
            title = sh.lsx.untisx.ui.util.AppDateFormats.formatter(context, "EEEdMMM").format(date),
            count = if (lessons.isEmpty()) "" else context.resources.getQuantityString(R.plurals.widget_lesson_count, held.size, held.size) + span,
        )
        look.color(views, R.id.widget_prev, WidgetLook.Role.TEXT)
        look.color(views, R.id.widget_next_day, WidgetLook.Role.TEXT)
        look.highlight(views, R.id.widget_prev)
        look.highlight(views, R.id.widget_next_day)
        views.removeAllViews(R.id.widget_list)

        if (!loggedIn || lessons.isEmpty()) {
            views.setViewVisibility(R.id.widget_empty, View.VISIBLE)
            look.setText(views, R.id.widget_empty, context.getString(if (loggedIn) R.string.widget_free_day else R.string.widget_login))
            return views
        }
        views.setViewVisibility(R.id.widget_empty, View.GONE)

        // big rows when the whole day fits, otherwise one line per lesson
        val available = if (heightDp <= 0) 520 else heightDp - HEADER_DP
        val lessonCount = rows.count { it is FullDayRow.Lesson }
        val freeCount = rows.size - lessonCount
        val compact = lessonCount * ROW_DP + freeCount * FREE_ROW_DP > available
        val rowHeight = if (compact) COMPACT_ROW_DP else ROW_DP
        var used = 0
        val nowTime = now.hour * 100 + now.minute
        val isToday = date == now.toLocalDate()
        val running = if (isToday) lessons.firstOrNull { !it.cancelled && it.start <= nowTime && it.end > nowTime } else null

        for (row in rows) {
            val needed = if (row is FullDayRow.Free) FREE_ROW_DP else rowHeight
            if (used + needed > available) break
            used += needed

            when (row) {
                is FullDayRow.Free -> {
                    val free = RemoteViews(context.packageName, R.layout.widget_free_row)
                    look.setText(
                        free,
                        R.id.row_subject,
                        context.getString(R.string.timetable_free, freeDurationText(row.minutes)) + "  " +
                            WidgetUpdater.time(context, row.start) + " – " + WidgetUpdater.time(context, row.end),
                    )
                    look.color(free, R.id.row_subject, WidgetLook.Role.ACCENT)
                    look.colors?.let { free.setInt(R.id.row_bar, "setColorFilter", it.accent) }
                    views.addView(R.id.widget_list, free)
                }

                is FullDayRow.Lesson -> {
                    val lesson = row.lesson
                    val view = WidgetUpdater.lessonRow(context, lesson, style, compact)
                    if (lesson == running) look.highlight(view, R.id.row_root)
                    if (isToday && lesson.end <= nowTime) view.setFloat(R.id.row_root, "setAlpha", 0.45f)

                    views.addView(R.id.widget_list, view)
                }
            }
        }

        return views
    }
}

/** Redraws the widgets right after an app update, which otherwise leaves them empty until the next refresh. */
class WidgetPackageReceiver : android.content.BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_MY_PACKAGE_REPLACED) WidgetUpdater.updateAll(context)
    }
}
