package caib.charge.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSerializable
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.rememberDecoratedNavEntries
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.runtime.serialization.NavKeySerializer
import androidx.savedstate.compose.serialization.serializers.MutableStateSerializer

@Composable
fun rememberTopLevelNavigation(start: NavKey, routes: Set<NavKey>): TopLevelNavigationState {
    val active = rememberSerializable(
        start,
        routes,
        serializer = MutableStateSerializer(NavKeySerializer())
    ) { mutableStateOf(start) }
    val stacks = routes.associateWith { rememberNavBackStack(it) }
    return remember(start, routes) { TopLevelNavigationState(start, active, stacks) }
}

class TopLevelNavigationState(
    val start: NavKey,
    private val activeState: MutableState<NavKey>,
    val stacks: Map<NavKey, NavBackStack<NavKey>>
) {
    var active: NavKey by activeState
    val activeRoute: State<NavKey> = activeState

    val activeStack: NavBackStack<NavKey>
        get() = checkNotNull(stacks[active]) { "未知主导航：$active" }

    val isAtRoot: Boolean
        get() = activeStack.size <= 1

    fun push(key: NavKey) {
        activeStack.add(key)
    }

    fun pop(): Boolean {
        if (activeStack.size <= 1) return false
        activeStack.removeAt(activeStack.lastIndex)
        return true
    }
}

@Composable
fun TopLevelNavigationState.entries(
    provider: (NavKey) -> NavEntry<NavKey>
): List<Pair<NavKey, NavEntry<NavKey>>> = stacks.mapNotNull { (route, stack) ->
    val saveable = rememberSaveableStateHolderNavEntryDecorator<NavKey>()
    val viewModels = rememberViewModelStoreNavEntryDecorator<NavKey>()
    val decorators = remember(saveable, viewModels) { listOf(saveable, viewModels) }
    val decorated = rememberDecoratedNavEntries(stack, decorators, provider)
    decorated.lastOrNull()?.let { route to it }
}
