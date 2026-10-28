package caib.charge.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import caib.charge.ui.theme.successColor
import caib.charge.ui.theme.successContentColor
import kotlin.math.hypot
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/** Fast motion tokens chosen from the focused entry prototype. */
internal object EntryMotionTokens {
    const val PressMillis = 80
    const val LaunchMillis = 350
    const val ReturnCoverMillis = 60
    const val ReturnRevealMillis = 70
    const val ReturnMillis = 160
    const val ReturnReadinessTimeoutMillis = 1_200
    const val AmountMillis = 150
    const val InsertMillis = 220
}

/**
 * Draw-phase container hand-off from the warm central action to a precomposed editor preview.
 * The real editor is pushed only after the preview is fully visible, and the preview remains until
 * the destination reports its first layout. This avoids exposing an empty transition surface while
 * the destination performs its first composition.
 */
@Composable
internal fun EntryLaunchOverlay(
    sessionId: Int,
    destinationReady: Boolean,
    onCovered: () -> Unit,
    onFinished: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val coverage = remember(sessionId) { Animatable(0f) }
    var previewPlaced by remember(sessionId) { mutableStateOf(false) }
    var covered by remember(sessionId) { mutableStateOf(false) }
    val currentOnCovered by rememberUpdatedState(onCovered)
    val currentOnFinished by rememberUpdatedState(onFinished)
    val density = LocalDensity.current
    val navigationBottom = WindowInsets.navigationBars.getBottom(density)
    val actionColor = MaterialTheme.colorScheme.tertiary
    val destinationColor = MaterialTheme.colorScheme.background
    val seedRadius = with(density) { 28.dp.toPx() }
    val fabCenterOffset = with(density) { 40.dp.toPx() }
    val contentTravel = with(density) { 6.dp.toPx() }

    LaunchedEffect(sessionId, previewPlaced) {
        if (!previewPlaced) return@LaunchedEffect
        coverage.snapTo(0f)
        coverage.animateTo(
            targetValue = 1f,
            animationSpec = tween(
                durationMillis = EntryMotionTokens.LaunchMillis,
                easing = FastOutSlowInEasing,
            ),
        )
        covered = true
        currentOnCovered()
    }

    LaunchedEffect(sessionId, covered, destinationReady) {
        if (!covered || !destinationReady) return@LaunchedEffect
        // Keep the already-rendered preview for one more frame so the destination is drawn behind it.
        withFrameNanos { }
        currentOnFinished()
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .testTag("entry_launch_overlay")
            .clearAndSetSemantics {
                contentDescription = "正在打开新增记账"
                stateDescription = if (previewPlaced) "记账页面已准备" else "正在准备记账页面"
            }
            .pointerInput(sessionId) {
                awaitPointerEventScope {
                    while (true) awaitPointerEvent().changes.forEach { it.consume() }
                }
            },
    ) {
        Canvas(Modifier.fillMaxSize().testTag("entry_launch_surface")) {
            val center = Offset(
                x = size.width / 2f,
                y = size.height - navigationBottom.toFloat() - fabCenterOffset,
            )
            val farthestX = maxOf(center.x, size.width - center.x)
            val farthestY = maxOf(center.y, size.height - center.y)
            val coverRadius = hypot(farthestX, farthestY)
            val progress = coverage.value
            val revealAlpha = (progress / 0.14f).coerceIn(0f, 1f)
            val contentReveal = ((progress - 0.68f) / 0.26f).coerceIn(0f, 1f)
            drawCircle(
                color = lerp(
                    actionColor,
                    destinationColor,
                    (progress / 0.4f).coerceIn(0f, 1f),
                ),
                radius = seedRadius + (coverRadius - seedRadius) * progress,
                center = center,
                alpha = revealAlpha,
            )
            // Cross-fade the remaining corners instead of clipping a full editor tree every frame.
            // The expanding cyan/yellow container remains the visual anchor, while this draw-only
            // wash avoids the expensive full-screen path mask seen on Android 12 class hardware.
            if (contentReveal > 0f) {
                drawRect(destinationColor, alpha = contentReveal)
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .onGloballyPositioned {
                    if (!previewPlaced) previewPlaced = true
                }
                .testTag("entry_launch_preview"),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        val reveal = ((coverage.value - 0.68f) / 0.26f).coerceIn(0f, 1f)
                        alpha = reveal
                        translationY = contentTravel * (1f - reveal)
                    },
                content = content,
            )
        }
    }
}

/**
 * Shared draw state for the reverse container transform. Both values are consumed from Canvas,
 * so the return does not recompose or remeasure the editor on animation frames.
 */
@Stable
internal class EntryReturnMotionState internal constructor() {
    internal val veil = Animatable(0f)
    internal val pulse = Animatable(1f)
    internal var collapsed by mutableStateOf(false)
}

@Composable
internal fun rememberEntryReturnMotionState(sessionId: Int?): EntryReturnMotionState? =
    remember(sessionId) { sessionId?.let { EntryReturnMotionState() } }

/**
 * Covers the real editor with the theme background in 60ms, then retracts that simple surface to
 * the central action over the restored root. The reveal phase is only 70ms and the remaining motion
 * is a small CTA pulse, avoiding a long full-screen redraw on Android 12 class hardware.
 */
