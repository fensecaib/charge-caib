package caib.charge.ui.profile

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Category
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.FileDownload
import androidx.compose.material.icons.rounded.FileUpload
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.NotificationsOff
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Savings
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import caib.charge.ui.components.CaibCard
import caib.charge.ui.components.SakuTopBar
import caib.charge.BuildConfig
import caib.charge.domain.model.CaibThemeMode
import java.time.YearMonth

enum class ExportScopeChoice { CURRENT_MONTH, CURRENT_YEAR, ALL }

internal data class FontLicenseContent(
    val notice: String,
    val license: String? = null,
)

@Composable
fun ProfileScreen(
    recordCount: Long,
    recordDays: Long,
    onManageCategories: () -> Unit,
    onImportUri: (Uri) -> Unit,
    onExportUri: (Uri, ExportScopeChoice) -> Unit,
    modifier: Modifier = Modifier,
    currentMonth: YearMonth = YearMonth.now(),
    themeMode: CaibThemeMode = CaibThemeMode.SYSTEM,
    onThemeModeChange: (CaibThemeMode) -> Unit = {},
) {
    var showExportDialog by remember { mutableStateOf(false) }
    var showLicenses by remember { mutableStateOf(false) }
    var showAppearanceDialog by remember { mutableStateOf(false) }
    var exportScope by remember { mutableStateOf(ExportScopeChoice.CURRENT_MONTH) }
    val context = LocalContext.current
    val fontLicenseContent = remember(context) { loadFontLicenseContent(context) }
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> uri?.let(onImportUri) }
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/csv")
    ) { uri -> uri?.let { onExportUri(it, exportScope) } }

    Column(modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        SakuTopBar(title = "我的", subtitle = "数据只保存在这台设备")
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp, 8.dp, 16.dp, 112.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
        item {
            CaibCard(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(18.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    Stat("记录", recordCount.toString(), "笔")
                    Stat("坚持", recordDays.toString(), "天")
                    Stat("存储", "本地", "离线")
                }
            }
        }
        item { SectionTitle("应用设置") }
        item {
            SettingsCard {
                SettingRow(Icons.Rounded.Palette, "外观", themeMode.displayLabel()) {
                    showAppearanceDialog = true
                }
                SettingRow(Icons.Rounded.Category, "分类管理", "添加、排序和归档分类", onClick = onManageCategories)
                SettingRow(Icons.Rounded.FileUpload, "导入 CSV", "预览、去重后批量导入") {
                    importLauncher.launch(
                        arrayOf(
                            "text/*",
                            "application/csv",
                            "application/vnd.ms-excel",
                            "application/octet-stream"
                        )
                    )
                }
                SettingRow(Icons.Rounded.FileDownload, "导出 CSV", "按月、年或全部导出") { showExportDialog = true }
            }
        }
        item { SectionTitle("后续版本") }
        item {
            SettingsCard {
                SettingRow(Icons.Rounded.Savings, "预算", "后续版本", enabled = false)
                SettingRow(Icons.Rounded.NotificationsOff, "记账提醒", "后续版本", enabled = false)
                SettingRow(Icons.Rounded.CloudOff, "云同步", "后续版本", enabled = false)
            }
        }
        item { SectionTitle("关于") }
        item {
            SettingsCard {
                SettingRow(Icons.Rounded.Security, "隐私", "无账号、无网络权限、无自动备份", enabled = false)
                SettingRow(Icons.Rounded.Info, "版本与开源许可", "彩笔记账 ${BuildConfig.VERSION_NAME} · 查看第三方许可") {
                    showLicenses = true
                }
            }
        }
        }
    }

    if (showAppearanceDialog) {
        ThemeModeDialog(
            selectedMode = themeMode,
            onModeSelected = onThemeModeChange,
            onDismiss = { showAppearanceDialog = false },
        )
    }

    if (showExportDialog) {
        AlertDialog(
            onDismissRequest = { showExportDialog = false },
            title = { Text("选择导出范围") },
            text = {
                Column {
                    ExportScopeChoice.entries.forEach { scope ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(selected = exportScope == scope, onClick = { exportScope = scope })
                            Text(
                                when (scope) {
                                    ExportScopeChoice.CURRENT_MONTH -> "${currentMonth.monthValue} 月账单"
                                    ExportScopeChoice.CURRENT_YEAR -> "${currentMonth.year} 年账单"
                                    ExportScopeChoice.ALL -> "全部账单"
                                }
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    showExportDialog = false
                    val suffix = when (exportScope) {
                        ExportScopeChoice.CURRENT_MONTH -> currentMonth.toString()
                        ExportScopeChoice.CURRENT_YEAR -> currentMonth.year.toString()
                        ExportScopeChoice.ALL -> "全部"
                    }
                    exportLauncher.launch("彩笔记账_$suffix.csv")
                }) { Text("创建文件") }
            },
            dismissButton = { TextButton(onClick = { showExportDialog = false }) { Text("取消") } }
        )
    }

    if (showLicenses) {
        AlertDialog(
            onDismissRequest = { showLicenses = false },
            title = { Text("版本与开源许可") },
            text = {
                Column(
                    modifier = Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("彩笔记账 ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
                    Text("AndroidX / Jetpack Compose / Room / Hilt：Apache License 2.0")
                    Text("Kotlin 与 kotlinx.coroutines：Apache License 2.0")
                    Text(fontLicenseContent.notice)
                    fontLicenseContent.license?.let { license ->
                        Text(license, style = MaterialTheme.typography.bodySmall)
                    }
                    Text("本应用不包含第三方图表库，图表由 Compose Canvas 绘制。")
                }
            },
            confirmButton = {
                TextButton(onClick = { showLicenses = false }) { Text("知道了") }
            }
        )
    }
}

@Composable
private fun ThemeModeDialog(
    selectedMode: CaibThemeMode,
    onModeSelected: (CaibThemeMode) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(10.dp),
        title = { Text("外观") },
        text = {
            Column {
                CaibThemeMode.entries.forEach { mode ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .testTag("theme_mode_${mode.name}")
                            .selectable(
                                selected = mode == selectedMode,
                                role = Role.RadioButton,
                                onClick = { onModeSelected(mode) },
                            )
                            .padding(horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = mode == selectedMode, onClick = null)
                        Text(mode.displayLabel(), modifier = Modifier.padding(start = 8.dp))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("完成") }
        },
    )
}

private fun CaibThemeMode.displayLabel(): String = when (this) {
    CaibThemeMode.SYSTEM -> "跟随系统"
    CaibThemeMode.LIGHT -> "浅色"
    CaibThemeMode.DARK -> "深色"
}

@Composable
private fun Stat(label: String, value: String, suffix: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(verticalAlignment = Alignment.Bottom) {
            Text(value, style = MaterialTheme.typography.titleLarge)
            Text(suffix, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(start = 3.dp, bottom = 2.dp))
        }
    }
}

@Composable
private fun SectionTitle(title: String) {
    Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 4.dp))
}

@Composable
private fun SettingsCard(content: @Composable () -> Unit) {
    CaibCard(modifier = Modifier.fillMaxWidth()) { Column { content() } }
}

@Composable
private fun SettingRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    enabled: Boolean = true,
    onClick: () -> Unit = {}
) {
    val tint = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
            .then(if (enabled) Modifier.background(MaterialTheme.colorScheme.surface) else Modifier)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        androidx.compose.material3.Surface(
            modifier = Modifier.size(40.dp),
            color = tint.copy(alpha = 0.14f),
            shape = CircleShape
        ) { Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.padding(9.dp)) }
        Column(modifier = Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant)
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (enabled) Icon(Icons.Rounded.ChevronRight, contentDescription = "打开$title")
    }
}
