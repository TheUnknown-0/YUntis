package sh.lsx.untisx.data.widget

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.text.SpannableString
import android.text.Spanned
import android.text.style.StrikethroughSpan
import android.view.View
import android.widget.RemoteViews
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import sh.lsx.untisx.MainActivity
import sh.lsx.untisx.R
import sh.lsx.untisx.data.SubjectColorResolver
import sh.lsx.untisx.data.SubjectColorsPrefs
import sh.lsx.untisx.data.TimetableCache
import sh.lsx.untisx.data.model.toUntisDate
import sh.lsx.untisx.data.notifications.Notifications
import sh.lsx.untisx.data.prefs.AccountPrefs
import sh.lsx.untisx.data.prefs.AccountStore
import sh.lsx.untisx.data.subjectLongLabel
import sh.lsx.untisx.data.subjectShortLabel
import sh.lsx.untisx.ui.util.AppDateFormats
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/** Today's (or the next school day's) lessons. */
class DayWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) = WidgetUpdater.updateAll(context)

    override fun onAppWidgetOptionsChanged(context: Context, manager: AppWidgetManager, id: Int, options: android.os.Bundle) =
        WidgetUpdater.updateAll(context)
}

/** The lesson running now or coming next. */
class NextWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) = WidgetUpdater.updateAll(context)
}

object WidgetUpdater {
    private const val REQUEST_REFRESH = 4501

    /** Rows that fit a large widget; a small one simply cuts the list off. */
    private const val MAX_ROWS = 9
    private const val HEADER_DP = 82
    private const val ROW_DP = 50
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** How a lesson looks in a widget, resolved once per update. */
    internal class Style(
        val label: (WidgetLesson) -> String,
        val color: (String) -> Int,
        val cancelled: Int,
        val changed: Int,
        val look: WidgetLook,
    )

    /** Redraws all widgets from the cached timetable of the active account. Cheap, no network. */
    fun updateAll(context: Context) {
        val app = context.applicationContext
        val manager = AppWidgetManager.getInstance(app)
        val dayIds = manager.getAppWidgetIds(ComponentName(app, DayWidgetProvider::class.java))
        val nextIds = manager.getAppWidgetIds(ComponentName(app, NextWidgetProvider::class.java))
        val fullIds = manager.getAppWidgetIds(ComponentName(app, FullDayWidgetProvider::class.java))
        if (dayIds.isEmpty() && nextIds.isEmpty() && fullIds.isEmpty()) return

        scope.launch {
            AccountStore.ensureLoaded(app)
            val auth = AccountStore.active
            val cache = auth?.let { TimetableCache.load(app, AccountPrefs.accountId(it)) }
            val now = LocalDateTime.now()
            val day = WidgetContent.build(
                now = now,
                entries = cache?.weeks?.values?.flatten().orEmpty(),
                subjects = cache?.subjects?.associateBy { it.id }.orEmpty(),
                rooms = cache?.rooms?.associateBy { it.id }.orEmpty(),
                teachers = cache?.teachers?.associateBy { it.id }.orEmpty(),
            )

            // the same subject colours and short names as in the app
            val aliases = SubjectColorsPrefs.getAliases(app)
            val colors = SubjectColorResolver.cardColors(SubjectColorsPrefs.getKnown(app), SubjectColorsPrefs.sources(app))
            val fallback = SubjectColorsPrefs.getDefaultColor(app)
            val look = WidgetLook.resolve(app)
            val style = Style(
                // an own long name wins, then the school's full name, then the short code
                label = { lesson ->
                    subjectLongLabel(aliases, lesson.subject).takeIf { it != lesson.subject && it.isNotBlank() }
                        ?: lesson.subjectLong.ifBlank { subjectShortLabel(aliases, lesson.subject).ifBlank { lesson.subject } }
                },
                color = { colors[it] ?: fallback },
                cancelled = look.colors?.cancelled ?: ContextCompat.getColor(app, R.color.widget_cancelled),
                changed = look.colors?.changed ?: ContextCompat.getColor(app, R.color.widget_changed),
                look = look,
            )

            dayIds.forEach { id ->
                val height = manager.getAppWidgetOptions(id).getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT)
                manager.updateAppWidget(id, dayViews(app, day, now, auth != null, style, rowsFor(height)))
            }
            nextIds.forEach { manager.updateAppWidget(it, nextViews(app, day, now, auth != null, style)) }

            val entries = cache?.weeks?.values?.flatten().orEmpty()
            val subjects = cache?.subjects?.associateBy { it.id }.orEmpty()
            val rooms = cache?.rooms?.associateBy { it.id }.orEmpty()
            val teachers = cache?.teachers?.associateBy { it.id }.orEmpty()
            fullIds.forEach { id ->
                val height = manager.getAppWidgetOptions(id).getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT)
                manager.updateAppWidget(
                    id,
                    FullDayWidget.views(
                        context = app,
                        id = id,
                        heightDp = height,
                        entries = entries,
                        lessonsFor = { raw -> WidgetContent.lessonsOf(raw, subjects, rooms, teachers) },
                        auto = day.date,
                        now = now,
                        loggedIn = auth != null,
                        style = style,
                    ),
                )
            }

