package sh.lsx.untisx.data

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.edit
import androidx.core.os.LocaleListCompat
import sh.lsx.untisx.UntisXApp

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

        AppCompatDelegate.setApplicationLocales(locales)
    }
}
