package caib.charge.ui.theme

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import caib.charge.domain.model.CaibThemeMode
import caib.charge.domain.repository.ThemeRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

sealed interface ThemeUiState {
    data object Loading : ThemeUiState
    data class Ready(val mode: CaibThemeMode) : ThemeUiState
}

@HiltViewModel
class ThemeViewModel @Inject constructor(
    private val repository: ThemeRepository,
) : ViewModel() {
    private data class PendingSelection(val id: Long, val mode: CaibThemeMode)

    private val nextSelectionId = AtomicLong(0L)
    private val pendingSelection = MutableStateFlow<PendingSelection?>(null)
    private val persistenceMutex = Mutex()

    val uiState: StateFlow<ThemeUiState> = combine(
        repository.themeMode,
        pendingSelection,
    ) { storedMode, pending ->
        ThemeUiState.Ready(pending?.mode ?: storedMode)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = ThemeUiState.Loading,
    )

    fun selectThemeMode(mode: CaibThemeMode) {
        val currentMode = (uiState.value as? ThemeUiState.Ready)?.mode
        if (currentMode == mode && pendingSelection.value == null) return

        val request = PendingSelection(nextSelectionId.incrementAndGet(), mode)
        pendingSelection.value = request
        viewModelScope.launch {
            persistenceMutex.withLock {
                if (pendingSelection.value != request) return@withLock
                try {
                    repository.setThemeMode(mode)
                } catch (cancellation: CancellationException) {
                    throw cancellation
                } catch (_: Exception) {
                    // Clearing the optimistic value below restores the last persisted mode.
                } finally {
                    pendingSelection.compareAndSet(expect = request, update = null)
                }
            }
        }
    }
}
