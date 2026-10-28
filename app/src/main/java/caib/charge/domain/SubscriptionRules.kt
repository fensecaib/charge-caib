package caib.charge.domain

import caib.charge.domain.model.MoneyLimits
import caib.charge.domain.model.SubscriptionAppearance
import caib.charge.domain.model.SubscriptionCreationMode
import caib.charge.domain.model.SubscriptionDateRange
import caib.charge.domain.model.SubscriptionDuration
import caib.charge.domain.model.SubscriptionDurationUnit
import caib.charge.domain.model.SubscriptionInspection
import caib.charge.domain.model.SubscriptionLimits
import caib.charge.domain.model.SubscriptionPeriodDraft
import caib.charge.domain.model.SubscriptionProject
import caib.charge.domain.model.SubscriptionStatus
import java.time.DateTimeException
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** Calendar arithmetic shared by the editor, persistence and status/progress rendering. */
object SubscriptionRules {
    fun normalizeAppearance(value: SubscriptionAppearance): SubscriptionAppearance = SubscriptionAppearance(
        iconKey = value.iconKey.takeIf { it in SubscriptionAppearance.ICON_KEYS } ?: "auto",
        colorKey = value.colorKey.takeIf { it in 0..7 } ?: 0,
    )

    fun dateRange(
        mode: SubscriptionCreationMode,
        anchorDate: LocalDate,
        duration: SubscriptionDuration,
    ): SubscriptionDateRange = when (mode) {
        SubscriptionCreationMode.START_DURATION -> SubscriptionDateRange(anchorDate, addDuration(anchorDate, duration))
        SubscriptionCreationMode.END_DURATION -> SubscriptionDateRange(subtractDuration(anchorDate, duration), anchorDate)
    }

    fun addDuration(date: LocalDate, duration: SubscriptionDuration): LocalDate = shift(date, duration, 1)
    fun subtractDuration(date: LocalDate, duration: SubscriptionDuration): LocalDate = shift(date, duration, -1)

    /** Month-end clamping is not invertible; either calendar direction can define the range. */
    fun matchesDuration(start: LocalDate, end: LocalDate, duration: SubscriptionDuration): Boolean =
        runCatching { addDuration(start, duration) == end }.getOrDefault(false) ||
            runCatching { subtractDuration(end, duration) == start }.getOrDefault(false)

    private fun shift(date: LocalDate, duration: SubscriptionDuration, direction: Long): LocalDate {
        validateDate(date)
        if (duration.value <= 0) throw DomainValidationException("持续时长须为正整数")
        val count = duration.value.toLong() * direction
        val result = try {
            when (duration.unit) {
                SubscriptionDurationUnit.DAY -> date.plusDays(count)
                SubscriptionDurationUnit.MONTH -> date.plusMonths(count)
                SubscriptionDurationUnit.YEAR -> date.plusYears(count)
            }
        } catch (_: DateTimeException) {
            throw DomainValidationException("持续时长超出支持的日期范围")
        } catch (_: ArithmeticException) {
            throw DomainValidationException("持续时长超出支持的日期范围")
        }
        validateDate(result)
        return result
    }

    fun validateDate(date: LocalDate) {
        if (date < SubscriptionLimits.MIN_DATE || date > SubscriptionLimits.MAX_DATE) {
            throw DomainValidationException("订阅日期须在 1900 至 9999 年之间")
        }
    }

    fun validateProjectText(name: String, note: String) {
        if (name.isBlank()) throw DomainValidationException("请输入订阅名称")
        if (name.codePointCount(0, name.length) > SubscriptionLimits.MAX_NAME_LENGTH) {
            throw DomainValidationException("订阅名称不能超过 60 字")
        }
        validateNote(note)
    }

    fun validateNote(note: String) {
        if (note.codePointCount(0, note.length) > SubscriptionLimits.MAX_NOTE_LENGTH) {
            throw DomainValidationException("备注不能超过 100 字")
        }
    }

    /** Accepts reverse-derived month-end ranges without silently changing their dates. */
    fun validatePeriod(draft: SubscriptionPeriodDraft) {
        validateDate(draft.startDate)
        validateDate(draft.endDate)
        if (draft.endDate <= draft.startDate) throw DomainValidationException("到期日必须晚于开始日")
        if (draft.amountCents != null && draft.amountCents !in 0..MoneyLimits.MAX_CENTS) {
            throw DomainValidationException("费用可留空，或填写 0 至 999,999,999.99 元")
        }
        validateNote(draft.note)
        draft.duration?.let { duration ->
            if (duration.value <= 0) throw DomainValidationException("持续时长须为正整数")
            if (!matchesDuration(draft.startDate, draft.endDate, duration)) {
                throw DomainValidationException("持续时长与起止日期不一致")
            }
        }
    }

