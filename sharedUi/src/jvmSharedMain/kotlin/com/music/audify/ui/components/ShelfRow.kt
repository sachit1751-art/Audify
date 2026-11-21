package com.music.audify.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyListPrefetchStrategy
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier

/**
 * What a sideways shelf is wrapped in, on top of the row itself.
 *
 * Nothing on the phone, where a finger drags the row. A window's mouse can't
 * swipe sideways, so the desktop wraps each row in the arrows it pages with,
 * the drag that pulls it, and the horizontal wheel.
 */
interface ShelfRowChrome {
    @Composable
    fun Wrap(state: LazyListState, row: @Composable () -> Unit)
}

private object NoShelfRowChrome : ShelfRowChrome {
    @Composable
    override fun Wrap(state: LazyListState, row: @Composable () -> Unit) = row()
}

val LocalShelfRowChrome = staticCompositionLocalOf<ShelfRowChrome> { NoShelfRowChrome }

/** Shared bounded prefetch policy for LazyRows nested inside a vertical feed. */
@Composable
@OptIn(ExperimentalFoundationApi::class)
fun rememberNestedShelfListState(): LazyListState {
    val strategy = remember { LazyListPrefetchStrategy(nestedPrefetchItemCount = 3) }
    return rememberLazyListState(prefetchStrategy = strategy)
}

/**
 * Kept as a source-compatible modifier for existing shelf call sites.
 * Per-card entrance animations were removed from active scrolling: starting
 * several spring/alpha/scale animations while a LazyRow is flinging competes
 * with artwork decode and the parent feed for the frame budget.
 */
@Suppress("UNUSED_PARAMETER")
fun Modifier.shelfStagger(index: Int): Modifier = this

/**
 * A sideways-scrolling shelf, in whatever the platform wraps one in — see [ShelfRowChrome].
 * Snaps to each cell rather than stopping mid-card: a fling settles with a
 * column aligned to the leading edge instead of leaving items cut in half.
 */
@Composable
fun ShelfRow(
    contentPadding: PaddingValues,
    horizontalArrangement: Arrangement.Horizontal,
    modifier: Modifier = Modifier,
    state: LazyListState = rememberNestedShelfListState(),
    content: LazyListScope.() -> Unit,
) {
    LocalShelfRowChrome.current.Wrap(state) {
        LazyRow(
            modifier = modifier,
            state = state,
            contentPadding = contentPadding,
            horizontalArrangement = horizontalArrangement,
            content = content,
        )
    }
}
