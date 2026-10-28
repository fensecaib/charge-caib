package caib.charge.domain.model

import java.time.LocalDate

enum class SubscriptionDurationUnit(val displayName: String) {
    DAY("天"), MONTH("个月"), YEAR("年"),
}

data class SubscriptionDuration(val value: Int, val unit: SubscriptionDurationUnit)

enum class SubscriptionCreationMode { START_DURATION, END_DURATION }

data class SubscriptionDateRange(val startDate: LocalDate, val endDate: LocalDate)

/** Persist only stable preset keys; rendered colors remain a theme concern. */
data class SubscriptionAppearance(val iconKey: String = "auto", val colorKey: Int = 0) {
    companion object {
        val ICON_KEYS: Set<String> = setOf(
            "auto", "video", "chat", "play", "music", "gift", "cloud", "notebook", "palette",
            "game", "book", "headphones", "code", "globe", "storage", "heart", "coffee",
        )
    }
}

/** End dates are exclusive: a period expires at the beginning of [endDate]. */
data class SubscriptionPeriod(
    val id: Long,
    val projectId: Long,
    val startDate: LocalDate,
    val endDate: LocalDate,
    val duration: SubscriptionDuration?,
    val amountCents: Long?,
    val note: String,
    val createdAt: Long,
    val updatedAt: Long,
)

data class SubscriptionProject(
    val id: Long,
    val name: String,
    val note: String,
    val isStopped: Boolean,
    /** Always chronological without overlaps; deleting history can leave gaps. */
    val periods: List<SubscriptionPeriod>,
    val revision: Long,
    val createdAt: Long,
    val updatedAt: Long,
    val appearance: SubscriptionAppearance = SubscriptionAppearance(),
)

data class SubscriptionPeriodDraft(
    val startDate: LocalDate,
    val endDate: LocalDate,
    val duration: SubscriptionDuration? = null,
    val amountCents: Long? = null,
    val note: String = "",
)

data class SubscriptionProjectDraft(
    val name: String,
    val note: String = "",
    val firstPeriod: SubscriptionPeriodDraft,
    val appearance: SubscriptionAppearance = SubscriptionAppearance(),
)

enum class SubscriptionStatus(val displayName: String) {
    ACTIVE("进行中"), UPCOMING("尚未开始"), EXPIRED("已到期"), STOPPED("不再续费"),
}

data class SubscriptionInspection(
    val status: SubscriptionStatus,
    val temporalStatus: SubscriptionStatus,
    val currentPeriod: SubscriptionPeriod?,
    val currentIndex: Int,
    val queuedCount: Int,
    val remainingDays: Long,
    val progress: Float,
    val firstStart: LocalDate?,
    val lastEnd: LocalDate?,
    val totalDays: Long,
    /** Null means no period has a recorded fee; recorded zero remains zero. */
    val recordedTotalCents: Long?,
    val recordedFeeCount: Int,
)

object SubscriptionLimits {
    val MIN_DATE: LocalDate = LocalDate.of(1900, 1, 1)
    val MAX_DATE: LocalDate = LocalDate.of(9999, 12, 31)
    const val MAX_NAME_LENGTH = 60
    const val MAX_NOTE_LENGTH = 100
}