    fun inspect(project: SubscriptionProject, today: LocalDate): SubscriptionInspection {
        val periods = project.periods
        var activeIndex = -1
        var upcomingIndex = -1
        var queuedCount = 0
        var recordedTotalCents = 0L
        var recordedFeeCount = 0
        periods.forEachIndexed { index, period ->
            if (activeIndex < 0 && period.startDate <= today && today < period.endDate) activeIndex = index
            if (period.startDate > today) {
                if (upcomingIndex < 0) upcomingIndex = index
                queuedCount += 1
            }
            period.amountCents?.let { amount ->
                recordedTotalCents = Math.addExact(recordedTotalCents, amount)
                recordedFeeCount += 1
            }
        }
        val temporalStatus = when {
            activeIndex >= 0 -> SubscriptionStatus.ACTIVE
            upcomingIndex >= 0 -> SubscriptionStatus.UPCOMING
            else -> SubscriptionStatus.EXPIRED
        }
        val index = when {
            activeIndex >= 0 -> activeIndex
            upcomingIndex >= 0 -> upcomingIndex
            else -> periods.lastIndex
        }
        val current = periods.getOrNull(index)
        val duration = current?.let { ChronoUnit.DAYS.between(it.startDate, it.endDate) } ?: 0
        val elapsed = current?.let { ChronoUnit.DAYS.between(it.startDate, today) } ?: 0
        val remaining = when (temporalStatus) {
            SubscriptionStatus.ACTIVE -> ChronoUnit.DAYS.between(today, current!!.endDate)
            SubscriptionStatus.UPCOMING -> ChronoUnit.DAYS.between(today, current!!.startDate)
            else -> 0
        }
        return SubscriptionInspection(
            status = if (project.isStopped) SubscriptionStatus.STOPPED else temporalStatus,
            temporalStatus = temporalStatus,
            currentPeriod = current,
            currentIndex = if (current == null) 0 else index + 1,
            queuedCount = queuedCount,
            remainingDays = remaining.coerceAtLeast(0),
            progress = if (duration > 0) (elapsed.toFloat() / duration).coerceIn(0f, 1f) else 0f,
            firstStart = periods.firstOrNull()?.startDate,
            lastEnd = periods.lastOrNull()?.endDate,
            totalDays = if (periods.isEmpty()) 0 else ChronoUnit.DAYS.between(periods.first().startDate, periods.last().endDate),
            recordedTotalCents = recordedTotalCents.takeIf { recordedFeeCount > 0 },
            recordedFeeCount = recordedFeeCount,
        )
    }

    fun sortProjects(projects: List<SubscriptionProject>, today: LocalDate): List<SubscriptionProject> =
        sortProjects(projects, projects.associate { it.id to inspect(it, today) })

    /** Reuses inspections from the same project/date snapshot; no period traversal occurs here. */
    fun sortProjects(
        projects: List<SubscriptionProject>,
        inspections: Map<Long, SubscriptionInspection>,
    ): List<SubscriptionProject> {
        require(projects.all { it.id in inspections }) { "Every project needs an inspection from the same snapshot" }
        return projects.sortedWith { left, right ->
            val a = requireNotNull(inspections[left.id])
            val b = requireNotNull(inspections[right.id])
            val statusOrder = a.status.ordinal.compareTo(b.status.ordinal)
            if (statusOrder != 0) statusOrder else {
                val dateOrder = when (a.status) {
                    SubscriptionStatus.ACTIVE -> compareValues(a.currentPeriod?.endDate, b.currentPeriod?.endDate)
                    SubscriptionStatus.UPCOMING -> compareValues(a.currentPeriod?.startDate, b.currentPeriod?.startDate)
                    SubscriptionStatus.EXPIRED -> compareValues(b.lastEnd, a.lastEnd)
                    SubscriptionStatus.STOPPED -> 0
                }
                if (dateOrder != 0) dateOrder else left.id.compareTo(right.id)
            }
        }
    }
}
