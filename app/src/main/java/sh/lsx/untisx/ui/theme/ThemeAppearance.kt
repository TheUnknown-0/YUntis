package sh.lsx.untisx.ui.theme

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import sh.lsx.untisx.data.ThemeStyle

internal data class ThemeAppearance(
    val style: ThemeStyle = ThemeStyle.DEFAULT,
    val dark: Boolean = false,
    val colors: ColorScheme = lightColorScheme(),
    val typography: Typography = Typography(),
    val shapes: Shapes = Shapes(),
    val cardRadius: Dp? = null,
    val settingsBorder: BorderStroke? = null,
    val settingsElevation: Dp = 0.dp,
    val screenContainerColor: Color = Color.Unspecified,
)

internal val LocalThemeAppearance = staticCompositionLocalOf { ThemeAppearance() }

internal fun themeAppearance(style: ThemeStyle, dark: Boolean, colors: ColorScheme): ThemeAppearance = when (style) {
    ThemeStyle.DEFAULT -> ThemeAppearance(colors = colors, dark = dark)
    ThemeStyle.NOTEBOOK -> {
        val paper = colors.onNotebookPaper(dark)

        ThemeAppearance(
            style = style,
            dark = dark,
            colors = paper,
            typography = notebookTypography(),
            shapes = NotebookShapes,
            cardRadius = 3.dp,
            settingsBorder = BorderStroke(0.7.dp, paper.outlineVariant),
            settingsElevation = 2.dp,
            screenContainerColor = Color.Transparent,
        )
    }
}

@Composable
internal fun appCardShape(defaultRadius: Dp): RoundedCornerShape =
    RoundedCornerShape(LocalThemeAppearance.current.cardRadius ?: defaultRadius)

@Composable
internal fun appScreenContainerColor(): Color {
    val color = LocalThemeAppearance.current.screenContainerColor

    return if (color == Color.Unspecified) MaterialTheme.colorScheme.background else color
}

@Composable
internal fun Modifier.appBackground(): Modifier = when (LocalThemeAppearance.current.style) {
    ThemeStyle.DEFAULT -> this
    ThemeStyle.NOTEBOOK -> notebookPaper()
}

internal fun Modifier.appTexture(appearance: ThemeAppearance): Modifier = when (appearance.style) {
    ThemeStyle.DEFAULT -> this
    ThemeStyle.NOTEBOOK -> paperGrain(appearance.dark)
}

@Composable
internal fun Modifier.currentDayHighlight(): Modifier = when (LocalThemeAppearance.current.style) {
    ThemeStyle.DEFAULT -> this
    ThemeStyle.NOTEBOOK -> notebookDayHighlight()
}
