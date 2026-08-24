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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import caib.charge.domain.AmountError
import caib.charge.domain.AmountExpressionEngine
import caib.charge.ui.components.SakuCard
import caib.charge.ui.components.SakuCtaButton
import caib.charge.ui.model.UiLedgerEntry
import caib.charge.ui.util.formatFullDate
import caib.charge.ui.util.formatMoney
import java.time.LocalDate
import kotlin.math.roundToInt

@Composable
fun QuickAmountEditorOverlay(
    entry: UiLedgerEntry,
    sourceBoundsInWindow: Rect,
    today: LocalDate,
    isSaving: Boolean,
    onDismiss: () -> Unit,
    onSave: (EntrySheetResult) -> Unit,
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

    fun requestDismiss() {
        when {
            isSaving -> Unit
            dirty -> showDiscardConfirm = true
            else -> onDismiss()
        }
    }

    fun save() {
        val cents = evaluation.resultCents ?: return
        if (isSaving) return
        onSave(
            EntrySheetResult(
                id = entry.id,
                type = entry.type,
                categoryId = entry.categoryId,
                amountCents = cents,
                date = date,
                note = entry.note,
                originalDate = entry.date,
            )
        )
    }

    BackHandler { requestDismiss() }
    Box(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize().background(Color(0x522C4A5A)).clickable(onClick = ::requestDismiss))

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
                    Column(Modifier.weight(1f)) {
                        Text(entry.note.ifBlank { entry.categoryName }, style = MaterialTheme.typography.titleMedium)
                        Text(formatFullDate(date), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(expression.ifBlank { "0" }, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(evaluation.resultCents?.let(::formatMoney) ?: "¥0.00", style = MaterialTheme.typography.headlineMedium)
                    }
                }
                val error = evaluation.error
                if (error != null && error != AmountError.EMPTY) {
                    Text(
                        amountErrorMessage(error),
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                        textAlign = TextAlign.End,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                EntryKeypad(
                    expression = expression,
                    enabled = !isSaving,
                    canSave = evaluation.isValid && !isSaving,
                    isSaving = isSaving,
                    onExpressionChange = { expression = it },
                    onDateClick = { showDatePicker = true },
                    onSave = ::save,
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
                TextButton(onClick = onDismiss, enabled = !isSaving) { Text("放弃") }
            },
            dismissButton = { TextButton(onClick = { showDiscardConfirm = false }) { Text("继续修改") } },
        )
    }
}

@Composable
fun QuickNoteEditorDialog(
    entry: UiLedgerEntry,
    isSaving: Boolean,
    onDismiss: () -> Unit,
    onSave: (EntrySheetResult) -> Unit,
) {
    var note by rememberSaveable(entry.id) { mutableStateOf(entry.note) }
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(entry.id) { focusRequester.requestFocus() }

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
                    onValueChange = { note = it.take(100) },
                    modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
                    label = { Text("备注（可选）") },
                    supportingText = { Text("${note.length}/100") },
                    singleLine = true,
                    enabled = !isSaving,
                    shape = RoundedCornerShape(10.dp),
                )
            }
        },
        confirmButton = {
            SakuCtaButton(
                onClick = {
                    onSave(
                        EntrySheetResult(
                            id = entry.id,
                            type = entry.type,
                            categoryId = entry.categoryId,
                            amountCents = entry.amountCents,
                            date = entry.date,
                            note = note.trim(),
                            originalDate = entry.date,
                        )
                    )
                },
                enabled = !isSaving,
            ) { Text("保存备注") }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !isSaving) { Text("取消") } },
    )
}
