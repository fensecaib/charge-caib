package caib.charge.domain.repository

import caib.charge.domain.model.SubscriptionPeriodDraft
import caib.charge.domain.model.SubscriptionAppearance
import caib.charge.domain.model.SubscriptionProject
import caib.charge.domain.model.SubscriptionProjectDraft
import kotlinx.coroutines.flow.Flow

/** Subscriptions are independent from ledger entries and their transfer formats. */
interface SubscriptionRepository {
    fun observeProjects(): Flow<List<SubscriptionProject>>
    suspend fun getProject(id: Long): SubscriptionProject?
    suspend fun create(draft: SubscriptionProjectDraft): Long
    suspend fun updateProject(id: Long, expectedRevision: Long, name: String, note: String)
    suspend fun updateAppearance(id: Long, expectedRevision: Long, appearance: SubscriptionAppearance)
    suspend fun renew(id: Long, expectedRevision: Long, period: SubscriptionPeriodDraft)
    /** Boundaries may leave gaps but never overlap; editing never shifts other paid periods. */
    suspend fun editPeriod(id: Long, expectedRevision: Long, periodId: Long, period: SubscriptionPeriodDraft)
    /** Removing the final period requires the separate whole-project deletion flow. */
    suspend fun deletePeriod(id: Long, expectedRevision: Long, periodId: Long)
    suspend fun setStopped(id: Long, expectedRevision: Long, stopped: Boolean)
    suspend fun delete(id: Long, expectedRevision: Long)
}
