/**
 * SachitMusic Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.sachit.music.ui.screens.library

import android.Manifest
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.sachit.music.LocalPlayerAwareWindowInsets
import com.sachit.music.LocalPlayerConnection
import com.sachit.music.R
import com.sachit.music.constants.CONTENT_TYPE_HEADER
import com.sachit.music.constants.CONTENT_TYPE_SONG
import com.sachit.music.constants.SongFilter
import com.sachit.music.constants.SongSortType
import com.sachit.music.playback.queues.ListQueue
import com.sachit.music.ui.component.ChipsRow
import com.sachit.music.ui.component.HideOnScrollFAB
import com.sachit.music.ui.component.LibrarySearchHeader
import com.sachit.music.ui.component.LocalMenuState
import com.sachit.music.ui.component.SongListItem
import com.sachit.music.ui.component.SortHeader
import com.sachit.music.ui.menu.SongMenu
import com.sachit.music.viewmodels.LocalSongsViewModel
import com.sachit.music.viewmodels.SongWithStatus
import com.sachit.music.extensions.toMediaItem

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LocalSongsScreen(
    navController: NavController,
    onDeselect: () -> Unit,
    viewModel: LocalSongsViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val menuState = LocalMenuState.current
    val playerConnection = LocalPlayerConnection.current ?: return

    val sortType by viewModel.sortType.collectAsStateWithLifecycle()
    val sortDescending by viewModel.sortDescending.collectAsStateWithLifecycle()
    val songs by viewModel.localSongs.collectAsStateWithLifecycle()
    val filter by viewModel.filter.collectAsStateWithLifecycle()
    val isScanning by viewModel.isScanning.collectAsStateWithLifecycle()
    val scanResult by viewModel.scanResult.collectAsStateWithLifecycle()

    // Copy scanResult to a local variable for smart cast
    val currentScanResult = scanResult

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { isGranted ->
        if (isGranted) {
            viewModel.scan()
        } else {
            Toast.makeText(
                context,
                R.string.local_library_permission_denied,
                Toast.LENGTH_LONG,
            ).show()
        }
    }

    LaunchedEffect(Unit) {
        viewModel.checkAndRequestPermission()
    }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            state = rememberLazyListState(),
            contentPadding = LocalPlayerAwareWindowInsets.current.asPaddingValues(),
        ) {
            item(key = "filter", contentType = CONTENT_TYPE_HEADER) {
                Row {
                    Spacer(Modifier.width(12.dp))
                    FilterChip(
                        label = { Text(stringResource(R.string.local_files)) },
                        selected = true,
                        colors = FilterChipDefaults.filterChipColors(
                            containerColor = MaterialTheme.colorScheme.surface,
                        ),
                        onClick = onDeselect,
                        shape = RoundedCornerShape(16.dp),
                        leadingIcon = {
                            Icon(
                                painter = painterResource(R.drawable.close),
                                contentDescription = "",
                            )
                        },
                    )
                    ChipsRow(
                        chips = listOf(
                            SongFilter.LIKED to stringResource(R.string.filter_liked),
                        ),
                        currentValue = filter,
                        onValueUpdate = { newFilter ->
                            viewModel.setFilter(newFilter)
                        },
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            item(key = "header", contentType = CONTENT_TYPE_HEADER) {
                LibrarySearchHeader(
                    isSearchActive = false,
                    searchQuery = "",
                    onSearchQueryChange = {},
                    onBack = {},
                    keyboardController = null,
                    modifier = Modifier.padding(start = 16.dp),
                ) {
                    SortHeader(
                        sortType = sortType,
                        sortDescending = sortDescending,
                        onSortTypeChange = viewModel::setSortType,
                        onSortDescendingChange = viewModel::setSortDescending,
                        sortTypeText = { st ->
                            when (st) {
                                SongSortType.CREATE_DATE -> R.string.sort_by_create_date
                                SongSortType.NAME -> R.string.sort_by_name
                                SongSortType.ARTIST -> R.string.sort_by_artist
                                SongSortType.PLAY_TIME -> R.string.sort_by_play_time
                            }
                        },
                    )
                    Spacer(Modifier.weight(1f))
                    Text(
                        text = when {
                            isScanning -> stringResource(R.string.local_library_scanning)
                            currentScanResult != null && currentScanResult.total > 0 -> stringResource(
                                R.plurals.n_song,
                                currentScanResult.total,
                                currentScanResult.total,
                            )
                            else -> stringResource(R.string.no_local_files_found)
                        },
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.secondary,
                    )
                    if (isScanning) {
                        CircularProgressIndicator(
                            modifier = Modifier.padding(start = 8.dp),
                            strokeWidth = 2.dp,
                        )
                    } else if (currentScanResult != null && currentScanResult.total > 0) {
                        IconButton(
                            onClick = { viewModel.scan() },
                            modifier = Modifier.padding(start = 8.dp, end = 8.dp).height(40.dp).width(40.dp),
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.refresh),
                                contentDescription = null,
                            )
                        }
                    }
                }
            }

            if (songs.isEmpty() && !isScanning) {
                item(key = "empty", contentType = CONTENT_TYPE_HEADER) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = stringResource(R.string.no_local_files_found),
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                            )
                            Spacer(Modifier.height(8.dp))
                            Button(
                                onClick = {
                                    if (ContextCompat.checkSelfPermission(
                                            context,
                                            Manifest.permission.READ_MEDIA_AUDIO,
                                        ) == PackageManager.PERMISSION_GRANTED
                                    ) {
                                        viewModel.scan()
                                    } else {
                                        permissionLauncher.launch(
                                            Manifest.permission.READ_MEDIA_AUDIO,
                                        )
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary,
                                ),
                            ) {
                                Text(stringResource(R.string.on_device))
                            }
                        }
                    }
                }
            }

            itemsIndexed(
                items = songs,
                key = { index, item -> "${item.song.id}_$index" },
                contentType = { _, _ -> CONTENT_TYPE_SONG },
            ) { index, item ->
                val song = (item as SongWithStatus).song
                val queueTitle = stringResource(R.string.queue_all_songs)
                SongListItem(
                    song = song,
                    showInLibraryIcon = true,
                    isActive = song.id == playerConnection.mediaMetadata.value?.id,
                    isPlaying = playerConnection.isEffectivelyPlaying.value,
                    showLikedIcon = true,
                    showDownloadIcon = false,
                    trailingContent = {
                        IconButton(
                            onClick = {
                                menuState.show {
                                    SongMenu(
                                        originalSong = song,
                                        onDismiss = menuState::dismiss,
                                    )
                                }
                            },
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.more_vert),
                                contentDescription = null,
                            )
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            if (song.id == playerConnection.mediaMetadata.value?.id) {
                                playerConnection.togglePlayPause()
                            } else {
                                playerConnection.playQueue(
                                    ListQueue(
                                        title = queueTitle,
                                        items = songs.map { (it.song as com.sachit.music.db.entities.Song).toMediaItem() },
                                        startIndex = index,
                                    ),
                                )
                            }
                        },
                )
            }
        }

        val queueTitle = stringResource(R.string.queue_all_songs)
        HideOnScrollFAB(
            visible = songs.isNotEmpty() && !isScanning,
            lazyListState = rememberLazyListState(),
            icon = R.drawable.shuffle,
            onClick = {
                playerConnection.playQueue(
                    ListQueue(
                        title = queueTitle,
                        items = songs.shuffled().map { (it.song as com.sachit.music.db.entities.Song).toMediaItem() },
                    ),
                )
            },
        )
    }
}
