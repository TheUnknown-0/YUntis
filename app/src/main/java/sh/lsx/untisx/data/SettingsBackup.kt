package sh.lsx.untisx.data

import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import sh.lsx.untisx.data.notifications.NotificationPrefs
import sh.lsx.untisx.data.notifications.NotificationScheduler
import sh.lsx.untisx.data.prefs.AnimationPrefs
import sh.lsx.untisx.data.prefs.AppearancePrefs
import sh.lsx.untisx.data.prefs.HapticsPrefs
import sh.lsx.untisx.data.prefs.TimetablePrefs
import java.util.Locale

@Serializable
data class GeneralSettingsBackup(
    val language: String? = null,
    val haptics: Boolean? = null,
    val showLastRefreshed: Boolean? = null,
)

@Serializable
data class AppearanceSettingsBackup(
    val themeMode: String? = null,
    val themePreset: String? = null,
    val themeCustomSeed: String? = null,
    val animations: Boolean? = null,
)

@Serializable
data class NotificationSettingsBackup(
    val enabled: Boolean? = null,
    val changesEnabled: Boolean? = null,
    val cancelled: Boolean? = null,
    val room: Boolean? = null,
    val teacher: Boolean? = null,
    val changesSilent: Boolean? = null,
    val silentDuringLessons: Boolean? = null,
    val syncIntervalMinutes: Int? = null,
    val overviewEnabled: Boolean? = null,
    val overviewMinute: Int? = null,
    val overviewShowChanges: Boolean? = null,
    val overviewDetailed: Boolean? = null,
    val overviewSilent: Boolean? = null,
    val examRemindersEnabled: Boolean? = null,
    val examReminderDays: Int? = null,
    val examReminderMinute: Int? = null,
    val newsMessages: Boolean? = null,
    val newsEvents: Boolean? = null,
    val newsAnnouncements: Boolean? = null,
)

@Serializable
data class TimetableSettingsBackup(
    val subjectColors: Map<String, String>? = null,
    val subjectTextColors: Map<String, String>? = null,
    val knownSubjects: List<String>? = null,
    val defaultSubjectColor: String? = null,
    val lessonTextColor: String? = null,
    val cancelledStrike: Boolean? = null,
    val strikeColor: String? = null,
    val examColor: String? = null,
    val cancelledDesaturation: Float? = null,
    val cancelledBrightness: Float? = null,
    val pastDim: Float? = null,
    val showTeacher: Boolean? = null,
    val showRoom: Boolean? = null,
    val showChanges: Boolean? = null,
    val hideCancelled: Boolean? = null,
    val showExamBadge: Boolean? = null,
)

@Serializable
data class SettingsBackup(
    val type: String = SettingsBackupCodec.TYPE,
    val version: Int = SettingsBackupCodec.CURRENT_VERSION,
    val appVersion: String = "",
    val exportedAt: Long = 0,
    val general: GeneralSettingsBackup? = null,
    val appearance: AppearanceSettingsBackup? = null,
    val notifications: NotificationSettingsBackup? = null,
    val timetable: TimetableSettingsBackup? = null,
)

object SettingsBackupCodec {

    const val TYPE = "untisx-settings"
    const val CURRENT_VERSION = 1

    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    sealed interface DecodeResult {
        data class Valid(val backup: SettingsBackup) : DecodeResult
        data object Invalid : DecodeResult
        data object UnsupportedVersion : DecodeResult
    }

    fun encode(backup: SettingsBackup): String = json.encodeToString(backup)

    fun decode(text: String): DecodeResult {
        val backup = runCatching { json.decodeFromString<SettingsBackup>(text) }.getOrNull()
            ?: return DecodeResult.Invalid

        if (backup.type != TYPE) return DecodeResult.Invalid
        if (backup.version > CURRENT_VERSION) return DecodeResult.UnsupportedVersion

        return DecodeResult.Valid(backup)
    }
}

sealed interface SettingsImportResult {
    data object Success : SettingsImportResult
    data object InvalidFile : SettingsImportResult
    data object UnsupportedVersion : SettingsImportResult
    data class Failed(val message: String?) : SettingsImportResult
}

object SettingsBackupManager {

    fun export(context: Context): String = SettingsBackupCodec.encode(read(context))

    fun import(context: Context, text: String): SettingsImportResult =
        when (val decoded = SettingsBackupCodec.decode(text)) {
            is SettingsBackupCodec.DecodeResult.Invalid -> SettingsImportResult.InvalidFile
            is SettingsBackupCodec.DecodeResult.UnsupportedVersion -> SettingsImportResult.UnsupportedVersion
            is SettingsBackupCodec.DecodeResult.Valid -> runCatching {
                apply(context, decoded.backup)
            }.fold(
                onSuccess = { SettingsImportResult.Success },
                onFailure = { SettingsImportResult.Failed(it.message) },
            )
        }

