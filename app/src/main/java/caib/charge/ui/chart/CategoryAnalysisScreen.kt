package caib.charge.ui.chart

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import caib.charge.ui.components.CaibCard
import caib.charge.ui.components.CategoryIcon
import caib.charge.ui.components.SakuTopBar
import caib.charge.ui.model.UiCategory
import caib.charge.ui.model.UiChartPeriod
import caib.charge.ui.model.UiChartPoint
import caib.charge.ui.model.UiEntryRankingOrder
import caib.charge.ui.model.UiEntryType
import caib.charge.ui.model.UiLedgerEntry
import caib.charge.ui.model.UiNoteBreakdownSlice
import caib.charge.ui.theme.caibIsDark
import caib.charge.ui.theme.caibPaletteColor
import caib.charge.ui.theme.categoryContainerColor
import caib.charge.ui.theme.categoryContentColor
import caib.charge.ui.theme.controlOutlineColor
import caib.charge.ui.theme.paletteColor
import caib.charge.ui.util.formatMoney
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.roundToInt

private val NoteDonutStrokeWidth = 24.dp
private val NoteDonutEdgePadding = 2.dp
private val AnalysisEntryDateFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("M月d日")

private data class DonutBandGeometry(
    val center: Offset,
    val innerRadius: Float,
    val outerRadius: Float,
    val centerLineRadius: Float,
)

private fun donutBandGeometry(
    width: Float,
    height: Float,
    strokeWidth: Float,
    edgePadding: Float,
): DonutBandGeometry {
    val center = Offset(width / 2f, height / 2f)
    val outerRadius = (min(width, height) / 2f - edgePadding).coerceAtLeast(0f)
    val innerRadius = (outerRadius - strokeWidth).coerceAtLeast(0f)
    return DonutBandGeometry(
        center = center,
        innerRadius = innerRadius,
        outerRadius = outerRadius,
        centerLineRadius = (innerRadius + outerRadius) / 2f,
    )
}

