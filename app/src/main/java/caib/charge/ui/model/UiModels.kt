package caib.charge.ui.model

import androidx.compose.ui.graphics.Color
import caib.charge.domain.TextNormalizer
import java.time.LocalDate
import java.time.YearMonth

enum class UiEntryType { EXPENSE, INCOME }

data class UiCategory(
    val id: String,
    val type: UiEntryType,
    val name: String,
    val iconKey: String,
    val colorKey: Int,
    val sortOrder: Int,
    val builtIn: Boolean,
    val archived: Boolean
)

data class UiLedgerEntry(
    val id: Long,
    val type: UiEntryType,
    val categoryId: String,
    val categoryName: String,
    val amountCents: Long,
    val date: LocalDate,
    val note: String,
    val categoryIconKey: String = "more_horiz",
    val categoryColorKey: Int = 0,
    val normalizedNote: String = TextNormalizer.normalize(note),
)

data class UiHistoryTemplate(
    val amountCents: Long,
    val note: String
)

data class UiHistoryTemplateKey(
    val type: UiEntryType,
    val categoryId: String,
)

/**
 * History recommendations are meaningful only for the exact entry type/category pair that
 * produced them. Keeping the key and items in one value prevents a retained StateFlow value
 * from being rendered after the editor has already switched context.
 */
data class UiHistoryTemplateState(
    val key: UiHistoryTemplateKey? = null,
    val items: List<UiHistoryTemplate> = emptyList(),
) {
    fun itemsFor(type: UiEntryType, categoryId: String?): List<UiHistoryTemplate> =
        if (categoryId != null && key == UiHistoryTemplateKey(type, categoryId)) items else emptyList()
}

sealed interface UiRecentCategoriesState {
    data object Loading : UiRecentCategoriesState

    data class Ready(
        val categoryIds: Map<UiEntryType, String>,
    ) : UiRecentCategoriesState
}

data class UiDetailFilter(
    val month: YearMonth = YearMonth.now(),
    val year: Int? = null,
    val type: UiEntryType? = null,
    val categoryId: String? = null,
    val exactDate: LocalDate? = null
)

data class UiPeriodSummary(
    val incomeCents: Long = 0,
    val expenseCents: Long = 0
) {
    val balanceCents: Long get() = incomeCents - expenseCents
}

enum class UiChartPeriod { WEEK, MONTH, YEAR }

data class UiChartPoint(
    val index: Int,
    val label: String,
    val amountCents: Long,
    val date: LocalDate? = null,
    val month: YearMonth? = null,
    val startDate: LocalDate = date ?: month?.atDay(1) ?: LocalDate.MIN,
    val endDate: LocalDate = date ?: month?.atEndOfMonth() ?: startDate,
    val isFuture: Boolean = false,
    val selectable: Boolean = !isFuture,
    val selectionKey: String = "",
)

data class UiCategorySlice(
    val categoryId: String,
    val name: String,
    val amountCents: Long,
    val color: Color,
    val percentage: Float,
    val selectionKey: String = "",
)

data class UiNoteBreakdownSlice(
    val id: String,
    val label: String,
    val amountCents: Long,
    val count: Int,
    val percentage: Float,
    val normalizedNotes: Set<String>,
    val color: Color,
    val isRemainder: Boolean = false,
)

enum class UiEntryRankingOrder { AMOUNT, DATE }

enum class UiImportStatus { READY, DUPLICATE, INVALID, MAPPED_OTHER }

data class UiImportRow(
    val index: Int,
    val date: LocalDate?,
    val type: UiEntryType?,
    val categoryName: String,
    val amountCents: Long?,
    val note: String,
    val status: UiImportStatus,
    val message: String,
    val selected: Boolean
)
