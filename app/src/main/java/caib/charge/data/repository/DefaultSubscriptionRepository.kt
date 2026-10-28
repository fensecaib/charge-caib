package caib.charge.data.repository

import androidx.room.withTransaction
import caib.charge.data.local.CaibDatabase
import caib.charge.data.local.dao.SubscriptionDao
import caib.charge.data.local.entity.SubscriptionPeriodEntity
import caib.charge.data.local.entity.SubscriptionProjectEntity
import caib.charge.data.local.entity.SubscriptionProjectWithPeriods
import caib.charge.di.DefaultDispatcher
import caib.charge.domain.DomainValidationException
import caib.charge.domain.SubscriptionRules
import caib.charge.domain.model.SubscriptionAppearance
import caib.charge.domain.model.SubscriptionDuration
import caib.charge.domain.model.SubscriptionDurationUnit
import caib.charge.domain.model.SubscriptionPeriod
import caib.charge.domain.model.SubscriptionPeriodDraft
import caib.charge.domain.model.SubscriptionProject
import caib.charge.domain.model.SubscriptionProjectDraft
import caib.charge.domain.repository.SubscriptionRepository
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map

@Singleton
class DefaultSubscriptionRepository @Inject constructor(
    private val database: CaibDatabase,
    private val dao: SubscriptionDao,
    private val clock: Clock,
    @DefaultDispatcher private val computationDispatcher: CoroutineDispatcher,
) : SubscriptionRepository {
    internal constructor(database: CaibDatabase, clock: Clock) :
        this(database, database.subscriptionDao(), clock, Dispatchers.Unconfined)

    override fun observeProjects(): Flow<List<SubscriptionProject>> = dao.observeProjects()
        .map { rows -> rows.map { it.toDomain() } }
        .distinctUntilChanged()
        .flowOn(computationDispatcher)

    override suspend fun getProject(id: Long): SubscriptionProject? = dao.getProject(id)?.toDomain()

    override suspend fun create(draft: SubscriptionProjectDraft): Long = database.withTransaction {
        val name = draft.name.trim()
        val note = draft.note.trim()
        SubscriptionRules.validateProjectText(name, note)
        SubscriptionRules.validatePeriod(draft.firstPeriod)
        val appearance = SubscriptionRules.normalizeAppearance(draft.appearance)
        val now = clock.millis()
        val id = dao.insertProject(SubscriptionProjectEntity(
            name = name, note = note, isStopped = false, revision = 0, createdAt = now, updatedAt = now,
            iconKey = appearance.iconKey, colorKey = appearance.colorKey,
        ))
        dao.insertPeriod(draft.firstPeriod.toEntity(projectId = id, sortOrder = 0, now = now))
        id
    }

    override suspend fun updateProject(id: Long, expectedRevision: Long, name: String, note: String) {
        database.withTransaction {
            val current = requireCurrentProject(id, expectedRevision)
            val trimmedName = name.trim()
            val trimmedNote = note.trim()
            SubscriptionRules.validateProjectText(trimmedName, trimmedNote)
            persistProject(current.copy(name = trimmedName, note = trimmedNote), expectedRevision)
        }
    }

    override suspend fun updateAppearance(id: Long, expectedRevision: Long, appearance: SubscriptionAppearance) {
        database.withTransaction {
            requireCurrentProject(id, expectedRevision)
            val normalized = SubscriptionRules.normalizeAppearance(appearance)
            if (dao.updateAppearance(id, expectedRevision, normalized.iconKey, normalized.colorKey, clock.millis()) != 1) {
                throw stale()
            }
        }
    }

    override suspend fun renew(id: Long, expectedRevision: Long, period: SubscriptionPeriodDraft) {
        database.withTransaction {
            val current = requireCurrent(id, expectedRevision)
            val last = current.periods.maxByOrNull { it.sortOrder }
            if (last == null || period.startDate.toEpochDay() != last.endEpochDay) {
                throw DomainValidationException("本期开始日须接上一期到期日")
            }
            SubscriptionRules.validatePeriod(period)
            val nextOrder = last.sortOrder.toLong() + 1
            if (nextOrder > Int.MAX_VALUE) throw DomainValidationException("周期记录数量超出支持范围")
            dao.insertPeriod(period.toEntity(projectId = id, sortOrder = nextOrder.toInt(), now = clock.millis()))
            persistProject(current.project.copy(isStopped = false), expectedRevision)
        }
    }

    override suspend fun editPeriod(id: Long, expectedRevision: Long, periodId: Long, period: SubscriptionPeriodDraft) {
        database.withTransaction {
            val current = requireCurrent(id, expectedRevision)
            val periods = current.periods.sortedBy { it.sortOrder }
            val index = periods.indexOfFirst { it.id == periodId }
            if (index < 0) throw DomainValidationException("周期记录不存在")
            if (index > 0 && period.startDate.toEpochDay() < periods[index - 1].endEpochDay) {
                throw DomainValidationException("本期开始日不能早于上一期到期日")
            }
            if (index < periods.lastIndex && period.endDate.toEpochDay() > periods[index + 1].startEpochDay) {
                throw DomainValidationException("本期到期日不能晚于下一期开始日")
            }
            SubscriptionRules.validatePeriod(period)
            val existing = periods[index]
            val now = clock.millis()
            val edited = period.toEntity(id, existing.sortOrder, now).copy(
                id = existing.id,
                createdAt = existing.createdAt,
            )
            if (dao.updatePeriod(edited) != 1) throw DomainValidationException("周期记录已不存在")
            persistProject(current.project, expectedRevision)
        }
    }

    override suspend fun deletePeriod(id: Long, expectedRevision: Long, periodId: Long) {
        database.withTransaction {
            val current = requireCurrent(id, expectedRevision)
            if (current.periods.none { it.id == periodId }) throw DomainValidationException("周期记录不存在")
            if (current.periods.size == 1) throw DomainValidationException("这是最后一条周期，请删除整个订阅")
            if (dao.deletePeriod(id, periodId) != 1) throw DomainValidationException("周期记录已不存在")
            persistProject(current.project, expectedRevision)
        }
    }

    override suspend fun setStopped(id: Long, expectedRevision: Long, stopped: Boolean) {
        database.withTransaction {
            val current = requireCurrentProject(id, expectedRevision)
            persistProject(current.copy(isStopped = stopped), expectedRevision)
        }
    }

    override suspend fun delete(id: Long, expectedRevision: Long) {
        database.withTransaction {
            requireCurrentProject(id, expectedRevision)
            if (dao.deleteProject(id, expectedRevision) != 1) throw stale()
        }
    }

    /** Metadata-only writes must not materialize a project's complete renewal history. */
    private suspend fun requireCurrentProject(id: Long, expectedRevision: Long): SubscriptionProjectEntity {
        val current = dao.getProjectRecord(id) ?: throw DomainValidationException("订阅已不存在")
        if (current.revision != expectedRevision) throw stale()
        return current
    }

    private suspend fun requireCurrent(id: Long, expectedRevision: Long): SubscriptionProjectWithPeriods {
        val current = dao.getProject(id) ?: throw DomainValidationException("订阅已不存在")
        if (current.project.revision != expectedRevision) throw stale()
        return current
    }

    private suspend fun persistProject(project: SubscriptionProjectEntity, expectedRevision: Long) {
        if (dao.updateProject(project.id, expectedRevision, project.name, project.note, project.isStopped, clock.millis()) != 1) {
            throw stale()
        }
    }

    private fun stale() = DomainValidationException("订阅已更新，请返回详情后重试")
}

