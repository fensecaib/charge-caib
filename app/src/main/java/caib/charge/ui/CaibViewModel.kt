package caib.charge.ui

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import caib.charge.data.transfer.SafDocumentGateway
import caib.charge.domain.model.Category
import caib.charge.domain.model.CsvExportScope
import caib.charge.domain.model.DetailFilter
import caib.charge.domain.model.EntryDateLimits
import caib.charge.domain.model.EntryType
import caib.charge.domain.model.ImportPreviewBatch
import caib.charge.domain.model.ImportRowStatus
import caib.charge.domain.model.LedgerEntry
import caib.charge.domain.model.LedgerEntryDraft
import caib.charge.domain.repository.CategoryRepository
import caib.charge.domain.repository.CsvTransferService
import caib.charge.domain.repository.LedgerRepository
import caib.charge.ui.entry.EntryRecommendationStore
import caib.charge.ui.entry.EntrySheetResult
import caib.charge.ui.model.UiCategory
import caib.charge.ui.model.UiDetailFilter
import caib.charge.ui.model.UiEntryType
import caib.charge.ui.model.UiHistoryTemplateState
import caib.charge.ui.model.UiImportRow
import caib.charge.ui.model.UiImportStatus
import caib.charge.ui.model.UiLedgerEntry
import caib.charge.ui.model.UiPeriodSummary
import caib.charge.ui.model.UiRecentCategoriesState
import caib.charge.ui.model.toDomain
import caib.charge.ui.model.toUi
import caib.charge.ui.profile.ExportScopeChoice
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import java.time.LocalDate
import java.time.YearMonth
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex

data class UiMessage(val text: String, val actionLabel: String? = null, val action: (() -> Unit)? = null)

