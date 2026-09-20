package sh.lsx.untisx.ui.util

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import java.time.format.DateTimeFormatter

@Composable
fun rememberDateFormat(pattern: String): DateTimeFormatter {
    val locale = LocalConfiguration.current.locales[0]

    return remember(locale, pattern) { DateTimeFormatter.ofPattern(pattern, locale) }
}
