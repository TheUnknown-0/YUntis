package sh.lsx.untisx.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import sh.lsx.untisx.R
import sh.lsx.untisx.data.widget.NotebookPaper
import sh.lsx.untisx.data.widget.PostItColor
import sh.lsx.untisx.data.widget.WidgetLook
import sh.lsx.untisx.data.widget.WidgetStyle
import sh.lsx.untisx.data.widget.WidgetStylePrefs
import sh.lsx.untisx.ui.util.Haptics
import sh.lsx.untisx.ui.util.tapHaptic

/** Choose how the home screen widgets look, with a small preview. */
@Composable
fun WidgetStyleSettingsCard() {
    val context = LocalContext.current
    var style by remember { mutableStateOf(WidgetStylePrefs.style(context)) }
    var paper by remember { mutableStateOf(WidgetStylePrefs.postItColor(context)) }
    var opacity by remember { mutableIntStateOf(WidgetStylePrefs.opacity(context)) }
    var notebookPaper by remember { mutableStateOf(WidgetStylePrefs.notebookPaper(context)) }

    SettingsSectionCard(title = stringResource(R.string.widget_style_card), subtitle = stringResource(R.string.widget_style_desc)) {
        WidgetPreview(style, paper, opacity, notebookPaper)
        Spacer(Modifier.height(12.dp))

        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(
                WidgetStyle.AUTO to R.string.widget_style_auto,
                WidgetStyle.LIGHT to R.string.widget_style_light,
                WidgetStyle.DARK to R.string.widget_style_dark,
                WidgetStyle.DYNAMIC to R.string.widget_style_dynamic,
                WidgetStyle.POSTIT to R.string.widget_style_postit,
                WidgetStyle.NOTEBOOK to R.string.widget_style_notebook,
            ).forEach { (value, label) ->
                FilterChip(
                    selected = style == value,
                    onClick = Haptics.haptic {
                        style = value
                        WidgetStylePrefs.setStyle(context, value)
                    },
                    label = { Text(stringResource(label)) },
                )
            }
        }

        if (style == WidgetStyle.POSTIT) {
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                PostItColor.entries.forEach { color ->
                    Box(
                        Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(Color(color.paper))
                            .border(
                                width = if (paper == color) 3.dp else 1.dp,
                                color = if (paper == color) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                                shape = CircleShape,
                            )
                            .tapHaptic {
                                paper = color
                                WidgetStylePrefs.setPostItColor(context, color)
                            },
                    )
                }
            }
        }

        if (style == WidgetStyle.NOTEBOOK) {
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(
                    NotebookPaper.LINED to R.string.widget_paper_lined,
                    NotebookPaper.SQUARED to R.string.widget_paper_squared,
                ).forEach { (value, label) ->
                    FilterChip(
                        selected = notebookPaper == value,
                        onClick = Haptics.haptic {
                            notebookPaper = value
                            WidgetStylePrefs.setNotebookPaper(context, value)
                        },
                        label = { Text(stringResource(label)) },
                    )
                }
            }
        }

        SettingsSliderRow(
            title = stringResource(R.string.widget_style_opacity),
            value = opacity.toFloat(),
            valueLabel = "$opacity %",
            valueRange = 20f..100f,
            steps = 15,
            onValueChange = {
                opacity = it.toInt()
                WidgetStylePrefs.setOpacity(context, opacity)
            },
        )
    }
}

