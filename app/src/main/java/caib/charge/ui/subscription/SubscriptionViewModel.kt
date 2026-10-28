package caib.charge.ui.subscription

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import caib.charge.domain.SubscriptionRules
import caib.charge.domain.DomainValidationException
import caib.charge.di.DefaultDispatcher
import caib.charge.domain.model.SubscriptionPeriodDraft
import caib.charge.domain.model.SubscriptionInspection
import caib.charge.domain.model.SubscriptionProject
import caib.charge.domain.model.SubscriptionProjectDraft
import caib.charge.domain.model.SubscriptionAppearance
import caib.charge.domain.repository.SubscriptionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import java.time.Duration
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.retryWhen
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class SubscriptionEditorMode { NEW, RENEW, EDIT_PERIOD, EDIT_PROJECT }

data class SubscriptionEditorInput(
    val name: String,
    val note: String,
    val period: SubscriptionPeriodDraft?,
)

sealed interface SubscriptionEvent {
    data class Saved(val projectId: Long, val expectedRevision: Long = 0) : SubscriptionEvent
    data class Deleted(val projectId: Long) : SubscriptionEvent
}

/** Rows, calendar date and summaries are published as one background-computed snapshot. */
data class SubscriptionOverview(
    val projects: List<SubscriptionProject> = emptyList(),
    val inspections: Map<Long, SubscriptionInspection> = emptyMap(),
    val today: LocalDate,
    val loaded: Boolean = false,
)

@HiltViewModel
class SubscriptionViewModel @Inject constructor(
    private val repository: SubscriptionRepository,
    private val clock: Clock,
    @DefaultDispatcher private val computationDispatcher: CoroutineDispatcher = Dispatchers.Default,
) : ViewModel() {
    private val _today = MutableStateFlow(LocalDate.now(clock))
    val today = _today.asStateFlow()
    private val _busy = MutableStateFlow(false)
    val busy = _busy.asStateFlow()
    private val _error = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()
    private val _readError = MutableStateFlow<String?>(null)
    val readError = _readError.asStateFlow()
    private val eventChannel = Channel<SubscriptionEvent>(Channel.BUFFERED)
    val events = eventChannel.receiveAsFlow()
    val overview = combine(
        repository.observeProjects().onEach { _readError.value = null }.retryWhen { cause, attempt ->
                if (cause is CancellationException) return@retryWhen false
                _readError.value = "订阅读取失败，正在重试"
                delay((1_000L * (attempt + 1).coerceAtMost(5)))
                true
            },
        _today,
    ) { rows, date ->
        // Keep only pure derivation off Main. flowOn adjacent to stateIn can fuse the
        // sharing coroutine onto Default, including synchronous UI collector resumes.
        withContext(computationDispatcher) {
            val inspections = rows.associate { it.id to SubscriptionRules.inspect(it, date) }
            val sorted = SubscriptionRules.sortProjects(rows, inspections)
            SubscriptionOverview(sorted, inspections, date, loaded = true)
        }
    }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SubscriptionOverview(today = _today.value))

    private var midnightJob: Job? = null

    init { refreshToday() }

    fun refreshToday() {
        val now = clock.instant()
        val zone = clock.zone
        _today.value = now.atZone(zone).toLocalDate()
        // Resuming in a new zone must replace the old zone's midnight deadline.
        midnightJob?.cancel()
        midnightJob = viewModelScope.launch {
            var anchorInstant = now
            var anchorZone = zone
            while (true) {
                val next = anchorInstant.atZone(anchorZone).toLocalDate().plusDays(1)
                    .atStartOfDay(anchorZone).toInstant()
                delay(Duration.between(anchorInstant, next).toMillis().coerceAtLeast(1_000))
                anchorInstant = clock.instant()
                anchorZone = clock.zone
                // Do not call refreshToday here: the ticker must not cancel itself.
                _today.value = anchorInstant.atZone(anchorZone).toLocalDate()
            }
        }
    }
    fun clearError() { _error.value = null }

    fun save(mode: SubscriptionEditorMode, project: SubscriptionProject?, periodId: Long?, input: SubscriptionEditorInput) {
        mutate {
            // An edit is bound to the record selected when opening the editor.
            // A missing/restored target must fail rather than fall through to renewal.
            if (mode == SubscriptionEditorMode.EDIT_PERIOD && (periodId == null || project?.periods?.none { it.id == periodId } != false)) {
                throw DomainValidationException("本期记录已不存在，请返回详情重试")
            }
            if (mode != SubscriptionEditorMode.EDIT_PERIOD && periodId != null) {
                throw DomainValidationException("编辑目标已变更，请重新打开")
            }
            val id = if (mode == SubscriptionEditorMode.NEW) {
                repository.create(SubscriptionProjectDraft(input.name.trim(), firstPeriod = requireNotNull(input.period)))
            } else {
                val existing = requireNotNull(project) { "订阅已不存在" }
                when (mode) {
                    SubscriptionEditorMode.RENEW -> repository.renew(existing.id, existing.revision, requireNotNull(input.period))
                    SubscriptionEditorMode.EDIT_PERIOD -> repository.editPeriod(existing.id, existing.revision, requireNotNull(periodId), requireNotNull(input.period))
                    SubscriptionEditorMode.EDIT_PROJECT -> repository.updateProject(existing.id, existing.revision, input.name.trim(), input.note)
                    SubscriptionEditorMode.NEW -> Unit
                }
                existing.id
            }
            eventChannel.send(SubscriptionEvent.Saved(id, if (mode == SubscriptionEditorMode.NEW) 0 else requireNotNull(project).revision + 1))
        }
    }

    fun toggleStopped(project: SubscriptionProject) = mutate {
        repository.setStopped(project.id, project.revision, !project.isStopped)
        eventChannel.send(SubscriptionEvent.Saved(project.id, project.revision + 1))
    }

    fun updateAppearance(project: SubscriptionProject, appearance: SubscriptionAppearance) = mutate {
        repository.updateAppearance(project.id, project.revision, appearance)
        eventChannel.send(SubscriptionEvent.Saved(project.id, project.revision + 1))
    }

    fun deletePeriod(project: SubscriptionProject, periodId: Long) = mutate {
        repository.deletePeriod(project.id, project.revision, periodId)
        eventChannel.send(SubscriptionEvent.Saved(project.id, project.revision + 1))
    }

    fun delete(project: SubscriptionProject) = mutate {
        repository.delete(project.id, project.revision)
        eventChannel.send(SubscriptionEvent.Deleted(project.id))
    }

    private fun mutate(block: suspend () -> Unit) {
        if (_busy.value) return
        _busy.value = true
        _error.value = null
        viewModelScope.launch {
            try { block() }
            catch (cause: CancellationException) { throw cause }
            catch (cause: DomainValidationException) { _error.value = cause.message ?: "保存失败，请重试" }
            catch (_: Exception) { _error.value = "保存失败，请重试" }
            finally { _busy.value = false }
        }
    }
}
