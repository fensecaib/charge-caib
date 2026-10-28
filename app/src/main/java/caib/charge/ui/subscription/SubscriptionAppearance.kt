package caib.charge.ui.subscription

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.rounded.CardGiftcard
import androidx.compose.material.icons.rounded.ChatBubbleOutline
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CloudQueue
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.Coffee
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.PlayCircleOutline
import androidx.compose.material.icons.rounded.SportsEsports
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import caib.charge.domain.SubscriptionRules
import caib.charge.domain.model.SubscriptionAppearance
import caib.charge.domain.model.SubscriptionProject
import caib.charge.ui.components.CaibTextButton
import caib.charge.ui.components.SakuCtaButton
import caib.charge.ui.components.SakuTopBar
import caib.charge.ui.theme.DarkColors
import caib.charge.ui.theme.LightColors
import caib.charge.ui.theme.caibCategoryColor
import caib.charge.ui.theme.caibIsDark
import java.util.Locale
import kotlin.math.max
import kotlin.math.min

internal data class SubscriptionIconOption(val key: String, val label: String, val image: ImageVector?)

/** Stable keys match the domain whitelist; no downloaded logos, assets, or fonts. */
internal val SubscriptionIconOptions = listOf(
    SubscriptionIconOption("auto", "自动首字", null),
    SubscriptionIconOption("video", "影片", Icons.Rounded.Movie),
    SubscriptionIconOption("chat", "对话", Icons.Rounded.ChatBubbleOutline),
    SubscriptionIconOption("play", "播放", Icons.Rounded.PlayCircleOutline),
    SubscriptionIconOption("music", "音乐", Icons.Rounded.MusicNote),
    SubscriptionIconOption("gift", "礼物", Icons.Rounded.CardGiftcard),
    SubscriptionIconOption("cloud", "云端", Icons.Rounded.CloudQueue),
    SubscriptionIconOption("notebook", "笔记", Icons.Rounded.Description),
    SubscriptionIconOption("palette", "创作", Icons.Rounded.Palette),
    SubscriptionIconOption("game", "游戏", Icons.Rounded.SportsEsports),
    SubscriptionIconOption("book", "阅读", Icons.AutoMirrored.Rounded.MenuBook),
    SubscriptionIconOption("headphones", "音频", Icons.Rounded.Headphones),
    SubscriptionIconOption("code", "开发", Icons.Rounded.Code),
    SubscriptionIconOption("globe", "网站", Icons.Rounded.Language),
    SubscriptionIconOption("storage", "存储", Icons.Rounded.Storage),
    SubscriptionIconOption("heart", "生活", Icons.Rounded.FavoriteBorder),
    SubscriptionIconOption("coffee", "日常", Icons.Rounded.Coffee),
)

private val subscriptionColorNames = listOf("青色", "黄色", "珊瑚", "绿色", "紫色", "橙色", "蓝色", "粉色")

@Immutable
internal data class SubscriptionAppearanceColors(
    val accent: Color,
    val container: Color,
    val glyph: Color,
    val progress: Color,
    val track: Color,
)

private fun contrast(first: Color, second: Color): Float {
    val a = first.luminance()
    val b = second.luminance()
    return (max(a, b) + .05f) / (min(a, b) + .05f)
}

private fun appearancePalette(scheme: ColorScheme): List<SubscriptionAppearanceColors> = List(8) { key ->
    val accent = scheme.caibCategoryColor(key)
    val container = accent.copy(alpha = if (scheme.caibIsDark) .12f else .22f).compositeOver(scheme.surface)
    // The list and detail share this explicit opaque track. Check both surrounding
    // surfaces as well, so a near-complete bar remains distinguishable on either card.
    val backgrounds = listOf(scheme.surfaceVariant, scheme.surface, scheme.primaryContainer)
    val progress = (0..200).firstNotNullOf { step ->
        Color(lerp(accent, scheme.onSurface, step / 200f).toArgb()).takeIf { candidate ->
            backgrounds.all { contrast(candidate, it) >= 3.3f }
        }
    }
    SubscriptionAppearanceColors(
        accent = accent, container = container,
        glyph = if (scheme.caibIsDark) accent else scheme.onSurface,
        progress = progress, track = scheme.surfaceVariant,
    )
}

