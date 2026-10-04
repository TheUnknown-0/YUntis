package sh.lsx.untisx.ui.util

import android.content.Context
import android.text.format.DateFormat
import sh.lsx.untisx.data.appLocale
import java.time.format.DateTimeFormatter
import java.util.Locale

object AppDateFormats {

    fun pattern(locale: Locale, skeleton: String): String =
        DateFormat.getBestDateTimePattern(locale, skeleton)

    fun formatter(context: Context, skeleton: String): DateTimeFormatter {
        val locale = context.appLocale()

        return DateTimeFormatter.ofPattern(pattern(locale, skeleton), locale)
    }

    fun time(context: Context): DateTimeFormatter = formatter(context, "jm")
}
