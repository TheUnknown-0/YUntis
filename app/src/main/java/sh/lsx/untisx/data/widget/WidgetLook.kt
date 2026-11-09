package sh.lsx.untisx.data.widget

import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.text.SpannableString
import android.text.Spanned
import android.text.style.TypefaceSpan
import android.view.View
import android.widget.RemoteViews
import androidx.annotation.ColorInt
import androidx.annotation.DrawableRes
import androidx.core.content.ContextCompat
import sh.lsx.untisx.R

/** How the widgets look; one setting for all of them. */
enum class WidgetStyle { AUTO, LIGHT, DARK, DYNAMIC, POSTIT, NOTEBOOK }

/** Paper of the Schulheft style: ruled like the app, or squared like a maths notebook. */
enum class NotebookPaper { LINED, SQUARED }

/** Paper colours of the Post-it style. */
enum class PostItColor(@param:ColorInt val paper: Int) {
    YELLOW(0xFFFFF0A0.toInt()),
    PINK(0xFFFFC6D9.toInt()),
    GREEN(0xFFCDEDB0.toInt()),
    BLUE(0xFFBFE2F6.toInt()),
    ORANGE(0xFFFFD2A0.toInt()),
}

object WidgetStylePrefs {
    private const val PREFS = "widget_style"

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun style(context: Context): WidgetStyle =
        runCatching { WidgetStyle.valueOf(prefs(context).getString("style", null) ?: "") }.getOrDefault(WidgetStyle.AUTO)

    fun postItColor(context: Context): PostItColor =
        runCatching { PostItColor.valueOf(prefs(context).getString("postit", null) ?: "") }.getOrDefault(PostItColor.YELLOW)

    fun notebookPaper(context: Context): NotebookPaper =
        runCatching { NotebookPaper.valueOf(prefs(context).getString("paper", null) ?: "") }.getOrDefault(NotebookPaper.LINED)

    fun setNotebookPaper(context: Context, paper: NotebookPaper) = save(context) { putString("paper", paper.name) }

    /** Background opacity in percent (20 to 100). */
    fun opacity(context: Context): Int = prefs(context).getInt("opacity", 100).coerceIn(20, 100)

    fun setStyle(context: Context, style: WidgetStyle) = save(context) { putString("style", style.name) }
    fun setPostItColor(context: Context, color: PostItColor) = save(context) { putString("postit", color.name) }
    fun setOpacity(context: Context, percent: Int) = save(context) { putInt("opacity", percent.coerceIn(20, 100)) }

    private fun save(context: Context, block: android.content.SharedPreferences.Editor.() -> Unit) {
        prefs(context).edit().apply(block).apply()
        WidgetUpdater.updateAll(context)
    }
}

/**
 * Resolved colours of one widget update. For [WidgetStyle.AUTO] the colours stay [colors] = null:
 * the layouts then keep their resource colours, which follow the system's light/dark switch by
 * themselves even without an update.
 */