@HiltViewModel
@OptIn(ExperimentalCoroutinesApi::class)
class CaibViewModel @Inject constructor(
    private val ledgerRepository: LedgerRepository,
    private val categoryRepository: CategoryRepository,
    private val csvTransferService: CsvTransferService,
    private val documentGateway: SafDocumentGateway,
    private val clock: Clock,
) : ViewModel() {
    val currentDate: LocalDate get() = LocalDate.now(clock)
    private val today: LocalDate get() = currentDate
    private val currentMonth: YearMonth get() = YearMonth.from(today)
    private val detailFilter = MutableStateFlow(UiDetailFilter(month = currentMonth))
    val currentFilter: StateFlow<UiDetailFilter> = detailFilter

    val categories: StateFlow<List<UiCategory>> = categoryRepository
        .observeCategories(includeArchived = true)
        .map { list -> list.map(Category::toUi) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val entries: StateFlow<List<UiLedgerEntry>> = detailFilter
        .flatMapLatest { ledgerRepository.observeEntries(it.toDomain()) }
        .map { list -> list.map(LedgerEntry::toUi) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val summary: StateFlow<UiPeriodSummary> = detailFilter
        .flatMapLatest { ledgerRepository.observeSummary(it.toDomain()) }
        .map { UiPeriodSummary(it.incomeCents, it.expenseCents) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UiPeriodSummary())

    val stats = ledgerRepository.observeStats()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), caib.charge.domain.model.LedgerStats(0, 0))

    private val entryRecommendations = EntryRecommendationStore(ledgerRepository, viewModelScope)
    val historyTemplateState: StateFlow<UiHistoryTemplateState> = entryRecommendations.historyState

    private val _importPreview = MutableStateFlow<ImportPreviewBatch?>(null)
    val importPreview: StateFlow<ImportPreviewBatch?> = _importPreview
    private val importMutex = Mutex()
    private val _isImporting = MutableStateFlow(false)
    val isImporting: StateFlow<Boolean> = _isImporting
    private var importPreviewJob: Job? = null
    private var importPreviewGeneration = 0L

    private val messageChannel = Channel<UiMessage>(Channel.BUFFERED)
    val messages = messageChannel.receiveAsFlow()
    private data class PendingUndo(val token: Long, val entry: LedgerEntry)
    private var pendingUndo: PendingUndo? = null
    private var nextUndoToken = 0L
    private val saveMutex = Mutex()
    private val _isSaving = MutableStateFlow(false)
    val isSaving: StateFlow<Boolean> = _isSaving
    val recentCategoriesState: StateFlow<UiRecentCategoriesState> = entryRecommendations.recentState
    private val _editorEntry = MutableStateFlow<UiLedgerEntry?>(null)
    val editorEntry: StateFlow<UiLedgerEntry?> = _editorEntry
    private val _editorLoading = MutableStateFlow(false)
    val editorLoading: StateFlow<Boolean> = _editorLoading
    private var editorLoadJob: Job? = null
    private var editorSelectionInitialized = false
    private var editorSelectionId: Long? = null
    private var editorRequestGeneration = 0L

    fun prepareEditor(entryId: Long?, force: Boolean = false) {
        if (!force && editorSelectionInitialized && editorSelectionId == entryId) return
        editorSelectionInitialized = true
        editorSelectionId = entryId
        val requestGeneration = ++editorRequestGeneration
        editorLoadJob?.cancel()
        _editorEntry.value = null
        _editorLoading.value = entryId != null
        if (entryId == null) return
        editorLoadJob = viewModelScope.launch {
            try {
                val entry = ledgerRepository.getEntry(entryId)
                if (requestGeneration == editorRequestGeneration && editorSelectionId == entryId) {
                    _editorEntry.value = entry?.toUi()
                    if (entry == null) messageChannel.send(UiMessage("账目不存在或已被删除"))
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                if (requestGeneration == editorRequestGeneration && editorSelectionId == entryId) {
                    messageChannel.send(UiMessage(error.message ?: "读取账目失败"))
                }
            } finally {
                if (requestGeneration == editorRequestGeneration && editorSelectionId == entryId) {
                    _editorLoading.value = false
                }
            }
        }
    }

    fun setMonth(month: YearMonth) {
        detailFilter.value = detailFilter.value.copy(month = month, year = null, exactDate = null)
    }

    fun revealSavedEntry(date: LocalDate) {
        detailFilter.value = UiDetailFilter(month = YearMonth.from(date))
    }

    fun previousDetailPeriod() {
        val current = detailFilter.value
        detailFilter.value = if (current.year != null) {
            val previousYear = current.year - 1
            if (previousYear >= EntryDateLimits.MIN_DATE.year) {
                current.copy(year = previousYear, month = YearMonth.of(previousYear, 1), exactDate = null)
            } else {
                current
            }
        } else {
            val previousMonth = current.month.minusMonths(1)
            if (previousMonth >= YearMonth.from(EntryDateLimits.MIN_DATE)) {
                current.copy(month = previousMonth, exactDate = null)
            } else {
                current
            }
        }
    }

    fun nextDetailPeriod() {
        val current = detailFilter.value
        detailFilter.value = if (current.year != null) {
            val nextYear = current.year + 1
            if (nextYear <= currentMonth.year) {
                current.copy(year = nextYear, month = YearMonth.of(nextYear, 1), exactDate = null)
            } else {
                current
            }
        } else {
            val nextMonth = current.month.plusMonths(1)
            if (nextMonth <= currentMonth) current.copy(month = nextMonth, exactDate = null) else current
        }
    }

    fun setTypeFilter(type: UiEntryType?) {
        val currentCategory = detailFilter.value.categoryId
        val compatible = currentCategory == null || categories.value.any { it.id == currentCategory && (type == null || it.type == type) }
        detailFilter.value = detailFilter.value.copy(type = type, categoryId = currentCategory.takeIf { compatible }, exactDate = null)
    }

    fun setCategoryFilter(categoryId: String?) {
        detailFilter.value = detailFilter.value.copy(categoryId = categoryId, exactDate = null)
    }

    fun setTemplateContext(type: UiEntryType, categoryId: String?) {
        entryRecommendations.setHistoryContext(type, categoryId)
    }

    fun clearTemplateContext() {
        entryRecommendations.setHistoryContext(UiEntryType.EXPENSE, null)
    }

    fun saveEntry(result: EntrySheetResult, onSaved: (Long) -> Unit) {
        if (!saveMutex.tryLock()) return
        _isSaving.value = true
        viewModelScope.launch {
            try {
                val savedId = ledgerRepository.upsert(
                    LedgerEntryDraft(
                        id = result.id,
                        type = result.type.toDomain(),
                        categoryId = result.categoryId,
                        amountCents = result.amountCents,
                        entryDate = result.date,
                        note = result.note,
                    )
                )
                if (categories.value.any { category ->
                        category.id == result.categoryId && category.type == result.type && !category.archived
                    }
                ) {
                    entryRecommendations.recordRecentCategory(result.type, result.categoryId)
                }
                onSaved(savedId)
                val message = when {
                    result.id == null -> "已保存账目"
                    result.originalDate != null && result.originalDate != result.date ->
                        "已更新并移动到 ${result.date.monthValue}月${result.date.dayOfMonth}日"
                    else -> "已更新账目"
                }
                messageChannel.send(UiMessage(message))
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                messageChannel.send(UiMessage(error.message ?: "保存失败"))
            } finally {
                _isSaving.value = false
                saveMutex.unlock()
            }
        }
    }

    fun deleteEntry(id: Long, onDeleted: () -> Unit) {
        viewModelScope.launch {
            try {
                val deleted = ledgerRepository.delete(id)
                if (deleted != null) {
                    val token = ++nextUndoToken
                    pendingUndo = PendingUndo(token, deleted)
                    onDeleted()
                    messageChannel.send(UiMessage("账目已删除", "撤销") { restoreDeleted(token) })
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                messageChannel.send(UiMessage(error.message ?: "删除失败"))
            }
        }
    }

    private fun restoreDeleted(token: Long) {
        val pending = pendingUndo?.takeIf { it.token == token } ?: return
        viewModelScope.launch {
            try {
                ledgerRepository.restore(pending.entry)
                if (pendingUndo?.token == token) pendingUndo = null
                messageChannel.send(UiMessage("已撤销删除"))
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                messageChannel.send(UiMessage(error.message ?: "撤销失败"))
            }
        }
    }

    fun createCategory(type: UiEntryType, name: String, icon: String, color: Int) = launchAction("已创建分类") {
        categoryRepository.createCustom(type.toDomain(), name, icon, color.toString())
    }

    fun updateCategory(category: UiCategory, name: String, icon: String, color: Int) = launchAction("已更新分类") {
        categoryRepository.updateCustom(category.id, name, icon, color.toString())
    }

    fun reorderCategories(type: UiEntryType, ids: List<String>) = launchAction(null) {
        categoryRepository.reorder(type.toDomain(), ids)
    }

    fun archiveCategory(category: UiCategory) = launchAction("分类已归档") {
        categoryRepository.archive(category.id)
    }

    fun previewImport(uri: Uri) {
        if (_isImporting.value) return
        val requestGeneration = ++importPreviewGeneration
        importPreviewJob?.cancel()
        importPreviewJob = viewModelScope.launch {
            try {
                val document = documentGateway.readForImport(uri)
                val preview = csvTransferService.previewImport(document.fileName, document.bytes)
                if (requestGeneration == importPreviewGeneration) _importPreview.value = preview
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                if (requestGeneration == importPreviewGeneration) {
                    messageChannel.send(UiMessage(error.message ?: "文件解析失败"))
                }
            }
        }
    }

    fun dismissImport() {
        if (!_isImporting.value) _importPreview.value = null
    }

    fun commitImport(selectedRows: Set<Int>) {
        val batch = _importPreview.value ?: return
        if (!importMutex.tryLock()) return
        _isImporting.value = true
        val duplicates = batch.rows.filter { it.status == ImportRowStatus.DUPLICATE && it.rowNumber in selectedRows }.mapTo(mutableSetOf()) { it.rowNumber }
        viewModelScope.launch {
            try {
                val result = csvTransferService.commitImport(batch, selectedRows, duplicates)
                if (_importPreview.value === batch) _importPreview.value = null
                messageChannel.send(UiMessage("已导入 ${result.insertedRows} 笔账目"))
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                messageChannel.send(UiMessage(error.message ?: "导入失败"))
            } finally {
                _isImporting.value = false
                importMutex.unlock()
            }
        }
    }

    fun export(uri: Uri, scope: ExportScopeChoice) {
        viewModelScope.launch {
            try {
                val now = currentMonth
                val domainScope = when (scope) {
                    ExportScopeChoice.CURRENT_MONTH -> CsvExportScope.Month(now.year, now.monthValue)
                    ExportScopeChoice.CURRENT_YEAR -> CsvExportScope.Year(now.year)
                    ExportScopeChoice.ALL -> CsvExportScope.All
                }
                val result = csvTransferService.export(domainScope)
                documentGateway.write(uri, result.bytes)
                messageChannel.send(UiMessage("已导出 ${result.rowCount} 笔账目"))
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                messageChannel.send(UiMessage(error.message ?: "导出失败"))
            }
        }
    }

    fun importRowsToUi(batch: ImportPreviewBatch): List<UiImportRow> = batch.rows.map { row ->
        UiImportRow(
            index = row.rowNumber,
            date = row.entryDate,
            type = row.type?.toUi(),
            categoryName = row.categoryName.orEmpty(),
            amountCents = row.amountCents,
            note = row.note,
            status = when {
                row.status == ImportRowStatus.INVALID -> UiImportStatus.INVALID
                row.status == ImportRowStatus.DUPLICATE -> UiImportStatus.DUPLICATE
                row.mappedToOther -> UiImportStatus.MAPPED_OTHER
                else -> UiImportStatus.READY
            },
            message = row.message ?: if (row.mappedToOther) "未知分类已映射到其他" else "可导入",
            selected = row.includedByDefault
        )
    }

    private fun launchAction(success: String?, block: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                block()
                if (success != null) messageChannel.send(UiMessage(success))
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                messageChannel.send(UiMessage(error.message ?: "操作失败"))
            }
        }
    }

}

private fun UiDetailFilter.toDomain(): DetailFilter {
    val start = exactDate ?: year?.let { LocalDate.of(it, 1, 1) } ?: month.atDay(1)
    val end = exactDate ?: year?.let { LocalDate.of(it, 12, 31) } ?: month.atEndOfMonth()
    return DetailFilter(start.toEpochDay(), end.toEpochDay(), type?.toDomain(), categoryId)
}