    private fun read(context: Context): SettingsBackup {
        val theme = ThemePrefs.get(context)

        return SettingsBackup(
            appVersion = appVersion(context),
            exportedAt = System.currentTimeMillis(),
            general = GeneralSettingsBackup(
                language = LanguagePrefs.get(context).name,
                haptics = HapticsPrefs.isEnabled(context),
                showLastRefreshed = TimetablePrefs.showLastRefreshed(context),
            ),
            appearance = AppearanceSettingsBackup(
                themeMode = theme.mode.name,
                themePreset = theme.preset.name,
                themeCustomSeed = colorToHex(theme.customSeed),
                animations = AnimationPrefs.isEnabled(context),
            ),
            notifications = NotificationSettingsBackup(
                enabled = NotificationPrefs.isEnabled(context),
                changesEnabled = NotificationPrefs.areChangesEnabled(context),
                cancelled = NotificationPrefs.notifyCancelled(context),
                room = NotificationPrefs.notifyRoomChange(context),
                teacher = NotificationPrefs.notifyTeacherChange(context),
                changesSilent = NotificationPrefs.changesSilent(context),
                silentDuringLessons = NotificationPrefs.silentDuringLessons(context),
                syncIntervalMinutes = NotificationPrefs.syncIntervalMinutes(context),
                overviewEnabled = NotificationPrefs.isOverviewEnabled(context),
                overviewMinute = NotificationPrefs.overviewMinute(context),
                overviewShowChanges = NotificationPrefs.overviewShowChanges(context),
                overviewDetailed = NotificationPrefs.overviewDetailed(context),
                overviewSilent = NotificationPrefs.overviewSilent(context),
                examRemindersEnabled = NotificationPrefs.examRemindersEnabled(context),
                examReminderDays = NotificationPrefs.examReminderDaysBefore(context),
                examReminderMinute = NotificationPrefs.examReminderMinute(context),
                newsMessages = NotificationPrefs.notifyNewMessages(context),
                newsEvents = NotificationPrefs.notifyNewEvents(context),
                newsAnnouncements = NotificationPrefs.notifyNewAnnouncements(context),
            ),
            timetable = TimetableSettingsBackup(
                subjectColors = SubjectColorsPrefs.getColors(context).mapValues { colorToHex(it.value) },
                subjectTextColors = SubjectColorsPrefs.getSubjectTextColors(context).mapValues { colorToHex(it.value) },
                knownSubjects = SubjectColorsPrefs.getKnown(context),
                defaultSubjectColor = colorToHex(SubjectColorsPrefs.getDefaultColor(context)),
                lessonTextColor = colorToHex(SubjectColorsPrefs.getTextColor(context)),
                cancelledStrike = SubjectColorsPrefs.isCancelledStrike(context),
                strikeColor = SubjectColorsPrefs.getStrikeColor(context)?.let(::colorToHex),
                examColor = SubjectColorsPrefs.getExamColor(context)?.let(::colorToHex),
                cancelledDesaturation = AppearancePrefs.getCancelledDesaturation(context),
                cancelledBrightness = AppearancePrefs.getCancelledBrightness(context),
                pastDim = AppearancePrefs.getPastDim(context),
                showTeacher = AppearancePrefs.showTeacher(context),
                showRoom = AppearancePrefs.showRoom(context),
                showChanges = AppearancePrefs.showChanges(context),
                hideCancelled = AppearancePrefs.hideCancelled(context),
                showExamBadge = AppearancePrefs.showExamBadge(context),
            ),
        )
    }

