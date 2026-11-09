package sh.lsx.untisx.data.api

import sh.lsx.untisx.data.model.Absence
import sh.lsx.untisx.data.model.AuthResult
import sh.lsx.untisx.data.model.DayMessage
import sh.lsx.untisx.data.model.DirectMessage
import sh.lsx.untisx.data.model.ExamInfo
import sh.lsx.untisx.data.model.Holiday
import sh.lsx.untisx.data.model.MasterDataItem
import sh.lsx.untisx.data.model.MessagesData
import sh.lsx.untisx.data.model.School
import sh.lsx.untisx.data.model.SchoolYear
import sh.lsx.untisx.data.model.TimegridDay
import sh.lsx.untisx.data.model.TimetableTarget
import sh.lsx.untisx.data.model.TimetableEntry
import sh.lsx.untisx.data.prefs.AuthData
import sh.lsx.untisx.data.prefs.AccountPrefs
import java.time.LocalDate

object UntisClient {
    const val RPC_INVALID = -1
    const val RPC_NO_TIMETABLE = -7004
    const val RPC_TOO_MANY_SCHOOLS = -6003
    const val RPC_BAD_CREDENTIALS = -8500
    const val RPC_NO_RIGHTS = -8507
    const val RPC_SESSION_EXPIRED = -8520

    var allowHttpForTests: Boolean
        get() = UntisRpc.allowHttpForTests
        set(value) {
            UntisRpc.allowHttpForTests = value
        }

    /**
     * Points a debuggable build at a local test server (plain HTTP allowed).
     * Release builds never call this.
     */
    fun useLocalTestServer(baseUrl: String) {
        UntisRpc.allowHttpForTests = true
        UntisAuthApi.schoolSearchUrlOverride = baseUrl.trimEnd('/') + "/ms/schoolquery2"
    }

    fun clearSessionCache(accountId: String) = UntisRpc.clearSessionCache(accountId)

    suspend fun searchSchools(query: String): List<School> = UntisAuthApi.searchSchools(query)

    suspend fun authenticate(server: String, loginName: String, user: String, password: String): AuthResult =
        UntisAuthApi.authenticate(server, loginName, user, password)

    suspend fun logout(auth: AuthData) = UntisAuthApi.logout(auth)

    suspend fun getTimetable(
        auth: AuthData,
        startDate: Int,
        endDate: Int,
        target: TimetableTarget? = null,
    ): List<TimetableEntry> = InFlightRequests.read(listOf("legacy", AccountPrefs.accountId(auth), auth.session.sessionId, startDate, endDate, target ?: "own")) {
        UntisTimetableApi.getTimetable(auth, startDate, endDate, target)
    }

    /** Classes the account may see. Empty when the school does not allow it. */
    suspend fun getKlassen(auth: AuthData): List<MasterDataItem> = UntisMasterDataApi.getKlassen(auth)

    /** Students the account may see. Usually empty for student accounts. */
    suspend fun getStudents(auth: AuthData): List<MasterDataItem> = UntisMasterDataApi.getStudents(auth)

    suspend fun getSubjects(auth: AuthData): List<MasterDataItem> = UntisMasterDataApi.getSubjects(auth)

    suspend fun getTeachers(auth: AuthData): List<MasterDataItem> = UntisMasterDataApi.getTeachers(auth)

    suspend fun getRooms(auth: AuthData): List<MasterDataItem> = UntisMasterDataApi.getRooms(auth)

    suspend fun getHolidays(auth: AuthData): List<Holiday> = UntisMasterDataApi.getHolidays(auth)

