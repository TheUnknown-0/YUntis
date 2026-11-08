package sh.lsx.untisx.ui.theme

import android.app.Activity
import android.content.ContextWrapper
import android.os.Build
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.google.android.material.color.utilities.Scheme
import sh.lsx.untisx.data.ThemeMode
import sh.lsx.untisx.data.ThemePreset
import sh.lsx.untisx.data.ThemeStyle
import sh.lsx.untisx.ui.util.AppMotion
import sh.lsx.untisx.ui.util.LocalAnimationsEnabled

@Suppress("RestrictedApi") // material-color-utilities scheme is used intentionally for seeded themes
@Composable
fun UntisXTheme(
    mode: ThemeMode = ThemeMode.SYSTEM,
    preset: ThemePreset = ThemePreset.DEVICE,
    customSeed: Int = ThemePreset.ORANGE.seed,
    style: ThemeStyle = ThemeStyle.DEFAULT,
    content: @Composable () -> Unit,
) {
    val darkTheme = when (mode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }

    val context = LocalContext.current
    val view = LocalView.current
    val activity = remember(context) {
        generateSequence(context) { (it as? ContextWrapper)?.baseContext }
            .filterIsInstance<Activity>().firstOrNull()
    }

    SideEffect {
        activity?.window?.let { window ->
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !darkTheme
                isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }

    val colorScheme = if (preset == ThemePreset.DEVICE && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    } else {
        val seed = when {
            preset == ThemePreset.CUSTOM && customSeed != 0 -> customSeed
            preset.seed != 0 -> preset.seed
            else -> ThemePreset.ORANGE.seed
        }

        seedScheme(if (darkTheme) Scheme.dark(seed) else Scheme.light(seed), darkTheme)
    }

    val appearance = remember(style, darkTheme, colorScheme) { themeAppearance(style, darkTheme, colorScheme) }
    val texture = remember(style, darkTheme) { Modifier.appTexture(appearance) }

    CompositionLocalProvider(LocalThemeAppearance provides appearance) {
        MaterialTheme(
            colorScheme = if (LocalAnimationsEnabled.current) appearance.colors.animated() else appearance.colors,
            typography = appearance.typography,
            shapes = appearance.shapes,
        ) {
            CompositionLocalProvider(
                LocalContentColor provides when (style) {
                    ThemeStyle.DEFAULT -> LocalContentColor.current
                    ThemeStyle.NOTEBOOK -> MaterialTheme.colorScheme.onBackground
                },
            ) {
                Box(Modifier.fillMaxSize().then(texture)) {
                    content()
                }
            }
        }
    }
}

@Composable
private fun ColorScheme.animated(): ColorScheme {
    @Composable
    fun animate(color: Color): Color =
        animateColorAsState(
            targetValue = color,
            animationSpec = AppMotion.effects(),
            label = "themeColor",
        ).value

    return copy(
        primary = animate(primary),
        onPrimary = animate(onPrimary),
        primaryContainer = animate(primaryContainer),
        onPrimaryContainer = animate(onPrimaryContainer),
        secondary = animate(secondary),
        onSecondary = animate(onSecondary),
        secondaryContainer = animate(secondaryContainer),
        onSecondaryContainer = animate(onSecondaryContainer),
        tertiary = animate(tertiary),
        onTertiary = animate(onTertiary),
        tertiaryContainer = animate(tertiaryContainer),
        onTertiaryContainer = animate(onTertiaryContainer),
        error = animate(error),
        onError = animate(onError),
        errorContainer = animate(errorContainer),
        onErrorContainer = animate(onErrorContainer),
        background = animate(background),
        onBackground = animate(onBackground),
        surface = animate(surface),
        onSurface = animate(onSurface),
        surfaceVariant = animate(surfaceVariant),
        onSurfaceVariant = animate(onSurfaceVariant),
        outline = animate(outline),
        outlineVariant = animate(outlineVariant),
        scrim = animate(scrim),
        inverseSurface = animate(inverseSurface),
        inverseOnSurface = animate(inverseOnSurface),
        inversePrimary = animate(inversePrimary),
        surfaceBright = animate(surfaceBright),
        surfaceDim = animate(surfaceDim),
        surfaceContainer = animate(surfaceContainer),
        surfaceContainerHigh = animate(surfaceContainerHigh),
        surfaceContainerHighest = animate(surfaceContainerHighest),
        surfaceContainerLow = animate(surfaceContainerLow),
        surfaceContainerLowest = animate(surfaceContainerLowest),
    )
}

@Suppress("RestrictedApi") // material-color-utilities scheme is used intentionally for seeded themes
private fun seedScheme(s: Scheme, dark: Boolean): ColorScheme {
    return if (dark) darkColorScheme(
        primary = Color(s.primary),
        onPrimary = Color(s.onPrimary),
        primaryContainer = Color(s.primaryContainer),
        onPrimaryContainer = Color(s.onPrimaryContainer),
        secondary = Color(s.secondary),
        onSecondary = Color(s.onSecondary),
        secondaryContainer = Color(s.secondaryContainer),
        onSecondaryContainer = Color(s.onSecondaryContainer),
        tertiary = Color(s.tertiary),
        onTertiary = Color(s.onTertiary),
        tertiaryContainer = Color(s.tertiaryContainer),
        onTertiaryContainer = Color(s.onTertiaryContainer),
        error = Color(s.error),
        onError = Color(s.onError),
        errorContainer = Color(s.errorContainer),
        onErrorContainer = Color(s.onErrorContainer),
        background = Color(s.background),
        onBackground = Color(s.onBackground),
        surface = Color(s.surface),
        onSurface = Color(s.onSurface),
        surfaceVariant = Color(s.surfaceVariant),
        onSurfaceVariant = Color(s.onSurfaceVariant),
        outline = Color(s.outline),
        outlineVariant = Color(s.outlineVariant),
        scrim = Color(s.scrim),
        inverseSurface = Color(s.inverseSurface),
        inverseOnSurface = Color(s.inverseOnSurface),
        inversePrimary = Color(s.inversePrimary),
    ) else lightColorScheme(
        primary = Color(s.primary),
        onPrimary = Color(s.onPrimary),
        primaryContainer = Color(s.primaryContainer),
        onPrimaryContainer = Color(s.onPrimaryContainer),
        secondary = Color(s.secondary),
        onSecondary = Color(s.onSecondary),
        secondaryContainer = Color(s.secondaryContainer),
        onSecondaryContainer = Color(s.onSecondaryContainer),
        tertiary = Color(s.tertiary),
        onTertiary = Color(s.onTertiary),
        tertiaryContainer = Color(s.tertiaryContainer),
        onTertiaryContainer = Color(s.onTertiaryContainer),
        error = Color(s.error),
        onError = Color(s.onError),
        errorContainer = Color(s.errorContainer),
        onErrorContainer = Color(s.onErrorContainer),
        background = Color(s.background),
        onBackground = Color(s.onBackground),
        surface = Color(s.surface),
        onSurface = Color(s.onSurface),
        surfaceVariant = Color(s.surfaceVariant),
        onSurfaceVariant = Color(s.onSurfaceVariant),
        outline = Color(s.outline),
        outlineVariant = Color(s.outlineVariant),
        scrim = Color(s.scrim),
        inverseSurface = Color(s.inverseSurface),
        inverseOnSurface = Color(s.inverseOnSurface),
        inversePrimary = Color(s.inversePrimary),
    )
}
