package sh.lsx.untisx.data

import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.edit
import androidx.core.os.LocaleListCompat
import sh.lsx.untisx.UntisXApp
import java.util.Locale

enum class AppLanguage(val tag: String) {
    SYSTEM(""),
    ENGLISH("en"),
    GERMAN("de"),
}

object LanguagePrefs {

    const val KEY = "app_language"

    fun get(context: Context): AppLanguage {
        val stored = context
            .getSharedPreferences(UntisXApp.PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY, null)
            ?.let { stored -> AppLanguage.entries.firstOrNull { it.name == stored } }

        return stored ?: AppLanguage.SYSTEM
    }

    fun save(context: Context, language: AppLanguage) {
        context.getSharedPreferences(UntisXApp.PREFS_NAME, Context.MODE_PRIVATE)
            .edit { putString(KEY, language.name) }

        apply(language)
    }

    fun apply(language: AppLanguage) {
        val locales = if (language == AppLanguage.SYSTEM) {
            LocaleListCompat.getEmptyLocaleList()
        } else {
            LocaleListCompat.forLanguageTags(language.tag)
        }

        if (Looper.myLooper() == Looper.getMainLooper()) {
            AppCompatDelegate.setApplicationLocales(locales)
        } else {
            Handler(Looper.getMainLooper()).post {
                AppCompatDelegate.setApplicationLocales(locales)
            }
        }
    }
}

fun Context.appLocale(): Locale {
    val language = LanguagePrefs.get(this)

    if (language != AppLanguage.SYSTEM) return Locale.forLanguageTag(language.tag)

    return resources.configuration.locales.get(0) ?: Locale.getDefault()
}
