package caib.charge.ui.chart

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import caib.charge.domain.NoteBreakdownCalculator
import caib.charge.domain.model.Category
import caib.charge.domain.model.ChartGranularity
import caib.charge.domain.model.ChartPeriod
import caib.charge.domain.model.DetailFilter
import caib.charge.domain.model.EntryType
import caib.charge.domain.model.LedgerEntry
import caib.charge.domain.repository.CategoryRepository
import caib.charge.domain.repository.LedgerRepository
import caib.charge.ui.model.UiCategory
import caib.charge.ui.model.UiCategorySlice
import caib.charge.ui.model.UiChartPeriod
import caib.charge.ui.model.UiChartPoint
import caib.charge.ui.model.UiEntryRankingOrder
import caib.charge.ui.model.UiEntryType
import caib.charge.ui.model.UiLedgerEntry
import caib.charge.ui.model.UiNoteBreakdownSlice
import caib.charge.ui.model.toDomain
import caib.charge.ui.model.toUi
import caib.charge.ui.theme.CategoryPalette
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import java.time.LocalDate
import java.time.YearMonth
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

data class ChartSelection(
    val type: UiEntryType,
    val period: UiChartPeriod,
    val anchorDate: LocalDate,
)

data class CategoryAnalysisSelection(
    val categoryId: String,
    val type: UiEntryType,
    val period: UiChartPeriod,
    val anchorDate: LocalDate,
)

private data class ChartControlState(
    val selection: ChartSelection,
    val selectedPointIndex: Int? = null,
)

private data class SelectedPointContext(
    val selection: ChartSelection,
    val point: UiChartPoint?,
)

