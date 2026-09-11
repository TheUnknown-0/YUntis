package sh.lsx.untisx.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.google.android.material.color.utilities.Scheme
import sh.lsx.untisx.data.ThemeMode
import sh.lsx.untisx.data.ThemePreset

@Suppress("RestrictedApi") // material-color-utilities scheme is used intentionally for seeded themes
@Composable
fun UntisXTheme(
    mode: ThemeMode = ThemeMode.SYSTEM,
    preset: ThemePreset = ThemePreset.DEVICE,
    content: @Composable () -> Unit,
) {
    val darkTheme = when (mode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }

    val context = LocalContext.current
    val colorScheme = if (preset == ThemePreset.DEVICE && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    } else {
        val seed = if (preset.seed != 0) preset.seed else ThemePreset.ORANGE.seed
        seedScheme(if (darkTheme) Scheme.dark(seed) else Scheme.light(seed), darkTheme)
    }

    MaterialTheme(
        colorScheme = colorScheme,
        content = content,
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
