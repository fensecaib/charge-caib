package caib.charge.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import caib.charge.domain.model.MigrationLimits
import caib.charge.domain.model.MigrationPreview
import caib.charge.domain.repository.MigrationTransferService
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import java.time.LocalDate
import java.time.format.DateTimeParseException
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class MigrationUiState(
    val busy: Boolean = false,
    val code: String = "",
    val input: String = "",
    val message: String = "",
    val preview: MigrationPreview? = null,
)

@HiltViewModel
class MigrationViewModel @Inject constructor(
    private val repository: MigrationTransferService,
    private val clock: Clock,
) : ViewModel() {
    private val mutable = MutableStateFlow(MigrationUiState())
    val state = mutable.asStateFlow()
    val today: LocalDate get() = LocalDate.now(clock)
    private var operation: Job? = null
    private var session = 0L

    /** Invalidate results even if a service finishes after coroutine cancellation. */
    fun clearSession() {
        session++
        operation?.cancel()
        operation = null
        mutable.value = MigrationUiState()
    }

    fun reset() {
        if (!mutable.value.busy) mutable.value = MigrationUiState(input = mutable.value.input)
    }

    fun clearPreview() {
        if (!mutable.value.busy) mutable.value = mutable.value.copy(preview = null)
    }

    fun updateInput(value: String) {
        if (mutable.value.busy) return
        val oversized = value.length > MigrationLimits.MAX_PASTED_CHARACTERS
        mutable.value = mutable.value.copy(
            code = "",
            input = if (oversized) "" else value,
            preview = null,
            message = if (oversized) "代码过长，请分日期范围导出" else "",
        )
    }

    fun export(start: String, end: String) = run {
        val result = repository.export(LocalDate.parse(start), LocalDate.parse(end))
        MigrationUiState(code = result.first, message = "$start 至 $end · ${result.second} 笔 · ${result.first.length} 字符")
    }

    fun preview() {
        val code = mutable.value.input
        run { MigrationUiState(preview = repository.preview(code)) }
    }

    fun commit() {
        val preview = mutable.value.preview ?: return
        run(clearInputOnSuccess = true) {
            MigrationUiState(message = "覆盖完成，已导入 ${repository.commit(preview)} 笔账目")
        }
    }

    private fun run(clearInputOnSuccess: Boolean = false, action: suspend () -> MigrationUiState) {
        if (mutable.value.busy) return
        val activeSession = session
        val input = mutable.value.input
        mutable.value = MigrationUiState(busy = true, input = input)
        operation = viewModelScope.launch {
            try {
                val result = action()
                if (session == activeSession) {
                    mutable.value = result.copy(input = if (clearInputOnSuccess) "" else input)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (session == activeSession) {
                    val message = when (e) {
                        is DateTimeParseException -> "请输入有效日期，格式为 YYYY-MM-DD，例如 2026-01-01"
                        is IllegalArgumentException -> e.message ?: "迁移数据无效，请重新预览"
                        else -> "操作失败，请重新预览后重试"
                    }
                    mutable.value = MigrationUiState(input = input, message = message)
                }
            } finally {
                if (session == activeSession) mutable.value = mutable.value.copy(busy = false)
            }
        }
    }
}
