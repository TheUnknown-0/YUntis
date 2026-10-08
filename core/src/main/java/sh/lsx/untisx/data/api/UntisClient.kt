package sh.lsx.untisx.data.api

import kotlinx.serialization.Serializable
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
import sh.lsx.untisx.data.model.TimetableEntry
import sh.lsx.untisx.data.prefs.AuthData
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

    fun clearSessionCache(accountId: String) = UntisRpc.clearSessionCache(accountId)

    suspend fun searchSchools(query: String): List<School> = UntisAuthApi.searchSchools(query)

    suspend fun authenticate(server: String, loginName: String, user: String, password: String): AuthResult =
        UntisAuthApi.authenticate(server, loginName, user, password)

    suspend fun logout(auth: AuthData) = UntisAuthApi.logout(auth)

    suspend fun getTimetable(
        auth: AuthData,
        startDate: Int,
        endDate: Int,
    ): List<TimetableEntry> = UntisTimetableApi.getTimetable(auth, startDate, endDate)

    suspend fun getSubjects(auth: AuthData): List<MasterDataItem> = UntisMasterDataApi.getSubjects(auth)

    suspend fun getTeachers(auth: AuthData): List<MasterDataItem> = UntisMasterDataApi.getTeachers(auth)

    suspend fun getRooms(auth: AuthData): List<MasterDataItem> = UntisMasterDataApi.getRooms(auth)

    suspend fun getHolidays(auth: AuthData): List<Holiday> = UntisMasterDataApi.getHolidays(auth)

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
    ): List<TimetableEntry> = UntisTimetableApi.getTimetableIntern(auth, password, from, to)

    suspend fun fetchTimetableEntries(
        auth: AuthData,
        password: String,
        from: LocalDate,
        to: LocalDate,
    ): List<TimetableEntry>? = UntisTimetableApi.fetchTimetableEntries(auth, password, from, to)

    suspend fun fetchTimegridFromInternalRpc(auth: AuthData): List<TimegridDay> =
        UntisTimetableApi.fetchTimegridFromInternalRpc(auth)

    suspend fun fetchTimegridRpc(auth: AuthData): List<TimegridDay> = UntisTimetableApi.fetchTimegridRpc(auth)

    suspend fun fetchExams(
        auth: AuthData,
        password: String,
        from: LocalDate,
        to: LocalDate,
    ): List<ExamInfo> = UntisExamsApi.fetchExams(auth, password, from, to)

    suspend fun fetchCurrentSchoolYear(auth: AuthData, password: String): SchoolYear? =
        UntisExamsApi.fetchCurrentSchoolYear(auth, password)

    suspend fun fetchMessages(auth: AuthData, password: String): MessagesData? =
        UntisMessagesApi.fetchMessages(auth, password)

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
    )

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
