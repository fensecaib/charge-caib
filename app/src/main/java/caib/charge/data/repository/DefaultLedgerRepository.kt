package caib.charge.data.repository

import androidx.room.withTransaction
import caib.charge.data.local.CaibDatabase
import caib.charge.data.local.dao.CategoryDao
import caib.charge.data.local.dao.LedgerEntryDao
import caib.charge.data.local.entity.LedgerEntryEntity
import caib.charge.domain.DomainValidationException
import caib.charge.domain.Fingerprints
import caib.charge.domain.TextNormalizer
import caib.charge.domain.model.Category
import caib.charge.domain.model.CategoryRanking
import caib.charge.domain.model.ChartGranularity
import caib.charge.domain.model.ChartPeriod
import caib.charge.domain.model.DetailFilter
import caib.charge.domain.model.EntryDateLimits
import caib.charge.domain.model.EntrySource
import caib.charge.domain.model.EntryType
import caib.charge.domain.model.LedgerEntry
import caib.charge.domain.model.LedgerEntryDraft
import caib.charge.domain.model.LedgerEntryPatch
import caib.charge.domain.model.LedgerStats
import caib.charge.domain.model.MoneyLimits
import caib.charge.domain.model.PeriodSummary
import caib.charge.domain.model.RecentNoteSuggestion
import caib.charge.domain.model.TrendPoint
import caib.charge.domain.repository.LedgerRepository
import caib.charge.di.ApplicationScope
import caib.charge.di.DefaultDispatcher
import java.time.Clock
import java.time.LocalDate
import java.time.YearMonth
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.shareIn

