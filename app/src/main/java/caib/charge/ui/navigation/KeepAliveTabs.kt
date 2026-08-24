package caib.charge.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey

@Composable
fun KeepAliveTabs(
    entries: List<Pair<NavKey, NavEntry<NavKey>>>,
    activeRoute: State<NavKey>,
    modifier: Modifier = Modifier
) {
    val active = activeRoute.value
    Box(modifier) {
        entries.firstOrNull { (route, _) -> route == active }?.let { (route, entry) ->
            key(route) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                ) { entry.Content() }
            }
        }
    }
}
