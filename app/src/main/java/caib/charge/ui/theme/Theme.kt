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

internal val LightColors = lightColorScheme(
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

internal val DarkColors = darkColorScheme(
    primary = QuietBlue.primary,
    onPrimary = QuietBlue.onPrimary,
    primaryContainer = QuietBlue.primaryContainer,
    onPrimaryContainer = CaibDarkText,
    inversePrimary = QuietBlue.onPrimary,
    secondary = QuietBlue.income,
    onSecondary = QuietBlue.onIncome,
    secondaryContainer = QuietBlue.incomeContainer,
    onSecondaryContainer = CaibDarkText,
    tertiary = QuietBlue.cta,
    onTertiary = QuietBlue.onCta,
    tertiaryContainer = QuietBlue.ctaContainer,
    onTertiaryContainer = QuietBlue.onCtaContainer,
    background = CaibDarkBackground,
    onBackground = CaibDarkText,
    surface = CaibDarkSurface,
    onSurface = CaibDarkText,
    surfaceVariant = CaibDarkPanel,
    onSurfaceVariant = CaibDarkMuted,
    surfaceTint = QuietBlue.primary,
    inverseSurface = CaibDarkText,
    inverseOnSurface = CaibDarkBackground,
    outline = QuietBlue.boundary,
    outlineVariant = QuietBlue.divider,
    error = QuietBlue.error,
    onError = QuietBlue.onError,
    errorContainer = QuietBlue.errorContainer,
    onErrorContainer = QuietBlue.onErrorContainer,
    scrim = Color.Black,
    surfaceBright = QuietBlue.surfaceHighest,
    surfaceDim = CaibDarkBackground,
    surfaceContainer = CaibDarkSurface,
    surfaceContainerHigh = QuietBlue.surfaceHigh,
    surfaceContainerHighest = QuietBlue.surfaceHighest,
    surfaceContainerLow = QuietBlue.surfaceLow,
    surfaceContainerLowest = CaibDarkBackground,
    primaryFixed = QuietBlue.primary,
    primaryFixedDim = QuietBlue.primary,
    onPrimaryFixed = QuietBlue.onPrimary,
    onPrimaryFixedVariant = QuietBlue.onPrimary,
    secondaryFixed = QuietBlue.income,
    secondaryFixedDim = QuietBlue.income,
    onSecondaryFixed = QuietBlue.onIncome,
    onSecondaryFixedVariant = QuietBlue.onIncome,
    tertiaryFixed = QuietBlue.cta,
    tertiaryFixedDim = QuietBlue.cta,
    onTertiaryFixed = QuietBlue.onCta,
    onTertiaryFixedVariant = QuietBlue.onCta,
)

private object ColorTokens {
    val yellowSoft = androidx.compose.ui.graphics.Color(0xFFFFF9E6)
    val lightErrorContainer = Color(0xFFFFE9ED)
    val lightSurfaceDim = Color(0xFFDDEAF0)
    val lightSurfaceHigh = Color(0xFFF2F8FA)
    val lightSurfaceHighest = Color(0xFFEAF3F7)
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
