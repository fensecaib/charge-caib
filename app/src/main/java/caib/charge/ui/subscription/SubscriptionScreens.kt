package caib.charge.ui.subscription

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.FilterList
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import caib.charge.ui.components.CaibTextButton
import caib.charge.domain.SubscriptionRules
import caib.charge.domain.model.SubscriptionInspection
import caib.charge.domain.model.SubscriptionPeriod
import caib.charge.domain.model.SubscriptionProject
import caib.charge.domain.model.SubscriptionStatus
import caib.charge.ui.components.SakuCard
import caib.charge.ui.components.SakuCtaButton
import caib.charge.ui.components.SakuTopBar
import caib.charge.ui.theme.caibIsDark
import java.math.BigDecimal
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

enum class SubscriptionFilter(val label: String, val status: SubscriptionStatus?) {
    ALL("全部订阅", null), ACTIVE("进行中", SubscriptionStatus.ACTIVE),
    UPCOMING("尚未开始", SubscriptionStatus.UPCOMING), EXPIRED("已到期", SubscriptionStatus.EXPIRED),
    STOPPED("不再续费", SubscriptionStatus.STOPPED),
}

internal fun subscriptionMoney(cents: Long?): String = cents?.let {
    "¥" + BigDecimal.valueOf(it, 2).stripTrailingZeros().toPlainString()
} ?: "费用未记录"

internal fun subscriptionFeeInput(cents: Long?): String = cents?.let {
    BigDecimal.valueOf(it, 2).stripTrailingZeros().toPlainString()
}.orEmpty()

private val subscriptionDateFormatter = DateTimeFormatter.ofPattern("uuuu.MM.dd")
internal fun subscriptionDate(date: LocalDate): String = date.format(subscriptionDateFormatter)

internal fun subscriptionDuration(period: SubscriptionPeriod): String {
    val days = ChronoUnit.DAYS.between(period.startDate, period.endDate)
    return period.duration?.takeIf { it.unit != caib.charge.domain.model.SubscriptionDurationUnit.DAY }
        ?.let { "${it.value}${it.unit.displayName} · $days 天" } ?: "$days 天"
}

@Composable
internal fun caibSubscriptionNoteColor(): Color =
    if (MaterialTheme.colorScheme.caibIsDark) Color(0xFF8D97A3) else Color(0xFF66717D)