@Composable
private fun WidgetPreview(style: WidgetStyle, paper: PostItColor, opacity: Int, notebookPaper: NotebookPaper) {
    val context = LocalContext.current
    // the same colours as the widget; "automatic" shows what the system theme gives right now
    val colors = remember(style, paper, notebookPaper) { WidgetLook.resolve(context).colors }
    val notebook = style == WidgetStyle.NOTEBOOK
    val background = colors?.background?.let(::Color) ?: colorResource(R.color.widget_background)
    val text = colors?.text?.let(::Color) ?: colorResource(R.color.widget_text)
    val soft = colors?.soft?.let(::Color) ?: colorResource(R.color.widget_text_soft)
    val accent = colors?.accent?.let(::Color) ?: colorResource(R.color.widget_accent)
    val postIt = style == WidgetStyle.POSTIT
    val shape = when {
        postIt -> RoundedCornerShape(3.dp, 3.dp, 14.dp, 3.dp)
        notebook -> RoundedCornerShape(4.dp)
        else -> RoundedCornerShape(24.dp)
    }
    // the same handwriting the widget gets through TypefaceSpan("casual")
    val font = if (postIt || notebook) remember { FontFamily(android.graphics.Typeface.create("casual", android.graphics.Typeface.NORMAL)) } else FontFamily.Default
    val alpha = opacity / 100f

    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Brush.linearGradient(listOf(Color(0xFF5B7DB1), Color(0xFF9B6FB0), Color(0xFFD98C6A))))
            .padding(18.dp),
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.width(250.dp)) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(shape)
                    .background(background.copy(alpha = alpha))
                    .then(
                        if (postIt) Modifier.background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.12f * alpha)))) else Modifier,
                    )
                    .then(if (notebook) Modifier.notebookRuling(notebookPaper, alpha) else Modifier)
                    .padding(horizontal = 16.dp, vertical = 14.dp),
            ) {
                Text(stringResource(R.string.widget_today_label).uppercase(), color = accent, fontSize = 11.sp, fontWeight = FontWeight.Medium, fontFamily = font, letterSpacing = 0.8.sp)
                Text(stringResource(R.string.widget_style_preview_day), color = text, fontSize = 20.sp, fontWeight = FontWeight.Black, fontFamily = font)
                Spacer(Modifier.height(8.dp))
                listOf(
                    Triple(Color(0xFF4F8EF7), stringResource(R.string.widget_style_preview_subject1), "08:00"),
                    Triple(Color(0xFF43A047), stringResource(R.string.widget_style_preview_subject2), "09:50"),
                ).forEach { (bar, subject, start) ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.width(5.dp).height(28.dp).clip(RoundedCornerShape(3.dp)).background(bar))
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(subject, color = text, fontSize = 14.sp, fontWeight = FontWeight.Bold, fontFamily = font)
                            Text(stringResource(R.string.widget_style_preview_room), color = soft, fontSize = 11.sp, fontFamily = font)
                        }
                        Text(start, color = text, fontSize = 13.sp, fontFamily = font)
                    }
                }
            }
            if (postIt) {
                Box(
                    Modifier
                        .align(Alignment.TopCenter)
                        .offset(y = (-6).dp)
                        .rotate(-3f)
                        .width(64.dp)
                        .height(18.dp)
                        .background(Color(0x8CF4EFE4))
                        .border(1.dp, Color(0x1A000000)),
                )
            }
        }
    }
}

/** The same ruling as the widget tiles: lines every 24 dp or squares of 16 dp, plus the margin. */
private fun Modifier.notebookRuling(paper: NotebookPaper, alpha: Float): Modifier = drawBehind {
    val line = Color(0xFF6A95B0).copy(alpha = 0.26f * alpha)
    val stroke = 0.7.dp.toPx()
    val step = (if (paper == NotebookPaper.SQUARED) 16.dp else 24.dp).toPx()
    var y = step
    while (y < size.height) {
        drawLine(line, Offset(0f, y), Offset(size.width, y), stroke)
        y += step
    }
    if (paper == NotebookPaper.SQUARED) {
        var x = step
        while (x < size.width) {
            drawLine(line, Offset(x, 0f), Offset(x, size.height), stroke)
            x += step
        }
    }
    val margin = 10.dp.toPx()
    drawLine(Color(0xFFCA7770).copy(alpha = 0.35f * alpha), Offset(margin, 0f), Offset(margin, size.height), 1.dp.toPx())
}