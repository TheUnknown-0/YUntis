package sh.lsx.untisx.ui.util

import android.text.format.DateFormat
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import java.time.format.DateTimeFormatter

@Composable
fun rememberDateFormat(skeleton: String): DateTimeFormatter {
    val locale = LocalConfiguration.current.locales[0]

    return remember(locale, skeleton) {
        DateTimeFormatter.ofPattern(DateFormat.getBestDateTimePattern(locale, skeleton), locale)
    }
}

@Composable
fun rememberTimeFormat(): DateTimeFormatter = rememberDateFormat("jm")
