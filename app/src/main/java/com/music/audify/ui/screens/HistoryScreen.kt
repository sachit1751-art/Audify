// Copyright (c) 2026 Sachit. All rights reserved.
// Project: AUDIFY
package com.music.audify.ui.screens

import com.music.audify.data.model.stableSongComposeKeys

import com.music.audify.R

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.music.audify.data.model.Song
import com.music.audify.data.model.UiState
import com.music.audify.data.model.playingRowMatcher
import com.music.audify.ui.components.MessageState
import com.music.audify.ui.components.PlayingAccent
import com.music.audify.ui.components.ROW_DIVIDER_INSET
import com.music.audify.ui.components.SongRow
import com.music.audify.ui.components.songListSkeleton

/**
 * What the account has been listening to, most recent first.
 *
 * A plain list rather than a page with a header: no single cover stands for a
 * history, and borrowing one — the newest track's, say — would claim the page
 * belonged to it.
 *
 * Tapping a row plays it with the rest of the history behind it, so the list
 * doubles as a queue that has already been approved once. That is the useful
 * reading of a history: not a receipt, but everything worth playing again.
 */
@Composable
fun HistoryScreen(
    state: UiState<List<Song>>,
    listState: LazyListState,
    onSongClick: (List<Song>, Int) -> Unit,
    onSongLongPress: (Song) -> Unit,
    onSongSwipe: (Song) -> Unit,
    onRetry: () -> Unit,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
    currentSong: Song? = null,
    isPlaying: Boolean = false,
) {
    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxSize(),
        contentPadding = contentPadding,
    ) {
        when (state) {
            is UiState.Loading -> songListSkeleton(count = 10, keyPrefix = "skeleton:history")

            is UiState.Error -> item(key = "history:message") {
                MessageState(
                    message = state.message,
                    actionLabel = stringResource(R.string.try_again),
                    onAction = onRetry,
                )
            }

            is UiState.Success -> {
                val songs = state.data
                if (songs.isEmpty()) {
                    item(key = "history:empty") {
                        MessageState(stringResource(R.string.history_empty))
                    }
                } else {
                    val songKeys = songs.stableSongComposeKeys()
                    val isCurrentRow = playingRowMatcher(songs, currentSong)
                    // Duplicate video IDs receive occurrence suffixes, so keys stay
                    // unique without depending on a row's current position.
                    itemsIndexed(songs, songKeys) { index, song ->
                        SongRow(
                            song = song,
                            onClick = { onSongClick(songs, index) },
                            onLongPress = { onSongLongPress(song) },
                            onSwipeToQueue = { onSongSwipe(song) },
                            isCurrent = isCurrentRow(song),
                            isPlaying = isCurrentRow(song) && isPlaying,
                            searchPlayingStyle = true,
                            activeTint = PlayingAccent,
                        )
                        if (index < songs.lastIndex) {
                            HorizontalDivider(
                                modifier = Modifier.padding(start = ROW_DIVIDER_INSET),
                                thickness = 0.5.dp,
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Keyed on both, for the reason given at the call site. */
private fun androidx.compose.foundation.lazy.LazyListScope.itemsIndexed(
    songs: List<Song>,
    keys: List<String>,
    row: @Composable (Int, Song) -> Unit,
) = items(
    count = songs.size,
    key = { keys[it] },
) { index -> row(index, songs[index]) }
