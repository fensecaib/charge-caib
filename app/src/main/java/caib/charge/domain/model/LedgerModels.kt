package caib.charge.domain.model

import java.time.Clock
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.TemporalAdjusters

enum class EntryType(val displayName: String) {
    EXPENSE("支出"),
    INCOME("收入"),
}

enum class EntrySource {
    MANUAL,
    CSV_IMPORT,
}

data class Category(
    val id: String,
    val type: EntryType,
    val name: String,
    val normalizedName: String,
    val iconKey: String,
    val colorKey: String,
    val sortOrder: Int,
    val isBuiltIn: Boolean,
    val isArchived: Boolean,
)

data class LedgerEntry(
    val id: Long,
    val type: EntryType,
    val category: Category,
    val amountCents: Long,
    val entryDate: LocalDate,
    val note: String,
    val normalizedNote: String,
    val source: EntrySource,
    val importBatchId: String?,
    val fingerprint: String,
    val createdAt: Long,
    val updatedAt: Long,
)

data class LedgerEntryDraft(
    val id: Long? = null,
    val type: EntryType,
    val categoryId: String,
    val amountCents: Long,
    val entryDate: LocalDate,
    val note: String = "",
)

data class DetailFilter(
    val startEpochDay: Long? = null,
    val endEpochDay: Long? = null,
    val type: EntryType? = null,
    val categoryId: String? = null,
) {
    init {
        require(startEpochDay == null || endEpochDay == null || startEpochDay <= endEpochDay) {
            "开始日期不能晚于结束日期"
        }
    }

    companion object {
        fun month(month: YearMonth, type: EntryType? = null, categoryId: String? = null) =
            DetailFilter(
                startEpochDay = month.atDay(1).toEpochDay(),
                endEpochDay = month.atEndOfMonth().toEpochDay(),
                type = type,
                categoryId = categoryId,
            )

        fun day(date: LocalDate, type: EntryType? = null, categoryId: String? = null) =
            DetailFilter(date.toEpochDay(), date.toEpochDay(), type, categoryId)

        fun period(period: ChartPeriod, type: EntryType? = null, categoryId: String? = null) =
            DetailFilter(
                startEpochDay = period.startDate.toEpochDay(),
                endEpochDay = period.endDate.toEpochDay(),
                type = type,
                categoryId = categoryId,
            )
    }
}

data class PeriodSummary(
    val incomeCents: Long,
    val expenseCents: Long,
) {
    val balanceCents: Long get() = incomeCents - expenseCents
}

object EntryDateLimits {
    val MIN_DATE: LocalDate = LocalDate.of(1900, 1, 1)
}

enum class ChartGranularity {
    WEEK,
    MONTH,
    YEAR,
}

/**
 * A calendar-aligned chart period containing [anchorDate]. Weeks use ISO Monday as their first day.
 *
 * The `(year, month?)` constructor and [year]/[month] accessors keep the original month/year API
 * source-compatible while callers move to the explicit [granularity] model.
 */
data class ChartPeriod(
    val granularity: ChartGranularity,
    val anchorDate: LocalDate,
) {
    constructor(year: Int, month: Int? = null) : this(
        granularity = if (month == null) ChartGranularity.YEAR else ChartGranularity.MONTH,
        anchorDate = if (month == null) LocalDate.of(year, 1, 1) else LocalDate.of(year, month, 1),
    )

    init {
        require(anchorDate.year in EntryDateLimits.MIN_DATE.year..LocalDate.MAX.year) { "年份超出范围" }
    }

    val year: Int get() = anchorDate.year
    val month: Int? get() = anchorDate.monthValue.takeIf { granularity == ChartGranularity.MONTH }

    val startDate: LocalDate
        get() = when (granularity) {
            ChartGranularity.WEEK -> anchorDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
            ChartGranularity.MONTH -> YearMonth.from(anchorDate).atDay(1)
            ChartGranularity.YEAR -> LocalDate.of(anchorDate.year, 1, 1)
        }

    val endDate: LocalDate
        get() = when (granularity) {
            ChartGranularity.WEEK -> if (startDate > LocalDate.MAX.minusDays(6)) {
                LocalDate.MAX
            } else {
                startDate.plusDays(6)
            }
            ChartGranularity.MONTH -> YearMonth.from(anchorDate).atEndOfMonth()
            ChartGranularity.YEAR -> LocalDate.of(anchorDate.year, 12, 31)
        }

    fun previous(): ChartPeriod {
        if (startDate <= EntryDateLimits.MIN_DATE) return this
        return copy(anchorDate = when (granularity) {
            ChartGranularity.WEEK -> anchorDate.minusWeeks(1)
            ChartGranularity.MONTH -> anchorDate.minusMonths(1)
            ChartGranularity.YEAR -> anchorDate.minusYears(1)
        })
    }

    fun next(): ChartPeriod {
        if (endDate >= LocalDate.MAX) return this
        return copy(anchorDate = when (granularity) {
            ChartGranularity.WEEK -> anchorDate.plusWeeks(1)
            ChartGranularity.MONTH -> anchorDate.plusMonths(1)
            ChartGranularity.YEAR -> anchorDate.plusYears(1)
        })
    }

    companion object {
        fun current(granularity: ChartGranularity, today: LocalDate = LocalDate.now()) =
            ChartPeriod(granularity, today)

        fun current(granularity: ChartGranularity, clock: Clock) =
            current(granularity, LocalDate.now(clock))

        fun currentWeek(today: LocalDate = LocalDate.now()) = current(ChartGranularity.WEEK, today)
        fun currentMonth(today: LocalDate = LocalDate.now()) = current(ChartGranularity.MONTH, today)
        fun currentYear(today: LocalDate = LocalDate.now()) = current(ChartGranularity.YEAR, today)

        fun currentWeek(clock: Clock) = currentWeek(LocalDate.now(clock))
        fun currentMonth(clock: Clock) = currentMonth(LocalDate.now(clock))
        fun currentYear(clock: Clock) = currentYear(LocalDate.now(clock))
    }
}

data class TrendPoint(
    val index: Int,
    val label: String,
    val startEpochDay: Long,
    val endEpochDay: Long,
    val amountCents: Long,
)

data class CategoryRanking(
    val category: Category,
    val amountCents: Long,
    val percentage: Float,
)

data class NoteBreakdownSlice(
    /** Stable UI key. Normal slices use `note:<normalized-note>` and the aggregate uses `other`. */
    val id: String,
    val label: String,
    val amountCents: Long,
    val count: Int,
    val percentage: Float,
    /** Normalized notes represented by this slice; an empty string represents an unnoted entry. */
    val normalizedNotes: Set<String>,
    val isRemainder: Boolean,
) {
    val key: String get() = id
    val entryCount: Int get() = count
    val memberNormalizedNotes: Set<String> get() = normalizedNotes
    val isOther: Boolean get() = isRemainder
}

data class HistoryTemplate(
    val amountCents: Long,
    val note: String,
    val normalizedNote: String,
    val lastCreatedAt: Long,
)

data class LedgerStats(
    val entryCount: Int,
    val bookkeepingDays: Int,
)

object MoneyLimits {
    const val MIN_CENTS: Long = 1
    const val MAX_CENTS: Long = 99_999_999_999
}
