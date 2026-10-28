package caib.charge.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Archive
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.window.DialogProperties
import caib.charge.ui.components.CaibTextButton
import caib.charge.ui.components.caibOutlinedTextFieldColors
import caib.charge.ui.components.CaibCard
import caib.charge.ui.components.CaibPrimaryButton
import caib.charge.ui.components.CategoryIcon
import caib.charge.ui.components.CategoryIconOptions
import caib.charge.domain.DomainValidationException
import caib.charge.ui.model.UiCategory
import caib.charge.ui.model.UiEntryType
import caib.charge.ui.theme.CategoryPalette
import caib.charge.ui.theme.caibIsDark
import caib.charge.ui.theme.categoryColor
import caib.charge.ui.theme.categoryContainerColor
import caib.charge.ui.theme.categoryContentColor
import caib.charge.ui.theme.expenseContentColor
import kotlinx.coroutines.CancellationException

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun CategoryManagerSheet(
    initialType: UiEntryType,
    categories: List<UiCategory>,
    onDismiss: () -> Unit,
    onCreate: (UiEntryType, String, String, Int, (Result<Unit>) -> Unit) -> Unit,
    onUpdate: (UiCategory, String, String, Int, (Result<Unit>) -> Unit) -> Unit,
    onReorder: (UiEntryType, List<String>) -> Unit,
    onArchive: (UiCategory, (Result<Unit>) -> Unit) -> Unit
) {
    var type by remember { mutableStateOf(initialType) }
    var editing by remember { mutableStateOf<UiCategory?>(null) }
    var creating by remember { mutableStateOf(false) }
    var archiving by remember { mutableStateOf<UiCategory?>(null) }
    var mutationBusy by remember { mutableStateOf(false) }
    var operationError by remember { mutableStateOf<String?>(null) }
    var mutationSequence by remember { mutableStateOf(0L) }
    val sheetState = rememberModalBottomSheetState(confirmValueChange = { !mutationBusy })
    val visible = categories.filter { it.type == type && !it.archived }.sortedBy { it.sortOrder }

    fun submit(start: ((Result<Unit>) -> Unit) -> Unit, onSuccess: () -> Unit) {
        if (mutationBusy) return
        mutationBusy = true
        operationError = null
        val attempt = ++mutationSequence
        val complete: (Result<Unit>) -> Unit = { result ->
            if (mutationBusy && mutationSequence == attempt) {
                mutationBusy = false
                if (result.isSuccess) onSuccess()
                // Only domain validation messages are intended for display, not storage diagnostics.
                else operationError = (result.exceptionOrNull() as? DomainValidationException)?.message ?: "操作失败，请重试"
            }
        }
        try {
            start(complete)
        } catch (cancelled: CancellationException) {
            if (mutationSequence == attempt) mutationBusy = false
            throw cancelled
        } catch (failure: Exception) {
            complete(Result.failure(failure))
        }
    }

    ModalBottomSheet(
        onDismissRequest = { if (!mutationBusy) onDismiss() },
        sheetState = sheetState,
        dragHandle = null,
        containerColor = MaterialTheme.colorScheme.background
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f).padding(start = 8.dp)) {
                    Text("分类管理", style = MaterialTheme.typography.headlineMedium)
                    Text("内置分类锁定，自定义分类可改名和归档", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = { if (!mutationBusy) onDismiss() }, enabled = !mutationBusy) { Icon(Icons.Rounded.Close, contentDescription = "关闭分类管理") }
            }
            Row(modifier = Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(type == UiEntryType.EXPENSE, { type = UiEntryType.EXPENSE }, { Text("支出分类") }, enabled = !mutationBusy)
                FilterChip(type == UiEntryType.INCOME, { type = UiEntryType.INCOME }, { Text("收入分类") }, enabled = !mutationBusy)
            }
            LazyColumn(
                modifier = Modifier.weight(1f).testTag("category_list_${visible.size}"),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                itemsIndexed(visible, key = { _, item -> item.id }) { index, item ->
                    CategoryManageRow(
                        category = item,
                        enabled = !mutationBusy,
                        canUp = index > 0,
                        canDown = index < visible.lastIndex,
                        onUp = {
                            val ids = visible.map { it.id }.toMutableList()
                            val moved = ids.removeAt(index)
                            ids.add(index - 1, moved)
                            onReorder(type, ids)
                        },
                        onDown = {
                            val ids = visible.map { it.id }.toMutableList()
                            val moved = ids.removeAt(index)
                            ids.add(index + 1, moved)
                            onReorder(type, ids)
                        },
                        onEdit = if (item.builtIn) null else ({ operationError = null; editing = item }),
                        onArchive = if (item.builtIn) null else ({ operationError = null; archiving = item })
                    )
                }
            }
            CaibPrimaryButton(
                onClick = { operationError = null; creating = true },
                enabled = !mutationBusy,
                modifier = Modifier.fillMaxWidth().padding(16.dp)
            ) {
                Icon(Icons.Rounded.Add, contentDescription = null)
                Text("新建${if (type == UiEntryType.EXPENSE) "支出" else "收入"}分类")
            }
        }
    }

    if (creating) {
        CategoryEditDialog(
            title = "新建分类",
            initialName = "",
            initialIcon = "wallet",
            initialColor = visible.size.mod(CategoryPalette.size),
            busy = mutationBusy,
            errorMessage = operationError,
            onClearError = { operationError = null },
            onDismiss = { if (!mutationBusy) creating = false },
            onConfirm = { name, icon, color ->
                submit(
                    start = { complete -> onCreate(type, name, icon, color, complete) },
                    onSuccess = { creating = false },
                )
            }
        )
    }
    editing?.let { category ->
        CategoryEditDialog(
            title = "编辑分类",
            initialName = category.name,
            initialIcon = category.iconKey,
            initialColor = category.colorKey,
            busy = mutationBusy,
            errorMessage = operationError,
            onClearError = { operationError = null },
            onDismiss = { if (!mutationBusy) editing = null },
            onConfirm = { name, icon, color ->
                submit(
                    start = { complete -> onUpdate(category, name, icon, color, complete) },
                    onSuccess = { editing = null },
                )
            }
        )
    }
    archiving?.let { category ->
        AlertDialog(
            onDismissRequest = { if (!mutationBusy) archiving = null },
            properties = DialogProperties(dismissOnBackPress = !mutationBusy, dismissOnClickOutside = !mutationBusy),
            title = { Text("归档${category.name}？") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("归档后将不再用于新增记账，历史记录仍保留。")
                    operationError?.let { CategoryOperationError(it) }
                }
            },
            confirmButton = {
                CaibTextButton(
                    modifier = Modifier.testTag("category_archive_confirm"),
                    enabled = !mutationBusy,
                    onClick = {
                        submit(
                            start = { complete -> onArchive(category, complete) },
                            onSuccess = { archiving = null },
                        )
                    },
                ) { Text(if (mutationBusy) "正在归档…" else "确认归档") }
            },
            dismissButton = {
                CaibTextButton(onClick = { if (!mutationBusy) archiving = null }, enabled = !mutationBusy) { Text("取消") }
            },
        )
    }
}