class WidgetLook(
    val style: WidgetStyle,
    val colors: Colors?,
    val opacity: Int,
    val paper: NotebookPaper = NotebookPaper.LINED,
) {
    class Colors(
        @param:ColorInt val background: Int,
        @param:ColorInt val text: Int,
        @param:ColorInt val soft: Int,
        @param:ColorInt val accent: Int,
        @param:ColorInt val cancelled: Int,
        @param:ColorInt val changed: Int,
        @param:DrawableRes val highlight: Int,
    )

    val postIt: Boolean get() = style == WidgetStyle.POSTIT
    val notebook: Boolean get() = style == WidgetStyle.NOTEBOOK

    /** Text in the handwriting font for the Post-it style; other styles leave it as is. */
    fun text(value: CharSequence): CharSequence {
        // widgets only get system fonts: the Schulheft style uses the system handwriting too
        if (!(postIt || notebook) || value.isEmpty()) return value
        return SpannableString(value).apply {
            setSpan(TypefaceSpan("casual"), 0, length, Spanned.SPAN_INCLUSIVE_INCLUSIVE)
        }
    }

    fun setText(views: RemoteViews, id: Int, value: CharSequence) = views.setTextViewText(id, text(value))

    /** Background, paper shade and tape of a widget root. */
    fun applyFrame(views: RemoteViews) {
        val alpha = opacity * 255 / 100
        views.setInt(
            R.id.widget_bg,
            "setImageResource",
            when {
                postIt -> R.drawable.widget_background_postit
                notebook -> R.drawable.widget_background_notebook
                else -> R.drawable.widget_background
            },
        )
        colors?.let { views.setInt(R.id.widget_bg, "setColorFilter", it.background) }
        views.setInt(R.id.widget_bg, "setImageAlpha", alpha)
        views.setViewVisibility(R.id.widget_shade, if (postIt) View.VISIBLE else View.GONE)
        views.setViewVisibility(R.id.widget_tape, if (postIt) View.VISIBLE else View.GONE)
        if (postIt) views.setInt(R.id.widget_shade, "setImageAlpha", alpha)
        views.setViewVisibility(R.id.widget_paper, if (notebook) View.VISIBLE else View.GONE)
        views.setViewVisibility(R.id.widget_margin, if (notebook) View.VISIBLE else View.GONE)
        if (notebook) {
            views.setInt(
                R.id.widget_paper,
                "setImageResource",
                if (paper == NotebookPaper.SQUARED) R.drawable.widget_paper_squared else R.drawable.widget_paper_lined,
            )
            views.setInt(R.id.widget_paper, "setImageAlpha", alpha)
            views.setInt(R.id.widget_margin, "setImageAlpha", alpha)
        }
    }

    enum class Role { TEXT, SOFT, ACCENT }

    fun color(views: RemoteViews, id: Int, role: Role) {
        val c = colors ?: return
        views.setTextColor(
            id,
            when (role) {
                Role.TEXT -> c.text
                Role.SOFT -> c.soft
                Role.ACCENT -> c.accent
            },
        )
    }

    fun highlight(views: RemoteViews, id: Int) =
        views.setInt(id, "setBackgroundResource", colors?.highlight ?: R.drawable.widget_row_highlight)

    companion object {
        private val LIGHT = Colors(
            background = 0xFFFFFBF8.toInt(), text = 0xFF1F1B16.toInt(), soft = 0xFF5F5A54.toInt(),
            accent = 0xFFC9441D.toInt(), cancelled = 0xFFD32F2F.toInt(), changed = 0xFFE08600.toInt(),
            highlight = R.drawable.widget_row_highlight_light,
        )
        private val DARK = Colors(
            background = 0xFF1E1E20.toInt(), text = 0xFFF2ECE6.toInt(), soft = 0xFFBDB5AE.toInt(),
            accent = 0xFFFF9670.toInt(), cancelled = 0xFFFF6B6B.toInt(), changed = 0xFFFFB74D.toInt(),
            highlight = R.drawable.widget_row_highlight_dark,
        )

        private fun postIt(paper: Int) = Colors(
            background = paper, text = 0xFF22263A.toInt(), soft = 0xCC3B4157.toInt(),
            accent = 0xFFB0361A.toInt(), cancelled = 0xFFC62828.toInt(), changed = 0xFFA85400.toInt(),
            highlight = R.drawable.widget_row_highlight_ink,
        )

        /** Paper and ink of the Schulheft style, the same as in the app. */
        private val NOTEBOOK_LIGHT = Colors(
            background = 0xFFF5EFDF.toInt(), text = 0xFF302E29.toInt(), soft = 0xFF625B4D.toInt(),
            accent = 0xFF2D5A9B.toInt(), cancelled = 0xFFC0392B.toInt(), changed = 0xFFB5651D.toInt(),
            highlight = R.drawable.widget_row_highlight_ink,
        )
        private val NOTEBOOK_DARK = Colors(
            background = 0xFF25241F.toInt(), text = 0xFFF2E9D5.toInt(), soft = 0xFFD0C7B5.toInt(),
            accent = 0xFF9DB8E8.toInt(), cancelled = 0xFFFF7B6B.toInt(), changed = 0xFFF0B060.toInt(),
            highlight = R.drawable.widget_row_highlight_dark,
        )

        fun resolve(context: Context): WidgetLook {
            val style = WidgetStylePrefs.style(context)
            val night = (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
            val colors = when (style) {
                WidgetStyle.AUTO -> null
                WidgetStyle.LIGHT -> LIGHT
                WidgetStyle.DARK -> DARK
                WidgetStyle.POSTIT -> postIt(WidgetStylePrefs.postItColor(context).paper)
                WidgetStyle.DYNAMIC -> dynamic(context, night) ?: if (night) DARK else LIGHT
                WidgetStyle.NOTEBOOK -> if (night) NOTEBOOK_DARK else NOTEBOOK_LIGHT
            }
            return WidgetLook(style, colors, WidgetStylePrefs.opacity(context), WidgetStylePrefs.notebookPaper(context))
        }

        /** Material You colours from the wallpaper (Android 12+). */
        private fun dynamic(context: Context, night: Boolean): Colors? {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return null
            fun c(id: Int) = ContextCompat.getColor(context, id)
            return if (night) {
                Colors(
                    background = c(android.R.color.system_neutral1_900), text = c(android.R.color.system_neutral1_50),
                    soft = c(android.R.color.system_neutral2_200), accent = c(android.R.color.system_accent1_200),
                    cancelled = 0xFFFF6B6B.toInt(), changed = c(android.R.color.system_accent3_200),
                    highlight = R.drawable.widget_row_highlight_dark,
                )
            } else {
                Colors(
                    background = c(android.R.color.system_neutral1_50), text = c(android.R.color.system_neutral1_900),
                    soft = c(android.R.color.system_neutral2_700), accent = c(android.R.color.system_accent1_600),
                    cancelled = 0xFFD32F2F.toInt(), changed = c(android.R.color.system_accent3_600),
                    highlight = R.drawable.widget_row_highlight_light,
                )
            }
        }
    }
}
