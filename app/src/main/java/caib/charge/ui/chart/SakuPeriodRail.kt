package caib.charge.ui.chart

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import caib.charge.domain.model.ChartGranularity
import caib.charge.domain.model.ChartPeriod
import caib.charge.domain.model.ChartPeriodAxis
import caib.charge.ui.model.UiChartPeriod
import caib.charge.ui.model.toDomainGranularity
import java.time.LocalDate
import java.time.temporal.WeekFields

private val PeriodRailShape = RoundedCornerShape(10.dp)
private val IsoWeekFields = WeekFields.ISO

/**
 * A lazy, finite period rail. Horizontal scrolling only browses; selecting an item is the sole
 * operation that changes application state.
 */
@Composable
internal fun SakuPeriodRail(
    period: UiChartPeriod,
    selectedAnchorDate: LocalDate,
    today: LocalDate,
    onPeriodSelected: (LocalDate) -> Unit,
    contentDescriptionPrefix: String,
    testTagPrefix: String,
    modifier: Modifier = Modifier,
) {
    val granularity = period.toDomainGranularity()
    val axis = remember(granularity, today) {
        ChartPeriodAxis(granularity = granularity, maxDate = today)
    }
    val selectedIndex = remember(axis, selectedAnchorDate) {
        runCatching { axis.indexOf(ChartPeriod(granularity, selectedAnchorDate)) }
            .getOrNull()
            ?: axis.itemCount - 1
    }
    val firstVisibleIndex = (selectedIndex - 1).coerceAtLeast(0)
    // Each granularity has a radically different index range. Reusing a month/week scroll index
    // for the much shorter year axis causes an avoidable blank measurement before correction.
    val listState = remember(granularity) { LazyListState(firstVisibleIndex) }
    val selectionToken = "$granularity:${axis.periodAt(selectedIndex).startDate.toEpochDay()}"
    var positionedSelectionToken by rememberSaveable(testTagPrefix, granularity) {
        mutableStateOf(selectionToken)
    }

    LaunchedEffect(granularity, axis.itemCount, selectedIndex) {
        if (positionedSelectionToken != selectionToken) {
            listState.scrollToItem(firstVisibleIndex)
            positionedSelectionToken = selectionToken
        }
    }

    val accessibilityActions = buildList {
        if (selectedIndex > 0) {
            add(
                CustomAccessibilityAction("选择上一个周期") {
                    onPeriodSelected(axis.periodAt(selectedIndex - 1).startDate)
                    true
                }
            )
        }
        if (selectedIndex < axis.itemCount - 1) {
            add(
                CustomAccessibilityAction("选择下一个周期") {
                    onPeriodSelected(axis.periodAt(selectedIndex + 1).startDate)
                    true
                }
            )
        }
    }

    LazyRow(
        state = listState,
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp)
            .testTag("${testTagPrefix}_period_rail")
            .semantics { customActions = accessibilityActions },
        contentPadding = PaddingValues(0.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        items(
            count = axis.itemCount,
            key = { index -> "$granularity-${axis.periodAt(index).startDate.toEpochDay()}" },
        ) { index ->
            val item = axis.periodAt(index)
            PeriodRailItem(
                period = item,
                selected = index == selectedIndex,
                contentDescriptionPrefix = contentDescriptionPrefix,
                testTagPrefix = testTagPrefix,
                width = period.itemWidth(),
                onClick = { onPeriodSelected(item.startDate) },
            )
        }
    }
}

@Composable
private fun PeriodRailItem(
    period: ChartPeriod,
    selected: Boolean,
    contentDescriptionPrefix: String,
    testTagPrefix: String,
    width: Dp,
    onClick: () -> Unit,
) {
    val containerColor by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
        animationSpec = tween(durationMillis = 150),
        label = "period-rail-container",
    )
    val outlineColor by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
        animationSpec = tween(durationMillis = 150),
        label = "period-rail-outline",
    )
    val label = period.railLabel()
    val description = buildString {
        append(contentDescriptionPrefix)
        append(period.accessibilityKind())
        append('：')
        append(label)
        append('，')
        append(period.accessibilityDateRange())
        if (selected) append("，已选择")
    }

    Surface(
        modifier = Modifier
            .width(width)
            .height(48.dp)
            .testTag("${testTagPrefix}_period_${period.granularity.name}_${period.startDate.toEpochDay()}")
            .semantics { contentDescription = description }
            .selectable(
                selected = selected,
                onClick = onClick,
                role = Role.RadioButton,
            ),
        color = containerColor,
        contentColor = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
        border = BorderStroke(if (selected) 2.dp else 1.dp, outlineColor),
        shape = PeriodRailShape,
    ) {
        androidx.compose.foundation.layout.Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

internal fun ChartPeriod.railLabel(): String = when (granularity) {
    ChartGranularity.WEEK -> {
        val weekYear = startDate.get(IsoWeekFields.weekBasedYear())
        val week = startDate.get(IsoWeekFields.weekOfWeekBasedYear())
        "${weekYear}年第${week}周"
    }
    ChartGranularity.MONTH -> "${startDate.year}年${startDate.monthValue}月"
    ChartGranularity.YEAR -> "${startDate.year}年"
}

private fun ChartPeriod.accessibilityKind(): String = when (granularity) {
    ChartGranularity.WEEK -> "周"
    ChartGranularity.MONTH -> "月份"
    ChartGranularity.YEAR -> "年份"
}

private fun ChartPeriod.accessibilityDateRange(): String =
    "${startDate.year}年${startDate.monthValue}月${startDate.dayOfMonth}日至" +
        "${endDate.year}年${endDate.monthValue}月${endDate.dayOfMonth}日"

private fun UiChartPeriod.itemWidth(): Dp = when (this) {
    UiChartPeriod.WEEK -> 116.dp
    UiChartPeriod.MONTH -> 88.dp
    UiChartPeriod.YEAR -> 72.dp
}
