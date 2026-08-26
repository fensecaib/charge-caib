package caib.charge.domain.repository

import caib.charge.domain.model.CategoryRanking
import caib.charge.domain.model.ChartPeriod
import caib.charge.domain.model.DetailFilter
import caib.charge.domain.model.EntryType
import caib.charge.domain.model.LedgerEntry
import caib.charge.domain.model.LedgerEntryDraft
import caib.charge.domain.model.LedgerEntryPatch
import caib.charge.domain.model.LedgerStats
import caib.charge.domain.model.PeriodSummary
import caib.charge.domain.model.RecentNoteSuggestion
import caib.charge.domain.model.TrendPoint
import kotlinx.coroutines.flow.Flow

interface LedgerRepository {
    fun observeEntries(filter: DetailFilter = DetailFilter()): Flow<List<LedgerEntry>>
    fun observeSummary(filter: DetailFilter = DetailFilter()): Flow<PeriodSummary>
    fun observeTrend(period: ChartPeriod, type: EntryType, categoryId: String? = null): Flow<List<TrendPoint>>
    fun observeCategoryRanking(period: ChartPeriod, type: EntryType): Flow<List<CategoryRanking>>
    fun observeRecentNotes(
        type: EntryType,
        categoryId: String,
        limit: Int = 10,
    ): Flow<List<RecentNoteSuggestion>>
    fun observeStats(): Flow<LedgerStats>
    suspend fun getEntry(id: Long): LedgerEntry?
    suspend fun getMostRecentActiveCategoryId(type: EntryType): String?
    suspend fun upsert(draft: LedgerEntryDraft): Long
    suspend fun update(id: Long, patch: LedgerEntryPatch): Long
    suspend fun delete(id: Long): LedgerEntry?
    suspend fun restore(entry: LedgerEntry)
}
