package caib.charge.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import caib.charge.ui.components.CaibCard
import caib.charge.ui.components.CaibPrimaryButton
import caib.charge.ui.components.CategoryIcon
import caib.charge.ui.components.CategoryIconOptions
import caib.charge.ui.model.UiCategory
import caib.charge.ui.model.UiEntryType
import caib.charge.ui.theme.CategoryPalette

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun CategoryManagerSheet(
    initialType: UiEntryType,
    categories: List<UiCategory>,
    onDismiss: () -> Unit,
    onCreate: (UiEntryType, String, String, Int) -> Unit,
    onUpdate: (UiCategory, String, String, Int) -> Unit,
    onReorder: (UiEntryType, List<String>) -> Unit,
    onArchive: (UiCategory) -> Unit
) {
    var type by remember { mutableStateOf(initialType) }
    var editing by remember { mutableStateOf<UiCategory?>(null) }
    var creating by remember { mutableStateOf(false) }
    val visible = categories.filter { it.type == type && !it.archived }.sortedBy { it.sortOrder }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
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
                IconButton(onClick = onDismiss) { Icon(Icons.Rounded.Close, contentDescription = "关闭分类管理") }
            }
            Row(modifier = Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(type == UiEntryType.EXPENSE, { type = UiEntryType.EXPENSE }, { Text("支出分类") })
                FilterChip(type == UiEntryType.INCOME, { type = UiEntryType.INCOME }, { Text("收入分类") })
            }
            LazyColumn(
                modifier = Modifier.weight(1f).testTag("category_list_${visible.size}"),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                itemsIndexed(visible, key = { _, item -> item.id }) { index, item ->
                    CategoryManageRow(
                        category = item,
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
                        onEdit = if (item.builtIn) null else ({ editing = item }),
                        onArchive = if (item.builtIn) null else ({ onArchive(item) })
                    )
                }
            }
            CaibPrimaryButton(
                onClick = { creating = true },
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
            onDismiss = { creating = false },
            onConfirm = { name, icon, color ->
                creating = false
                onCreate(type, name, icon, color)
            }
        )
    }
    editing?.let { category ->
        CategoryEditDialog(
            title = "编辑分类",
            initialName = category.name,
            initialIcon = category.iconKey,
            initialColor = category.colorKey,
            onDismiss = { editing = null },
            onConfirm = { name, icon, color ->
                editing = null
                onUpdate(category, name, icon, color)
            }
        )
    }
}

@Composable
private fun CategoryManageRow(
    category: UiCategory,
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
                Modifier.size(40.dp).background(CategoryPalette[category.colorKey.mod(CategoryPalette.size)].copy(alpha = 0.25f), CircleShape),
                contentAlignment = Alignment.Center
            ) { CategoryIcon(category.iconKey, contentDescription = category.name, modifier = Modifier.size(22.dp)) }
            Column(modifier = Modifier.weight(1f).padding(horizontal = 10.dp)) {
                Text(category.name, style = MaterialTheme.typography.titleMedium)
                Text(if (category.builtIn) "内置分类" else "自定义分类", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = onUp, enabled = canUp) { Icon(Icons.Rounded.ArrowUpward, contentDescription = "上移${category.name}") }
            IconButton(onClick = onDown, enabled = canDown) { Icon(Icons.Rounded.ArrowDownward, contentDescription = "下移${category.name}") }
            if (onEdit != null) IconButton(onClick = onEdit) { Icon(Icons.Rounded.Edit, contentDescription = "编辑${category.name}") }
            if (onArchive != null) IconButton(onClick = onArchive) { Icon(Icons.Rounded.Archive, contentDescription = "归档${category.name}") }
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
    onDismiss: () -> Unit,
    onConfirm: (String, String, Int) -> Unit
) {
    var name by remember { mutableStateOf(initialName) }
    var icon by remember { mutableStateOf(initialIcon) }
    var color by remember { mutableIntStateOf(initialColor.mod(CategoryPalette.size)) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(12) },
                    label = { Text("分类名称") },
                    supportingText = { Text("1–12 个字符") },
                    singleLine = true
                )
                Text("颜色")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CategoryPalette.forEachIndexed { index, value ->
                        Box(
                            Modifier
                                .size(if (index == color) 38.dp else 32.dp)
                                .clip(CircleShape)
                                .background(value)
                                .clickable { color = index }
                        )
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
                                .size(42.dp)
                                .clip(MaterialTheme.shapes.small)
                                .background(if (icon == key) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant)
                                .clickable { icon = key },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(image, contentDescription = key, modifier = Modifier.size(22.dp))
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(name.trim(), icon, color) }, enabled = name.trim().isNotEmpty()) { Text("保存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}
