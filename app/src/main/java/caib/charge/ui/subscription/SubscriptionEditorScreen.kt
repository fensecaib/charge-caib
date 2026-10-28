package caib.charge.ui.subscription

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import caib.charge.ui.components.CaibTextButton
import caib.charge.domain.SubscriptionRules
import caib.charge.domain.model.*
import caib.charge.ui.components.SakuCard
import caib.charge.ui.components.SakuCtaButton
import caib.charge.ui.components.SakuSegmentedControl
import caib.charge.ui.components.SakuTopBar
import java.math.BigDecimal
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit

private enum class PeriodEntryMethod(val label: String) {
    START_DURATION("开始日＋时长"), END_DURATION("到期日＋时长"), END_DATE("到期日"),
}

@Composable
fun SubscriptionEditorScreen(
    mode: SubscriptionEditorMode,
    project: SubscriptionProject?,
    periodId: Long?,
    today: LocalDate,
    busy: Boolean,
    error: String?,
    onBack: () -> Unit,
    onSave: (SubscriptionEditorInput) -> Unit,
    modifier: Modifier = Modifier,
    onClearError: () -> Unit = {},
    onDeletePeriod: (() -> Unit)? = null,
) {
    val existing = remember(project?.id, periodId) { project?.periods?.firstOrNull { it.id == periodId } }
    val index = remember(project?.id, periodId) { project?.periods?.indexOfFirst { it.id == periodId } ?: -1 }
    val previousEnd = if (mode == SubscriptionEditorMode.RENEW) project?.periods?.lastOrNull()?.endDate
        else project?.periods?.getOrNull(index - 1)?.endDate
    val nextStart = if (mode == SubscriptionEditorMode.EDIT_PERIOD) project?.periods?.getOrNull(index + 1)?.startDate else null
    val initialDuration = existing?.duration ?: if (mode == SubscriptionEditorMode.RENEW) project?.periods?.lastOrNull()?.duration else null
    val seedDuration = remember(initialDuration, existing) {
        initialDuration ?: existing?.let {
            SubscriptionDuration(ChronoUnit.DAYS.between(it.startDate, it.endDate).toInt(), SubscriptionDurationUnit.DAY)
        } ?: SubscriptionDuration(1, SubscriptionDurationUnit.MONTH)
    }
    val seedStart = remember(existing, previousEnd, today) {
        existing?.startDate ?: previousEnd ?: today.coerceIn(SubscriptionLimits.MIN_DATE, SubscriptionLimits.MAX_DATE)
    }
    val initialEnd = remember(existing, seedStart, seedDuration) {
        existing?.endDate ?: runCatching { SubscriptionRules.addDuration(seedStart, seedDuration) }.getOrDefault(seedStart)
    }
    val initialStart = remember(mode, initialEnd, seedDuration, seedStart) {
        if (mode == SubscriptionEditorMode.NEW) runCatching { SubscriptionRules.subtractDuration(initialEnd, seedDuration) }.getOrDefault(seedStart) else seedStart
    }
    val initialMethod = when (mode) {
        SubscriptionEditorMode.NEW -> PeriodEntryMethod.END_DURATION
        SubscriptionEditorMode.EDIT_PERIOD -> PeriodEntryMethod.END_DATE
        else -> PeriodEntryMethod.START_DURATION
    }
    var name by rememberSaveable { mutableStateOf(if (mode == SubscriptionEditorMode.NEW) "" else project?.name.orEmpty()) }
    var note by rememberSaveable { mutableStateOf(if (mode == SubscriptionEditorMode.EDIT_PROJECT) project?.note.orEmpty() else existing?.note.orEmpty()) }
    var fee by rememberSaveable { mutableStateOf(subscriptionFeeInput(existing?.amountCents)) }
    var startText by rememberSaveable { mutableStateOf(initialStart.toString()) }
    var endText by rememberSaveable { mutableStateOf(initialEnd.toString()) }
    var durationValue by rememberSaveable { mutableStateOf(seedDuration.value.toString()) }
    var unitName by rememberSaveable { mutableStateOf(seedDuration.unit.name) }
    var methodName by rememberSaveable { mutableStateOf(initialMethod.name) }
    var calculationFailed by rememberSaveable { mutableStateOf(false) }
    var localError by rememberSaveable { mutableStateOf<String?>(null) }
    var discard by rememberSaveable { mutableStateOf(false) }
    var deletePeriodOpen by rememberSaveable { mutableStateOf(false) }
    var dateTarget by rememberSaveable { mutableStateOf<String?>(null) }
    var unitOpen by remember { mutableStateOf(false) }
    val method = PeriodEntryMethod.valueOf(methodName)
    val unit = SubscriptionDurationUnit.valueOf(unitName)
    val start = remember(startText) { runCatching { LocalDate.parse(startText) }.getOrNull() }
    val end = remember(endText) { runCatching { LocalDate.parse(endText) }.getOrNull() }
    val duration = remember(durationValue, unit) {
        durationValue.toIntOrNull()?.takeIf { it > 0 }?.let { SubscriptionDuration(it, unit) }
    }
    // Keep field boundaries intact: editable text may itself contain any separator.
    // ArrayList<String> is directly saveable and preserves the original baseline on recreation.
    val draftFields = remember(name, note, fee, startText, endText, durationValue, unitName) {
        arrayListOf(name, note, fee, startText, endText, durationValue, unitName)
    }
    val initialFields = rememberSaveable { draftFields }
    val dirty = draftFields != initialFields
    val keyboard = LocalSoftwareKeyboardController.current
    fun requestBack() {
        if (!busy) {
            keyboard?.hide()
            if (dirty) discard = true else onBack()
        }
    }
    BackHandler { requestBack() }

    fun clearError() { localError = null; onClearError() }
    fun calculate(value: String = durationValue, selectedUnit: SubscriptionDurationUnit = unit, anchor: LocalDate? = if (method == PeriodEntryMethod.END_DURATION) end else start) {
        clearError()
        calculationFailed = true
        val count = value.toIntOrNull()?.takeIf { it > 0 } ?: return
        if (anchor == null) return
        runCatching {
            SubscriptionRules.dateRange(
                if (method == PeriodEntryMethod.END_DURATION) SubscriptionCreationMode.END_DURATION else SubscriptionCreationMode.START_DURATION,
                anchor, SubscriptionDuration(count, selectedUnit),
            )
        }.onSuccess { range -> startText = range.startDate.toString(); endText = range.endDate.toString(); calculationFailed = false }
            .onFailure { localError = it.message ?: "无法计算周期" }
    }
    fun save() {
        if (busy) return
        val result = runCatching {
            if (mode == SubscriptionEditorMode.EDIT_PROJECT) {
                SubscriptionRules.validateProjectText(name.trim(), note)
                SubscriptionEditorInput(name.trim(), note, null)
            } else {
                if (mode == SubscriptionEditorMode.NEW) SubscriptionRules.validateProjectText(name.trim(), "")
                requireNotNull(start) { "请选择开始日" }
                requireNotNull(end) { "请选择到期日" }
                if (method != PeriodEntryMethod.END_DATE && duration == null) throw IllegalArgumentException("持续时长须为正整数")
                require(method == PeriodEntryMethod.END_DATE || !calculationFailed) { "请重新填写有效的持续时长" }
                val amount = if (fee.isBlank()) null else {
                    require(Regex("[0-9]+(?:\\.[0-9]{1,2})?").matches(fee.trim())) { "费用须为非负金额，最多两位小数" }
                    val decimal = BigDecimal(fee.trim())
                    require(decimal <= BigDecimal.valueOf(MoneyLimits.MAX_CENTS, 2)) { "费用不能超过 999,999,999.99 元" }
                    decimal.movePointRight(2).longValueExact()
                }
                val unchangedDates = existing?.let { it.startDate == start && it.endDate == end } == true
                val savedDuration = if (method == PeriodEntryMethod.END_DATE) {
                    if (unchangedDates) existing.duration else null
                }
                    else duration?.takeIf { SubscriptionRules.matchesDuration(start, end, it) }
                val draft = SubscriptionPeriodDraft(start, end, savedDuration, amount, note)
                SubscriptionRules.validatePeriod(draft)
                if (previousEnd != null) {
                    if (mode == SubscriptionEditorMode.RENEW) require(start == previousEnd) { "开始日须衔接上一期到期日" }
                    else require(start >= previousEnd) { "本期与上一期重叠" }
                }
                if (nextStart != null) require(end <= nextStart) { "本期与下一期重叠" }
                SubscriptionEditorInput(name.trim(), "", draft)
            }
        }
        result.onSuccess { keyboard?.hide(); onSave(it) }.onFailure { localError = it.message ?: "请检查填写内容" }
    }

    Column(modifier.fillMaxSize().imePadding().testTag("subscription-editor-screen")) {
        SakuTopBar(
            title = when (mode) { SubscriptionEditorMode.NEW -> "新建订阅"; SubscriptionEditorMode.RENEW -> "新增第 ${(project?.periods?.size ?: 0) + 1} 期"; SubscriptionEditorMode.EDIT_PERIOD -> "编辑第 ${index + 1} 期"; SubscriptionEditorMode.EDIT_PROJECT -> "编辑订阅" },
            onBack = { requestBack() },
            actions = {
                if (onDeletePeriod != null) IconButton(
                    onClick = { keyboard?.hide(); deletePeriodOpen = true }, enabled = !busy,
                    modifier = Modifier.size(48.dp).testTag("subscription-delete-period"),
                ) { Icon(Icons.Rounded.DeleteOutline, "删除本期", tint = subscriptionErrorContentColor()) }
            },
        )
        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp, 10.dp).testTag("subscription-editor-fields"),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Uncolored theme styles let each control's state colors win. An inherited
            // TextStyle.color would otherwise override both input and decoration colors.
            if (mode == SubscriptionEditorMode.NEW || mode == SubscriptionEditorMode.EDIT_PROJECT) {
                OutlinedTextField(
                    name, { name = it; clearError() }, modifier = Modifier.fillMaxWidth().testTag("subscription-name").semantics { contentDescription = "订阅名称" },
                    placeholder = { Text("订阅名称", style = MaterialTheme.typography.bodyLarge) }, singleLine = true, enabled = !busy,
                    textStyle = MaterialTheme.typography.bodyLarge, shape = RoundedCornerShape(10.dp), colors = subscriptionInputColors(),
                )
            } else Text(project?.name.orEmpty(), style = MaterialTheme.typography.titleMedium)
            if (mode != SubscriptionEditorMode.EDIT_PROJECT) {
                run {
                    val methods = when {
                        mode == SubscriptionEditorMode.NEW -> listOf(PeriodEntryMethod.START_DURATION, PeriodEntryMethod.END_DURATION)
                        mode == SubscriptionEditorMode.EDIT_PERIOD -> PeriodEntryMethod.entries
                        else -> listOf(PeriodEntryMethod.START_DURATION, PeriodEntryMethod.END_DATE)
                    }
                    SakuSegmentedControl(
                        items = methods, selectedItem = method, enabled = !busy,
                        onItemSelected = { selected ->
                            if (method == PeriodEntryMethod.END_DATE && selected != PeriodEntryMethod.END_DATE && start != null && end != null && end > start) {
                                val consistent = duration?.let { value ->
                                    SubscriptionRules.matchesDuration(start, end, value)
                                } == true
                                if (!consistent) {
                                    durationValue = ChronoUnit.DAYS.between(start, end).toString()
                                    unitName = SubscriptionDurationUnit.DAY.name
                                }
                                calculationFailed = false
                            }
                            methodName = selected.name
                            clearError()
                        },
                        itemLabel = {
                            when {
                                methods.size == 3 && it == PeriodEntryMethod.START_DURATION -> "开始＋时长"
                                methods.size == 3 && it == PeriodEntryMethod.END_DURATION -> "到期＋时长"
                                mode == SubscriptionEditorMode.RENEW && it == PeriodEntryMethod.START_DURATION -> "持续时长"
                                mode == SubscriptionEditorMode.EDIT_PERIOD && it == PeriodEntryMethod.END_DATE -> "起止日期"
                                else -> it.label
                            }
                        },
                        itemContentDescription = { "周期录入方式：${it.label}" },
                        modifier = Modifier.fillMaxWidth().testTag("subscription-create-method"),
                    )
                }
                if (method == PeriodEntryMethod.END_DATE) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SubscriptionDateField("开始日", start, enabled = !busy && mode != SubscriptionEditorMode.RENEW, onClick = { keyboard?.hide(); dateTarget = "start" }, Modifier.weight(1f).testTag("subscription-start-date"))
                        SubscriptionDateField("到期日", end, enabled = !busy, onClick = { keyboard?.hide(); dateTarget = "end" }, Modifier.weight(1f).testTag("subscription-end-date"))
                    }
                } else {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        val endAnchor = method == PeriodEntryMethod.END_DURATION
                        SubscriptionDateField(if (endAnchor) "到期日" else "开始日", if (endAnchor) end else start,
                            enabled = !busy && (endAnchor || mode != SubscriptionEditorMode.RENEW), onClick = { keyboard?.hide(); dateTarget = if (endAnchor) "end" else "start" },
                            modifier = Modifier.weight(1.65f).testTag(if (endAnchor) "subscription-end-date" else "subscription-start-date"))
                        OutlinedTextField(
                            durationValue, { if (it.length <= 10) { durationValue = it; calculate(value = it) } },
                            modifier = Modifier.weight(.75f).testTag("subscription-duration-value").semantics { contentDescription = "持续时长数值" },
                            singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), enabled = !busy,
                            textStyle = MaterialTheme.typography.bodyLarge.copy(fontSize = 13.sp), shape = RoundedCornerShape(10.dp), colors = subscriptionInputColors(),
                        )
                        Box(Modifier.weight(.95f)) {
                            OutlinedButton(onClick = { unitOpen = true }, enabled = !busy, colors = subscriptionOutlinedButtonColors(), border = subscriptionFieldBorder(!busy), contentPadding = PaddingValues(horizontal = 6.dp), shape = RoundedCornerShape(10.dp), modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).testTag("subscription-duration-unit")) {
                                Text(unit.displayName, style = MaterialTheme.typography.labelLarge, fontSize = 12.sp, modifier = Modifier.testTag("subscription-duration-unit-text"))
                                Icon(Icons.Rounded.ExpandMore, "时长单位", Modifier.size(16.dp))
                            }
                            DropdownMenu(unitOpen, { unitOpen = false }) {
                                SubscriptionDurationUnit.entries.forEach { selected ->
                                    DropdownMenuItem(text = { Text(selected.displayName, style = MaterialTheme.typography.bodyLarge) }, onClick = { unitName = selected.name; unitOpen = false; calculate(selectedUnit = selected) })
                                }
                            }
                        }
                    }
                }
                SakuCard(Modifier.fillMaxWidth(), containerColor = MaterialTheme.colorScheme.primaryContainer) {
                    Column(Modifier.fillMaxWidth().padding(6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            CaibTextButton(onClick = { keyboard?.hide(); dateTarget = "correct-start" }, enabled = !busy && mode != SubscriptionEditorMode.RENEW, colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onPrimaryContainer), modifier = Modifier.weight(1f).heightIn(min = 48.dp)) { Text(start?.let(::subscriptionDate) ?: "开始日", Modifier.testTag("subscription-preview-start-text"), fontSize = 12.sp) }
                            Text("→", fontSize = 12.sp)
                            CaibTextButton(onClick = { keyboard?.hide(); dateTarget = "correct-end" }, enabled = !busy, colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onPrimaryContainer), modifier = Modifier.weight(1f).heightIn(min = 48.dp)) { Text(end?.let(::subscriptionDate) ?: "到期日", Modifier.testTag("subscription-preview-end-text"), fontSize = 12.sp) }
                        }
                        Text(if (start != null && end != null && end > start) "${ChronoUnit.DAYS.between(start, end)} 天 · 到期日当天失效" else "到期日须晚于开始日", fontSize = 10.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 4.dp).testTag("subscription-period-preview"))
                    }
                }
                if (mode == SubscriptionEditorMode.EDIT_PERIOD && (previousEnd != null || nextStart != null)) {
                    Text("仅修改本期；日期不能与相邻周期重叠", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                OutlinedTextField(
                    fee, { fee = it; clearError() }, modifier = Modifier.fillMaxWidth().testTag("subscription-fee").semantics { contentDescription = "本期费用，选填；留空为未记录，0 为免费" },
                    placeholder = { Text("费用（选填）", style = MaterialTheme.typography.bodyLarge) }, prefix = { Text("¥", style = MaterialTheme.typography.bodyLarge) }, singleLine = true, enabled = !busy,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), textStyle = MaterialTheme.typography.bodyLarge, shape = RoundedCornerShape(10.dp), colors = subscriptionInputColors(),
                )
            }
            OutlinedTextField(
                note, { note = it; clearError() }, modifier = Modifier.fillMaxWidth().testTag("subscription-note").semantics { contentDescription = if (mode == SubscriptionEditorMode.EDIT_PROJECT) "订阅备注，选填" else "本期备注，选填" },
                placeholder = { Text("备注（选填）", style = MaterialTheme.typography.bodyLarge) }, minLines = 2, maxLines = 4, enabled = !busy, textStyle = MaterialTheme.typography.bodyLarge, shape = RoundedCornerShape(10.dp), colors = subscriptionInputColors(),
            )
            (localError ?: error)?.let { message ->
                Text(message, Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite }.testTag("subscription-form-error"), color = subscriptionErrorContentColor(), fontSize = 12.sp)
            }
        }
        SakuCtaButton(onClick = { save() }, enabled = !busy, modifier = Modifier.fillMaxWidth().padding(16.dp, 8.dp).heightIn(min = 48.dp).testTag("subscription-save")) {
            Text(if (busy) "正在保存…" else if (mode == SubscriptionEditorMode.EDIT_PROJECT || mode == SubscriptionEditorMode.EDIT_PERIOD) "保存修改" else "保存记录")
        }
    }
    if (deletePeriodOpen && onDeletePeriod != null && existing != null) AlertDialog(
        onDismissRequest = { if (!busy) deletePeriodOpen = false },
        title = { Text(if (project?.periods?.size == 1) "删除最后一期及订阅？" else "删除第 ${index + 1} 期？") },
        text = {
            Text(if (project?.periods?.size == 1) "这是唯一一期，将同时删除“${project.name}”。"
                else "将删除 ${subscriptionDate(existing.startDate)} — ${subscriptionDate(existing.endDate)} 的记录。其他周期的日期和费用保持不变。")
        },
        confirmButton = { CaibTextButton(onClick = { if (!busy) { deletePeriodOpen = false; onDeletePeriod() } }, enabled = !busy) { Text("删除", color = subscriptionErrorContentColor()) } },
        dismissButton = { CaibTextButton(onClick = { deletePeriodOpen = false }, enabled = !busy) { Text("取消") } },
    )
    if (discard) AlertDialog(
        onDismissRequest = { discard = false }, title = { Text("放弃修改？") }, text = { Text("本次填写的内容尚未保存。") },
        confirmButton = { CaibTextButton(onClick = { discard = false; keyboard?.hide(); onBack() }) { Text("放弃修改") } },
        dismissButton = { CaibTextButton(onClick = { discard = false }) { Text("继续编辑") } },
    )
    dateTarget?.let { target ->
        SubscriptionDateDialog(
            initialDate = (if (target.endsWith("start")) start else end) ?: today,
            title = if (target.endsWith("start")) "选择开始日" else "选择到期日",
            onDismiss = { dateTarget = null },
            onConfirm = { selected ->
                clearError()
                if (target.endsWith("start")) startText = selected.toString() else endText = selected.toString()
                calculationFailed = false
                if (!target.startsWith("correct") && method != PeriodEntryMethod.END_DATE) calculate(anchor = selected)
                if (target.startsWith("correct") && method != PeriodEntryMethod.END_DATE) {
                    val correctedStart = if (target.endsWith("start")) selected else start
                    val correctedEnd = if (target.endsWith("end")) selected else end
                    if (correctedStart != null && correctedEnd != null && correctedEnd > correctedStart) {
                        val consistent = duration?.let { value ->
                            SubscriptionRules.matchesDuration(correctedStart, correctedEnd, value)
                        } == true
                        if (!consistent) {
                            durationValue = ChronoUnit.DAYS.between(correctedStart, correctedEnd).toString()
                            unitName = SubscriptionDurationUnit.DAY.name
                        }
                    }
                }
                dateTarget = null
            },
        )
    }
}

