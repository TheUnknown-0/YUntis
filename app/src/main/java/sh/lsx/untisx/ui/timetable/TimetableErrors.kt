package sh.lsx.untisx.ui.timetable

import android.content.Context
import sh.lsx.untisx.R
import sh.lsx.untisx.data.api.UntisException
import sh.lsx.untisx.ui.util.AppDateFormats
import sh.lsx.untisx.ui.util.UiText
import java.time.Instant
import java.time.ZoneId

sealed interface LoadError {
    val text: UiText

    data class Network(override val text: UiText) : LoadError
    data class Auth(override val text: UiText) : LoadError
    data class Server(override val text: UiText) : LoadError
    data class Unknown(override val text: UiText) : LoadError
}

internal fun classifyError(e: Exception): LoadError = when (e) {
    is UntisException.Network -> LoadError.Network(friendlyError(e))
    is UntisException.Certificate -> LoadError.Network(friendlyError(e))
    is UntisException.BadCredentials -> LoadError.Auth(friendlyError(e))
    is UntisException.SessionExpired -> LoadError.Auth(friendlyError(e))
    is UntisException.Api -> LoadError.Server(friendlyError(e))
    else -> LoadError.Unknown(friendlyError(e))
}

internal fun friendlyError(e: Exception): UiText = when (e) {
    is UntisException.Network -> UiText.Res(R.string.error_network)
    is UntisException.Certificate -> UiText.Res(R.string.error_certificate)
    is UntisException.BadCredentials -> UiText.Res(R.string.error_bad_credentials)
    is UntisException.SessionExpired -> UiText.Res(R.string.error_session_expired)
    is UntisException.Api -> UiText.Res(
        R.string.error_untis_generic,
        listOf(UiText.Raw(e.code.toString()), UiText.Raw(e.message)),
    )

    else -> UiText.Res(
        R.string.error_unexpected,
        listOf(UiText.Raw(e.message ?: "")),
    )
}

internal fun refreshFailureMessage(context: Context, lastFetch: Long, e: Exception): UiText {
    if (lastFetch > 0) {
        val timestamp = UiText.Raw(
            AppDateFormats.formatter(context, "EEEdMMMjm")
                .format(Instant.ofEpochMilli(lastFetch).atZone(ZoneId.systemDefault())),
        )

        return UiText.Res(
            R.string.error_refresh_cached_at,
            listOf(timestamp, friendlyError(e)),
        )
    }

    return UiText.Res(R.string.error_refresh_cached, listOf(friendlyError(e)))
}
