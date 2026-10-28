package caib.charge.ui.entry

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import caib.charge.ui.components.CaibTextButton
import caib.charge.ui.components.caibOutlinedTextFieldColors
import caib.charge.ui.theme.overlayScrimColor
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import caib.charge.domain.AmountExpressionEngine
import caib.charge.ui.components.SakuCard
import caib.charge.ui.components.SakuCtaButton
import caib.charge.ui.model.UiLedgerEntry
import caib.charge.ui.util.formatFullDate
import caib.charge.ui.util.formatMoney
import caib.charge.ui.util.codePointLength
import caib.charge.ui.util.takeCodePoints
import java.time.LocalDate
import kotlin.math.roundToInt

private val QuickAmountAutoSize = TextAutoSize.StepBased(
    minFontSize = 12.sp,
    maxFontSize = 24.sp,
    stepSize = 1.sp,
)

@Composable
fun QuickAmountEditorOverlay(
    entry: UiLedgerEntry,
    sourceBoundsInWindow: Rect,
    today: LocalDate,
    isSaving: Boolean,
    onDismiss: () -> Unit,
    onSave: (amountCents: Long, date: LocalDate) -> Unit,
) {
    val initialExpression = remember(entry.id) { centsToExpression(entry.amountCents) }
    var expression by rememberSaveable(entry.id) { mutableStateOf(initialExpression) }
    var dateEpochDay by rememberSaveable(entry.id) { mutableLongStateOf(entry.date.toEpochDay()) }
    val date = LocalDate.ofEpochDay(dateEpochDay)
    var showDatePicker by remember { mutableStateOf(false) }
    var showDiscardConfirm by remember { mutableStateOf(false) }
    val evaluation = remember(expression) { AmountExpressionEngine.evaluate(expression) }
    val dirty = expression != initialExpression || date != entry.date
    val density = LocalDensity.current
    val currentEvaluation = rememberUpdatedState(evaluation)
    val currentDate = rememberUpdatedState(date)
    val currentDirty = rememberUpdatedState(dirty)
    val currentIsSaving = rememberUpdatedState(isSaving)
    val currentOnDismiss = rememberUpdatedState(onDismiss)
    val currentOnSave = rememberUpdatedState(onSave)
    val requestDismiss: () -> Unit = remember(entry.id) {
        {
            when {
                currentIsSaving.value -> Unit
                currentDirty.value -> showDiscardConfirm = true
                else -> currentOnDismiss.value()
            }
        }
    }
    val save: () -> Unit = remember(entry.id) {
        save@{
            val cents = currentEvaluation.value.resultCents ?: return@save
            if (currentIsSaving.value) return@save
            currentOnSave.value(cents, currentDate.value)
        }
    }

    BackHandler(onBack = requestDismiss)
    Box(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize().background(overlayScrimColor()).clickable(onClick = requestDismiss))

        val width = with(density) { sourceBoundsInWindow.width.toDp() }
        val height = with(density) { sourceBoundsInWindow.height.toDp() }
        SakuCard(
            modifier = Modifier
                .offset { IntOffset(sourceBoundsInWindow.left.roundToInt(), sourceBoundsInWindow.top.roundToInt()) }
                .size(width = width, height = height),
            containerColor = MaterialTheme.colorScheme.surface,
        ) {
            Box(Modifier.fillMaxSize().padding(horizontal = 8.dp), contentAlignment = Alignment.CenterEnd) {
                Text(
                    evaluation.resultCents?.let(::formatMoney) ?: expression.ifBlank { "¥0.00" },
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        Surface(
            modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().navigationBarsPadding(),
            color = MaterialTheme.colorScheme.background,
            shape = RoundedCornerShape(topStart = 10.dp, topEnd = 10.dp),
            tonalElevation = 2.dp,
        ) {
            Column(Modifier.fillMaxWidth().padding(top = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f).padding(end = 8.dp)) {
                        Text(
                            entry.note.ifBlank { entry.categoryName },
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(formatFullDate(date), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                        EntryAmountExpression(expression = expression, textAlign = TextAlign.End)
                        Text(
                            amountResultText(evaluation.resultCents, evaluation.error),
                            modifier = Modifier.fillMaxWidth().testTag("quick_amount_result"),
                            textAlign = TextAlign.End,
                            style = MaterialTheme.typography.headlineMedium,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Clip,
                            autoSize = QuickAmountAutoSize,
                        )
                    }
                }
                EntryAmountStatus(
                    evaluationError = evaluation.error,
                    modifier = Modifier.padding(horizontal = 16.dp),
                    textAlign = TextAlign.End,
                )
                EntryKeypad(
                    expression = expression,
                    enabled = !isSaving,
                    canSave = evaluation.isValid && !isSaving,
                    isSaving = isSaving,
                    onExpressionChange = { expression = it },
                    onDateClick = { showDatePicker = true },
                    onSave = save,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
            }
        }
    }

    if (showDatePicker) {
        SakuDatePickerDialog(
            initialDate = date,
            today = today,
            onDismiss = { showDatePicker = false },
            onConfirm = {
                dateEpochDay = it.toEpochDay()
                showDatePicker = false
            },
        )
    }

    if (showDiscardConfirm) {
        AlertDialog(
            onDismissRequest = { showDiscardConfirm = false },
            title = { Text("放弃金额修改？") },
            text = { Text("尚未保存的金额或日期将不会保留。") },
            confirmButton = {
                CaibTextButton(
                    onClick = { currentOnDismiss.value() },
                    enabled = !isSaving,
                ) { Text("放弃") }
            },
            dismissButton = { CaibTextButton(onClick = { showDiscardConfirm = false }) { Text("继续修改") } },
        )
    }
}

@Composable
fun QuickNoteEditorDialog(
    entry: UiLedgerEntry,
    isSaving: Boolean,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
) {
    var note by rememberSaveable(entry.id) { mutableStateOf(entry.note) }
    val focusRequester = remember { FocusRequester() }

    AlertDialog(
        modifier = Modifier.imePadding(),
        onDismissRequest = { if (!isSaving) onDismiss() },
        shape = RoundedCornerShape(10.dp),
        title = { Text("修改备注") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("${entry.categoryName} · ${formatMoney(entry.amountCents)}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it.takeCodePoints(100) },
                    modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
                    label = { Text("备注（可选）") },
                    supportingText = { Text("${note.codePointLength()}/100") },
                    singleLine = true,
                    enabled = !isSaving,
                    colors = caibOutlinedTextFieldColors(),
                    shape = RoundedCornerShape(10.dp),
                )
                // The field is composed in Dialog's separate owner. Request focus
                // only from that composition, after its focus target is attached.
                LaunchedEffect(entry.id) { focusRequester.requestFocus() }
            }
        },
        confirmButton = {
            SakuCtaButton(
                onClick = {
                    onSave(note.trim())
                },
                enabled = !isSaving,
            ) { Text("保存备注") }
        },
        dismissButton = { CaibTextButton(onClick = onDismiss, enabled = !isSaving) { Text("取消") } },
    )
}