@Composable
private fun SubscriptionDateField(label: String, date: LocalDate?, enabled: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    OutlinedButton(onClick = onClick, enabled = enabled, colors = subscriptionOutlinedButtonColors(), border = subscriptionFieldBorder(enabled), modifier = modifier.heightIn(min = 56.dp).semantics { contentDescription = "$label：${date?.toString().orEmpty()}${if (!enabled) "，与相邻周期衔接" else ""}" }, shape = RoundedCornerShape(10.dp), contentPadding = PaddingValues(6.dp)) {
        Column(Modifier.weight(1f), horizontalAlignment = Alignment.Start) {
            Text(label, fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(date?.let(::subscriptionDate) ?: "选择日期", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface, textAlign = TextAlign.Start)
        }
        if (enabled) Icon(Icons.Rounded.CalendarMonth, null, Modifier.size(16.dp))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SubscriptionDateDialog(initialDate: LocalDate, title: String, onDismiss: () -> Unit, onConfirm: (LocalDate) -> Unit) {
    val state = rememberDatePickerState(
        initialSelectedDateMillis = initialDate.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
        yearRange = SubscriptionLimits.MIN_DATE.year..SubscriptionLimits.MAX_DATE.year,
    )
    DatePickerDialog(onDismissRequest = onDismiss,
        confirmButton = { CaibTextButton(onClick = { state.selectedDateMillis?.let { onConfirm(java.time.Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()) } }, enabled = state.selectedDateMillis != null) { Text("确定") } },
        dismissButton = { CaibTextButton(onClick = onDismiss) { Text("取消") } },
    ) { DatePicker(state = state, colors = subscriptionDatePickerColors(), title = { Text(title, Modifier.padding(24.dp, 16.dp), fontSize = 14.sp) }) }
}