            scheduleRefresh(app, WidgetContent.nextRefresh(now, day))
        }
    }

    internal fun openApp(context: Context, date: LocalDate): PendingIntent = PendingIntent.getActivity(
        context,
        REQUEST_REFRESH + 1,
        Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            .putExtra(Notifications.EXTRA_FOCUS_DATE, date.toUntisDate()),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    internal fun dayLabel(context: Context, date: LocalDate, now: LocalDateTime): String = when (date) {
        now.toLocalDate() -> context.getString(R.string.widget_today_label)
        now.toLocalDate().plusDays(1) -> context.getString(R.string.widget_tomorrow_label)
        else -> AppDateFormats.formatter(context, "EEEE").format(date)
    }

    internal fun time(context: Context, untis: Int): String =
        LocalTime.of(untis / 100, untis % 100).format(AppDateFormats.time(context))

    /** A bright subject colour, a little calmer for cancelled lessons. */
    internal fun barColor(lesson: WidgetLesson, style: Style): Int = when {
        lesson.cancelled -> style.cancelled
        else -> ColorUtils.setAlphaComponent(style.color(lesson.subject), 255)
    }

    internal fun subjectText(lesson: WidgetLesson, style: Style): CharSequence {
        val label = style.label(lesson)
        if (!lesson.cancelled) return style.look.text(label)
        return style.look.text(SpannableString(label).apply { setSpan(StrikethroughSpan(), 0, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE) })
    }

    internal fun detailText(context: Context, lesson: WidgetLesson): String = when {
        lesson.cancelled -> context.getString(R.string.widget_cancelled)
        else -> listOf(lesson.teacher, lesson.room, lesson.info).filter { it.isNotBlank() }.joinToString(" · ")
    }

    /** Whole rows that fit below the header; unknown heights get a sensible default. */
    private fun rowsFor(heightDp: Int): Int =
        if (heightDp <= 0) 4 else ((heightDp - HEADER_DP) / ROW_DP).coerceIn(1, MAX_ROWS)

    /** One lesson line, in the colours and font of the chosen widget style. */
    internal fun lessonRow(context: Context, lesson: WidgetLesson, style: Style, compact: Boolean): RemoteViews {
        val look = style.look
        val row = RemoteViews(context.packageName, if (compact) R.layout.widget_lesson_row_compact else R.layout.widget_lesson_row)
        row.setInt(R.id.row_bar, "setColorFilter", barColor(lesson, style))
        row.setTextViewText(R.id.row_subject, subjectText(lesson, style))
        look.setText(row, R.id.row_detail, detailText(context, lesson))
        look.setText(row, R.id.row_start, time(context, lesson.start))
        if (!compact) look.setText(row, R.id.row_end, time(context, lesson.end))

        look.color(row, R.id.row_subject, WidgetLook.Role.TEXT)
        look.color(row, R.id.row_detail, WidgetLook.Role.SOFT)
        look.color(row, R.id.row_start, if (compact) WidgetLook.Role.SOFT else WidgetLook.Role.TEXT)
        if (!compact) look.color(row, R.id.row_end, WidgetLook.Role.SOFT)

        when {
            lesson.cancelled -> {
                row.setTextColor(R.id.row_subject, style.cancelled)
                row.setTextColor(R.id.row_detail, style.cancelled)
            }
            lesson.changed -> row.setTextColor(R.id.row_detail, style.changed)
        }
        return row
    }

    /** Header texts shared by the day widgets. */
    internal fun header(views: RemoteViews, look: WidgetLook, label: String, title: String, count: String) {
        look.applyFrame(views)
        look.setText(views, R.id.widget_label, label)
        look.setText(views, R.id.widget_title, title)
        look.setText(views, R.id.widget_count, count)
        look.color(views, R.id.widget_label, WidgetLook.Role.ACCENT)
        look.color(views, R.id.widget_title, WidgetLook.Role.TEXT)
        look.color(views, R.id.widget_count, WidgetLook.Role.SOFT)
        look.color(views, R.id.widget_empty, WidgetLook.Role.SOFT)
    }

    private fun dayViews(
        context: Context,
        day: WidgetDay,
        now: LocalDateTime,
        loggedIn: Boolean,
        style: Style,
        rows: Int,
    ): RemoteViews {
        val look = style.look
        val views = RemoteViews(context.packageName, R.layout.widget_day)
        views.setOnClickPendingIntent(R.id.widget_root, openApp(context, day.date))
        views.removeAllViews(R.id.widget_list)

        // today only what is still ahead; another day completely
        val nowTime = now.hour * 100 + now.minute
        val shown = if (day.date == now.toLocalDate()) day.lessons.filter { it.end > nowTime } else day.lessons
        val held = day.lessons.count { !it.cancelled }

        header(
            views,
            look,
            label = dayLabel(context, day.date, now),
            title = AppDateFormats.formatter(context, "EEEdMMM").format(day.date),
            count = if (day.lessons.isEmpty()) "" else context.resources.getQuantityString(R.plurals.widget_lesson_count, held, held),
        )

        if (!loggedIn || shown.isEmpty()) {
            views.setViewVisibility(R.id.widget_empty, View.VISIBLE)
            look.setText(views, R.id.widget_empty, context.getString(if (loggedIn) R.string.widget_no_lessons else R.string.widget_login))
            return views
        }
        views.setViewVisibility(R.id.widget_empty, View.GONE)

        shown.take(rows).forEach { lesson ->
            val row = lessonRow(context, lesson, style, compact = false)
            if (lesson == day.next) look.highlight(row, R.id.row_root)
            views.addView(R.id.widget_list, row)
        }

        return views
    }

    private fun nextViews(context: Context, day: WidgetDay, now: LocalDateTime, loggedIn: Boolean, style: Style): RemoteViews {
        val look = style.look
        val views = RemoteViews(context.packageName, R.layout.widget_next)
        views.setOnClickPendingIntent(R.id.widget_root, openApp(context, day.date))
        look.applyFrame(views)
        look.color(views, R.id.widget_label, WidgetLook.Role.ACCENT)
        look.color(views, R.id.widget_main, WidgetLook.Role.TEXT)
        look.color(views, R.id.widget_sub, WidgetLook.Role.SOFT)
        look.color(views, R.id.row_start, WidgetLook.Role.TEXT)
        look.color(views, R.id.row_end, WidgetLook.Role.SOFT)
        val next = day.next

        if (!loggedIn || next == null) {
            look.setText(views, R.id.widget_label, dayLabel(context, day.date, now))
            look.setText(views, R.id.widget_main, context.getString(if (loggedIn) R.string.widget_no_lessons_short else R.string.widget_login))
            views.setTextViewText(R.id.widget_sub, "")
            views.setTextViewText(R.id.row_start, "")
            views.setTextViewText(R.id.row_end, "")
            views.setInt(R.id.row_bar, "setColorFilter", Color.GRAY)
            return views
        }

        val label = when {
            day.running -> context.getString(R.string.widget_now)
            day.date == now.toLocalDate() -> context.getString(R.string.widget_next)
            else -> dayLabel(context, day.date, now)
        }
        look.setText(views, R.id.widget_label, label)
        look.setText(views, R.id.widget_main, style.label(next))
        look.setText(views, R.id.widget_sub, detailText(context, next))
        if (next.changed) views.setTextColor(R.id.widget_sub, style.changed)
        look.setText(views, R.id.row_start, time(context, next.start))
        look.setText(views, R.id.row_end, time(context, next.end))
        views.setInt(R.id.row_bar, "setColorFilter", barColor(next, style))
        return views
    }

    private fun scheduleRefresh(context: Context, at: LocalDateTime) {
        val alarm = context.getSystemService(AlarmManager::class.java) ?: return
        val intent = PendingIntent.getBroadcast(
            context,
            REQUEST_REFRESH,
            Intent(context, DayWidgetProvider::class.java)
                .setAction(AppWidgetManager.ACTION_APPWIDGET_UPDATE)
                // AppWidgetProvider only calls onUpdate when ids are present; the ids themselves are not used
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, intArrayOf(0)),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        // inexact is fine: a widget a minute late is better than an exact alarm permission
        alarm.setAndAllowWhileIdle(AlarmManager.RTC, at.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(), intent)
    }
}