    /** Time of the school's last Untis import (epoch millis); changes whenever the timetable data changed. */
    suspend fun getLatestImportTime(auth: AuthData): Long =
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            UntisRpc.rpcCall(auth, "getLatestImportTime").let { result ->
                (result as? kotlinx.serialization.json.JsonPrimitive)?.content?.toLongOrNull() ?: 0L
            }
        }

    data class MasterData(
        val subjects: List<MasterDataItem>,
        val teachers: List<MasterDataItem>,
        val rooms: List<MasterDataItem>,
        val holidays: List<Holiday>,
        val timegrid: List<TimegridDay>,
    )

    suspend fun fetchMasterData(auth: AuthData, password: String): MasterData =
        UntisMasterDataApi.fetchMasterData(auth, password)

    suspend fun fetchTimegridViaUserData(auth: AuthData, password: String): List<TimegridDay> =
        UntisMasterDataApi.fetchTimegridViaUserData(auth, password)

    suspend fun fetchTeachersViaUserData(auth: AuthData, password: String): List<MasterDataItem> =
        UntisMasterDataApi.fetchTeachersViaUserData(auth, password)

    suspend fun getTimetableIntern(
        auth: AuthData,
        password: String,
        from: LocalDate,
        to: LocalDate,
        target: TimetableTarget? = null,
    ): List<TimetableEntry> = InFlightRequests.read(listOf("internal", AccountPrefs.accountId(auth), from, to, target ?: "own")) {
        UntisTimetableApi.getTimetableIntern(auth, password, from, to, target)
    }

    suspend fun fetchTimetableEntries(
        auth: AuthData,
        password: String,
        from: LocalDate,
        to: LocalDate,
        target: TimetableTarget? = null,
    ): List<TimetableEntry>? = InFlightRequests.read(listOf("rest", AccountPrefs.accountId(auth), from, to, target ?: "own")) {
        UntisTimetableApi.fetchTimetableEntries(auth, password, from, to, target)
    }

    /** Week of another class or student via the WebUntis web page endpoint. Null if refused. */
    suspend fun fetchWebWeek(
        auth: AuthData,
        monday: LocalDate,
        target: TimetableTarget,
    ): List<TimetableEntry>? = InFlightRequests.read(listOf("web", AccountPrefs.accountId(auth), auth.session.sessionId, monday, target)) {
        UntisTimetableApi.fetchWebWeek(auth, monday, target)
    }

    suspend fun fetchTimegridFromInternalRpc(auth: AuthData): List<TimegridDay> =
        UntisTimetableApi.fetchTimegridFromInternalRpc(auth)

    suspend fun fetchTimegridRpc(auth: AuthData): List<TimegridDay> = UntisTimetableApi.fetchTimegridRpc(auth)

    suspend fun fetchExams(
        auth: AuthData,
        password: String,
        from: LocalDate,
        to: LocalDate,
    ): List<ExamInfo> = InFlightRequests.read(listOf("exams", AccountPrefs.accountId(auth), from, to)) {
        UntisExamsApi.fetchExams(auth, password, from, to)
    }

    /** Own absences; throws UntisException.Api with RPC_NO_RIGHTS if the school does not share them. */
    suspend fun fetchAbsences(
        auth: AuthData,
        password: String,
        from: LocalDate,
        to: LocalDate,
    ): List<Absence> = UntisAbsencesApi.fetchAbsences(auth, password, from, to)

    suspend fun fetchCurrentSchoolYear(auth: AuthData, password: String): SchoolYear? =
        UntisExamsApi.fetchCurrentSchoolYear(auth, password)

    suspend fun fetchMessages(auth: AuthData, password: String): MessagesData? =
        InFlightRequests.read(listOf("messages", AccountPrefs.accountId(auth))) { UntisMessagesApi.fetchMessages(auth, password) }

    suspend fun fetchMessage(auth: AuthData, password: String, id: Long): DirectMessage? =
        UntisMessagesApi.fetchMessage(auth, password, id)

    suspend fun fetchMessagesOfDay(
        auth: AuthData,
        password: String,
        date: LocalDate,
    ): List<DayMessage> = UntisMessagesApi.fetchMessagesOfDay(auth, password, date)

    data class PeriodNotes(
        val topic: String?,
        val notes: List<String>,
        val homeworks: List<String> = emptyList(),
        /** True when the teacher checked attendance for this lesson. */
        val attendanceChecked: Boolean? = null,
        /** True when an absence of the student is recorded for this lesson. */
        val absent: Boolean = false,
    )

    /** A teacher or room that was swapped: [current] replaces [previous] (either may be empty). */
    data class Swap(val kind: String, val current: String, val previous: String)

    suspend fun fetchPeriodNotes(
        auth: AuthData,
        password: String,
        ttId: Int,
    ): PeriodNotes? = UntisLessonApi.fetchPeriodNotes(auth, password, ttId)

    data class LessonDetail(
        val info: String?,
        val substitution: String?,
        val topic: String?,
        val notes: List<String>,
        val homeworks: List<String> = emptyList(),
        val swaps: List<Swap> = emptyList(),
        val exam: String? = null,
        val attendanceChecked: Boolean? = null,
        val absent: Boolean = false,
    )

    suspend fun fetchLessonDetails(
        auth: AuthData,
        password: String,
        entryId: Int,
        date: LocalDate,
        startTime: Int,
        endTime: Int,
    ): LessonDetail? = UntisLessonApi.fetchLessonDetails(auth, password, entryId, date, startTime, endTime)
}