@Singleton
class DefaultLedgerRepository private constructor(
    private val ledgerDao: LedgerEntryDao,
    private val categoryDao: CategoryDao,
    private val clock: Clock,
    private val transactionRunner: TransactionRunner,
    applicationScope: CoroutineScope?,
    private val computationDispatcher: CoroutineDispatcher,
) : LedgerRepository {
    @Inject
    constructor(
        database: CaibDatabase,
        ledgerDao: LedgerEntryDao,
        categoryDao: CategoryDao,
        clock: Clock,
        @ApplicationScope applicationScope: CoroutineScope,
        @DefaultDispatcher computationDispatcher: CoroutineDispatcher,
    ) : this(
        ledgerDao = ledgerDao,
        categoryDao = categoryDao,
        clock = clock,
        transactionRunner = TransactionRunner { block -> database.withTransaction { block() } },
        applicationScope = applicationScope,
        computationDispatcher = computationDispatcher,
    )

    /** Keeps instrumentation helpers source-compatible while production receives a shared scope. */
    internal constructor(
        database: CaibDatabase,
        ledgerDao: LedgerEntryDao,
        categoryDao: CategoryDao,
        clock: Clock,
    ) : this(
        ledgerDao = ledgerDao,
        categoryDao = categoryDao,
        clock = clock,
        transactionRunner = TransactionRunner { block -> database.withTransaction { block() } },
        applicationScope = null,
        computationDispatcher = Dispatchers.Unconfined,
    )

    /** Keeps pure JVM repository tests independent from an Android Room runtime. */
    internal constructor(
        ledgerDao: LedgerEntryDao,
        categoryDao: CategoryDao,
        clock: Clock,
    ) : this(
        ledgerDao,
        categoryDao,
        clock,
        TransactionRunner { block -> block() },
        null,
        Dispatchers.Unconfined,
    )

    private val entries = ledgerDao.observeAllWithCategories()
        .map { rows -> rows.map { it.toDomain() } }
        .let { source ->
            applicationScope?.let {
                source.shareIn(it, started = SharingStarted.WhileSubscribed(5_000), replay = 1)
            } ?: source
        }

    override fun observeEntries(filter: DetailFilter): Flow<List<LedgerEntry>> = entries.map { rows ->
        rows.filter { it.matches(filter) }
    }.flowOn(computationDispatcher)

    override fun observeSummary(filter: DetailFilter): Flow<PeriodSummary> = entries.map { rows ->
        var income = 0L
        var expense = 0L
        rows.forEach { entry ->
            if (entry.matches(filter)) {
                if (entry.type == EntryType.INCOME) income += entry.amountCents
                else expense += entry.amountCents
            }
        }
        PeriodSummary(
            incomeCents = income,
            expenseCents = expense,
        )
    }.flowOn(computationDispatcher)

    override fun observeTrend(
        period: ChartPeriod,
        type: EntryType,
        categoryId: String?,
    ): Flow<List<TrendPoint>> = entries.map { rows ->
        val startEpochDay = period.startDate.toEpochDay()
        val endEpochDay = period.endDate.toEpochDay()
        val pointCount = when (period.granularity) {
            ChartGranularity.WEEK -> 7
            ChartGranularity.MONTH -> YearMonth.from(period.startDate).lengthOfMonth()
            ChartGranularity.YEAR -> 12
        }
        val totals = LongArray(pointCount)
        rows.forEach { entry ->
            val epochDay = entry.entryDate.toEpochDay()
            if (entry.type == type &&
                (categoryId == null || entry.category.id == categoryId) &&
                epochDay in startEpochDay..endEpochDay
            ) {
                val index = when (period.granularity) {
                    ChartGranularity.WEEK -> (epochDay - startEpochDay).toInt()
                    ChartGranularity.MONTH -> entry.entryDate.dayOfMonth - 1
                    ChartGranularity.YEAR -> entry.entryDate.monthValue - 1
                }
                totals[index] += entry.amountCents
            }
        }
        when (period.granularity) {
            ChartGranularity.WEEK, ChartGranularity.MONTH -> {
                val dates = if (period.granularity == ChartGranularity.WEEK) {
                    (0L..6L).map(period.startDate::plusDays)
                } else {
                    val month = YearMonth.from(period.startDate)
                    (1..month.lengthOfMonth()).map(month::atDay)
                }
                dates.mapIndexed { index, date ->
                    TrendPoint(
                        index = index + 1,
                        label = if (period.granularity == ChartGranularity.WEEK) {
                            WEEKDAY_LABELS[index]
                        } else {
                            "${index + 1}日"
                        },
                        startEpochDay = date.toEpochDay(),
                        endEpochDay = date.toEpochDay(),
                        amountCents = totals[index],
                    )
                }
            }

            ChartGranularity.YEAR -> {
                (1..12).map { monthNumber ->
                    val month = YearMonth.of(period.startDate.year, monthNumber)
                    TrendPoint(
                        index = monthNumber,
                        label = "${monthNumber}月",
                        startEpochDay = month.atDay(1).toEpochDay(),
                        endEpochDay = month.atEndOfMonth().toEpochDay(),
                        amountCents = totals[monthNumber - 1],
                    )
                }
            }
        }
    }.flowOn(computationDispatcher)

    override fun observeCategoryRanking(
        period: ChartPeriod,
        type: EntryType,
    ): Flow<List<CategoryRanking>> = entries.map { rows ->
        val startEpochDay = period.startDate.toEpochDay()
        val endEpochDay = period.endDate.toEpochDay()
        val totalsByCategory = mutableMapOf<String, RankingAccumulator>()
        var total = 0L
        rows.forEach { entry ->
            if (entry.type == type && entry.entryDate.toEpochDay() in startEpochDay..endEpochDay) {
                val accumulator = totalsByCategory.getOrPut(entry.category.id) {
                    RankingAccumulator(entry.category)
                }
                accumulator.amountCents += entry.amountCents
                total += entry.amountCents
            }
        }
        totalsByCategory.values.map { accumulator ->
            CategoryRanking(
                category = accumulator.category,
                amountCents = accumulator.amountCents,
                percentage = if (total == 0L) 0f else accumulator.amountCents.toFloat() / total.toFloat(),
            )
        }.sortedByDescending { it.amountCents }
    }.flowOn(computationDispatcher)

    override fun observeRecentNotes(
        type: EntryType,
        categoryId: String,
        limit: Int,
    ): Flow<List<RecentNoteSuggestion>> {
        require(limit in 1..100) { "最近备注数量必须在 1 到 100 之间" }
        return entries.map { rows ->
            val latest = mutableMapOf<String, LedgerEntry>()
            rows.forEach { entry ->
                if (
                    entry.type == type &&
                    entry.category.id == categoryId &&
                    entry.normalizedNote.isNotBlank()
                ) {
                    val previous = latest[entry.normalizedNote]
                    if (
                        previous == null ||
                        entry.createdAt > previous.createdAt ||
                        (entry.createdAt == previous.createdAt && entry.id > previous.id)
                    ) {
                        latest[entry.normalizedNote] = entry
                    }
                }
            }
            latest.values.asSequence()
                .sortedWith(
                    compareByDescending<LedgerEntry> { it.createdAt }
                        .thenByDescending { it.id }
                )
                .take(limit)
                .map {
                    RecentNoteSuggestion(
                        note = it.note,
                        normalizedNote = it.normalizedNote,
                        lastCreatedAt = it.createdAt,
                    )
                }
                .toList()
        }.flowOn(computationDispatcher)
    }

    override fun observeStats(): Flow<LedgerStats> = entries.map { rows ->
        val days = mutableSetOf<Long>()
        rows.forEach { days += it.entryDate.toEpochDay() }
        LedgerStats(rows.size, days.size)
    }.flowOn(computationDispatcher)

    override suspend fun getEntry(id: Long): LedgerEntry? = ledgerDao.getWithCategory(id)?.toDomain()

    override suspend fun getMostRecentActiveCategoryId(type: EntryType): String? =
        ledgerDao.getMostRecentActiveCategoryId(type)

    override suspend fun upsert(draft: LedgerEntryDraft): Long {
        var savedId: Long? = null
        transactionRunner.run {
            val existing = draft.id?.let { ledgerDao.getById(it) }
            if (draft.id != null && existing == null) throw DomainValidationException("账目不存在")
            savedId = persistDraft(draft, existing)
        }
        return checkNotNull(savedId) { "账目保存事务未返回结果" }
    }

    override suspend fun update(id: Long, patch: LedgerEntryPatch): Long {
        var savedId: Long? = null
        transactionRunner.run {
            val existing = ledgerDao.getById(id) ?: throw DomainValidationException("账目不存在")
            val draft = when (patch) {
                is LedgerEntryPatch.AmountAndDate -> LedgerEntryDraft(
                    id = id,
                    type = existing.type,
                    categoryId = existing.categoryId,
                    amountCents = patch.amountCents,
                    entryDate = patch.entryDate,
                    note = existing.note,
                )
                is LedgerEntryPatch.Note -> LedgerEntryDraft(
                    id = id,
                    type = existing.type,
                    categoryId = existing.categoryId,
                    amountCents = existing.amountCents,
                    entryDate = LocalDate.ofEpochDay(existing.entryDateEpochDay),
                    note = patch.note,
                )
                is LedgerEntryPatch.Category -> LedgerEntryDraft(
                    id = id,
                    type = existing.type,
                    categoryId = patch.categoryId,
                    amountCents = existing.amountCents,
                    entryDate = LocalDate.ofEpochDay(existing.entryDateEpochDay),
                    note = existing.note,
                )
            }
            savedId = persistDraft(draft, existing)
        }
        return checkNotNull(savedId) { "账目更新事务未返回结果" }
    }

    private suspend fun persistDraft(
        draft: LedgerEntryDraft,
        existing: LedgerEntryEntity?,
    ): Long {
        val note = draft.note.trim()
        validateDraft(draft, note)
        val category = categoryDao.getById(draft.categoryId)
            ?: throw DomainValidationException("分类不存在")
        if (category.type != draft.type) throw DomainValidationException("分类与收支类型不匹配")
        if (category.isArchived && existing?.categoryId != category.id) {
            throw DomainValidationException("不能使用已归档分类新建账目")
        }

        val normalizedNote = TextNormalizer.normalize(note)
        val now = clock.millis()
        val entity = LedgerEntryEntity(
            id = existing?.id ?: 0,
            type = draft.type,
            categoryId = category.id,
            amountCents = draft.amountCents,
            entryDateEpochDay = draft.entryDate.toEpochDay(),
            note = note,
            normalizedNote = normalizedNote,
            source = existing?.source ?: EntrySource.MANUAL,
            importBatchId = existing?.importBatchId,
            contentFingerprint = Fingerprints.entry(
                draft.entryDate,
                draft.type,
                draft.amountCents,
                category.id,
                normalizedNote,
            ),
            createdAt = existing?.createdAt ?: now,
            updatedAt = now,
        )
        return if (existing == null) {
            ledgerDao.insert(entity)
        } else {
            if (ledgerDao.update(entity) != 1) throw DomainValidationException("账目已不存在")
            entity.id
        }
    }

    override suspend fun delete(id: Long): LedgerEntry? {
        var deleted: LedgerEntry? = null
        transactionRunner.run {
            val row = ledgerDao.getWithCategory(id) ?: return@run
            if (ledgerDao.delete(row.entry) != 1) throw DomainValidationException("账目已不存在")
            deleted = row.toDomain()
        }
        return deleted
    }

    override suspend fun restore(entry: LedgerEntry) {
        transactionRunner.run {
            if (ledgerDao.getById(entry.id) != null) throw DomainValidationException("无法恢复：账目 ID 已存在")
            val category = categoryDao.getById(entry.category.id)
                ?: throw DomainValidationException("无法恢复：分类不存在")
            if (category.type != entry.type) throw DomainValidationException("无法恢复：分类与收支类型不匹配")
            val trimmedNote = entry.note.trim()
            validateDraft(
                LedgerEntryDraft(
                    id = entry.id,
                    type = entry.type,
                    categoryId = category.id,
                    amountCents = entry.amountCents,
                    entryDate = entry.entryDate,
                    note = trimmedNote,
                ),
                trimmedNote,
            )
            val normalizedNote = TextNormalizer.normalize(trimmedNote)
            val expectedFingerprint = Fingerprints.entry(
                entry.entryDate,
                entry.type,
                entry.amountCents,
                category.id,
                normalizedNote,
            )
            if (entry.note != trimmedNote || entry.normalizedNote != normalizedNote || entry.fingerprint != expectedFingerprint) {
                throw DomainValidationException("无法恢复：账目内容校验失败")
            }
            if (ledgerDao.insert(entry.toEntity()) != entry.id) {
                throw DomainValidationException("无法恢复：账目 ID 不一致")
            }
        }
    }

    private fun validateDraft(draft: LedgerEntryDraft, trimmedNote: String) {
        if (draft.amountCents !in MoneyLimits.MIN_CENTS..MoneyLimits.MAX_CENTS) {
            throw DomainValidationException("金额必须在 0.01 到 999,999,999.99 元之间")
        }
        if (draft.entryDate.isBefore(EntryDateLimits.MIN_DATE)) {
            throw DomainValidationException("记账日期不能早于 1900-01-01")
        }
        if (draft.entryDate.isAfter(LocalDate.now(clock))) throw DomainValidationException("记账日期不能晚于今天")
        if (trimmedNote.codePointCount(0, trimmedNote.length) > 100) throw DomainValidationException("备注不能超过 100 字")
    }

    private fun LedgerEntry.matches(filter: DetailFilter): Boolean {
        val day = entryDate.toEpochDay()
        return (filter.startEpochDay == null || day >= filter.startEpochDay) &&
            (filter.endEpochDay == null || day <= filter.endEpochDay) &&
            (filter.type == null || type == filter.type) &&
            (filter.categoryId == null || category.id == filter.categoryId)
    }

    private data class RankingAccumulator(
        val category: Category,
        var amountCents: Long = 0,
    )

    private companion object {
        val WEEKDAY_LABELS = listOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")
    }
}

private fun interface TransactionRunner {
    suspend fun run(block: suspend () -> Unit)
}
