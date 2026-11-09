package sh.lsx.untisx.data.notifications

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import kotlinx.coroutines.CancellationException
import sh.lsx.untisx.data.OwnExamsStore
import sh.lsx.untisx.data.SubjectColorsPrefs
import sh.lsx.untisx.data.TimetableCache
import sh.lsx.untisx.data.subjectLongLabel
import sh.lsx.untisx.data.api.UntisClient
import sh.lsx.untisx.data.api.UntisException
import sh.lsx.untisx.data.model.toUntisDate
import sh.lsx.untisx.data.prefs.AccountPrefs
import sh.lsx.untisx.data.prefs.AccountStore
import sh.lsx.untisx.data.prefs.CredentialsPrefs
import sh.lsx.untisx.data.prefs.PasswordVault
import java.io.IOException
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

        AccountStore.ensureLoaded(context)

        val auth = AccountStore.active ?: return Result.success()
        val accountId = AccountPrefs.accountId(auth)

        if (CredentialsPrefs.isInvalid(context, accountId)) return Result.success()

        val password = PasswordVault.retrieve(context, accountId) ?: return Result.success()

        return try {
            val today = LocalDate.now()
            val daysBefore = NotificationPrefs.examReminderDaysBefore(context)
            val target = today.plusDays(daysBefore.toLong())

            // own exams count like the school's exams
            val exams = UntisClient.fetchExams(auth, password, today.minusDays(1), target.plusDays(1)) +
                OwnExamsStore.load(context, accountId)
            val due = exams.filter { it.date == target.toUntisDate() }

            if (due.isEmpty()) return Result.success()

            val sent = NotificationPrefs.examReminderSentKeys(context)
            val fresh = due.filterNot { reminderKey(accountId, it.id, it.date) in sent }

            if (fresh.isEmpty()) return Result.success()

            val cache = TimetableCache.load(context, accountId)
            val subjects = cache?.subjects?.associateBy { it.id }.orEmpty()
            val teachers = cache?.teachers?.associateBy { it.id }.orEmpty()
            val rooms = cache?.rooms?.associateBy { it.id }.orEmpty()
            val subjectAliases = SubjectColorsPrefs.getAliases(context)

            val reminders = fresh.map { exam ->
                ExamReminder(
                    exam = exam,
                    subject = subjectLongLabel(
                        subjectAliases,
                        subjects[exam.subjectId]?.name?.takeIf { it.isNotBlank() } ?: exam.subjectName,
                    ),
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
        } catch (e: UntisException.BadCredentials) {
            Log.d(TAG, "exam reminder stopped: bad credentials")

            if (CredentialsPrefs.markInvalid(context, accountId)) {
                Notifications.notifyLoginRequired(context)
            }

            Result.success()
        } catch (e: UntisException.Api) {
            Log.d(TAG, "exam reminder stopped: api error ${e.code}")
            Result.success()
        } catch (e: UntisException.Network) {
            Log.d(TAG, "exam reminder failed: network")
            if (runAttemptCount < 2) Result.retry() else Result.success()
        } catch (e: IOException) {
            Log.d(TAG, "exam reminder failed: io")
            if (runAttemptCount < 2) Result.retry() else Result.success()
        } catch (e: Exception) {
            Log.d(TAG, "exam reminder failed: ${e.javaClass.simpleName}")
            Result.success()
        }
    }

    override suspend fun getForegroundInfo(): ForegroundInfo =
        ForegroundInfo(
            Notifications.FOREGROUND_EXAM_ID,
            Notifications.foregroundNotification(applicationContext),
        )

    private fun reminderKey(accountId: String, examId: Int, date: Int): String = "$accountId|$examId|$date"

    companion object {
        private const val TAG = "ExamReminderWorker"
    }
}
