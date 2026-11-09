package sh.lsx.untisx.data.rights

import android.content.Context
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import sh.lsx.untisx.data.TimetableCache
import sh.lsx.untisx.data.api.SessionManager
import sh.lsx.untisx.data.api.UntisClient
import sh.lsx.untisx.data.api.UntisException
import sh.lsx.untisx.data.model.TimetableTarget
import sh.lsx.untisx.data.model.toUntisDate
import sh.lsx.untisx.data.prefs.AccountPrefs
import sh.lsx.untisx.data.prefs.AuthData
import sh.lsx.untisx.data.prefs.PasswordVault
import java.time.DayOfWeek
import java.time.LocalDate

enum class ProbeStatus { ALLOWED, EMPTY, REFUSED, ERROR }

/** What one data source answered for this account. */
@Serializable
data class ProbeResult(val key: String, val status: ProbeStatus, val count: Int = -1)

/** The checks, in the order they are shown. */
enum class ProbeKey {
    TIMETABLE_REST,
    TIMETABLE_INTERNAL,
    TIMETABLE_LEGACY,
    OWN_CLASS,
    OTHER_CLASSES,
    EXAMS,
    ABSENCES,
    MESSAGES,
    DAY_MESSAGES,
    LESSON_NOTES,
    TEACHERS,
    STUDENTS,
    IMPORT_TIME,
}

/** Asks every data source once and records whether the school lets this account read it. */
object RightsProbe {
    private const val PREFS = "rights_probe"
    private val json = Json { ignoreUnknownKeys = true }

    data class Saved(val results: List<ProbeResult>, val at: Long)

    fun load(context: Context, accountId: String): Saved? {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val text = prefs.getString(accountId, null) ?: return null
        val results = runCatching { json.decodeFromString<List<ProbeResult>>(text) }.getOrNull() ?: return null
        return Saved(results, prefs.getLong("at_$accountId", 0L))
    }

    /** Last known answer for one check, null if never checked. */
    fun status(context: Context, accountId: String, key: ProbeKey): ProbeStatus? =
        load(context, accountId)?.results?.firstOrNull { it.key == key.name }?.status

    suspend fun run(context: Context, auth: AuthData, onProgress: (Int, Int) -> Unit = { _, _ -> }): List<ProbeResult> {
        val accountId = AccountPrefs.accountId(auth)
        val password = PasswordVault.retrieve(context, accountId)
        val monday = LocalDate.now().with(DayOfWeek.MONDAY)
        val friday = monday.plusDays(4)
        val results = ArrayList<ProbeResult>()
        val keys = ProbeKey.entries

        suspend fun check(key: ProbeKey, block: suspend () -> Int?) {
            onProgress(results.size, keys.size)
            results += try {
                val count = block()
                when {
                    count == null -> ProbeResult(key.name, ProbeStatus.REFUSED)
                    count == 0 -> ProbeResult(key.name, ProbeStatus.EMPTY, 0)
                    else -> ProbeResult(key.name, ProbeStatus.ALLOWED, count)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: UntisException.Api) {
                ProbeResult(key.name, ProbeStatus.REFUSED)
            } catch (e: UntisException.BadCredentials) {
                ProbeResult(key.name, ProbeStatus.ERROR)
            } catch (e: Exception) {
                ProbeResult(key.name, ProbeStatus.ERROR)
            }
        }

        suspend fun <T> session(block: suspend (AuthData) -> T): T =
            SessionManager.withValidSession(context, auth) { block(it) }.first

        fun pw() = password ?: throw UntisException.BadCredentials

        check(ProbeKey.TIMETABLE_REST) { UntisClient.fetchTimetableEntries(auth, pw(), monday, friday)?.size }
        check(ProbeKey.TIMETABLE_INTERNAL) { UntisClient.getTimetableIntern(auth, pw(), monday, friday).size }
        check(ProbeKey.TIMETABLE_LEGACY) { session { UntisClient.getTimetable(it, monday.toUntisDate(), friday.toUntisDate()) }.size }

        val ownClass = auth.session.klasseId
        check(ProbeKey.OWN_CLASS) {
            if (ownClass == 0) null else session { UntisClient.fetchWebWeek(it, monday, TimetableTarget(TimetableTarget.CLASS, ownClass, "")) }?.size
        }
        check(ProbeKey.OTHER_CLASSES) {
            val other = session { UntisClient.getKlassen(it) }.firstOrNull { it.id > 0 && it.id != ownClass } ?: return@check null
            session { UntisClient.fetchWebWeek(it, monday, TimetableTarget(TimetableTarget.CLASS, other.id, other.name)) }?.size
        }
        check(ProbeKey.EXAMS) { UntisClient.fetchExams(auth, pw(), monday, monday.plusMonths(3)).size }
        check(ProbeKey.ABSENCES) { UntisClient.fetchAbsences(auth, pw(), monday.minusMonths(3), LocalDate.now()).size }
        check(ProbeKey.MESSAGES) { UntisClient.fetchMessages(auth, pw())?.let { 1 } }
        check(ProbeKey.DAY_MESSAGES) { UntisClient.fetchMessagesOfDay(auth, pw(), LocalDate.now()).size }
        check(ProbeKey.LESSON_NOTES) {
            val lesson = TimetableCache.load(context, accountId)?.weeks?.values?.flatten()
                ?.firstOrNull { it.subject.isNotEmpty() && it.id > 0 } ?: return@check 0
            UntisClient.fetchPeriodNotes(auth, pw(), lesson.id)?.let { 1 } ?: 0
        }
        check(ProbeKey.TEACHERS) { session { UntisClient.getTeachers(it) }.size }
        check(ProbeKey.STUDENTS) { session { UntisClient.getStudents(it) }.size }
        check(ProbeKey.IMPORT_TIME) { session { UntisClient.getLatestImportTime(it) }.let { if (it > 0) 1 else 0 } }
        onProgress(keys.size, keys.size)

        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(accountId, json.encodeToString(results))
            .putLong("at_$accountId", System.currentTimeMillis())
            .apply()

        return results
    }
}
