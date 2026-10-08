package sh.lsx.untisx.data.api

sealed class UntisException(message: String) : Exception(message) {
    data object Network : UntisException("No connection to the Untis server.")
    data object Certificate : UntisException("The server certificate could not be verified.")
    data object BadCredentials : UntisException("Wrong username or password.")
    data object TooManyResults : UntisException("Too many schools match your search.")
    data object SessionExpired : UntisException("Session expired.")
    data class RateLimited(val retryAfterSeconds: Long? = null) : UntisException("Rate limit reached.")
    data class Api(val code: Int, override val message: String) : UntisException(message)
}
