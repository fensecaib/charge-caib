package caib.charge.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver

val CaibCyan = Color(0xFF7DD3E8)
val CaibCyanDeep = Color(0xFF5BC4E0)
val CaibCyanSoft = Color(0xFFE8F7FC)
val CaibYellow = Color(0xFFF9D85A)
val CaibYellowDeep = Color(0xFFF0C830)
val CaibPink = Color(0xFFFFB7C5)
val CaibInk = Color(0xFF2C4A5A)
val CaibInkSoft = Color(0xFF536C78)
val CaibBackground = Color(0xFFEEF8FC)
val CaibCard = Color(0xFFFFFFFF)
val CaibField = Color(0xFFFAFCFF)
val CaibPanel = Color(0xFFF7FAFB)
val CaibOutline = Color(0x1F2C4A5A)
val CaibSuccess = Color(0xFF2E8B65)
val CaibExpense = Color(0xFFE86E7C)
val CaibIncome = CaibCyanDeep

// User-approved OLED study: 06 / near-black quiet blue. Light tokens stay unchanged.
val CaibDarkBackground = Color(0xFF050607)
val CaibDarkSurface = Color(0xFF0D1015)
val CaibDarkPanel = Color(0xFF171C22)
val CaibDarkText = Color(0xFFC8D0D8)
val CaibDarkMuted = Color(0xFF9FABBA)

internal object QuietBlue {
    val primary = Color(0xFF8FAEC5)
    val onPrimary = Color(0xFF1E2C37)
    val primaryContainer = Color(0xFF1D2A36)
    val cta = Color(0xFFC7B17A)
    val onCta = Color(0xFF2A271E)
    val ctaContainer = Color(0xFF302C21)
    val onCtaContainer = Color(0xFFD7C49D)
    val expense = Color(0xFFC7A1AE)
    val expenseContainer = Color(0xFF30252C)
    val income = Color(0xFF9FB9B3)
    val onIncome = Color(0xFF21322F)
    val incomeContainer = Color(0xFF20312F)
    val error = Color(0xFFD3AAA5)
    val onError = Color(0xFF382522)
    val errorContainer = Color(0xFF332725)
    val onErrorContainer = Color(0xFFD7B7B2)
    val boundary = Color(0xFF67788B)
    val divider = Color(0xFF293440)
    val surfaceLow = Color(0xFF10141A)
    val surfaceHigh = Color(0xFF1C232B)
    val surfaceHighest = Color(0xFF242D37)
}

val CategoryPalette = listOf(
    Color(0xFF5BC4E0),
    Color(0xFFF9D85A),
    Color(0xFFFF9E9E),
    Color(0xFF7EC8A3),
    Color(0xFFA6A1E0),
    Color(0xFFF2A65A),
    Color(0xFF89B7E8),
    Color(0xFFFFB7C5)
)

/** Stable persisted color keys keep the original light palette; only presentation changes. */
internal val DarkCategoryPalette = listOf(
    QuietBlue.primary,
    QuietBlue.cta,
    QuietBlue.expense,
    QuietBlue.income,
    Color(0xFFABA3BD),
    Color(0xFFBCA78D),
    Color(0xFF93ABC0),
    Color(0xFFBA9DB2),
)

internal val ColorScheme.caibIsDark: Boolean get() = background == CaibDarkBackground

internal fun ColorScheme.caibCategoryColor(key: Int): Color {
    val palette = if (caibIsDark) DarkCategoryPalette else CategoryPalette
    return palette.getOrElse(key) { palette.first() }
}

/** Chart models retain stable light colors; resolve the displayed color at composition time. */
internal fun ColorScheme.caibPaletteColor(lightColor: Color): Color =
    if (caibIsDark) caibCategoryColor(CategoryPalette.indexOf(lightColor).coerceAtLeast(0)) else lightColor

internal fun ColorScheme.caibCategoryContainer(key: Int, lightAlpha: Float): Color =
    caibCategoryColor(key).copy(alpha = if (caibIsDark) 0.12f else lightAlpha).let {
        if (caibIsDark) it.compositeOver(surface) else it
    }

internal fun ColorScheme.caibPaletteContainer(lightColor: Color, lightAlpha: Float): Color =
    caibPaletteColor(lightColor).copy(alpha = if (caibIsDark) 0.12f else lightAlpha).let {
        if (caibIsDark) it.compositeOver(surface) else it
    }

internal val ColorScheme.caibIncome: Color get() = if (caibIsDark) QuietBlue.income else primary
internal val ColorScheme.caibExpense: Color get() = if (caibIsDark) QuietBlue.expense else error
internal val ColorScheme.caibIncomeContainer: Color get() = if (caibIsDark) secondaryContainer else primaryContainer
internal val ColorScheme.caibExpenseContainer: Color get() = if (caibIsDark) QuietBlue.expenseContainer else errorContainer
internal val ColorScheme.caibIncomeContent: Color get() = if (caibIsDark) caibIncome else onPrimaryContainer
internal val ColorScheme.caibExpenseContent: Color get() = if (caibIsDark) caibExpense else onErrorContainer
internal val ColorScheme.caibSuccess: Color get() = if (caibIsDark) QuietBlue.income else CaibSuccess
internal val ColorScheme.caibSuccessContent: Color get() = if (caibIsDark) QuietBlue.onIncome else Color.White
internal val ColorScheme.caibControlOutline: Color get() = if (caibIsDark) outline else outlineVariant
internal fun ColorScheme.caibMuted(lightAlpha: Float = 1f): Color =
    onSurfaceVariant.copy(alpha = if (caibIsDark) 1f else lightAlpha)

@Composable fun categoryColor(key: Int): Color = MaterialTheme.colorScheme.caibCategoryColor(key)
@Composable fun categoryContainerColor(key: Int, lightAlpha: Float = 0.22f): Color =
    MaterialTheme.colorScheme.caibCategoryContainer(key, lightAlpha)
@Composable fun paletteColor(lightColor: Color): Color = MaterialTheme.colorScheme.caibPaletteColor(lightColor)
@Composable fun paletteContainerColor(lightColor: Color, lightAlpha: Float = 0.22f): Color =
    MaterialTheme.colorScheme.caibPaletteContainer(lightColor, lightAlpha)
@Composable fun categoryContentColor(key: Int): Color = MaterialTheme.colorScheme.let {
    if (it.caibIsDark) it.caibCategoryColor(key) else it.onSurface
}
@Composable fun incomeColor(): Color = MaterialTheme.colorScheme.caibIncome
@Composable fun expenseColor(): Color = MaterialTheme.colorScheme.caibExpense
@Composable fun incomeContainerColor(): Color = MaterialTheme.colorScheme.caibIncomeContainer
@Composable fun expenseContainerColor(): Color = MaterialTheme.colorScheme.caibExpenseContainer
@Composable fun incomeContentColor(): Color = MaterialTheme.colorScheme.caibIncomeContent
@Composable fun expenseContentColor(): Color = MaterialTheme.colorScheme.caibExpenseContent
@Composable fun successColor(): Color = MaterialTheme.colorScheme.caibSuccess
@Composable fun successContentColor(): Color = MaterialTheme.colorScheme.caibSuccessContent
@Composable fun controlOutlineColor(): Color = MaterialTheme.colorScheme.caibControlOutline
@Composable fun mutedColor(lightAlpha: Float = 1f): Color = MaterialTheme.colorScheme.caibMuted(lightAlpha)
@Composable fun overlayScrimColor(): Color = MaterialTheme.colorScheme.let {
    if (it.caibIsDark) it.scrim.copy(alpha = 0.66f) else Color(0x522C4A5A)
}
