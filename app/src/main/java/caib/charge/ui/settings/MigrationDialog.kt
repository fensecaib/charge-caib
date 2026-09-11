package caib.charge.ui.settings

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.PersistableBundle
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import caib.charge.domain.model.MigrationLimits
import java.time.LocalDate

@Composable
fun MigrationDialog(onDismiss: () -> Unit, viewModel: MigrationViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val today = viewModel.today
    var start by rememberSaveable { mutableStateOf(today.withDayOfMonth(1).toString()) }
    var end by rememberSaveable { mutableStateOf(today.toString()) }
    val input = state.input
    var clipboardMessage by remember { mutableStateOf("") }
    val textButtonColors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurface)
    val textFieldColors = OutlinedTextFieldDefaults.colors(
        focusedLabelColor = MaterialTheme.colorScheme.onSurface,
        cursorColor = MaterialTheme.colorScheme.onSurface,
    )
    val context = LocalContext.current
    val clipboard = remember(context) { context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager }
    DisposableEffect(viewModel) { viewModel.clearSession(); onDispose { viewModel.clearSession() } }
    Dialog(onDismissRequest = { if (!state.busy) onDismiss() }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(modifier = Modifier.fillMaxSize().safeDrawingPadding().imePadding(), color = MaterialTheme.colorScheme.background) {
            Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("数据迁移", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(vertical = 12.dp))
                    TextButton(colors = textButtonColors, onClick = onDismiss, enabled = !state.busy) { Text("关闭迁移") }
                }
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("迁移码包含账目与所用分类，未加密，请妥善保管。导入仅覆盖指定日期范围，外观保持不变。")
                    Text("导出迁移码", style = MaterialTheme.typography.titleLarge)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(colors = textButtonColors, enabled = !state.busy, onClick = { viewModel.reset(); start = today.withDayOfMonth(1).toString(); end = today.toString() }) { Text("本月") }
                        TextButton(colors = textButtonColors, enabled = !state.busy, onClick = { viewModel.reset(); start = today.withDayOfYear(1).toString(); end = today.toString() }) { Text("本年") }
                    }
                    OutlinedTextField(colors = textFieldColors, value = start, onValueChange = { start = it; viewModel.reset() }, label = { Text("开始日期") }, supportingText = { Text("格式：2026-01-01，包含当天") }, singleLine = true, enabled = !state.busy, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(colors = textFieldColors, value = end, onValueChange = { end = it; viewModel.reset() }, label = { Text("结束日期") }, supportingText = { Text("可填写任意月份、年份或日期范围，不晚于今天") }, singleLine = true, enabled = !state.busy, modifier = Modifier.fillMaxWidth())
                    Button(onClick = { clipboardMessage = ""; viewModel.export(start, end) }, enabled = !state.busy, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("生成迁移码") }
                    if (state.code.isNotEmpty()) {
                        Text(state.message, style = MaterialTheme.typography.bodyMedium)
                        Button(onClick = {
                            clipboardMessage = try {
                                val clip = ClipData.newPlainText("彩笔记账迁移码", state.code)
                                clip.description.extras = PersistableBundle().apply { putBoolean("android.content.extra.IS_SENSITIVE", true) }
                                clipboard.setPrimaryClip(clip)
                                "迁移码已复制"
                            } catch (_: Exception) { "复制失败，请缩小日期范围后重试" }
                        }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("复制迁移码") }
                    }
                    HorizontalDivider()
                    Text("导入迁移码", style = MaterialTheme.typography.titleLarge)
                    Text("同名同类型分类复用本机配置；缺少的分类自动创建。范围外账目保留。")
                    OutlinedTextField(colors = textFieldColors, value = input, onValueChange = viewModel::updateInput, label = { Text("粘贴迁移码") }, minLines = 2, maxLines = 3, enabled = !state.busy, modifier = Modifier.fillMaxWidth())
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(colors = textButtonColors, enabled = !state.busy, onClick = {
                            clipboardMessage = try {
                                val text = clipboard.primaryClip?.getItemAt(0)?.text?.toString().orEmpty()
                                viewModel.updateInput(text)
                                if (text.length <= MigrationLimits.MAX_PASTED_CHARACTERS) "已读取剪贴板" else ""
                            } catch (_: Exception) { "无法读取剪贴板，请手动粘贴" }
                        }) { Text("从剪贴板粘贴") }
                        Button(onClick = { clipboardMessage = ""; viewModel.preview() }, enabled = !state.busy && input.isNotBlank()) { Text("校验并预览") }
                    }
                    if (state.busy) { CircularProgressIndicator(); Text("正在处理，请稍候…") }
                    if (state.code.isEmpty() && state.message.isNotEmpty()) Text(state.message)
                    if (clipboardMessage.isNotEmpty()) Text(clipboardMessage)
                    Spacer(Modifier.height(24.dp))
                }
            }
        }
    }
    state.preview?.let { preview ->
        AlertDialog(onDismissRequest = viewModel::clearPreview, title = { Text("确认按日期覆盖") }, text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("${LocalDate.ofEpochDay(preview.data.start)} 至 ${LocalDate.ofEpochDay(preview.data.end)}（含起止日期）")
                Text("将删除本机 ${preview.replaced} 笔，导入 ${preview.data.entries.size} 笔；携带 ${preview.data.categories.size} 个分类。")
                if (preview.data.entries.isEmpty()) Text("这是空账本代码：会清空该日期范围的全部账目。", color = MaterialTheme.colorScheme.error)
                Text("范围内账目完整替换，无法直接撤销。请先导出本机对应范围作为备份。范围外账目不变。")
            }
        }, confirmButton = { TextButton(colors = textButtonColors, onClick = { viewModel.commit() }) { Text("确认覆盖") } }, dismissButton = { TextButton(colors = textButtonColors, onClick = viewModel::clearPreview) { Text("取消") } })
    }
}
