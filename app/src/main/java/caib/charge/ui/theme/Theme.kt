package caib.charge.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import caib.charge.domain.model.CaibThemeMode

private val LightColors = lightColorScheme(
    primary = CaibCyanDeep,
    onPrimary = CaibInk,
    primaryContainer = CaibCyanSoft,
    onPrimaryContainer = CaibInk,
    inversePrimary = CaibCyan,
    secondary = CaibCyan,
    onSecondary = CaibInk,
    secondaryContainer = CaibCyanSoft,
    onSecondaryContainer = CaibInk,
    tertiary = CaibYellow,
    onTertiary = CaibInk,
    tertiaryContainer = ColorTokens.yellowSoft,
    onTertiaryContainer = CaibInk,
    background = CaibBackground,
    onBackground = CaibInk,
    surface = CaibCard,
    onSurface = CaibInk,
    surfaceVariant = CaibPanel,
    onSurfaceVariant = CaibInkSoft,
    surfaceTint = CaibCyanDeep,
    inverseSurface = CaibInk,
    inverseOnSurface = CaibBackground,
    outline = CaibInkSoft,
    outlineVariant = CaibOutline,
    error = CaibExpense,
    onError = CaibInk,
    errorContainer = ColorTokens.lightErrorContainer,
    onErrorContainer = CaibInk,
    scrim = Color.Black,
    surfaceBright = CaibCard,
    surfaceDim = ColorTokens.lightSurfaceDim,
    surfaceContainer = CaibPanel,
    surfaceContainerHigh = ColorTokens.lightSurfaceHigh,
    surfaceContainerHighest = ColorTokens.lightSurfaceHighest,
    surfaceContainerLow = CaibField,
    surfaceContainerLowest = CaibCard,
)

private val DarkColors = darkColorScheme(
    primary = CaibCyan,
    onPrimary = CaibInk,
    primaryContainer = ColorTokens.darkCyanContainer,
    onPrimaryContainer = CaibDarkText,
    inversePrimary = CaibCyanDeep,
    secondary = CaibCyanDeep,
    onSecondary = CaibInk,
    tertiary = CaibYellow,
    onTertiary = CaibInk,
    background = CaibDarkBackground,
    onBackground = CaibDarkText,
    surface = CaibDarkSurface,
    onSurface = CaibDarkText,
    surfaceVariant = CaibDarkPanel,
    onSurfaceVariant = CaibDarkMuted,
    surfaceTint = CaibCyan,
    inverseSurface = CaibDarkText,
    inverseOnSurface = CaibDarkBackground,
    outline = CaibDarkMuted,
    outlineVariant = ColorTokens.darkOutline,
    error = ColorTokens.darkError,
    onError = CaibInk,
    errorContainer = ColorTokens.darkErrorContainer,
    onErrorContainer = CaibDarkText,
    scrim = Color.Black,
    surfaceBright = ColorTokens.darkSurfaceBright,
    surfaceDim = CaibDarkBackground,
    surfaceContainer = CaibDarkSurface,
    surfaceContainerHigh = CaibDarkPanel,
    surfaceContainerHighest = ColorTokens.darkSurfaceHighest,
    surfaceContainerLow = ColorTokens.darkSurfaceLow,
    surfaceContainerLowest = ColorTokens.darkSurfaceLowest,
)

private object ColorTokens {
    val yellowSoft = androidx.compose.ui.graphics.Color(0xFFFFF9E6)
    val darkCyanContainer = androidx.compose.ui.graphics.Color(0xFF245463)
    val darkOutline = androidx.compose.ui.graphics.Color(0x3DF1FAFD)
    val darkError = androidx.compose.ui.graphics.Color(0xFFFFB4AB)
    val lightErrorContainer = Color(0xFFFFE9ED)
    val lightSurfaceDim = Color(0xFFDDEAF0)
    val lightSurfaceHigh = Color(0xFFF2F8FA)
    val lightSurfaceHighest = Color(0xFFEAF3F7)
    val darkErrorContainer = Color(0xFF6B343B)
    val darkSurfaceBright = Color(0xFF294956)
    val darkSurfaceHighest = Color(0xFF2B4B58)
    val darkSurfaceLow = Color(0xFF16303B)
    val darkSurfaceLowest = Color(0xFF0C2029)
}

@Composable
fun CaibTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    CaibThemeContent(darkTheme = darkTheme, content = content)
}

/** Applies a persisted appearance preference while retaining the Boolean API used by previews/tests. */
@Composable
fun CaibTheme(
    themeMode: CaibThemeMode,
    content: @Composable () -> Unit,
) {
    CaibThemeContent(
        darkTheme = themeMode.resolveDarkTheme(isSystemInDarkTheme()),
        content = content,
    )
}

@Composable
private fun CaibThemeContent(
    darkTheme: Boolean,
    content: @Composable () -> Unit,
) {
    val colors = if (darkTheme) DarkColors else LightColors
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = android.graphics.Color.TRANSPARENT
            window.navigationBarColor = colors.surface.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
            WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = !darkTheme
        }
    }
    MaterialTheme(
        colorScheme = colors,
        typography = CaibTypography,
        shapes = CaibShapes,
        content = content
    )
}
