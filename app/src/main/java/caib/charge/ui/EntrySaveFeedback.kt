package caib.charge.ui

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import caib.charge.ui.components.CaibSnackbarHost
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

internal data class EntrySaveConfirmation(val generation: Long, val text: String)

/** Accessed on the main thread, like the editor's save and navigation callbacks. */
internal class EntrySaveFeedback {
    private var generation = 0L
    private val pending = MutableStateFlow<EntrySaveConfirmation?>(null)
    val confirmation = pending.asStateFlow()

    fun beginInteraction() {
        generation += 1
        pending.value = null
    }

    fun captureGeneration(): Long = generation

    fun saved(expectedGeneration: Long, text: String) {
        if (generation == expectedGeneration) {
            pending.value = EntrySaveConfirmation(generation, text)
        }
    }

    fun dismiss(expected: EntrySaveConfirmation) {
        if (pending.value === expected) pending.value = null
    }
}

/** Removing this host also removes its exit animation before the next editor is drawn. */
@Composable
internal fun EntrySaveSnackbar(
    confirmation: EntrySaveConfirmation,
    onDismiss: (EntrySaveConfirmation) -> Unit,
) {
    val host = remember(confirmation) { SnackbarHostState() }
    LaunchedEffect(confirmation) {
        host.showSnackbar(confirmation.text)
        onDismiss(confirmation)
    }
    CaibSnackbarHost(host)
}