// Caib has exactly two fixed schemes. Derive each eight-key table once, never in
// a progress draw lambda, per frame, or once for every subscription in the list.
private val lightAppearancePalette by lazy { appearancePalette(LightColors) }
private val darkAppearancePalette by lazy { appearancePalette(DarkColors) }

@Composable
internal fun rememberSubscriptionAppearanceColors(
    appearance: SubscriptionAppearance,
    quiet: Boolean = false,
): SubscriptionAppearanceColors {
    val scheme = MaterialTheme.colorScheme
    val key = appearance.colorKey.takeIf { it in 0..7 } ?: 0
    return remember(scheme, key, quiet) {
        if (quiet) SubscriptionAppearanceColors(
            accent = scheme.onSurfaceVariant, container = scheme.surfaceVariant,
            glyph = scheme.onSurfaceVariant, progress = scheme.onSurfaceVariant, track = scheme.surfaceVariant,
        ) else (if (scheme.caibIsDark) darkAppearancePalette else lightAppearancePalette)[key]
    }
}

private fun firstSubscriptionLetter(name: String): String = name.codePoints().findFirst().orElse(0x8BA2)
    .let { String(Character.toChars(it)) }.uppercase(Locale.ROOT)

/** Decorative identity in the existing 32dp slot; the card owns its readable name. */
@Composable
internal fun SubscriptionAppearanceMark(
    name: String,
    appearance: SubscriptionAppearance,
    quiet: Boolean,
    modifier: Modifier = Modifier,
    size: Dp = 32.dp,
) {
    val colors = rememberSubscriptionAppearanceColors(appearance, quiet)
    val option = SubscriptionIconOptions.firstOrNull { it.key == appearance.iconKey } ?: SubscriptionIconOptions.first()
    Surface(
        modifier = modifier.size(size).clearAndSetSemantics {},
        color = colors.container, contentColor = colors.glyph, shape = RoundedCornerShape(8.dp),
    ) {
        Box(contentAlignment = Alignment.Center) {
            if (option.image == null) Text(
                firstSubscriptionLetter(name), fontSize = 16.sp, lineHeight = 22.sp,
                fontWeight = FontWeight.SemiBold, color = colors.glyph,
            ) else Icon(option.image, contentDescription = null, tint = colors.glyph, modifier = Modifier.size(20.dp))
        }
    }
}

