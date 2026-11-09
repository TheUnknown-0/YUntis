package sh.lsx.untisx.ui.screens

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import sh.lsx.untisx.R

/** One block of release notes written in the NewPipe-like style used for YUntis releases. */
sealed interface NoteBlock {
    data class Heading(val text: String, val level: Int) : NoteBlock
    data class Bullet(val tag: String?, val text: String) : NoteBlock
    data class Paragraph(val text: String) : NoteBlock
}

object ReleaseNotesParser {
    private val tagPattern = Regex("""^\[([^\]]+)]\s*(.*)$""")
    private val linkPattern = Regex("""\[([^\]]+)]\([^)]*\)""")

    /** Reads headings (#..####), bullets (* or -) with an optional leading [Tag], and plain paragraphs. */
    fun parse(markdown: String): List<NoteBlock> = markdown.lines().mapNotNull { raw ->
        val line = raw.trim().replace(linkPattern) { it.groupValues[1] }
        when {
            line.isBlank() -> null
            line.startsWith("#") -> {
                val level = line.takeWhile { it == '#' }.length
                NoteBlock.Heading(line.drop(level).trim(), level)
            }
            line.startsWith("* ") || line.startsWith("- ") -> {
                val body = line.drop(2).trim()
                val match = tagPattern.find(body)
                if (match != null) NoteBlock.Bullet(match.groupValues[1], match.groupValues[2]) else NoteBlock.Bullet(null, body)
            }
            else -> NoteBlock.Paragraph(line)
        }
    }

    /** Notes bundled with the app for the installed version (assets/release_notes.md). */
    fun bundled(context: Context): String =
        runCatching { context.assets.open("release_notes.md").bufferedReader().use { it.readText() } }.getOrDefault("")
}

/** **bold** spans inside a line. */
private fun styled(text: String): AnnotatedString = buildAnnotatedString {
    val parts = text.split("**")
    parts.forEachIndexed { index, part ->
        if (index % 2 == 1) withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(part) } else append(part)
    }
}

@Composable
fun ReleaseNotes(markdown: String, modifier: Modifier = Modifier, maxBlocks: Int = Int.MAX_VALUE) {
    val blocks = ReleaseNotesParser.parse(markdown).take(maxBlocks)

    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        blocks.forEach { block ->
            when (block) {
                is NoteBlock.Heading -> Text(
                    block.text,
                    style = if (block.level <= 2) MaterialTheme.typography.titleMedium else MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 8.dp),
                )

                is NoteBlock.Paragraph -> Text(styled(block.text), style = MaterialTheme.typography.bodyMedium)

                is NoteBlock.Bullet -> Row {
                    Text("•", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(8.dp))
                    Column(Modifier.fillMaxWidth()) {
                        block.tag?.let { tag ->
                            Text(
                                tag,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier
                                    .background(MaterialTheme.colorScheme.secondaryContainer, RoundedCornerShape(6.dp))
                                    .padding(horizontal = 6.dp, vertical = 1.dp),
                            )
                        }
                        Text(styled(block.text), style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
    }
}

/** Shown once after an update with the notes of the new version. */
@Composable
fun WhatsNewDialog(version: String, markdown: String, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.whats_new_title, version)) },
        text = {
            ReleaseNotes(
                markdown,
                Modifier
                    .heightIn(max = 460.dp)
                    .verticalScroll(rememberScrollState()),
            )
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.whats_new_ok)) } },
    )
}
