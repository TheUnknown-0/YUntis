package sh.lsx.untisx.data.notifications

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlinx.coroutines.CancellationException
import sh.lsx.untisx.data.TimetableCache
import sh.lsx.untisx.data.api.UntisClient
import sh.lsx.untisx.data.model.toUntisDate
import sh.lsx.untisx.data.prefs.AccountPrefs
import sh.lsx.untisx.data.prefs.PasswordVault
import java.time.LocalDate

class ExamReminderWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val context = applicationContext

        Notifications.ensureChannels(context)

        if (!NotificationPrefs.isEnabled(context) || !NotificationPrefs.examRemindersEnabled(context)) {
            return Result.success()
        }

        if (!Notifications.canPost(context)) return Result.success()

        val auth = AccountPrefs.getActive(context) ?: return Result.success()
        val accountId = AccountPrefs.accountId(auth)
        val password = PasswordVault.retrieve(context, accountId) ?: return Result.success()

        return try {
            val today = LocalDate.now()
            val daysBefore = NotificationPrefs.examReminderDaysBefore(context)
            val target = today.plusDays(daysBefore.toLong())

            val exams = UntisClient.fetchExams(auth, password, today.minusDays(1), target.plusDays(1))
            val due = exams.filter { it.date == target.toUntisDate() }

            if (due.isEmpty()) return Result.success()

            val sent = NotificationPrefs.examReminderSentKeys(context)
            val fresh = due.filterNot { reminderKey(accountId, it.id, it.date) in sent }

            if (fresh.isEmpty()) return Result.success()

            val cache = TimetableCache.load(context, accountId)
            val subjects = cache?.subjects?.associateBy { it.id }.orEmpty()
            val teachers = cache?.teachers?.associateBy { it.id }.orEmpty()
            val rooms = cache?.rooms?.associateBy { it.id }.orEmpty()

            val reminders = fresh.map { exam ->
                ExamReminder(
                    exam = exam,
                    subject = subjects[exam.subjectId]?.name?.takeIf { it.isNotBlank() }.orEmpty(),
                    rooms = exam.roomIds
                        .mapNotNull { id -> rooms[id]?.name?.takeIf { it.isNotBlank() } }
                        .distinct()
                        .joinToString(", "),
                    teachers = exam.teacherIds
                        .mapNotNull { id -> teachers[id]?.name?.takeIf { it.isNotBlank() } }
                        .distinct()
                        .joinToString(", "),
                )
            }

            Notifications.notifyExamReminders(
                context = context,
                reminders = reminders,
                silent = NotificationRules.isDuringLessons(context),
            )

            val cutoff = today.minusDays(14).toUntisDate()
            val updated = (sent + fresh.map { reminderKey(accountId, it.id, it.date) })
                .filter { key ->
                    val date = key.substringAfterLast('|').toIntOrNull()
                    date != null && date >= cutoff
                }
                .toSet()

            NotificationPrefs.setExamReminderSentKeys(context, updated)

            Result.success()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.d(TAG, "exam reminder failed: ${e.javaClass.simpleName}")

            if (runAttemptCount < 2) Result.retry() else Result.success()
        }
    }

    private fun reminderKey(accountId: String, examId: Int, date: Int): String = "$accountId|$examId|$date"

    companion object {
        private const val TAG = "ExamReminderWorker"
    }
}
