package sh.lsx.untisx.data.wear

import kotlinx.serialization.Serializable
import sh.lsx.untisx.data.SubjectAlias
import sh.lsx.untisx.data.model.Holiday
import sh.lsx.untisx.data.model.TimegridDay
import sh.lsx.untisx.data.model.TimetableEntry

@Serializable
data class WearTimetablePayload(
    val version: Int = VERSION,
    val accountId: String = "",
    val displayName: String = "",
    val server: String = "",
    val fetchedAt: Long = 0,
    val weekFetchedAt: Map<String, Long> = emptyMap(),
    val sentAt: Long = 0,
    val requestId: String = "",
    val refreshResult: WearRefreshResult = WearRefreshResult.CACHE,
    val defaultColor: Int = 0,
    val weeks: Map<String, List<TimetableEntry>> = emptyMap(),
    val holidays: List<Holiday> = emptyList(),
    val timegrid: List<TimegridDay> = emptyList(),
    val restrictedDays: Set<Int> = emptySet(),
    val subjectColors: Map<String, Int> = emptyMap(),
    val subjectAliases: Map<String, SubjectAlias> = emptyMap(),
) {
    companion object {
        const val VERSION = 1
    }
}

@Serializable
enum class WearRefreshResult { CACHE, UPDATED, FAILED }