@HiltViewModel
@OptIn(ExperimentalCoroutinesApi::class)
class ChartViewModel @Inject constructor(
    private val ledgerRepository: LedgerRepository,
    categoryRepository: CategoryRepository,
    private val clock: Clock,
) : ViewModel() {
    private val today = MutableStateFlow(LocalDate.now(clock))
    private val initialSelection = ChartSelection(UiEntryType.EXPENSE, UiChartPeriod.MONTH, today.value)
    private val chartControl = MutableStateFlow(ChartControlState(initialSelection))
    private val selection = chartControl.map { it.selection }.distinctUntilChanged()

    val chartType = selection.map { it.type }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UiEntryType.EXPENSE)
    val chartPeriodKind = selection.map { it.period }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UiChartPeriod.MONTH)
    val chartPeriodLabel = selection.map { it.toDomainPeriod().displayLabel() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "")
    val chartCanNext = combine(selection, today) { current, date -> current.toDomainPeriod().next().startDate <= date }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    val chartPoints: StateFlow<List<UiChartPoint>> = combine(selection, today) { current, date -> current to date }
        .flatMapLatest { (current, date) ->
            val period = current.toDomainPeriod()
            val selectionKey = current.key()
            ledgerRepository.observeTrend(period, current.type.toDomain()).map { points ->
                points.map { point -> point.toUi(date, selectionKey) }
            }.onStart { emit(emptyList()) }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val selectedPointContext: StateFlow<SelectedPointContext> = combine(
        chartControl,
        chartPoints,
    ) { control, points ->
        SelectedPointContext(
            selection = control.selection,
            point = control.selectedPointIndex?.let { selectedIndex ->
                points.firstOrNull { point -> point.index == selectedIndex }
            },
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        SelectedPointContext(initialSelection, null),
    )

    val selectedPoint: StateFlow<UiChartPoint?> = selectedPointContext.map { it.point }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val chartRanking: StateFlow<List<UiCategorySlice>> = selection
        .flatMapLatest { current ->
            val selectionKey = current.key()
            ledgerRepository.observeCategoryRanking(current.toDomainPeriod(), current.type.toDomain())
                .map { rows ->
                    rows.map { item ->
                        val key = item.category.colorKey.toIntOrNull() ?: 0
                        UiCategorySlice(
                            categoryId = item.category.id,
                            name = item.category.name,
                            amountCents = item.amountCents,
                            color = CategoryPalette[key.mod(CategoryPalette.size)],
                            percentage = item.percentage,
                            selectionKey = selectionKey,
                        )
                    }
                }
                .onStart { emit(emptyList()) }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val selectedPointEntries: StateFlow<List<UiLedgerEntry>> = selectedPointContext
        .flatMapLatest { (current, point) ->
            if (point == null) flowOf(emptyList())
            else ledgerRepository.observeEntries(
                DetailFilter(
                    startEpochDay = point.startDate.toEpochDay(),
                    endEpochDay = point.endDate.toEpochDay(),
                    type = current.type.toDomain(),
                )
            ).map { entries -> entries.sortedByDescending { it.amountCents }.take(3).map(LedgerEntry::toUi) }
                .onStart { emit(emptyList()) }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val allCategories = categoryRepository.observeCategories(includeArchived = true)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    private val analysisSelection = MutableStateFlow<CategoryAnalysisSelection?>(null)
    private val _selectedBreakdownId = MutableStateFlow<String?>(null)
    val selectedBreakdownId: StateFlow<String?> = _selectedBreakdownId
    val analysisRankingOrder = MutableStateFlow(UiEntryRankingOrder.AMOUNT)

    val analysisCategory: StateFlow<UiCategory?> = combine(allCategories, analysisSelection) { categories, current ->
        current?.let { key -> categories.firstOrNull { it.id == key.categoryId }?.toUi() }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val analysisType: StateFlow<UiEntryType> = analysisSelection.map { it?.type ?: UiEntryType.EXPENSE }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UiEntryType.EXPENSE)
    val analysisPeriodKind: StateFlow<UiChartPeriod> = analysisSelection.map { it?.period ?: UiChartPeriod.MONTH }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UiChartPeriod.MONTH)
    val analysisPeriodLabel: StateFlow<String> = analysisSelection.map { it?.toDomainPeriod()?.displayLabel().orEmpty() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "")
    val analysisCanNext: StateFlow<Boolean> = combine(analysisSelection, today) { current, date ->
        current?.toDomainPeriod()?.next()?.startDate?.let { start -> start <= date } ?: false
    }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    private val analysisDomainEntries: StateFlow<List<LedgerEntry>> = analysisSelection
        .flatMapLatest { current ->
            if (current == null) flowOf(emptyList())
            else ledgerRepository.observeEntries(DetailFilter.period(current.toDomainPeriod(), current.type.toDomain(), current.categoryId))
                .onStart { emit(emptyList()) }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val analysisEntries: StateFlow<List<UiLedgerEntry>> = analysisDomainEntries.map { rows -> rows.map(LedgerEntry::toUi) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val analysisPoints: StateFlow<List<UiChartPoint>> = combine(analysisSelection, today) { current, date -> current to date }
        .flatMapLatest { (current, date) ->
            if (current == null) flowOf(emptyList())
            else ledgerRepository.observeTrend(current.toDomainPeriod(), current.type.toDomain(), current.categoryId)
                .map { rows -> rows.map { it.toUi(date) } }
                .onStart { emit(emptyList()) }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val analysisTotalForType: StateFlow<Long> = analysisSelection
        .flatMapLatest { current ->
            if (current == null) flowOf(0L)
            else ledgerRepository.observeSummary(DetailFilter.period(current.toDomainPeriod(), current.type.toDomain()))
                .map { summary -> if (current.type == UiEntryType.EXPENSE) summary.expenseCents else summary.incomeCents }
                .onStart { emit(0L) }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0L)

    val analysisBreakdown: StateFlow<List<UiNoteBreakdownSlice>> = analysisDomainEntries
        .map { entries ->
            NoteBreakdownCalculator.calculate(entries).mapIndexed { index, slice ->
                UiNoteBreakdownSlice(
                    id = slice.id,
                    label = slice.label,
                    amountCents = slice.amountCents,
                    count = slice.count,
                    percentage = slice.percentage,
                    normalizedNotes = slice.normalizedNotes,
                    color = CategoryPalette[index.mod(CategoryPalette.size)],
                    isRemainder = slice.isRemainder,
                )
            }
        }
        .onEach { slices ->
            val selectedId = _selectedBreakdownId.value
            if (selectedId != null && slices.none { it.id == selectedId }) {
                _selectedBreakdownId.value = null
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun setChartType(type: UiEntryType) {
        chartControl.update { current ->
            current.copy(selection = current.selection.copy(type = type), selectedPointIndex = null)
        }
    }

    fun setChartPeriod(period: UiChartPeriod) {
        chartControl.update { current ->
            current.copy(selection = current.selection.copy(period = period), selectedPointIndex = null)
        }
    }

    fun previousChartPeriod() {
        chartControl.update { current ->
            current.copy(
                selection = current.selection.copy(
                    anchorDate = current.selection.toDomainPeriod().previous().anchorDate
                ),
                selectedPointIndex = null,
            )
        }
    }

    fun nextChartPeriod() {
        chartControl.update { current ->
            val next = current.selection.toDomainPeriod().next()
            current.copy(
                selection = if (next.startDate <= today.value) {
                    current.selection.copy(anchorDate = next.anchorDate)
                } else {
                    current.selection
                },
                selectedPointIndex = null,
            )
        }
    }

    fun selectPoint(point: UiChartPoint) {
        val current = chartControl.value.selection
        val period = current.toDomainPeriod()
        val belongsToSelection = point.selectionKey.isEmpty() || point.selectionKey == current.key()
        val belongsToPeriod = point.startDate >= period.startDate && point.endDate <= period.endDate
        if (point.selectable && !point.isFuture && belongsToSelection && belongsToPeriod) {
            chartControl.update { control -> control.copy(selectedPointIndex = point.index) }
        }
    }

    fun categoryAnalysisKey(slice: UiCategorySlice): CategoryAnalysisSelection? =
        chartControl.value.selection.takeIf { current ->
            slice.selectionKey.isEmpty() || slice.selectionKey == current.key()
        }?.let { current ->
            CategoryAnalysisSelection(slice.categoryId, current.type, current.period, current.anchorDate)
        }

    fun openCategoryAnalysis(categoryId: String, type: UiEntryType, period: UiChartPeriod, anchorDate: LocalDate) {
        val requested = CategoryAnalysisSelection(categoryId, type, period, anchorDate)
        if (analysisSelection.value == requested) return
        analysisSelection.value = requested
        _selectedBreakdownId.value = null
        analysisRankingOrder.value = UiEntryRankingOrder.AMOUNT
    }

    fun closeCategoryAnalysis() {
        analysisSelection.value = null
        _selectedBreakdownId.value = null
    }

    fun setAnalysisPeriod(period: UiChartPeriod) {
        analysisSelection.value = analysisSelection.value?.copy(period = period)
        _selectedBreakdownId.value = null
    }

    fun previousAnalysisPeriod() {
        analysisSelection.value = analysisSelection.value?.let { current ->
            current.copy(anchorDate = current.toDomainPeriod().previous().anchorDate)
        }
        _selectedBreakdownId.value = null
    }

    fun nextAnalysisPeriod() {
        analysisSelection.value = analysisSelection.value?.let { current ->
            val next = current.toDomainPeriod().next()
            if (next.startDate <= today.value) current.copy(anchorDate = next.anchorDate) else current
        }
        _selectedBreakdownId.value = null
    }

    fun selectBreakdown(id: String?) {
        _selectedBreakdownId.value = id
    }

    fun setAnalysisRankingOrder(order: UiEntryRankingOrder) {
        analysisRankingOrder.value = order
    }

    fun refreshToday() {
        today.value = LocalDate.now(clock)
    }
}

private fun ChartSelection.toDomainPeriod() = ChartPeriod(period.toGranularity(), anchorDate)
private fun ChartSelection.key(): String = "${type.name}|${period.name}|${anchorDate.toEpochDay()}"
private fun CategoryAnalysisSelection.toDomainPeriod() = ChartPeriod(period.toGranularity(), anchorDate)

private fun UiChartPeriod.toGranularity() = when (this) {
    UiChartPeriod.WEEK -> ChartGranularity.WEEK
    UiChartPeriod.MONTH -> ChartGranularity.MONTH
    UiChartPeriod.YEAR -> ChartGranularity.YEAR
}

private fun ChartPeriod.displayLabel(): String = when (granularity) {
    ChartGranularity.WEEK -> if (startDate.year == endDate.year) {
        "${startDate.year}年${startDate.monthValue}月${startDate.dayOfMonth}日–${endDate.monthValue}月${endDate.dayOfMonth}日"
    } else {
        "${startDate.year}年${startDate.monthValue}月${startDate.dayOfMonth}日–${endDate.year}年${endDate.monthValue}月${endDate.dayOfMonth}日"
    }
    ChartGranularity.MONTH -> "${startDate.year}年${startDate.monthValue}月"
    ChartGranularity.YEAR -> "${startDate.year}年"
}

private fun caib.charge.domain.model.TrendPoint.toUi(today: LocalDate, selectionKey: String = ""): UiChartPoint {
    val start = LocalDate.ofEpochDay(startEpochDay)
    val end = LocalDate.ofEpochDay(endEpochDay)
    val future = start.isAfter(today)
    return UiChartPoint(
        index = index,
        label = label,
        amountCents = amountCents,
        date = start.takeIf { start == end },
        month = YearMonth.from(start).takeIf { start.dayOfMonth == 1 && end == YearMonth.from(start).atEndOfMonth() },
        startDate = start,
        endDate = end,
        isFuture = future,
        selectable = !future,
        selectionKey = selectionKey,
    )
}
