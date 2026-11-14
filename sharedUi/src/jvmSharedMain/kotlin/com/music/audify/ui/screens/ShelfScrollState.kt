package com.music.audify.ui.screens

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

/**
 * Scroll position of each sideways shelf, kept outside the page's composition.
 *
 * Home is composed afresh every time it comes back from a pushed page, and a
 * row that remembers its own state starts again at its first card. The card
 * that was opened, if it had been scrolled out of view, was then not composed
 * at all, so the cover had nothing to fly back to.
 */
private val shelfListStates = HashMap<String, LazyListState>()

@Composable
internal fun rememberShelfListState(key: String): LazyListState =
    remember(key) { shelfListStates.getOrPut(key) { LazyListState() } }