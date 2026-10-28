package caib.charge.ui.subscription

import androidx.compose.foundation.focusGroup
import androidx.compose.material3.LocalRippleConfiguration
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.hideFromAccessibility
import androidx.compose.ui.unit.constrainHeight
import androidx.compose.ui.unit.constrainWidth

private class PaneFocusState { var hasFocus = false }

/**
 * Retain one screen's composition/measurement caches without an invisible full-screen layer.
 * The surrounding Box supplies loose minimum constraints: an inactive host is 0 x 0 and
 * never measures or places its children. Reactivation measures with the current constraints,
 * so size/font/inset changes invalidate the normal Compose measurement caches correctly.
 * Platform windows must additionally be gated by the screen's [active] flag.
 */
@Composable
internal fun SubscriptionPaneHost(
    active: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val focusManager = LocalFocusManager.current
    val inheritedRipple = LocalRippleConfiguration.current
    val focus = remember { PaneFocusState() }
    LaunchedEffect(active) {
        // Read the group's current focus here, not a captured flag. Never clear focus
        // that has already moved to the active editor or another pane.
        if (!active && focus.hasFocus) focusManager.clearFocus(force = true)
    }
    val semantics = if (active) Modifier else Modifier.clearAndSetSemantics { hideFromAccessibility() }
    Layout(
        modifier = modifier.then(semantics)
            .onFocusChanged { focus.hasFocus = it.hasFocus }
            .focusProperties { onEnter = { if (!active) cancelFocusChange() } }
            .focusGroup(),
        content = {
            // Unplacing does not detach Android's RippleHostView: its drawable can
            // keep invalidating a retained layer. Null undelegates that native ripple
            // while hidden; active panes inherit the exact parent value, including null.
            CompositionLocalProvider(LocalRippleConfiguration provides if (active) inheritedRipple else null) {
                content()
            }
        },
    ) { children, constraints ->
        if (!active) {
            layout(0, 0) { }
        } else {
            val placeables = children.map { it.measure(constraints) }
            val width = constraints.constrainWidth(placeables.maxOfOrNull { it.width } ?: 0)
            val height = constraints.constrainHeight(placeables.maxOfOrNull { it.height } ?: 0)
            layout(width, height) { placeables.forEach { it.place(0, 0) } }
        }
    }
}
