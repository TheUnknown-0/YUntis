package sh.lsx.untisx.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
data class JsonRpcRequest(
    val id: Int,
    val method: String,
    val params: JsonElement,
    val jsonrpc: String = "2.0",
)

@Serializable
data class JsonRpcResponse(
    @SerialName("jsonrpc") val jsonRpc: String? = null,
    val id: JsonElement? = null,
    val result: JsonElement? = null,
    val error: JsonRpcError? = null,
)

@Serializable
data class JsonRpcError(
    val code: Int,
    val message: String = "",
)

@Serializable
data class AuthResult(
    val sessionId: String = "",
    val personType: Int = 0,
    val personId: Int = 0,
    val klasseId: Int = 0,
)

@Serializable
data class SchoolSearchResult(
    val size: Int = 0,
    val schools: List<School> = emptyList(),
)

@Serializable
data class School(
    @SerialName("schoolId") val id: Int = 0,
    val server: String = "",
    val serverUrl: String? = null,
    val displayName: String = "",
    val loginName: String = "",
    val address: String = "",
    val useMobileServiceUrlAndroid: Boolean = false,
    val mobileServiceUrl: String? = null,
)
