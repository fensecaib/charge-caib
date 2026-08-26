package caib.charge.domain.model

import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit

/**
 * A finite, calendar-aligned axis used by period pickers.
 *
 * The axis performs constant-time index/date conversion so a UI can expose the complete
 * supported ledger range through a lazy container without first allocating every period.
 */
data class ChartPeriodAxis(
    val granularity: ChartGranularity,
    val maxDate: LocalDate,
    val minDate: LocalDate = EntryDateLimits.MIN_DATE,
) {
    init {
        require(!maxDate.isBefore(minDate)) { "周期上界不能早于下界" }
    }

    val firstPeriod: ChartPeriod = ChartPeriod(granularity, minDate)
    val lastPeriod: ChartPeriod = ChartPeriod(granularity, maxDate)

    val itemCount: Int = (distance(firstPeriod.startDate, lastPeriod.startDate) + 1L)
        .also { require(it in 1L..Int.MAX_VALUE.toLong()) { "周期数量超出支持范围" } }
        .toInt()

    fun periodAt(index: Int): ChartPeriod {
        require(index in 0 until itemCount) { "周期索引超出范围" }
        val anchor = when (granularity) {
            ChartGranularity.WEEK -> firstPeriod.startDate.plusWeeks(index.toLong())
            ChartGranularity.MONTH -> firstPeriod.startDate.plusMonths(index.toLong())
            ChartGranularity.YEAR -> firstPeriod.startDate.plusYears(index.toLong())
        }
        return ChartPeriod(granularity, anchor)
    }

    fun indexOf(period: ChartPeriod): Int? {
        if (period.granularity != granularity) return null
        val start = period.startDate
        if (start < firstPeriod.startDate || start > lastPeriod.startDate) return null
        val index = distance(firstPeriod.startDate, start)
        if (index !in 0L until itemCount.toLong()) return null
        return index.toInt().takeIf { periodAt(it).startDate == start }
    }

    fun contains(period: ChartPeriod): Boolean = indexOf(period) != null

    private fun distance(start: LocalDate, end: LocalDate): Long = when (granularity) {
        ChartGranularity.WEEK -> ChronoUnit.WEEKS.between(start, end)
        ChartGranularity.MONTH -> ChronoUnit.MONTHS.between(YearMonth.from(start), YearMonth.from(end))
        ChartGranularity.YEAR -> end.year.toLong() - start.year.toLong()
    }
}
