package caib.charge.ui.chart

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import caib.charge.ui.theme.CategoryPalette
import caib.charge.ui.util.formatMoney
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
    points: List<UiChartPoint>,
    entries: List<UiLedgerEntry>,
    totalForTypeCents: Long,
    breakdown: List<UiNoteBreakdownSlice>,
    selectedBreakdownId: String?,
    rankingOrder: UiEntryRankingOrder,
    onBack: () -> Unit,
    onPeriodChange: (UiChartPeriod) -> Unit,
    onPreviousPeriod: () -> Unit,
    onNextPeriod: () -> Unit,
    canNextPeriod: Boolean,
    onBreakdownSelect: (String?) -> Unit,
    onRankingOrderChange: (UiEntryRankingOrder) -> Unit,
    onEntryClick: (UiLedgerEntry) -> Unit,
    modifier: Modifier = Modifier,
) {
    val total = entries.sumOf { it.amountCents }
    val divisor = points.count { !it.isFuture }.coerceAtLeast(1)
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
        CategoryTopBar(category = category, onBack = onBack)
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
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onPreviousPeriod, modifier = Modifier.size(48.dp)) {
                        Icon(Icons.Rounded.ChevronLeft, contentDescription = "上一个周期")
                    }
                    Text(periodLabel, modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                    IconButton(onClick = onNextPeriod, enabled = canNextPeriod, modifier = Modifier.size(48.dp)) {
                        Icon(Icons.Rounded.ChevronRight, contentDescription = "下一个周期")
                    }
                }
            }
            item {
                CaibCard(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(if (type == UiEntryType.EXPENSE) "分类支出" else "分类收入", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                            Surface(
                                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable { onBreakdownSelect(null) },
                                color = if (selectedBreakdownId == null) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                                shape = RoundedCornerShape(8.dp),
                            ) {
                                Box(contentAlignment = Alignment.Center) { Text("全部构成", fontWeight = FontWeight.Medium) }
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
private fun CategoryTopBar(category: UiCategory?, onBack: () -> Unit) {
    SakuTopBar(
        title = category?.name ?: "分类分析",
        subtitle = if (category?.archived == true) "已归档分类" else "分类分析",
        onBack = onBack,
        actions = {
            if (category != null) {
                Box(
                    Modifier.size(40.dp).background(CategoryPalette[category.colorKey.mod(CategoryPalette.size)].copy(alpha = 0.22f), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    CategoryIcon(category.iconKey, contentDescription = category.name, modifier = Modifier.size(22.dp))
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
                    slices.forEach { slice ->
                        val sweep = if (total <= 0L) 0f else slice.amountCents.toFloat() / total * 360f
                        val emphasis = if (selectedId == null || slice.id == selectedId) 1f else 0.42f
                        drawArc(
                            color = slice.color.copy(alpha = slice.color.alpha * emphasis),
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
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).background(
            if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
            RoundedCornerShape(8.dp),
        ).clickable(onClick = onClick).padding(horizontal = 10.dp, vertical = 8.dp).semantics {
            contentDescription = "备注${slice.label}，${formatMoney(slice.amountCents)}，${(slice.percentage * 100).roundToInt()}%，${slice.count}笔"
        },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(12.dp).background(slice.color, CircleShape))
        Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
            Text(slice.label, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("${slice.count} 笔", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(formatMoney(slice.amountCents), fontWeight = FontWeight.Medium)
            Text("${(slice.percentage * 100).roundToInt()}%", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun AnalysisEntryRow(entry: UiLedgerEntry, onClick: (UiLedgerEntry) -> Unit) {
    CaibCard(modifier = Modifier.fillMaxWidth(), onClick = { onClick(entry) }) {
        Row(Modifier.fillMaxWidth().heightIn(min = 68.dp).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(40.dp).background(CategoryPalette[entry.categoryColorKey.mod(CategoryPalette.size)].copy(alpha = 0.22f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                CategoryIcon(entry.categoryIconKey, contentDescription = null, modifier = Modifier.size(22.dp))
            }
            Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
                Text(entry.note.ifBlank { entry.categoryName }, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(entry.date.format(AnalysisEntryDateFormatter), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(formatMoney(entry.amountCents), fontWeight = FontWeight.Medium)
        }
    }
}
