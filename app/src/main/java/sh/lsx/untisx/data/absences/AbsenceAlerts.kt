package sh.lsx.untisx.data.absences

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CancellationException
import sh.lsx.untisx.MainActivity
import sh.lsx.untisx.R
import sh.lsx.untisx.data.api.UntisClient
import sh.lsx.untisx.data.api.UntisException
import sh.lsx.untisx.data.model.Absence
import sh.lsx.untisx.data.model.toUntisLocalDateOrNull
import sh.lsx.untisx.data.notifications.Notifications
import sh.lsx.untisx.data.prefs.AuthData
import sh.lsx.untisx.data.prefs.PasswordVault
import sh.lsx.untisx.ui.util.AppDateFormats
import java.time.LocalDate
import java.time.LocalTime

/** Tells the user once about every absence the school records, so wrong entries are noticed in time. */
object AbsenceAlerts {
    private const val PREFS = "absence_alerts"
    private const val CHANNEL = "new_absences"
    private const val NOTIFICATION_ID = 720_001
    private const val CHECK_EVERY_MS = 6 * 60 * 60 * 1000L
    private const val PAUSE_WHEN_REFUSED_MS = 7 * 24 * 60 * 60 * 1000L
    const val FOCUS_ABSENCES = "absences"
    private const val TAG = "AbsenceAlerts"

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun enabled(context: Context) = prefs(context).getBoolean("enabled", true)
    fun setEnabled(context: Context, on: Boolean) = prefs(context).edit().putBoolean("enabled", on).apply()

    /** Stable identity of an absence, also when the school sends no id. */
    fun keyOf(absence: Absence): String =
        if (absence.id != 0) "id:${absence.id}" else "t:${absence.date}-${absence.startTime}-${absence.endDate}-${absence.endTime}"

    /** Absences not seen before; with no baseline yet nothing counts as new. */
    fun newOnes(current: List<Absence>, known: Set<String>?): List<Absence> =
        if (known == null) emptyList() else current.filter { keyOf(it) !in known }

    suspend fun check(context: Context, auth: AuthData, accountId: String) {
        if (!enabled(context)) return
        val prefs = prefs(context)
        val now = System.currentTimeMillis()
        if (now - prefs.getLong("last_$accountId", 0L) < CHECK_EVERY_MS) return
        if (now < prefs.getLong("paused_$accountId", 0L)) return

        val password = PasswordVault.retrieve(context, accountId) ?: return
        val today = LocalDate.now()

        val absences = try {
            UntisClient.fetchAbsences(auth, password, today.minusDays(45), today)
        } catch (e: CancellationException) {
            throw e
        } catch (e: UntisException.Api) {
            Log.d(TAG, "absences not shared, pausing")
            prefs.edit().putLong("paused_$accountId", now + PAUSE_WHEN_REFUSED_MS).apply()
            return
        } catch (e: Exception) {
            Log.d(TAG, "absence check failed: ${e.javaClass.simpleName}")
            return
        }

        val knownKey = "known_$accountId"
        val known = prefs.getStringSet(knownKey, null)?.toSet()
        val fresh = newOnes(absences, known)

        prefs.edit()
            .putStringSet(knownKey, (known.orEmpty() + absences.map(::keyOf)).toSet())
            .putLong("last_$accountId", now)
            .apply()

        if (fresh.isNotEmpty()) notify(context, fresh)
        OwnAbsencesStore.saveSchoolCache(context, accountId, absences)
    }

    @SuppressLint("MissingPermission")
    private fun notify(context: Context, fresh: List<Absence>) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        context.getSystemService(NotificationManager::class.java)?.createNotificationChannel(
            NotificationChannel(CHANNEL, context.getString(R.string.absence_alert_channel), NotificationManager.IMPORTANCE_DEFAULT),
        )

        val dateFormat = AppDateFormats.formatter(context, "EEEdMMM")
        val timeFormat = AppDateFormats.time(context)
        val lines = fresh.sortedBy { it.date * 10000L + it.startTime }.map { absence ->
            val day = absence.date.toUntisLocalDateOrNull()?.let(dateFormat::format).orEmpty()
            val from = LocalTime.of(absence.startTime / 100, absence.startTime % 100).format(timeFormat)
            val to = LocalTime.of((absence.endTime / 100).coerceIn(0, 23), absence.endTime % 100).format(timeFormat)
            val status = context.getString(if (absence.excused) R.string.absences_excused_one else R.string.absences_unexcused_one)
            "$day, $from – $to · $status"
        }

        val open = PendingIntent.getActivity(
            context,
            NOTIFICATION_ID,
            Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                .putExtra(Notifications.EXTRA_FOCUS_SCREEN, FOCUS_ABSENCES),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        NotificationManagerCompat.from(context).notify(
            NOTIFICATION_ID,
            NotificationCompat.Builder(context, CHANNEL)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(context.resources.getQuantityString(R.plurals.absence_alert_title, fresh.size, fresh.size))
                .setContentText(lines.first())
                .setStyle(NotificationCompat.BigTextStyle().bigText(lines.joinToString("\n")))
                .setContentIntent(open)
                .setAutoCancel(true)
                .build(),
        )
    }
}
