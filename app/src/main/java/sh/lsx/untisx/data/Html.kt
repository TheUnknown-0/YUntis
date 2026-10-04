package sh.lsx.untisx.data

import androidx.core.text.HtmlCompat

private val trailingWhitespace = Regex("[ \\t]+\\n")
private val excessNewlines = Regex("\\n{3,}")

internal fun stripHtml(text: String): String =
    HtmlCompat.fromHtml(text, HtmlCompat.FROM_HTML_MODE_LEGACY)
        .toString()
        .replace(trailingWhitespace, "\n")
        .replace(excessNewlines, "\n\n")
        .trim()
