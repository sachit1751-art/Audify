// Adapted from Zenith's GPL-3.0 elastic refresh implementation.
// Original project: https://github.com/rezapranata089-bit/Zenith
// See THIRD_PARTY_NOTICES.md for attribution and licence terms.
package com.music.audify.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.geometry.Offset
import com.music.audify.ui.LocalPullToRefreshEnabled
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.max

@Stable
class ElasticRefreshState internal constructor(private val scope: CoroutineScope) {
    var offset by mutableFloatStateOf(0f)
        private set
    var armed by mutableStateOf(false)
        private set
    var holding by mutableStateOf(false)
        private set
    /** Progress consumed by the frosted top bar's refresh puck. */
    val distanceFraction: Float get() = (offset / THRESHOLD_PX).coerceIn(0f, 1f)
    private var rawDrag = 0f
    private var dragging = false
    private var refreshStarted = false
    private var holdStartMs = 0L
    private var animation: Job? = null

    private fun resisted(value: Float): Float = MAX_PULL_PX * (1f - 1f / (value * RUBBER / MAX_PULL_PX + 1f))
    private fun unresisted(value: Float): Float {
        val p = (value / MAX_PULL_PX).coerceIn(0f, 0.98f)
        return MAX_PULL_PX * (1f / (1f - p) - 1f) / RUBBER
    }

    internal fun dragBy(delta: Float): Float {
        if (!dragging) {
            animation?.cancel()
            rawDrag = unresisted(offset)
            dragging = true
        }
        val next = max(0f, rawDrag + delta)
        val used = next - rawDrag
        rawDrag = next
        offset = resisted(next)
        armed = offset >= THRESHOLD_PX
        return used
    }

    internal fun release(refreshing: Boolean, onRefresh: () -> Unit): Boolean {
        if (!dragging) return false
        dragging = false
        rawDrag = 0f
        if (offset <= 0f) return false
        val shouldRefresh = armed && !holding
        armed = false
        if (shouldRefresh) {
            holding = true
            refreshStarted = refreshing
            holdStartMs = System.currentTimeMillis()
            settleTo(HOLD_PX)
            onRefresh()
            scope.launch {
                delay(1_000)
                if (holding && !refreshStarted) finish()
            }
        } else {
            settleTo(0f)
        }
        return true
    }

    internal fun onRefreshingChanged(refreshing: Boolean) {
        if (refreshing) refreshStarted = true else if (holding && refreshStarted) finish()
    }

    private fun finish() {
        scope.launch {
            val remaining = (900L - (System.currentTimeMillis() - holdStartMs)).coerceAtLeast(0L)
            if (remaining > 0) delay(remaining)
            holding = false
            refreshStarted = false
            if (!dragging) settleTo(0f)
        }
    }

    private fun settleTo(target: Float) {
        animation?.cancel()
        animation = scope.launch {
            Animatable(offset).animateTo(target, spring(dampingRatio = 0.88f, stiffness = 260f)) {
                offset = value.coerceAtLeast(0f)
            }
        }
    }

    private companion object {
        const val RUBBER = 0.72f
        const val MAX_PULL_PX = 260f
        const val THRESHOLD_PX = 112f
        const val HOLD_PX = 68f
    }
}

@Composable
fun rememberElasticRefreshState(): ElasticRefreshState {
    val scope = rememberCoroutineScope()
    return remember(scope) { ElasticRefreshState(scope) }
}

@Composable
fun ElasticPullToRefresh(
    refreshing: Boolean,
    onRefresh: () -> Unit,
    state: ElasticRefreshState,
    modifier: Modifier = Modifier,
    topInset: Dp = 0.dp,
    content: @Composable () -> Unit,
) {
    if (!LocalPullToRefreshEnabled.current) {
        Box(modifier.fillMaxSize()) { content() }
        return
    }
    val latestRefreshing by rememberUpdatedState(refreshing)
    val latestOnRefresh by rememberUpdatedState(onRefresh)
    val connection = remember(state) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset =
                if (source == NestedScrollSource.UserInput && available.y < 0f && state.offset > 0f) {
                    Offset(0f, state.dragBy(available.y))
                } else Offset.Zero

            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset =
                if (source == NestedScrollSource.UserInput && available.y > 0f && !state.holding) {
                    Offset(0f, state.dragBy(available.y))
                } else Offset.Zero

            override suspend fun onPreFling(available: Velocity): Velocity =
                if (state.release(latestRefreshing) { latestOnRefresh() }) available else Velocity.Zero
        }
    }
    LaunchedEffect(refreshing) { state.onRefreshingChanged(refreshing) }
    Box(modifier.fillMaxSize().nestedScroll(connection)) {
        Box(Modifier.fillMaxSize().graphicsLayer { translationY = state.offset }) { content() }
        if (state.offset > 0f) {
            Box(
                Modifier.align(Alignment.TopCenter).padding(top = topInset + 10.dp),
                contentAlignment = Alignment.Center,
            ) {
                if (state.holding) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                else Text(
                    text = if (state.armed) "Release to refresh" else "Pull to refresh",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