@Composable
internal fun EntryReturnOverlay(
    sessionId: Int,
    state: EntryReturnMotionState,
    readyToAnimate: Boolean,
    destinationReady: Boolean,
    onCollapsed: () -> Unit,
    onFinished: () -> Unit,
    modifier: Modifier = Modifier,
    onReadinessTimeout: () -> Unit = {},
) {
    val currentOnCollapsed by rememberUpdatedState(onCollapsed)
    val currentOnFinished by rememberUpdatedState(onFinished)
    val currentOnReadinessTimeout by rememberUpdatedState(onReadinessTimeout)
    val currentReadyToAnimate = rememberUpdatedState(readyToAnimate)
    val density = LocalDensity.current
    val navigationBottom = WindowInsets.navigationBars.getBottom(density)
    val navigationBottomPadding = with(density) { navigationBottom.toDp() }
    val actionColor = MaterialTheme.colorScheme.tertiary
    val destinationColor = MaterialTheme.colorScheme.background
    val seedRadius = with(density) { 28.dp.toPx() }

    LaunchedEffect(sessionId) {
        // Latch the first ready signal for this session. Later IME inset jitter must not cancel and
        // restart a cover that is already running.
        val becameReady = withTimeoutOrNull(EntryMotionTokens.ReturnReadinessTimeoutMillis.toLong()) {
            snapshotFlow { currentReadyToAnimate.value }.first { it }
        } != null
        if (!becameReady) {
            // Insets delivery can occasionally stall after a third-party IME hides. Never leave
            // the full-screen input blocker installed indefinitely: keep the editor in place and
            // let the user retry once the bounded readiness window expires.
            currentOnReadinessTimeout()
            return@LaunchedEffect
        }
        state.collapsed = false
        state.veil.snapTo(0f)
        state.pulse.snapTo(1f)
        state.veil.animateTo(
            targetValue = 1f,
            animationSpec = tween(
                durationMillis = EntryMotionTokens.ReturnCoverMillis,
                easing = FastOutSlowInEasing,
            ),
        )
        state.collapsed = true
        currentOnCollapsed()
    }

    LaunchedEffect(sessionId, state.collapsed, destinationReady) {
        if (!state.collapsed || !destinationReady) return@LaunchedEffect
        // Root and bottom bar are now measured behind an opaque, theme-matched surface. Reveal
        // them quickly while the tiny endpoint settles onto the real 56dp CTA.
        withFrameNanos { }
        coroutineScope {
            launch {
                state.veil.animateTo(
                    targetValue = 0f,
                    animationSpec = tween(EntryMotionTokens.ReturnRevealMillis),
                )
            }
            state.pulse.animateTo(
                targetValue = 0f,
                animationSpec = tween(
                    durationMillis = EntryMotionTokens.ReturnMillis,
                    easing = FastOutSlowInEasing,
                ),
            )
        }
        withFrameNanos { }
        currentOnFinished()
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .testTag("entry_return_overlay")
            .clearAndSetSemantics {
                contentDescription = "正在返回记账首页"
                stateDescription = if (readyToAnimate) "正在收起新增记账" else "正在收起键盘"
            }
            .pointerInput(sessionId) {
                awaitPointerEventScope {
                    while (true) awaitPointerEvent().changes.forEach { it.consume() }
                }
            },
    ) {
        Canvas(Modifier.fillMaxSize().testTag("entry_return_surface")) {
            drawRect(destinationColor, alpha = state.veil.value)
        }
        if (state.collapsed) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = navigationBottomPadding),
                contentAlignment = Alignment.BottomCenter,
            ) {
                Canvas(Modifier.size(80.dp).testTag("entry_return_endpoint")) {
                    drawCircle(
                        color = actionColor,
                        radius = seedRadius * (1f + 0.32f * state.pulse.value),
                        center = center,
                    )
                }
            }
        }
    }
}

/** Short non-blocking success acknowledgment shown after the Room write succeeds. */
@Composable
internal fun EntrySaveSuccessOverlay(
    sessionId: Int,
    onFinished: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scale = remember(sessionId) { Animatable(0.68f) }
    val alpha = remember(sessionId) { Animatable(0f) }
    val currentOnFinished by rememberUpdatedState(onFinished)
    val scrim = MaterialTheme.colorScheme.scrim

    LaunchedEffect(sessionId) {
        scale.snapTo(0.68f)
        alpha.snapTo(0f)
        coroutineScope {
            val scaleJob = launch {
                scale.animateTo(
                    targetValue = 1f,
                    animationSpec = spring(
                        dampingRatio = 0.68f,
                        stiffness = Spring.StiffnessHigh,
                    ),
                )
            }
            alpha.animateTo(1f, tween(90))
            alpha.animateTo(0f, tween(80, delayMillis = 110))
            scaleJob.cancel()
        }
        currentOnFinished()
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .drawBehind { drawRect(scrim, alpha = 0.06f * alpha.value) }
            .semantics { contentDescription = "账目保存成功" }
            .testTag("entry_save_success")
            .pointerInput(sessionId) {
                awaitPointerEventScope {
                    while (true) awaitPointerEvent().changes.forEach { it.consume() }
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            modifier = Modifier
                .size(68.dp)
                .graphicsLayer {
                    scaleX = scale.value
                    scaleY = scale.value
                    this.alpha = alpha.value
                },
            shape = RoundedCornerShape(22.dp),
            color = successColor(),
            contentColor = successContentColor(),
            shadowElevation = 6.dp,
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Rounded.Check,
                    contentDescription = null,
                    modifier = Modifier.size(34.dp),
                )
            }
        }
    }
}
