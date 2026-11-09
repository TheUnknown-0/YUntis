package sh.lsx.untisx.data.api

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.put
import sh.lsx.untisx.data.model.AuthResult
import sh.lsx.untisx.data.model.School
import sh.lsx.untisx.data.model.SchoolSearchResult
import sh.lsx.untisx.data.prefs.AuthData

internal object UntisAuthApi {

    private const val SCHOOL_SEARCH_URL = "https://mobile.webuntis.com/ms/schoolquery2"

    /** Only set by debuggable builds that point at a local test server. */
    @Volatile var schoolSearchUrlOverride: String? = null
    private const val CLIENT_NAME = "YUntis"

    suspend fun searchSchools(query: String): List<School> = withContext(Dispatchers.IO) {
        val params = buildJsonArray {
            add(
                buildJsonObject {
                    put("schoolid", 0)
                    put("search", query.trim())
                },
            )
        }

        try {
            val result = UntisRpc.executeRpc(schoolSearchUrlOverride ?: SCHOOL_SEARCH_URL, "searchSchool", params)

            val schools = runCatching {
                UntisRpc.json.decodeFromJsonElement<SchoolSearchResult>(result).schools
            }.getOrElse {
                UntisRpc.log("school search returned an unparseable response")
                return@withContext emptyList()
            }

            UntisRpc.log("school search -> ${schools.size} schools")
            schools.take(20)
        } catch (e: UntisException.Api) {
            UntisRpc.log("school search API error ${e.code}")
            if (e.code == UntisClient.RPC_TOO_MANY_SCHOOLS) throw UntisException.TooManyResults
            emptyList()
        }
    }

    suspend fun authenticate(server: String, loginName: String, user: String, password: String): AuthResult =
        withContext(Dispatchers.IO) {
            val params = buildJsonObject {
                put("user", user)
                put("password", password)
                put("client", CLIENT_NAME)
            }

            val url = UntisRpc.buildUrl(server, loginName)
            val result = UntisRpc.executeRpc(url.toString(), "authenticate", params)
            UntisRpc.json.decodeFromJsonElement<AuthResult>(result)
        }

    suspend fun logout(auth: AuthData) = withContext(Dispatchers.IO) {
        runCatching { UntisRpc.rpcCall(auth, "logout") }
            .onFailure { UntisRpc.log("server logout failed") }
        Unit
    }
}