    private fun apply(context: Context, backup: SettingsBackup) {
        backup.general?.let { general ->
            general.language
                ?.let { name -> AppLanguage.entries.firstOrNull { it.name == name } }
                ?.let { LanguagePrefs.save(context, it) }

            general.haptics?.let { HapticsPrefs.setEnabled(context, it) }
            general.showLastRefreshed?.let { TimetablePrefs.setShowLastRefreshed(context, it) }
        }

        backup.appearance?.let { appearance ->
            val current = ThemePrefs.get(context)
            val mode = appearance.themeMode
                ?.let { name -> ThemeMode.entries.firstOrNull { it.name == name } }
                ?: current.mode
            val preset = appearance.themePreset
                ?.let { name -> ThemePreset.entries.firstOrNull { it.name == name } }
                ?: current.preset
            val customSeed = appearance.themeCustomSeed?.let(::hexToColor) ?: current.customSeed

            ThemePrefs.save(context, AppTheme(mode = mode, preset = preset, customSeed = customSeed))

            appearance.animations?.let { AnimationPrefs.setEnabled(context, it) }
        }

        backup.notifications?.let { notifications ->
            notifications.enabled?.let { NotificationPrefs.setEnabled(context, it) }
            notifications.changesEnabled?.let { NotificationPrefs.setChangesEnabled(context, it) }
            notifications.cancelled?.let { NotificationPrefs.setNotifyCancelled(context, it) }
            notifications.room?.let { NotificationPrefs.setNotifyRoomChange(context, it) }
            notifications.teacher?.let { NotificationPrefs.setNotifyTeacherChange(context, it) }
            notifications.changesSilent?.let { NotificationPrefs.setChangesSilent(context, it) }
            notifications.silentDuringLessons?.let { NotificationPrefs.setSilentDuringLessons(context, it) }
            notifications.syncIntervalMinutes?.let { NotificationPrefs.setSyncIntervalMinutes(context, it) }
            notifications.overviewEnabled?.let { NotificationPrefs.setOverviewEnabled(context, it) }
            notifications.overviewMinute?.let { NotificationPrefs.setOverviewMinute(context, it) }
            notifications.overviewShowChanges?.let { NotificationPrefs.setOverviewShowChanges(context, it) }
            notifications.overviewDetailed?.let { NotificationPrefs.setOverviewDetailed(context, it) }
            notifications.overviewSilent?.let { NotificationPrefs.setOverviewSilent(context, it) }
            notifications.examRemindersEnabled?.let { NotificationPrefs.setExamRemindersEnabled(context, it) }
            notifications.examReminderDays?.let { NotificationPrefs.setExamReminderDaysBefore(context, it) }
            notifications.examReminderMinute?.let { NotificationPrefs.setExamReminderMinute(context, it) }
            notifications.newsMessages?.let { NotificationPrefs.setNotifyNewMessages(context, it) }
            notifications.newsEvents?.let { NotificationPrefs.setNotifyNewEvents(context, it) }
            notifications.newsAnnouncements?.let { NotificationPrefs.setNotifyNewAnnouncements(context, it) }

            NotificationScheduler.ensureAll(context)
        }

        backup.timetable?.let { timetable ->
            timetable.subjectColors
                ?.entries
                ?.take(MAX_SUBJECT_ENTRIES)
                ?.forEach { (name, hex) ->
                    val subject = name.trim().takeIf { it.isNotBlank() && it.length <= MAX_SUBJECT_NAME_LENGTH }

                    if (subject != null) hexToColor(hex)?.let { SubjectColorsPrefs.setColor(context, subject, it) }
                }

            timetable.subjectTextColors
                ?.entries
                ?.take(MAX_SUBJECT_ENTRIES)
                ?.forEach { (name, hex) ->
                    val subject = name.trim().takeIf { it.isNotBlank() && it.length <= MAX_SUBJECT_NAME_LENGTH }

                    if (subject != null) {
                        hexToColor(hex)?.let { SubjectColorsPrefs.setSubjectTextColor(context, subject, it) }
                    }
                }

            timetable.knownSubjects
                ?.asSequence()
                ?.map { it.trim() }
                ?.filter { it.isNotBlank() && it.length <= MAX_SUBJECT_NAME_LENGTH }
                ?.distinct()
                ?.sorted()
                ?.take(MAX_SUBJECT_ENTRIES)
                ?.toList()
                ?.let { SubjectColorsPrefs.setKnown(context, it) }

            timetable.defaultSubjectColor
                ?.let(::hexToColor)
                ?.let { SubjectColorsPrefs.setDefaultColor(context, it) }
            timetable.lessonTextColor
                ?.let(::hexToColor)
                ?.let { SubjectColorsPrefs.setTextColor(context, it) }
            timetable.cancelledStrike?.let { SubjectColorsPrefs.setCancelledStrike(context, it) }
            timetable.strikeColor?.let(::hexToColor)?.let { SubjectColorsPrefs.setStrikeColor(context, it) }
            timetable.examColor?.let(::hexToColor)?.let { SubjectColorsPrefs.setExamColor(context, it) }
            timetable.cancelledDesaturation?.let { AppearancePrefs.setCancelledDesaturation(context, it) }
            timetable.cancelledBrightness?.let { AppearancePrefs.setCancelledBrightness(context, it) }
            timetable.pastDim?.let { AppearancePrefs.setPastDim(context, it) }
            timetable.showTeacher?.let { AppearancePrefs.setShowTeacher(context, it) }
            timetable.showRoom?.let { AppearancePrefs.setShowRoom(context, it) }
            timetable.showChanges?.let { AppearancePrefs.setShowChanges(context, it) }
            timetable.hideCancelled?.let { AppearancePrefs.setHideCancelled(context, it) }
            timetable.showExamBadge?.let { AppearancePrefs.setShowExamBadge(context, it) }
        }
    }

    private const val MAX_SUBJECT_ENTRIES = 500
    private const val MAX_SUBJECT_NAME_LENGTH = 64

    private fun appVersion(context: Context): String = runCatching {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName
    }.getOrNull().orEmpty()
}

internal fun colorToHex(argb: Int): String {
    val alpha = (argb ushr 24) and 0xFF

    return if (alpha == 0xFF) {
        String.format(Locale.ROOT, "#%06X", argb and 0xFFFFFF)
    } else {
        String.format(Locale.ROOT, "#%08X", argb)
    }
}

internal fun hexToColor(hex: String): Int? {
    val clean = hex.trim().removePrefix("#")

    val color = when (clean.length) {
        6 -> clean.toLongOrNull(16)?.toInt()?.or(0xFF000000.toInt())
        8 -> clean.toLongOrNull(16)?.toInt()
        else -> null
    } ?: return null

    return if (color ushr 24 == 0) color or 0xFF000000.toInt() else color
}
