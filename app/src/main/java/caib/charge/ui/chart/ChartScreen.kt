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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import caib.charge.ui.components.CaibCard
import caib.charge.ui.components.SakuSegmentedControl
import caib.charge.ui.components.SakuTopBar
import caib.charge.ui.model.UiCategorySlice
import caib.charge.ui.model.UiChartPeriod
import caib.charge.ui.model.UiChartPoint
import caib.charge.ui.model.UiEntryType
import caib.charge.ui.model.UiLedgerEntry
import caib.charge.ui.theme.CaibCyanDeep
import caib.charge.ui.theme.CaibPink
import caib.charge.ui.util.formatMoney
import java.time.LocalDate
import kotlin.math.roundToInt

@Composable
fun ChartScreen(
    type: UiEntryType,
    period: UiChartPeriod,
    periodLabel: String,
    periodAnchorDate: LocalDate,
    today: LocalDate,
    points: List<UiChartPoint>,
    ranking: List<UiCategorySlice>,
    modifier: Modifier = Modifier,
    selectedPoint: UiChartPoint? = null,
    selectedPointEntries: List<UiLedgerEntry> = emptyList(),
    onTypeChange: (UiEntryType) -> Unit,
    onPeriodChange: (UiChartPeriod) -> Unit,
    onPeriodSelected: (LocalDate) -> Unit,
    onPointClick: (UiChartPoint) -> Unit,
    onCategoryClick: (UiCategorySlice) -> Unit,
) {
    val total = points.filterNot { it.isFuture }.sumOf { it.amountCents }
    val divisor = points.count { !it.isFuture }.coerceAtLeast(1)
    val average = total / divisor
    val unit = if (period == UiChartPeriod.YEAR) "月均" else "日均"

    Column(modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        ChartTopBar(
            title = "图表",
            periodLabel = periodLabel,
        )
        LazyColumn(
            modifier = Modifier.fillMaxSize().testTag("chart_scroll"),
            contentPadding = PaddingValues(start = 16.dp, top = 8.dp, end = 16.dp, bottom = 112.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        SakuSegmentedRow(
                            options = listOf(UiEntryType.EXPENSE to "支出", UiEntryType.INCOME to "收入"),
                            selected = type,
                            onSelect = onTypeChange,
                            contentDescriptionPrefix = "图表收支",
                            modifier = Modifier.weight(2f),
                        )
                        SakuSegmentedRow(
                            options = listOf(
                                UiChartPeriod.WEEK to "周",
                                UiChartPeriod.MONTH to "月",
                                UiChartPeriod.YEAR to "年",
                            ),
                            selected = period,
                            onSelect = onPeriodChange,
                            contentDescriptionPrefix = "图表周期",
                            modifier = Modifier.weight(3f),
                        )
                    }
                    SakuPeriodRail(
                        period = period,
                        selectedAnchorDate = periodAnchorDate,
                        today = today,
                        onPeriodSelected = onPeriodSelected,
                        contentDescriptionPrefix = "图表",
                        testTagPrefix = "chart",
                    )
                }
            }
            item {
                CaibCard(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(
                            if (type == UiEntryType.EXPENSE) "本期支出" else "本期收入",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
                            Text(formatMoney(total), style = MaterialTheme.typography.headlineLarge)
                            Spacer(Modifier.weight(1f))
                            Column(horizontalAlignment = Alignment.End) {
                                Text(unit, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(formatMoney(average), style = MaterialTheme.typography.titleMedium)
                            }
                        }
                        val activePointCount = points.count { !it.isFuture && it.amountCents > 0 }
                        val activePointUnit = if (period == UiChartPeriod.YEAR) "有账月" else "有账日"
                        Text(
                            "$periodLabel · $activePointCount 个$activePointUnit",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        TrendChart(
                            points = points,
                            selectedPoint = selectedPoint,
                            type = type,
                            onPointClick = onPointClick,
                        )
                    }
                }
            }
            if (selectedPoint != null) {
                item { PointCallout(selectedPoint, selectedPointEntries) }
            }
            item { Text("分类排行", style = MaterialTheme.typography.titleLarge) }
            if (ranking.isEmpty()) {
                item {
                    CaibCard(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            "当前周期暂无可分析的数据",
                            modifier = Modifier.padding(24.dp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            } else {
                items(ranking, key = { it.categoryId }) { item ->
                    CategoryRankingRow(item = item, total = total, onClick = { onCategoryClick(item) })
                }
            }
        }
    }
}

@Composable
private fun ChartTopBar(
    title: String,
    periodLabel: String,
) {
    SakuTopBar(title = title, subtitle = periodLabel)
}

@Composable
internal fun <T> SakuSegmentedRow(
    options: List<Pair<T, String>>,
    selected: T,
    onSelect: (T) -> Unit,
    contentDescriptionPrefix: String,
    modifier: Modifier = Modifier,
) {
    val items = options.map { it.first }
    SakuSegmentedControl(
        items = items,
        selectedItem = selected,
        onItemSelected = onSelect,
        itemLabel = { value -> options.first { it.first == value }.second },
        itemContentDescription = { value ->
            val label = options.first { it.first == value }.second
            "$contentDescriptionPrefix：$label${if (value == selected) "，已选择" else ""}"
        },
        modifier = modifier.fillMaxWidth(),
    )
}

@Composable
internal fun TrendChart(
    points: List<UiChartPoint>,
    selectedPoint: UiChartPoint?,
    type: UiEntryType,
    onPointClick: ((UiChartPoint) -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val lineColor = if (type == UiEntryType.EXPENSE) CaibPink else CaibCyanDeep
    val selectedColor = MaterialTheme.colorScheme.tertiary
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val futureColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)
    val surfaceColor = MaterialTheme.colorScheme.surface
    val max = remember(points) {
        points.asSequence()
            .filterNot { it.isFuture }
            .maxOfOrNull { it.amountCents }
            ?.coerceAtLeast(1L)
            ?: 1L
    }
    val summary = remember(points) {
        if (points.isEmpty()) "暂无趋势数据" else points.joinToString("，") {
            "${it.label}${if (it.isFuture) "未来" else formatMoney(it.amountCents)}"
        }
    }
    val selectablePoints = remember(points) { points.filter { it.selectable && !it.isFuture } }

    Column(modifier) {
        val pointInteraction = if (onPointClick == null) Modifier else Modifier.pointerInput(points) {
            detectTapGestures { tap ->
                if (points.isEmpty()) return@detectTapGestures
                val step = if (points.size <= 1) size.width.toFloat() else size.width / (points.size - 1f)
                val index = if (points.size <= 1) 0 else (tap.x / step).roundToInt().coerceIn(points.indices)
                points[index].takeIf { it.selectable && !it.isFuture }?.let(onPointClick)
            }
        }
        Canvas(
            modifier = Modifier.fillMaxWidth().height(190.dp).semantics {
                contentDescription = "趋势图：$summary"
            }.then(pointInteraction).drawWithCache {
                val gridStroke = 1.dp.toPx()
                val lineStroke = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
                val futureStroke = Stroke(width = gridStroke)
                val chartHeight = size.height - 20.dp.toPx()
                val step = if (points.size <= 1) 0f else size.width / (points.size - 1f)
                val path = Path()
                var activePointCount = 0
                points.forEachIndexed { index, point ->
                    if (!point.isFuture) {
                        val x = if (points.size <= 1) size.width / 2f else index * step
                        val y = size.height - (point.amountCents.toFloat() / max) * chartHeight
                        if (activePointCount++ == 0) path.moveTo(x, y) else path.lineTo(x, y)
                    }
                }
                onDrawBehind {
                    repeat(4) { index ->
                        val y = size.height * index / 3f
                        drawLine(gridColor, Offset(0f, y), Offset(size.width, y), strokeWidth = gridStroke)
                    }
                    if (activePointCount > 0) drawPath(path, lineColor, style = lineStroke)
                    points.forEachIndexed { index, point ->
                        val x = if (points.size <= 1) size.width / 2f else index * step
                        val y = size.height - (point.amountCents.toFloat() / max) * chartHeight
                        when {
                            point.isFuture -> drawCircle(
                                futureColor,
                                4.dp.toPx(),
                                Offset(x, size.height - gridStroke),
                                style = futureStroke,
                            )
                            selectedPoint?.index == point.index -> {
                                drawCircle(selectedColor, 7.dp.toPx(), Offset(x, y))
                                drawCircle(surfaceColor, 3.dp.toPx(), Offset(x, y))
                            }
                            else -> drawCircle(lineColor, 4.dp.toPx(), Offset(x, y))
                        }
                    }
                }
            },
        ) { }
        if (points.isNotEmpty()) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(points.first().label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (points.size > 2) Text(points[points.size / 2].label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(points.last().label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (onPointClick != null) {
            LazyRow(
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                contentPadding = PaddingValues(top = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                items(selectablePoints, key = { "point-${it.startDate}" }) { point ->
                    Surface(
                        modifier = Modifier
                            .widthIn(min = 48.dp)
                            .heightIn(min = 48.dp)
                            .clickable { onPointClick(point) }
                            .semantics {
                            role = Role.Button
                            selected = selectedPoint?.index == point.index
                            contentDescription = "趋势点${point.label}，${formatMoney(point.amountCents)}"
                        },
                        color = if (selectedPoint?.index == point.index) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(8.dp),
                    ) {
                        Box(Modifier.padding(horizontal = 12.dp), contentAlignment = Alignment.Center) {
                            Text(point.label, style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PointCallout(point: UiChartPoint, entries: List<UiLedgerEntry>) {
    CaibCard(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth()) {
                Text(point.label, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.weight(1f))
                Text(formatMoney(point.amountCents), fontWeight = FontWeight.Medium)
            }
            if (entries.isEmpty()) {
                Text("该点没有逐笔账目", color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                Text("金额最高的 ${entries.size.coerceAtMost(3)} 笔", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                entries.take(3).forEach { entry ->
                    Row(Modifier.fillMaxWidth()) {
                        Text(entry.note.ifBlank { entry.categoryName }, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(formatMoney(entry.amountCents), modifier = Modifier.padding(start = 12.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun CategoryRankingRow(item: UiCategorySlice, total: Long, onClick: () -> Unit) {
    val percent = if (total <= 0L) 0f else item.amountCents.toFloat() / total
    CaibCard(modifier = Modifier.fillMaxWidth(), onClick = onClick) {
        Column(
            modifier = Modifier.fillMaxWidth().heightIn(min = 72.dp).padding(horizontal = 14.dp, vertical = 12.dp).semantics {
                contentDescription = "分类${item.name}，${formatMoney(item.amountCents)}，占比${(percent * 100).roundToInt()}%，打开分类分析"
            },
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(40.dp).background(item.color.copy(alpha = 0.22f), CircleShape), contentAlignment = Alignment.Center) {
                    Box(Modifier.size(12.dp).background(item.color, CircleShape))
                }
                Text(item.name, modifier = Modifier.weight(1f).padding(horizontal = 12.dp), fontWeight = FontWeight.Medium)
                Column(horizontalAlignment = Alignment.End) {
                    Text(formatMoney(item.amountCents), fontWeight = FontWeight.Medium)
                    Text("${(percent * 100).roundToInt()}%", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Box(Modifier.fillMaxWidth().height(8.dp).background(MaterialTheme.colorScheme.surfaceVariant, CircleShape)) {
                Box(Modifier.fillMaxWidth(percent.coerceIn(0f, 1f)).height(8.dp).background(item.color, CircleShape))
            }
        }
    }
}