/** Owns only an appearance draft. The route keeps the project/revision snapshot. */
@Composable
internal fun SubscriptionAppearanceEditor(
    project: SubscriptionProject,
    busy: Boolean,
    error: String?,
    onBack: () -> Unit,
    onSave: (SubscriptionAppearance) -> Unit,
    modifier: Modifier = Modifier,
    preview: @Composable (SubscriptionAppearance) -> Unit,
) {
    val initial = remember(project.id) { SubscriptionRules.normalizeAppearance(project.appearance) }
    val initialIcon = rememberSaveable(project.id) { initial.iconKey }
    val initialColor = rememberSaveable(project.id) { initial.colorKey }
    var iconKey by rememberSaveable(project.id) { mutableStateOf(initialIcon) }
    var colorKey by rememberSaveable(project.id) { mutableIntStateOf(initialColor) }
    var discard by rememberSaveable(project.id) { mutableStateOf(false) }
    val dirty = iconKey != initialIcon || colorKey != initialColor
    val draft = remember(iconKey, colorKey) { SubscriptionAppearance(iconKey, colorKey) }
    fun requestBack() {
        if (!busy) {
            if (dirty) discard = true else onBack()
        }
    }
    BackHandler { requestBack() }

    Column(modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).testTag("subscription-appearance-screen")) {
        SakuTopBar("个性化外观", onBack = { requestBack() })
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(12.dp)
                .testTag("subscription-appearance-options"),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("实时预览", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Box(Modifier.fillMaxWidth().testTag("subscription-appearance-preview")) { preview(draft) }
            AppearanceHeading("图标")
            SubscriptionIconOptions.chunked(4).forEach { options ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    options.forEach { option ->
                        AppearanceChoice(
                            selected = iconKey == option.key, enabled = !busy,
                            description = "图标：${option.label}",
                            onClick = { iconKey = option.key },
                            modifier = Modifier.weight(1f).heightIn(min = 64.dp).testTag("subscription-appearance-icon-${option.key}"),
                        ) {
                            Column(
                                Modifier.padding(5.dp), horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(3.dp),
                            ) {
                                if (option.image == null) Text(
                                    firstSubscriptionLetter(project.name), style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                ) else Icon(option.image, null, Modifier.size(22.dp), tint = MaterialTheme.colorScheme.onSurface)
                                Text(
                                    option.label, modifier = Modifier.fillMaxWidth(), fontSize = 11.sp, lineHeight = 15.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center,
                                )
                            }
                        }
                    }
                    repeat(4 - options.size) { Spacer(Modifier.weight(1f)) }
                }
            }
            AppearanceHeading("识别色")
            subscriptionColorNames.withIndex().chunked(4).forEach { options ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    options.forEach { (key, name) ->
                        val colors = rememberSubscriptionAppearanceColors(SubscriptionAppearance(colorKey = key))
                        AppearanceChoice(
                            selected = colorKey == key, enabled = !busy,
                            description = "识别色：$name", onClick = { colorKey = key },
                            modifier = Modifier.weight(1f).heightIn(min = 56.dp).testTag("subscription-appearance-color-$key"),
                        ) {
                            Row(
                                Modifier.padding(horizontal = 5.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                Surface(
                                    Modifier.size(20.dp), shape = CircleShape, color = colors.accent,
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                                ) {}
                                Text(name, fontSize = 11.sp, lineHeight = 15.sp, color = MaterialTheme.colorScheme.onSurface)
                            }
                        }
                    }
                }
            }
            if (error != null) Text(
                error, color = subscriptionErrorContentColor(), style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite }
                    .testTag("subscription-appearance-error"),
            )
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Row(
            Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface).padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically,
        ) {
            CaibTextButton(
                enabled = !busy, onClick = { iconKey = "auto"; colorKey = 0 },
                modifier = Modifier.heightIn(min = 48.dp).testTag("subscription-appearance-default"),
            ) { Text("默认值") }
            SakuCtaButton(
                enabled = !busy, onClick = { onSave(draft) },
                modifier = Modifier.weight(1f).testTag("subscription-appearance-save"),
            ) { Text(if (busy) "保存中…" else "保存外观", style = MaterialTheme.typography.labelLarge) }
        }
    }
    if (discard) AlertDialog(
        onDismissRequest = { if (!busy) discard = false },
        title = { Text("放弃外观修改？", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onSurface) },
        text = { Text("尚未保存的图标和识别色将不会保留。", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) },
        confirmButton = { CaibTextButton(onClick = { if (!busy) { discard = false; onBack() } }, enabled = !busy) { Text("放弃修改") } },
        dismissButton = { CaibTextButton(onClick = { discard = false }, enabled = !busy) { Text("继续编辑") } },
    )
}

@Composable
private fun AppearanceHeading(text: String) {
    Text(text, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.semantics { heading() })
}

@Composable
private fun AppearanceChoice(
    selected: Boolean,
    enabled: Boolean,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier,
    content: @Composable () -> Unit,
) {
    Surface(
        onClick = onClick, enabled = enabled,
        modifier = modifier.semantics { this.selected = selected; role = Role.RadioButton; contentDescription = description },
        color = if (selected) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface, shape = RoundedCornerShape(8.dp),
        border = BorderStroke(if (selected) 2.dp else 1.dp, if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outline),
    ) {
        Box(contentAlignment = Alignment.Center) {
            content()
            if (selected) Icon(
                Icons.Rounded.Check, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.align(Alignment.TopEnd).padding(3.dp).size(12.dp),
            )
        }
    }
}