@Composable
fun SubscriptionListScreen(
    projects: List<SubscriptionProject>,
    today: LocalDate,
    onFilterChange: (SubscriptionFilter) -> Unit,
    onOpen: (Long) -> Unit,
    onNew: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    filter: SubscriptionFilter = SubscriptionFilter.ALL,
    inspections: Map<Long, SubscriptionInspection>? = null,
    active: Boolean = true,
) {
    var filterOpen by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(active) { if (!active) filterOpen = false }
    val inspected = remember(projects, today, inspections) {
        inspections ?: projects.associate { it.id to SubscriptionRules.inspect(it, today) }
    }
    val visible = remember(projects, inspected, filter) { projects.filter { filter.status == null || inspected[it.id]?.status == filter.status } }
    val listState = rememberSubscriptionListState(active)
    var lastFilterName by rememberSaveable { mutableStateOf(filter.name) }
    LaunchedEffect(filter) {
        if (lastFilterName != filter.name) {
            listState.scrollToItem(0)
            lastFilterName = filter.name
        }
    }
    Column(modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).testTag("subscription-list-screen")) {
        SakuTopBar("订阅管理", onBack = onBack, actions = {
            Box {
                FilledTonalIconButton(onClick = { if (active) filterOpen = true }, modifier = Modifier.size(48.dp).testTag("subscription-filter"), shape = CircleShape) {
                    Icon(Icons.Rounded.FilterList, "筛选订阅，当前${filter.label}")
                }
                if (active) DropdownMenu(expanded = filterOpen, onDismissRequest = { filterOpen = false }) {
                    SubscriptionFilter.entries.forEach { item ->
                        val count = if (item.status == null) projects.size else inspected.values.count { it.status == item.status }
                        DropdownMenuItem(
                            text = { Text("${item.label} · $count") },
                            onClick = { onFilterChange(item); filterOpen = false },
                            trailingIcon = { if (item == filter) Icon(Icons.Rounded.Check, "已选中") },
                            modifier = Modifier.heightIn(min = 48.dp).testTag("subscription-filter-${item.name}"),
                        )
                    }
                }
            }
            Spacer(Modifier.width(4.dp))
            FilledTonalIconButton(
                onClick = onNew,
                modifier = Modifier.size(48.dp).testTag("subscription-new"), shape = CircleShape,
                colors = IconButtonDefaults.filledTonalIconButtonColors(containerColor = MaterialTheme.colorScheme.tertiary, contentColor = MaterialTheme.colorScheme.onTertiary),
            ) { Icon(Icons.Rounded.Add, "新建订阅") }
            Spacer(Modifier.width(12.dp))
        })
        if (visible.isEmpty()) {
            Column(Modifier.weight(1f).fillMaxWidth().padding(24.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                Text(if (projects.isEmpty()) "从一个订阅开始" else "这里暂时没有订阅", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                Text(if (projects.isEmpty()) "记下每一段周期和续期费用" else "可切换筛选查看其他订阅", color = MaterialTheme.colorScheme.onSurfaceVariant)
                CaibTextButton(onClick = if (projects.isEmpty()) onNew else ({ onFilterChange(SubscriptionFilter.ALL) }), modifier = Modifier.heightIn(min = 48.dp)) {
                    Text(if (projects.isEmpty()) "添加第一个订阅" else "查看全部")
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f).testTag("subscription-project-list"), state = listState,
                contentPadding = PaddingValues(12.dp, 8.dp, 12.dp, 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(visible, key = { it.id }, contentType = { "subscription" }) { project ->
                    SubscriptionListCard(project, requireNotNull(inspected[project.id]), onClick = { onOpen(project.id) }, Modifier.animateItem())
                }
            }
        }
    }
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
internal fun SubscriptionListCard(project: SubscriptionProject, info: SubscriptionInspection, onClick: (() -> Unit)?, modifier: Modifier = Modifier) {
    val period = info.currentPeriod ?: return
    val quiet = info.status == SubscriptionStatus.EXPIRED || info.status == SubscriptionStatus.STOPPED
    val content = if (quiet) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
    SakuCard(
        modifier.fillMaxWidth().testTag("subscription-item-${project.id}"), onClick = onClick,
        containerColor = if (quiet) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surface,
    ) {
        Column(Modifier.padding(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SubscriptionAppearanceMark(project.name, project.appearance, quiet)
                Column(Modifier.weight(1f)) {
                    Text(project.name, color = content, fontSize = 14.sp, lineHeight = 19.5.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("第 ${info.currentIndex} 期 · ${subscriptionDuration(period)}", fontSize = 10.5.sp, lineHeight = 14.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(remainingHeadline(info), fontSize = 16.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold, color = content)
                    Text(remainingCaption(info), fontSize = 10.sp, lineHeight = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Spacer(Modifier.height(7.dp))
            SubscriptionProgress(info, project, Modifier.fillMaxWidth().height(4.dp))
            Spacer(Modifier.height(5.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("${subscriptionDate(period.startDate)} — ${subscriptionDate(period.endDate)}", Modifier.weight(1f), fontSize = 10.sp, lineHeight = 15.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                FlowRow(
                    Modifier.widthIn(max = with(LocalDensity.current) { 130.sp.toDp() }),
                    horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.End),
                    verticalArrangement = Arrangement.spacedBy(0.dp),
                ) {
                    Text(subscriptionMoney(period.amountCents), fontSize = 10.5.sp, lineHeight = 14.sp, color = content)
                    if (info.queuedCount > 0 && info.temporalStatus != SubscriptionStatus.UPCOMING) Text("已续下一期", fontSize = 9.sp, lineHeight = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

private fun remainingHeadline(info: SubscriptionInspection): String = when (info.temporalStatus) {
    SubscriptionStatus.ACTIVE, SubscriptionStatus.UPCOMING -> "${info.remainingDays} 天"
    else -> if (info.status == SubscriptionStatus.STOPPED) "已停续" else "已到期"
}

private fun remainingCaption(info: SubscriptionInspection): String = when {
    info.status == SubscriptionStatus.STOPPED && info.temporalStatus == SubscriptionStatus.ACTIVE -> "有效 · 不再续费"
    info.status == SubscriptionStatus.STOPPED -> "不再续费"
    info.temporalStatus == SubscriptionStatus.UPCOMING -> "后开始"
    info.temporalStatus == SubscriptionStatus.ACTIVE -> "剩余"
    else -> "历史记录保留"
}

@Composable
private fun SubscriptionProgress(info: SubscriptionInspection, project: SubscriptionProject, modifier: Modifier = Modifier) {
    val projectId = project.id
    val quiet = info.status == SubscriptionStatus.STOPPED || info.status == SubscriptionStatus.EXPIRED
    val colors = rememberSubscriptionAppearanceColors(project.appearance, quiet)
    val periodId = info.currentPeriod?.id
    var appeared by rememberSaveable(projectId, periodId) { mutableStateOf(false) }
    val value = remember(projectId, periodId) { Animatable(if (appeared) info.progress else 0f) }
    LaunchedEffect(info.progress, periodId) {
        appeared = true
        value.animateTo(info.progress, tween(360))
    }
    LinearProgressIndicator(
        progress = { value.value }, modifier = modifier.semantics { contentDescription = "当前周期已过 ${(info.progress * 100).toInt()}%" },
        color = colors.progress,
        trackColor = colors.track,
        gapSize = 0.dp,
        drawStopIndicator = {},
    )
}

@Composable
fun SubscriptionDetailScreen(
    project: SubscriptionProject,
    today: LocalDate,
    onBack: () -> Unit,
    onRenew: () -> Unit,
    onEditPeriod: (Long) -> Unit,
    onEditProject: () -> Unit,
    onToggleStopped: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
    busy: Boolean = false,
    inspection: SubscriptionInspection? = null,
    active: Boolean = true,
    onAppearance: () -> Unit = {},
) {
    val info = remember(project, today, inspection) { inspection ?: SubscriptionRules.inspect(project, today) }
    val historyState = rememberSubscriptionListState(active)
    var menuOpen by rememberSaveable { mutableStateOf(false) }
    var deleteOpen by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(active) { if (!active) { menuOpen = false; deleteOpen = false } }
    Column(modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).testTag("subscription-detail-screen")) {
        SakuTopBar("订阅详情", onBack = onBack, actions = {
            Box {
                IconButton(onClick = { if (active) menuOpen = true }, enabled = !busy, modifier = Modifier.size(48.dp)) { Icon(Icons.Rounded.MoreVert, "管理订阅") }
                if (active) DropdownMenu(menuOpen, { menuOpen = false }) {
                    DropdownMenuItem(text = { Text("编辑订阅") }, onClick = { menuOpen = false; onEditProject() }, enabled = !busy)
                    DropdownMenuItem(text = { Text("图标与周期色") }, onClick = { menuOpen = false; onAppearance() }, enabled = !busy)
                    DropdownMenuItem(text = { Text(if (project.isStopped) "恢复续费" else "不再续费") }, onClick = { menuOpen = false; onToggleStopped() }, enabled = !busy)
                    DropdownMenuItem(text = { Text("删除订阅", color = subscriptionErrorContentColor()) }, onClick = { menuOpen = false; deleteOpen = true }, enabled = !busy)
                }
            }
        })
        LazyColumn(
            modifier = Modifier.weight(1f).testTag("subscription-history-list"),
            state = historyState,
            contentPadding = PaddingValues(12.dp, 6.dp, 12.dp, 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item(key = "summary", contentType = "summary") { SubscriptionCurrentSummary(project, info) }
            item(key = "span", contentType = "span") {
                SakuCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text("完整订阅历程", fontSize = 11.sp, lineHeight = 15.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("${info.firstStart?.let(::subscriptionDate).orEmpty()} → ${info.lastEnd?.let(::subscriptionDate).orEmpty()}", fontSize = 12.sp, lineHeight = 17.5.sp)
                        Text("${info.totalDays} 天总周期 · ${project.periods.size} 期记录", fontSize = 11.sp, lineHeight = 15.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            item(key = "history-title", contentType = "heading") {
                Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("周期记录", Modifier.semantics { heading() }, fontSize = 14.sp, lineHeight = 19.sp, fontWeight = FontWeight.Medium)
                    Text("最近一期在前", fontSize = 10.sp, lineHeight = 15.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            itemsIndexed(project.periods.asReversed(), key = { _, period -> period.id }, contentType = { _, _ -> "period" }) { reverseIndex, period ->
                SubscriptionPeriodCard(period, project.periods.size - reverseIndex, info.currentPeriod?.id == period.id && info.temporalStatus == SubscriptionStatus.ACTIVE, period.startDate > today, { onEditPeriod(period.id) }, busy)
            }
            item(key = "total", contentType = "total") {
                Column(Modifier.fillMaxWidth().padding(4.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("已记录费用", fontSize = 12.sp, lineHeight = 17.sp)
                        Text(info.recordedTotalCents?.let(::subscriptionMoney) ?: "—", fontSize = 16.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold)
                    }
                    Text("${info.recordedFeeCount} 期已填写 · ${project.periods.size - info.recordedFeeCount} 期费用未记录", fontSize = 10.5.sp, lineHeight = 15.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        SakuCtaButton(onClick = onRenew, enabled = !busy, modifier = Modifier.fillMaxWidth().padding(12.dp, 6.dp).heightIn(min = 48.dp).testTag("subscription-renew")) { Text("记录续期") }
    }
    if (active && deleteOpen) AlertDialog(
        onDismissRequest = { if (!busy) deleteOpen = false }, title = { Text("删除订阅？") },
        text = { Text("将删除“${project.name}”及全部 ${project.periods.size} 期记录。") },
        confirmButton = { CaibTextButton(onClick = { deleteOpen = false; onDelete() }, enabled = !busy) { Text("删除", color = subscriptionErrorContentColor()) } },
        dismissButton = { CaibTextButton(onClick = { deleteOpen = false }, enabled = !busy) { Text("取消") } },
    )
}

@Composable
private fun SubscriptionCurrentSummary(project: SubscriptionProject, info: SubscriptionInspection) {
    val period = info.currentPeriod ?: return
    SakuCard(Modifier.fillMaxWidth(), containerColor = MaterialTheme.colorScheme.primaryContainer) {
        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                SubscriptionAppearanceMark(project.name, project.appearance, info.status == SubscriptionStatus.STOPPED || info.status == SubscriptionStatus.EXPIRED)
                Column(Modifier.weight(1f)) {
                    Text(project.name, fontSize = 16.sp, lineHeight = 21.5.sp, fontWeight = FontWeight.SemiBold)
                    Text(info.status.displayName, fontSize = 10.5.sp, lineHeight = 14.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(remainingHeadline(info), fontSize = 20.sp, lineHeight = 26.sp, fontWeight = FontWeight.SemiBold)
                    Text(remainingCaption(info), fontSize = 10.sp, lineHeight = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Text("第 ${info.currentIndex} 期 · ${subscriptionDuration(period)}", fontSize = 11.sp, lineHeight = 16.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            SubscriptionProgress(info, project, Modifier.fillMaxWidth().height(4.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(subscriptionDate(period.startDate), fontSize = 10.sp, lineHeight = 15.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("${subscriptionDate(period.endDate)} 到期", fontSize = 10.sp, lineHeight = 15.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (info.queuedCount > 0 && info.temporalStatus != SubscriptionStatus.UPCOMING) Text("已续后续 ${info.queuedCount} 期 · 到期自动切换", fontSize = 10.sp, lineHeight = 15.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (project.note.isNotBlank()) Text(project.note, fontSize = 11.sp, lineHeight = 17.sp, color = caibSubscriptionNoteColor())
        }
    }
}

/** Both metrics own the same column; optional note is a separate full-width footer. */
@Composable
@OptIn(ExperimentalLayoutApi::class)
internal fun SubscriptionPeriodCard(period: SubscriptionPeriod, index: Int, current: Boolean, upcoming: Boolean, onEdit: () -> Unit, busy: Boolean = false) {
    val metricWidth = with(LocalDensity.current) { 100.sp.toDp() }
    SakuCard(Modifier.fillMaxWidth().testTag("subscription-record-${period.id}")) {
        Column(Modifier.padding(9.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f).heightIn(min = 48.dp), verticalArrangement = Arrangement.Center) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalArrangement = Arrangement.spacedBy(0.dp)) {
                        Text("第 $index 期", fontSize = 12.sp, lineHeight = 18.sp, fontWeight = FontWeight.SemiBold)
                        if (current || upcoming) Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = RoundedCornerShape(4.dp)) {
                            Text(if (current) "当前周期" else "待开始", Modifier.padding(horizontal = 4.dp, vertical = 1.dp), fontSize = 9.sp, lineHeight = 13.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Text(subscriptionDate(period.startDate), fontSize = 10.5.sp, lineHeight = 15.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("— ${subscriptionDate(period.endDate)}", fontSize = 10.5.sp, lineHeight = 15.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Column(Modifier.width(metricWidth), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    val fee = subscriptionMoney(period.amountCents)
                    Text(
                        fee, Modifier.fillMaxWidth().testTag("subscription-period-price-${period.id}"),
                        textAlign = TextAlign.Center, fontSize = if (period.amountCents == null) 11.sp else if (fee.length > 12) 12.sp else 14.sp,
                        lineHeight = 19.sp,
                        fontWeight = if (period.amountCents == null) FontWeight.Normal else FontWeight.SemiBold,
                        color = if (period.amountCents == null) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                    )
                    Text(subscriptionDuration(period), Modifier.fillMaxWidth().testTag("subscription-period-duration-${period.id}"), textAlign = TextAlign.Center, fontSize = 11.sp, lineHeight = 16.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = onEdit, enabled = !busy, modifier = Modifier.size(48.dp).testTag("subscription-period-edit-${period.id}")) { Icon(Icons.Rounded.Edit, "编辑第 $index 期", Modifier.size(18.dp)) }
            }
            if (period.note.isNotBlank()) {
                HorizontalDivider(Modifier.padding(top = 6.dp, bottom = 6.dp), color = MaterialTheme.colorScheme.outlineVariant)
                Text(period.note, Modifier.fillMaxWidth().testTag("subscription-period-note-${period.id}"), fontSize = 11.sp, lineHeight = 17.sp, color = caibSubscriptionNoteColor())
            }
        }
    }
}