@Composable
private fun CategoryManageRow(
    category: UiCategory,
    enabled: Boolean,
    canUp: Boolean,
    canDown: Boolean,
    onUp: () -> Unit,
    onDown: () -> Unit,
    onEdit: (() -> Unit)?,
    onArchive: (() -> Unit)?
) {
    CaibCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier.size(40.dp).background(categoryContainerColor(category.colorKey, lightAlpha = 0.25f), CircleShape),
                contentAlignment = Alignment.Center
            ) { CategoryIcon(category.iconKey, contentDescription = category.name, modifier = Modifier.size(22.dp), tint = categoryContentColor(category.colorKey)) }
            Column(modifier = Modifier.weight(1f).padding(horizontal = 10.dp)) {
                Text(category.name, style = MaterialTheme.typography.titleMedium)
                Text(if (category.builtIn) "内置分类" else "自定义分类", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = onUp, enabled = enabled && canUp) { Icon(Icons.Rounded.ArrowUpward, contentDescription = "上移${category.name}") }
            IconButton(onClick = onDown, enabled = enabled && canDown) { Icon(Icons.Rounded.ArrowDownward, contentDescription = "下移${category.name}") }
            if (onEdit != null) IconButton(onClick = onEdit, enabled = enabled) { Icon(Icons.Rounded.Edit, contentDescription = "编辑${category.name}") }
            if (onArchive != null) IconButton(onClick = onArchive, enabled = enabled) { Icon(Icons.Rounded.Archive, contentDescription = "归档${category.name}") }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CategoryEditDialog(
    title: String,
    initialName: String,
    initialIcon: String,
    initialColor: Int,
    busy: Boolean,
    errorMessage: String?,
    onClearError: () -> Unit,
    onDismiss: () -> Unit,
    onConfirm: (String, String, Int) -> Unit
) {
    var name by rememberSaveable { mutableStateOf(initialName) }
    var icon by rememberSaveable { mutableStateOf(initialIcon) }
    var color by rememberSaveable { mutableIntStateOf(initialColor.mod(CategoryPalette.size)) }
    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        properties = DialogProperties(dismissOnBackPress = !busy, dismissOnClickOutside = !busy),
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                errorMessage?.let { CategoryOperationError(it) }
                Column(modifier = Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = truncateCategoryName(it); onClearError() },
                        modifier = Modifier.testTag("category_name_input"),
                        enabled = !busy,
                        isError = errorMessage != null,
                        colors = caibOutlinedTextFieldColors(),
                        label = { Text("分类名称") },
                        supportingText = { Text("1–12 个字符") },
                        singleLine = true
                    )
                    Text("颜色")
                    androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        CategoryPalette.indices.forEach { index ->
                            Box(
                                Modifier
                                    .size(48.dp)
                                    .semantics {
                                        contentDescription = "分类颜色：${index + 1}"
                                        selected = index == color
                                        role = Role.RadioButton
                                    }
                                    .clickable(enabled = !busy) { color = index },
                                contentAlignment = Alignment.Center,
                            ) {
                                Box(
                                    Modifier
                                        .size(if (index == color) 38.dp else 32.dp)
                                        .clip(CircleShape)
                                        .background(categoryColor(index))
                                        .then(if (index == color && MaterialTheme.colorScheme.caibIsDark) Modifier.border(2.dp, MaterialTheme.colorScheme.onPrimary, CircleShape) else Modifier),
                                )
                            }
                        }
                    }
                    Text("图标")
                    androidx.compose.foundation.layout.FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        CategoryIconOptions.forEach { (key, image) ->
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .semantics {
                                        contentDescription = "分类图标：$key"
                                        selected = icon == key
                                        role = Role.RadioButton
                                    }
                                    .clickable(enabled = !busy) { icon = key },
                                contentAlignment = Alignment.Center
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(42.dp)
                                        .clip(MaterialTheme.shapes.small)
                                        .background(if (icon == key) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant)
                                        .then(
                                            if (icon == key && MaterialTheme.colorScheme.caibIsDark) {
                                                Modifier.border(2.dp, MaterialTheme.colorScheme.primary, MaterialTheme.shapes.small)
                                            } else Modifier,
                                        ),
                                    contentAlignment = Alignment.Center,
                                ) { Icon(image, contentDescription = null, modifier = Modifier.size(22.dp)) }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            CaibTextButton(
                modifier = Modifier.testTag("category_save"),
                onClick = { if (!busy) onConfirm(name.trim(), icon, color) },
                enabled = !busy && name.trim().isNotEmpty(),
            ) { Text(if (busy) "正在保存…" else "保存") }
        },
        dismissButton = { CaibTextButton(onClick = onDismiss, enabled = !busy) { Text("取消") } }
    )
}

@Composable
private fun CategoryOperationError(message: String) {
    Text(
        text = message,
        modifier = Modifier.testTag("category_operation_error").semantics { liveRegion = LiveRegionMode.Polite },
        style = MaterialTheme.typography.bodyMedium,
        color = expenseContentColor(),
    )
}
