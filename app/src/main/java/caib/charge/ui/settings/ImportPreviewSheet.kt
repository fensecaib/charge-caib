package caib.charge.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import caib.charge.ui.theme.expenseContentColor
import androidx.compose.ui.unit.dp
import caib.charge.ui.components.CaibCard
import caib.charge.ui.components.CaibPrimaryButton
import caib.charge.ui.model.UiImportRow
import caib.charge.ui.model.UiImportStatus
import caib.charge.ui.util.formatMoney

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImportPreviewSheet(
    fileName: String,
    rows: List<UiImportRow>,
    isCommitting: Boolean = false,
    onDismiss: () -> Unit,
    onCommit: (Set<Int>) -> Unit
) {
    val selected = remember(rows) {
        mutableStateMapOf<Int, Unit>().apply {
            rows.filter { it.selected }.forEach { put(it.index, Unit) }
        }
    }
    val counts = remember(rows) {
        Triple(
            rows.count { it.status == UiImportStatus.READY || it.status == UiImportStatus.MAPPED_OTHER },
            rows.count { it.status == UiImportStatus.DUPLICATE },
            rows.count { it.status == UiImportStatus.INVALID },
        )
    }

    ModalBottomSheet(
        onDismissRequest = { if (!isCommitting) onDismiss() },
        containerColor = MaterialTheme.colorScheme.background,
        dragHandle = null
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f).padding(start = 8.dp)) {
                    Text("导入预览", style = MaterialTheme.typography.headlineMedium)
                    Text(fileName, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = onDismiss, enabled = !isCommitting) {
                    Icon(Icons.Rounded.Close, contentDescription = "关闭导入预览")
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                PreviewMetric("可导入", counts.first, MaterialTheme.colorScheme.onSurface)
                PreviewMetric("疑似重复", counts.second, MaterialTheme.colorScheme.onSurface)
                PreviewMetric("无效", counts.third, expenseContentColor())
            }
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(rows, key = { it.index }) { row ->
                    val selectable = row.status != UiImportStatus.INVALID && !isCommitting
                    val checked = row.index in selected
                    CaibCard(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = if (selectable) ({
                            if (checked) selected.remove(row.index) else selected[row.index] = Unit
                        }) else null
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = checked,
                                onCheckedChange = if (selectable) ({ value ->
                                    if (value) selected[row.index] = Unit else selected.remove(row.index)
                                }) else null,
                                enabled = selectable
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    listOfNotNull(row.date?.toString(), row.categoryName.takeIf { it.isNotBlank() }).joinToString(" · ")
                                        .ifBlank { "第 ${row.index} 行" },
                                    style = MaterialTheme.typography.titleMedium
                                )
                                Text(
                                    row.message,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = when (row.status) {
                                        UiImportStatus.INVALID -> expenseContentColor()
                                        UiImportStatus.DUPLICATE -> MaterialTheme.colorScheme.onSurfaceVariant
                                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                                    }
                                )
                                if (row.note.isNotBlank()) Text(row.note, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            row.amountCents?.let { Text(formatMoney(it), style = MaterialTheme.typography.titleMedium) }
                        }
                    }
                }
            }
            CaibPrimaryButton(
                onClick = { onCommit(selected.keys.toSet()) },
                enabled = selected.isNotEmpty() && !isCommitting,
                modifier = Modifier.fillMaxWidth().padding(16.dp)
            ) { Text(if (isCommitting) "正在导入…" else "导入 ${selected.size} 笔账目") }
        }
    }
}

@Composable
private fun PreviewMetric(label: String, count: Int, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(count.toString(), style = MaterialTheme.typography.titleLarge, color = color)
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