@Composable
fun CategoryAnalysisScreen(
    category: UiCategory?,
    type: UiEntryType,
    period: UiChartPeriod,
    periodLabel: String,
    periodAnchorDate: LocalDate,
    today: LocalDate,
    points: List<UiChartPoint>,
    entries: List<UiLedgerEntry>,
    totalForTypeCents: Long,
    breakdown: List<UiNoteBreakdownSlice>,
    selectedBreakdownId: String?,
    rankingOrder: UiEntryRankingOrder,
    onBack: () -> Unit,
    onPeriodChange: (UiChartPeriod) -> Unit,
    onPeriodSelected: (LocalDate) -> Unit,
    onBreakdownSelect: (String?) -> Unit,
    onRankingOrderChange: (UiEntryRankingOrder) -> Unit,
    onEntryClick: (UiLedgerEntry) -> Unit,
    modifier: Modifier = Modifier,
) {
    val total = remember(entries) { entries.sumOf { it.amountCents } }
    val divisor = remember(points) { points.count { !it.isFuture }.coerceAtLeast(1) }
    val average = total / divisor
    val share = if (totalForTypeCents <= 0L) 0f else total.toFloat() / totalForTypeCents
    val selectedNotes = breakdown.firstOrNull { it.id == selectedBreakdownId }?.normalizedNotes
    val visibleEntries = remember(entries, selectedNotes, rankingOrder) {
        entries.filter { selectedNotes == null || it.normalizedNote in selectedNotes }.let { list ->
            when (rankingOrder) {
                UiEntryRankingOrder.AMOUNT -> list.sortedWith(compareByDescending<UiLedgerEntry> { it.amountCents }.thenByDescending { it.date }.thenByDescending { it.id })
                UiEntryRankingOrder.DATE -> list.sortedWith(compareByDescending<UiLedgerEntry> { it.date }.thenByDescending { it.id })
            }
        }
    }

    Column(modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        CategoryTopBar(category = category, periodLabel = periodLabel, onBack = onBack)
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                SakuSegmentedRow(
                    options = listOf(UiChartPeriod.WEEK to "周", UiChartPeriod.MONTH to "月", UiChartPeriod.YEAR to "年"),
                    selected = period,
                    onSelect = onPeriodChange,
                    contentDescriptionPrefix = "分类分析周期",
                )
            }
            item {
                SakuPeriodRail(
                    period = period,
                    selectedAnchorDate = periodAnchorDate,
                    today = today,
                    onPeriodSelected = onPeriodSelected,
                    contentDescriptionPrefix = "分类分析",
                    testTagPrefix = "analysis",
                )
            }
            item {
                CaibCard(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            "$periodLabel · ${if (type == UiEntryType.EXPENSE) "分类支出" else "分类收入"}",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(formatMoney(total), style = MaterialTheme.typography.headlineLarge)
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            SummaryValue("同期占比", "${(share * 100).roundToInt()}%")
                            SummaryValue("记录", "${entries.size} 笔")
                            SummaryValue(if (period == UiChartPeriod.YEAR) "月均" else "日均", formatMoney(average))
                        }
                        TrendChart(points = points, selectedPoint = null, type = type, onPointClick = null)
                    }
                }
            }
            item {
                Text("备注构成", style = MaterialTheme.typography.titleLarge)
            }
            item {
                CaibCard(modifier = Modifier.fillMaxWidth()) {
                    if (breakdown.isEmpty()) {
                        Text("该分类在当前周期暂无备注构成数据", modifier = Modifier.padding(24.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        Column(Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            NoteDonutChart(
                                slices = breakdown,
                                total = total,
                                selectedId = selectedBreakdownId,
                                onSelect = onBreakdownSelect,
                            )
                            val allSelected = selectedBreakdownId == null
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 48.dp)
                                    .semantics { contentDescription = "全部备注构成" }
                                    .selectable(
                                        selected = allSelected,
                                        onClick = { onBreakdownSelect(null) },
                                        role = Role.RadioButton,
                                    ),
                                color = if (allSelected) MaterialTheme.colorScheme.primaryContainer
                                else MaterialTheme.colorScheme.surfaceVariant,
                                border = BorderStroke(
                                    if (allSelected) 2.dp else 1.dp,
                                    if (allSelected) MaterialTheme.colorScheme.primary
                                    else controlOutlineColor(),
                                ),
                                shape = RoundedCornerShape(8.dp),
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp),
                                    horizontalArrangement = Arrangement.Center,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    if (allSelected) {
                                        Icon(
                                            Icons.Rounded.Check,
                                            contentDescription = null,
                                            modifier = Modifier.size(18.dp),
                                        )
                                        Spacer(Modifier.size(6.dp))
                                    }
                                    Text("全部构成", fontWeight = FontWeight.Medium)
                                }
                            }
                            breakdown.forEach { slice ->
                                BreakdownLegendRow(slice, selected = slice.id == selectedBreakdownId) {
                                    onBreakdownSelect(if (slice.id == selectedBreakdownId) null else slice.id)
                                }
                            }
                        }
                    }
                }
            }
            item {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("逐笔排行", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                    Text(if (selectedBreakdownId == null) "全部" else breakdown.firstOrNull { it.id == selectedBreakdownId }?.label.orEmpty(), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            item {
                SakuSegmentedRow(
                    options = listOf(UiEntryRankingOrder.AMOUNT to "按金额", UiEntryRankingOrder.DATE to "按时间"),
                    selected = rankingOrder,
                    onSelect = onRankingOrderChange,
                    contentDescriptionPrefix = "账目排序",
                )
            }
            if (visibleEntries.isEmpty()) {
                item {
                    CaibCard(modifier = Modifier.fillMaxWidth()) {
                        Text("当前构成下没有账目", modifier = Modifier.padding(24.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            } else {
                items(visibleEntries, key = { it.id }) { entry -> AnalysisEntryRow(entry, onEntryClick) }
            }
        }
    }
}

@Composable
private fun CategoryTopBar(
    category: UiCategory?,
    periodLabel: String,
    onBack: () -> Unit,
) {
    SakuTopBar(
        title = category?.name ?: "分类分析",
        subtitle = "${if (category?.archived == true) "已归档" else "分类分析"} · $periodLabel",
        onBack = onBack,
        actions = {
            if (category != null) {
                Box(
                    Modifier.size(40.dp).background(categoryContainerColor(category.colorKey), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    CategoryIcon(category.iconKey, contentDescription = category.name, modifier = Modifier.size(22.dp), tint = categoryContentColor(category.colorKey))
                }
                Spacer(Modifier.size(8.dp))
            }
        },
    )
}

@Composable
private fun SummaryValue(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
private fun NoteDonutChart(
    slices: List<UiNoteBreakdownSlice>,
    total: Long,
    selectedId: String?,
    onSelect: (String?) -> Unit,
) {
    val summary = remember(slices) {
        slices.joinToString("，") { "${it.label}${(it.percentage * 100).roundToInt()}%" }
    }
    val colors = MaterialTheme.colorScheme
    val sliceColors = remember(slices, colors) { slices.map { colors.caibPaletteColor(it.color) } }
    Box(Modifier.size(210.dp), contentAlignment = Alignment.Center) {
        Canvas(
            modifier = Modifier.fillMaxSize().semantics { contentDescription = "备注构成环形图：$summary" }.pointerInput(slices, total, selectedId) {
                detectTapGestures { tap ->
                    if (slices.isEmpty() || total <= 0L) return@detectTapGestures
                    val geometry = donutBandGeometry(
                        width = size.width.toFloat(),
                        height = size.height.toFloat(),
                        strokeWidth = NoteDonutStrokeWidth.toPx(),
                        edgePadding = NoteDonutEdgePadding.toPx(),
                    )
                    val distance = hypot(tap.x - geometry.center.x, tap.y - geometry.center.y)
                    if (distance < geometry.innerRadius || distance > geometry.outerRadius) {
                        return@detectTapGestures
                    }
                    val angle = ((atan2(tap.y - geometry.center.y, tap.x - geometry.center.x) * 180f / PI.toFloat()) + 450f) % 360f
                    var cursor = 0f
                    slices.firstOrNull { slice ->
                        val sweep = slice.amountCents.toFloat() / total * 360f
                        val hit = angle >= cursor && angle < cursor + sweep
                        cursor += sweep
                        hit
                    }?.let { onSelect(if (it.id == selectedId) null else it.id) }
                }
            }.drawWithCache {
                val strokeWidth = NoteDonutStrokeWidth.toPx()
                val geometry = donutBandGeometry(
                    width = size.width,
                    height = size.height,
                    strokeWidth = strokeWidth,
                    edgePadding = NoteDonutEdgePadding.toPx(),
                )
                val arcSize = Size(geometry.centerLineRadius * 2f, geometry.centerLineRadius * 2f)
                val arcTopLeft = Offset(
                    geometry.center.x - geometry.centerLineRadius,
                    geometry.center.y - geometry.centerLineRadius,
                )
                val stroke = Stroke(width = strokeWidth, cap = StrokeCap.Butt)
                onDrawBehind {
                    var start = -90f
                    slices.forEachIndexed { index, slice ->
                        val sweep = if (total <= 0L) 0f else slice.amountCents.toFloat() / total * 360f
                        val emphasis = when {
                            selectedId == null || slice.id == selectedId -> 1f
                            colors.caibIsDark -> 0.72f
                            else -> 0.42f
                        }
                        drawArc(
                            color = sliceColors[index].copy(alpha = sliceColors[index].alpha * emphasis),
                            startAngle = start,
                            sweepAngle = sweep,
                            useCenter = false,
                            topLeft = arcTopLeft,
                            size = arcSize,
                            style = stroke,
                        )
                        start += sweep
                    }
                }
            },
        ) { }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("分类合计", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(formatMoney(total), style = MaterialTheme.typography.titleMedium)
        }
    }
}

@Composable
private fun BreakdownLegendRow(slice: UiNoteBreakdownSlice, selected: Boolean, onClick: () -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .semantics {
                contentDescription = "备注${slice.label}，${formatMoney(slice.amountCents)}，${(slice.percentage * 100).roundToInt()}%，${slice.count}笔"
            }
            .selectable(
                selected = selected,
                onClick = onClick,
                role = Role.RadioButton,
            ),
        color = if (selected) MaterialTheme.colorScheme.primaryContainer
        else MaterialTheme.colorScheme.surface,
        border = BorderStroke(
            if (selected) 2.dp else 1.dp,
            if (selected) MaterialTheme.colorScheme.primary
            else controlOutlineColor(),
        ),
        shape = RoundedCornerShape(8.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(12.dp).background(paletteColor(slice.color), CircleShape))
            Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
                Text(slice.label, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("${slice.count} 笔", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (selected) {
                Icon(
                    Icons.Rounded.Check,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.size(8.dp))
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(formatMoney(slice.amountCents), fontWeight = FontWeight.Medium)
                Text("${(slice.percentage * 100).roundToInt()}%", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun AnalysisEntryRow(entry: UiLedgerEntry, onClick: (UiLedgerEntry) -> Unit) {
    CaibCard(modifier = Modifier.fillMaxWidth(), onClick = { onClick(entry) }) {
        Row(Modifier.fillMaxWidth().heightIn(min = 68.dp).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(40.dp).background(categoryContainerColor(entry.categoryColorKey), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                CategoryIcon(entry.categoryIconKey, contentDescription = null, modifier = Modifier.size(22.dp), tint = categoryContentColor(entry.categoryColorKey))
            }
            Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
                Text(entry.note.ifBlank { entry.categoryName }, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(entry.date.format(AnalysisEntryDateFormatter), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(formatMoney(entry.amountCents), fontWeight = FontWeight.Medium)
        }
    }
}
