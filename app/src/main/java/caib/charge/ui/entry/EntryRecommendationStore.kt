package caib.charge.ui.entry

import caib.charge.domain.model.BuiltInCategoryIds
import caib.charge.domain.model.EntryType
import caib.charge.domain.repository.LedgerRepository
import caib.charge.ui.model.UiEntryType
import caib.charge.ui.model.UiRecentCategoriesState
import caib.charge.ui.model.UiRecentNoteKey
import caib.charge.ui.model.UiRecentNoteState
import caib.charge.ui.model.UiRecentNoteSuggestion
import caib.charge.ui.model.toDomain
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Owns entry recommendations without depending on Android framework state. */
@OptIn(ExperimentalCoroutinesApi::class)
internal class EntryRecommendationStore(
    private val ledgerRepository: LedgerRepository,
    private val scope: CoroutineScope,
) {
    private val recentNoteKey = MutableStateFlow<UiRecentNoteKey?>(null)

    val recentNoteState: StateFlow<UiRecentNoteState> = recentNoteKey
        .flatMapLatest { key ->
            if (key == null) {
                flowOf(UiRecentNoteState())
            } else {
                ledgerRepository.observeRecentNotes(
                    type = key.type.toDomain(),
                    categoryId = key.categoryId,
                    limit = 10,
                ).map { suggestions ->
                    UiRecentNoteState(
                        key = key,
                        items = suggestions.map {
                            UiRecentNoteSuggestion(
                                note = it.note,
                                normalizedNote = it.normalizedNote,
                            )
                        },
                    )
                }.onStart {
                    // StateFlow otherwise retains the previous key's clickable items until the
                    // repository delivers the first value for this key.
                    emit(UiRecentNoteState(key = key))
                }
            }
        }
        .stateIn(scope, SharingStarted.WhileSubscribed(5_000), UiRecentNoteState())

    private val recentLock = Any()
    private val recentOverrides = mutableMapOf<UiEntryType, String>()
    private val _recentState = MutableStateFlow<UiRecentCategoriesState>(UiRecentCategoriesState.Loading)
    val recentState: StateFlow<UiRecentCategoriesState> = _recentState

    init {
        scope.launch {
            val resolved = mapOf(
                UiEntryType.EXPENSE to mostRecentOrDefault(
                    EntryType.EXPENSE,
                    BuiltInCategoryIds.EXPENSE_FOOD,
                ),
                UiEntryType.INCOME to mostRecentOrDefault(
                    EntryType.INCOME,
                    BuiltInCategoryIds.INCOME_SALARY,
                ),
            )
            synchronized(recentLock) {
                _recentState.value = UiRecentCategoriesState.Ready(resolved + recentOverrides)
            }
        }
    }

    fun setRecentNoteContext(type: UiEntryType, categoryId: String?) {
        recentNoteKey.value = categoryId?.let { UiRecentNoteKey(type, it) }
    }

    /**
     * An existing entry can be saved while the cold-start lookup is still suspended. Retaining
     * the override makes that user action win over the older lookup result.
     */
    fun recordRecentCategory(type: UiEntryType, categoryId: String) {
        synchronized(recentLock) {
            recentOverrides[type] = categoryId
            val ready = _recentState.value as? UiRecentCategoriesState.Ready
            if (ready != null) {
                _recentState.value = ready.copy(categoryIds = ready.categoryIds + (type to categoryId))
            }
        }
    }

    private suspend fun mostRecentOrDefault(type: EntryType, defaultId: String): String = try {
        ledgerRepository.getMostRecentActiveCategoryId(type) ?: defaultId
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Exception) {
        defaultId
    }
}
