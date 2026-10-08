package sh.lsx.untisx.ui.timetable

import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import sh.lsx.untisx.data.api.UntisClient
import sh.lsx.untisx.data.model.Holiday
import sh.lsx.untisx.data.model.MasterDataItem
import sh.lsx.untisx.data.model.TimegridDay
import sh.lsx.untisx.data.prefs.AuthData

internal class TimetableStaticData(
    private val withPassword: suspend () -> String,
) {

    internal data class LegacyResult(
        val subjects: List<MasterDataItem>?,
        val teachers: List<MasterDataItem>?,
        val rooms: List<MasterDataItem>?,
        val holidays: List<Holiday>?,
        val timegrid: List<TimegridDay>?,
        val timegridFailed: Boolean,
    )

    private data class LegacyTimegrid(
        val days: List<TimegridDay>?,
        val failed: Boolean,
    )

    suspend fun loadMasterData(auth: AuthData): UntisClient.MasterData? =
        optional("masterData") { UntisClient.fetchMasterData(auth, withPassword()) }

    suspend fun loadLegacy(auth: AuthData): LegacyResult = coroutineScope {
        val subjects = async { optional("getSubjects") { UntisClient.getSubjects(auth) } }
        val teachers = async { loadTeachers(auth) }
        val rooms = async { optional("getRooms") { UntisClient.getRooms(auth) } }
        val holidays = async { optional("getHolidays") { UntisClient.getHolidays(auth) } }
        val timegrid = async {
            try {
                val days = resolveTimegrid(auth)
                LegacyTimegrid(days, failed = days == null)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.d(TAG, "static data: getTimegrid failed: ${e.javaClass.simpleName}")
                LegacyTimegrid(null, failed = false)
            }
        }

        val grid = timegrid.await()

        LegacyResult(
            subjects = subjects.await(),
            teachers = teachers.await(),
            rooms = rooms.await(),
            holidays = holidays.await(),
            timegrid = grid.days,
            timegridFailed = grid.failed,
        )
    }

    suspend fun resolveTimegrid(auth: AuthData): List<TimegridDay>? {
        val password: suspend () -> String = { withPassword() }

        val attempts: List<Pair<String, suspend () -> List<TimegridDay>>> = listOf(
            "getUserData2017" to { UntisClient.fetchTimegridViaUserData(auth, password()) },
            "getTimegrid (internal rpc)" to { UntisClient.fetchTimegridFromInternalRpc(auth) },
            "getTimegrid (public rpc)" to { UntisClient.fetchTimegridRpc(auth) },
        )

        for ((name, attempt) in attempts) {
            val days = try {
                attempt()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.d(TAG, "timegrid via $name failed")
                null
            } ?: continue

            if (days.isEmpty()) {
                Log.d(TAG, "timegrid via $name -> empty")
                continue
            }

            Log.d(TAG, "timegrid via $name -> ${days.size} days")
            return days
        }

        return null
    }

    private suspend fun <T> optional(label: String, block: suspend () -> T): T? =
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.d(TAG, "static data: $label failed: ${e.javaClass.simpleName}")
            null
        }

    private suspend fun loadTeachers(auth: AuthData): List<MasterDataItem>? {
        optional("getTeachers") { UntisClient.getTeachers(auth) }
            ?.takeIf { it.isNotEmpty() }
            ?.let { return it }

        return optional("getTeachers (user data)") {
            UntisClient.fetchTeachersViaUserData(auth, withPassword())
        }
    }

    private companion object {
        const val TAG = "TimetableStaticData"
    }
}
