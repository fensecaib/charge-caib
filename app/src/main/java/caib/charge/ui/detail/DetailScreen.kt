package caib.charge.ui.detail

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.FilterAlt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import caib.charge.ui.components.CategoryIcon
import caib.charge.ui.components.SakuCard
import caib.charge.ui.components.SakuSegmentedControl
import caib.charge.ui.components.SakuTopBar
import caib.charge.ui.components.EntryMotionTokens
import caib.charge.ui.model.UiCategory
import caib.charge.ui.model.UiDetailFilter
import caib.charge.ui.model.UiEntryType
import caib.charge.ui.model.UiLedgerEntry
import caib.charge.ui.model.UiPeriodSummary
import caib.charge.ui.theme.CategoryPalette
import caib.charge.ui.util.formatDay
import caib.charge.ui.util.formatMoney
import caib.charge.ui.util.formatMonth
import caib.charge.ui.util.formatSignedMoney
import java.time.LocalDate
import java.time.YearMonth

@Composable
fun DetailScreen(
    filter: UiDetailFilter,
    entries: List<UiLedgerEntry>,
    categories: List<UiCategory>,
    summary: UiPeriodSummary,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onTypeFilter: (UiEntryType?) -> Unit,
    onCategoryFilter: (String?) -> Unit,
    onEntryClick: (UiLedgerEntry) -> Unit,
    modifier: Modifier = Modifier,
    onCategoryHotspotClick: (UiLedgerEntry) -> Unit = onEntryClick,
    onInfoHotspotClick: (UiLedgerEntry) -> Unit = onEntryClick,
    onAmountHotspotClick: (UiLedgerEntry) -> Unit = onEntryClick,
    onAmountHotspotBoundsClick: ((UiLedgerEntry, Rect) -> Unit)? = null,
    onDeleteRequest: ((UiLedgerEntry) -> Unit)? = null,
    recentlyCreatedEntryId: Long? = null,
    onInsertAnimationFinished: (Long) -> Unit = {},
    today: LocalDate = LocalDate.now(),
) {
    val grouped = remember(entries) {
        entries.groupBy { it.date }.toList().sortedByDescending { it.first }
    }
    var longPressedEntry by remember { mutableStateOf<UiLedgerEntry?>(null) }
    var deleteCandidate by remember { mutableStateOf<UiLedgerEntry?>(null) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        DetailTopBar(
            filter = filter,
            canNext = filter.year?.let { it < today.year }
                ?: (filter.month < YearMonth.from(today)),
            onPrevious = onPreviousMonth,
            onNext = onNextMonth
        )
        LazyColumn(
            modifier = Modifier.fillMaxSize().testTag("detail_list"),
            contentPadding = PaddingValues(
                start = 12.dp,
                end = 12.dp,
                top = 6.dp,
                bottom = 16.dp
            ),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item(key = "summary") {
                CompactSummaryCard(summary = summary, annual = filter.year != null)
            }
            item(key = "filters") {
                CompactFilterRow(
                    filter = filter,
                    categories = categories,
                    onTypeFilter = onTypeFilter,
                    onCategoryFilter = onCategoryFilter
                )
            }
            if (entries.isEmpty()) {
                item(key = "empty") { EmptyDetailState() }
            } else {
                items(grouped, key = { "day-${it.first}" }) { (date, dayEntries) ->
                    DayLedgerCard(
                        dateLabel = formatDay(date),
                        entries = dayEntries,
                        onCategoryClick = onCategoryHotspotClick,
                        onInfoClick = onInfoHotspotClick,
                        onAmountClick = { entry, bounds ->
                            if (onAmountHotspotBoundsClick != null) onAmountHotspotBoundsClick(entry, bounds)
                            else onAmountHotspotClick(entry)
                        },
                        onLongClick = { longPressedEntry = it },
                        recentlyCreatedEntryId = recentlyCreatedEntryId,
                        onInsertAnimationFinished = onInsertAnimationFinished,
                    )
                }
            }
        }
    }

    longPressedEntry?.let { entry ->
        AlertDialog(
            onDismissRequest = { longPressedEntry = null },
            shape = RoundedCornerShape(10.dp),
            title = { Text("${entry.categoryName} · ${formatSignedMoney(entry.amountCents, entry.type == UiEntryType.INCOME)}") },
            text = { Text("修改会打开完整账目页；删除后仍可通过底部提示撤销。") },
            confirmButton = {
                Row {
                    TextButton(onClick = {
                        longPressedEntry = null
                        onEntryClick(entry)
                    }) { Text("修改") }
                    if (onDeleteRequest != null) {
                        TextButton(onClick = {
                            longPressedEntry = null
                            deleteCandidate = entry
                        }) { Text("删除", color = MaterialTheme.colorScheme.error) }
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { longPressedEntry = null }) { Text("取消") }
            }
        )
    }

    deleteCandidate?.let { entry ->
        AlertDialog(
            onDismissRequest = { deleteCandidate = null },
            shape = RoundedCornerShape(10.dp),
            title = { Text("删除这笔账目？") },
            text = { Text("删除后可在底部提示中立即撤销。") },
            confirmButton = {
                TextButton(
                    modifier = Modifier.testTag("confirm_delete_entry"),
                    onClick = {
                        deleteCandidate = null
                        onDeleteRequest?.invoke(entry)
                    },
                ) { Text("确认删除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { deleteCandidate = null }) { Text("取消") }
            },
        )
    }
}

@Composable
private fun DetailTopBar(
    filter: UiDetailFilter,
    canNext: Boolean,
    onPrevious: () -> Unit,
    onNext: () -> Unit
) {
    SakuTopBar(
        title = "明细",
        actions = {
            IconButton(onClick = onPrevious, modifier = Modifier.size(48.dp)) {
                Icon(Icons.Rounded.ChevronLeft, contentDescription = "上一个月")
            }
            Text(
                filter.year?.let { "${it}年" } ?: formatMonth(filter.month),
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1
            )
            IconButton(onClick = onNext, enabled = canNext, modifier = Modifier.size(48.dp)) {
                Icon(Icons.Rounded.ChevronRight, contentDescription = "下一个月")
            }
        }
    )
}

@Composable
private fun CompactSummaryCard(summary: UiPeriodSummary, annual: Boolean) {
    SakuCard(modifier = Modifier.fillMaxWidth().testTag("detail_summary")) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1.15f)) {
                Text(
                    if (annual) "本年结余" else "本月结余",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    formatMoney(summary.balanceCents),
                    style = MaterialTheme.typography.titleLarge,
                    color = if (summary.balanceCents >= 0) MaterialTheme.colorScheme.onSurface
                    else MaterialTheme.colorScheme.error,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            SummaryMetric(
                label = "收入",
                cents = summary.incomeCents,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.weight(0.8f)
            )
            SummaryMetric(
                label = "支出",
                cents = summary.expenseCents,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.weight(0.8f)
            )
        }
    }
}

@Composable
private fun SummaryMetric(label: String, cents: Long, color: Color, modifier: Modifier = Modifier) {
    Column(modifier = modifier, horizontalAlignment = Alignment.End) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            formatMoney(cents),
            style = MaterialTheme.typography.labelLarge,
            color = color,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun CompactFilterRow(
    filter: UiDetailFilter,
    categories: List<UiCategory>,
    onTypeFilter: (UiEntryType?) -> Unit,
    onCategoryFilter: (String?) -> Unit
) {
    val typeItems = listOf<UiEntryType?>(null, UiEntryType.EXPENSE, UiEntryType.INCOME)
    val selectedCategory = categories.firstOrNull { it.id == filter.categoryId }
    Row(
        modifier = Modifier.fillMaxWidth().testTag("detail_filters"),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        SakuSegmentedControl(
            items = typeItems,
            selectedItem = filter.type,
            onItemSelected = onTypeFilter,
            itemLabel = {
                when (it) {
                    UiEntryType.EXPENSE -> "支出"
                    UiEntryType.INCOME -> "收入"
                    null -> "全部"
                }
            },
            modifier = Modifier.weight(1f)
        )
        SakuCard(
            modifier = Modifier.width(112.dp).height(48.dp),
            onClick = {
                val compatible = categories.filter { filter.type == null || it.type == filter.type }
                val currentIndex = compatible.indexOfFirst { it.id == filter.categoryId }
                onCategoryFilter(
                    if (currentIndex + 1 in compatible.indices) compatible[currentIndex + 1].id else null
                )
            }
        ) {
            Row(
                modifier = Modifier.fillMaxSize().padding(horizontal = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    Icons.Rounded.FilterAlt,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Text(
                    selectedCategory?.name ?: "全部分类",
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun DayLedgerCard(
    dateLabel: String,
    entries: List<UiLedgerEntry>,
    onCategoryClick: (UiLedgerEntry) -> Unit,
    onInfoClick: (UiLedgerEntry) -> Unit,
    onAmountClick: (UiLedgerEntry, Rect) -> Unit,
    onLongClick: (UiLedgerEntry) -> Unit,
    recentlyCreatedEntryId: Long?,
    onInsertAnimationFinished: (Long) -> Unit,
) {
    val (expense, income) = remember(entries) {
        var expenseTotal = 0L
        var incomeTotal = 0L
        entries.forEach { entry ->
            if (entry.type == UiEntryType.EXPENSE) expenseTotal += entry.amountCents
            else incomeTotal += entry.amountCents
        }
        expenseTotal to incomeTotal
    }
    SakuCard(modifier = Modifier.fillMaxWidth()) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(42.dp)
                    .padding(horizontal = 10.dp)
                    .testTag("detail_day_header_$dateLabel"),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(dateLabel, style = MaterialTheme.typography.labelLarge)
                Text(
                    buildString {
                        if (income > 0L) append("收 ${formatMoney(income)}")
                        if (income > 0L && expense > 0L) append(" · ")
                        if (expense > 0L) append("支 ${formatMoney(expense)}")
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            entries.forEachIndexed { index, entry ->
                key(entry.id) {
                    val showDivider = index != entries.lastIndex
                    val categoryClick = { onCategoryClick(entry) }
                    val infoClick = { onInfoClick(entry) }
                    val amountClick = { bounds: Rect -> onAmountClick(entry, bounds) }
                    val longClick = { onLongClick(entry) }
                    if (entry.id == recentlyCreatedEntryId) {
                        AnimatedLedgerHotspotRow(
                            entry = entry,
                            showDivider = showDivider,
                            onCategoryClick = categoryClick,
                            onInfoClick = infoClick,
                            onAmountClick = amountClick,
                            onLongClick = longClick,
                            onInsertAnimationFinished = onInsertAnimationFinished,
                        )
                    } else {
                        LedgerHotspotRowBlock(
                            entry = entry,
                            showDivider = showDivider,
                            onCategoryClick = categoryClick,
                            onInfoClick = infoClick,
                            onAmountClick = amountClick,
                            onLongClick = longClick,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AnimatedLedgerHotspotRow(
    entry: UiLedgerEntry,
    showDivider: Boolean,
    onCategoryClick: () -> Unit,
    onInfoClick: () -> Unit,
    onAmountClick: (Rect) -> Unit,
    onLongClick: () -> Unit,
    onInsertAnimationFinished: (Long) -> Unit,
) {
    var reveal by remember(entry.id) { mutableStateOf(false) }
    var completionSent by remember(entry.id) { mutableStateOf(false) }
    val progress = animateFloatAsState(
        targetValue = if (reveal) 1f else 0f,
        animationSpec = tween(
            durationMillis = EntryMotionTokens.InsertMillis,
            easing = FastOutSlowInEasing,
        ),
        finishedListener = { value ->
            if (value == 1f && !completionSent) {
                completionSent = true
                onInsertAnimationFinished(entry.id)
            }
        },
        label = "detail_entry_insert_${entry.id}",
    )
    LaunchedEffect(entry.id) { reveal = true }
    val contentHeight = 64f + if (showDivider) 1f else 0f
    val translation = with(androidx.compose.ui.platform.LocalDensity.current) { 10.dp.toPx() }
    val highlightColor = MaterialTheme.colorScheme.tertiary

    Box(
        modifier = Modifier
            .fillMaxWidth()
            // Reserve the final row height once; only pixels animate. This avoids recomposing and
            // remeasuring the whole day card on every frame of the 220ms insertion feedback.
            .height(contentHeight.dp)
            .clipToBounds(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .graphicsLayer {
                    val value = progress.value
                    alpha = value
                    translationY = -translation * (1f - value)
                    scaleX = 0.98f + 0.02f * value
                    scaleY = 0.98f + 0.02f * value
                }
                .drawBehind {
                    drawRect(highlightColor, alpha = 0.18f * (1f - progress.value))
                },
        ) {
            LedgerHotspotRowBlock(
                entry = entry,
                showDivider = showDivider,
                onCategoryClick = onCategoryClick,
                onInfoClick = onInfoClick,
                onAmountClick = onAmountClick,
                onLongClick = onLongClick,
            )
        }
    }
}

@Composable
private fun LedgerHotspotRowBlock(
    entry: UiLedgerEntry,
    showDivider: Boolean,
    onCategoryClick: () -> Unit,
    onInfoClick: () -> Unit,
    onAmountClick: (Rect) -> Unit,
    onLongClick: () -> Unit,
) {
    LedgerHotspotRow(
        entry = entry,
        onCategoryClick = onCategoryClick,
        onInfoClick = onInfoClick,
        onAmountClick = onAmountClick,
        onLongClick = onLongClick,
    )
    if (showDivider) {
        HorizontalDivider(
            modifier = Modifier.padding(start = 96.dp),
            color = MaterialTheme.colorScheme.outlineVariant,
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun LedgerHotspotRow(
    entry: UiLedgerEntry,
    onCategoryClick: () -> Unit,
    onInfoClick: () -> Unit,
    onAmountClick: (Rect) -> Unit,
    onLongClick: () -> Unit
) {
    val categoryTint = CategoryPalette[entry.categoryColorKey.mod(CategoryPalette.size)]
    val amountTint = if (entry.type == UiEntryType.INCOME) MaterialTheme.colorScheme.primary
    else MaterialTheme.colorScheme.error
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp)
            .testTag("detail_entry_row_${entry.id}"),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier
                .width(96.dp)
                .fillMaxHeight()
                .background(categoryTint.copy(alpha = 0.10f))
                .testTag("detail_category_hotspot_${entry.id}")
                .semantics {
                    role = Role.Button
                    contentDescription = "修改分类：${entry.categoryName}"
                }
                .combinedClickable(onClick = onCategoryClick, onLongClick = onLongClick)
                .padding(horizontal = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(categoryTint.copy(alpha = 0.22f)),
                contentAlignment = Alignment.Center
            ) {
                CategoryIcon(entry.categoryIconKey, contentDescription = null, modifier = Modifier.size(18.dp))
            }
            Text(
                entry.categoryName,
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .testTag("detail_info_hotspot_${entry.id}")
                .semantics {
                    role = Role.Button
                    contentDescription = if (entry.note.isBlank()) "添加备注" else "修改备注：${entry.note}"
                }
                .combinedClickable(onClick = onInfoClick, onLongClick = onLongClick)
                .padding(horizontal = 8.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            Text(
                entry.note.ifBlank { entry.categoryName },
                style = MaterialTheme.typography.bodyMedium,
                color = if (entry.note.isBlank()) MaterialTheme.colorScheme.onSurfaceVariant
                else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        var amountBounds by remember(entry.id) { mutableStateOf(Rect.Zero) }
        Box(
            modifier = Modifier
                .width(112.dp)
                .fillMaxHeight()
                .background(amountTint.copy(alpha = 0.08f))
                .testTag("detail_amount_hotspot_${entry.id}")
                .semantics {
                    role = Role.Button
                    contentDescription = "修改金额与日期：${formatSignedMoney(entry.amountCents, entry.type == UiEntryType.INCOME)}，${entry.date}"
                }
                .onGloballyPositioned { amountBounds = it.boundsInWindow() }
                .combinedClickable(onClick = { onAmountClick(amountBounds) }, onLongClick = onLongClick)
                .padding(horizontal = 6.dp),
            contentAlignment = Alignment.CenterEnd
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    formatSignedMoney(entry.amountCents, entry.type == UiEntryType.INCOME),
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.End,
                    style = MaterialTheme.typography.labelLarge,
                    color = amountTint,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    "${entry.date.monthValue}/${entry.date.dayOfMonth}",
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.End,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun EmptyDetailState() {
    SakuCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(vertical = 36.dp, horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("这个月份还没有账目", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(8.dp))
            Text(
                "点击中央记账按钮，记下第一笔收支",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}