private fun SubscriptionPeriodDraft.toEntity(projectId: Long, sortOrder: Int, now: Long) = SubscriptionPeriodEntity(
    projectId = projectId,
    sortOrder = sortOrder,
    startEpochDay = startDate.toEpochDay(),
    endEpochDay = endDate.toEpochDay(),
    durationValue = duration?.value,
    durationUnit = duration?.unit?.name,
    amountCents = amountCents,
    note = note.trim(),
    createdAt = now,
    updatedAt = now,
)

private fun SubscriptionPeriodEntity.toDomain() = SubscriptionPeriod(
    id = id,
    projectId = projectId,
    startDate = LocalDate.ofEpochDay(startEpochDay),
    endDate = LocalDate.ofEpochDay(endEpochDay),
    duration = if (durationValue == null || durationUnit == null) null else {
        SubscriptionDuration(durationValue, SubscriptionDurationUnit.valueOf(durationUnit))
    },
    amountCents = amountCents,
    note = note,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

private fun SubscriptionProjectWithPeriods.toDomain() = SubscriptionProject(
    id = project.id,
    name = project.name,
    note = project.note,
    isStopped = project.isStopped,
    periods = periods.sortedBy { it.sortOrder }.map { it.toDomain() },
    revision = project.revision,
    createdAt = project.createdAt,
    updatedAt = project.updatedAt,
    appearance = SubscriptionRules.normalizeAppearance(SubscriptionAppearance(project.iconKey, project.colorKey)),
)
