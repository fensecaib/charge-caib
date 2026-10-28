package caib.charge.ui.subscription

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.MutatePriority
import androidx.compose.foundation.gestures.stopScroll
import androidx.compose.foundation.lazy.LazyListLayoutInfo
import androidx.compose.foundation.lazy.LazyListPrefetchResultScope
import androidx.compose.foundation.lazy.LazyListPrefetchScope
import androidx.compose.foundation.lazy.LazyListPrefetchStrategy
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.layout.LazyLayoutPrefetchState.PrefetchHandle
import androidx.compose.foundation.lazy.layout.NestedPrefetchScope
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember

/** A retained top-level list must stop work, as well as drawing, when its pane is hidden. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun rememberSubscriptionListState(active: Boolean): LazyListState {
    val prefetch = remember { SubscriptionPrefetchStrategy(active) }
    val state = rememberLazyListState(prefetchStrategy = prefetch)
    // Cancel queued premeasure requests in the same commit that hides the pane.
    SideEffect { prefetch.setActive(active) }
    DisposableEffect(prefetch) { onDispose { prefetch.setActive(false) } }
    LaunchedEffect(active, state) {
        // scroll() awaits first layout. A pane first composed hidden has never had a
        // scroll to cancel; do not leave a cancellation job waiting for that layout.
        if (!active && state.layoutInfo.visibleItemsInfo.isNotEmpty()) {
            state.stopScroll(MutatePriority.PreventUserInput)
        }
    }
    return state
}

/** Own the default strategy's latest request until its prefetched slot is consumed or cancelled. */
@OptIn(ExperimentalFoundationApi::class)
private class SubscriptionPrefetchStrategy(initiallyActive: Boolean) : LazyListPrefetchStrategy {
    private val ownership = SubscriptionPrefetchOwnership<PrefetchHandle>(initiallyActive) { it.cancel() }
    private var delegate = LazyListPrefetchStrategy()
    private var sourceScope: LazyListPrefetchScope? = null
    private var trackingScope: LazyListPrefetchScope? = null

    fun setActive(value: Boolean) {
        if (ownership.active == value) return
        if (!value) {
            // The old default strategy remembers its last scheduled index/handle.
            // A fresh one must schedule normally after that handle has been cancelled.
            delegate = LazyListPrefetchStrategy()
        }
        ownership.setActive(value)
    }

    override fun LazyListPrefetchScope.onScroll(delta: Float, layoutInfo: LazyListLayoutInfo) {
        if (ownership.active) with(delegate) { tracked(this@onScroll).onScroll(delta, layoutInfo) }
    }

    override fun LazyListPrefetchScope.onVisibleItemsUpdated(layoutInfo: LazyListLayoutInfo) {
        if (ownership.active) with(delegate) { tracked(this@onVisibleItemsUpdated).onVisibleItemsUpdated(layoutInfo) }
    }

    override fun NestedPrefetchScope.onNestedPrefetch(firstVisibleItemIndex: Int) {
        // Subscription panes are top-level, never nested Lazy items. The nested API
        // returns no cancellation handle, so intentionally do not schedule such work.
    }

    private fun tracked(source: LazyListPrefetchScope): LazyListPrefetchScope {
        if (sourceScope !== source) {
            sourceScope = source
            trackingScope = object : LazyListPrefetchScope {
                override fun schedulePrefetch(
                    index: Int,
                    onPrefetchFinished: (LazyListPrefetchResultScope.() -> Unit)?,
                ): PrefetchHandle {
                    // The default policy requests one item ahead. Completion means
                    // "premeasured", not disposed: retain that cleanup handle too.
                    // Cancelling an already consumed slot is safe (SubcomposeLayout
                    // removes it from its precompose map when normal layout takes it).
                    val token = ownership.beginRequest()
                    val handle = source.schedulePrefetch(index) {
                        // A synchronous completion can hide the pane or start another
                        // request before schedulePrefetch returns its native handle.
                        if (ownership.isCurrent(token)) onPrefetchFinished?.invoke(this)
                    }
                    ownership.attach(token, handle)
                    // PrefetchHandle is sealed: preserve the native implementation and
                    // its idempotent cancellation instead of implementing a proxy.
                    return handle
                }
            }
        }
        return requireNotNull(trackingScope)
    }

}

/** Native handle ownership only; completion leaves the unconsumed slot's cleanup alive. */
internal class SubscriptionPrefetchOwnership<H : Any>(initiallyActive: Boolean, private val cancel: (H) -> Unit) {
    var active = initiallyActive
        private set
    private var generation = 0L
    private var currentHandle: H? = null

    fun beginRequest(): Long {
        val token = ++generation
        releaseCurrent()
        return token
    }

    fun isCurrent(token: Long): Boolean = active && token == generation

    fun attach(token: Long, handle: H) {
        if (isCurrent(token)) currentHandle = handle else cancel(handle)
    }

    fun setActive(value: Boolean) {
        if (active == value) return
        active = value
        if (!value) {
            ++generation
            releaseCurrent()
        }
    }

    private fun releaseCurrent() {
        // Clear before native cancellation; a callback that reenters must never lose
        // the newer request's owner when this old cancellation returns.
        val previous = currentHandle
        currentHandle = null
        if (previous != null) cancel(previous)
    }
}
