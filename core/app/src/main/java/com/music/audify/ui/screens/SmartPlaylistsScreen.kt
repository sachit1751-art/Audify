// Copyright (c) 2026 Sachit. All rights reserved.
// Project: AUDIFY
package com.music.audify.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import com.music.audify.ui.components.AudifyAlertDialog as AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Slider
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.music.audify.data.LocalMediaRepository
import com.music.audify.data.model.Song
import com.music.audify.data.model.ShelfItem
import coil3.compose.AsyncImage
import com.music.audify.data.model.ROW_ART_PX
import com.music.audify.data.model.artworkAt
import com.music.audify.data.playlist.SmartPlaylistDefinition
import com.music.audify.data.playlist.SmartPlaylistEngine
import com.music.audify.data.playlist.SmartPlaylistRepository
import com.music.audify.data.playlist.SmartPlaylistSort
import com.music.audify.data.playlist.SmartPlaylistStore
import com.music.audify.data.stats.LocalLibraryStore
import com.music.audify.data.stats.ListeningStats
import com.music.audify.data.settings.AppSettings
import com.music.audify.ui.icons.SfIcons
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

/** Stable browse identity shared by the Library shelf and Smart Playlist detail route. */
const val SMART_PLAYLIST_BROWSE_PREFIX = "audify-smart:"

/** Current generated entries projected into the ordinary Library Playlists shelf. */
@Composable
fun rememberSmartPlaylistShelfItems(): List<ShelfItem> {
    val context = LocalContext.current
    val definitions by SmartPlaylistStore.definitions.collectAsStateWithLifecycle()
    val history by LocalLibraryStore.history.collectAsStateWithLifecycle()
    val favorites by LocalLibraryStore.favorites.collectAsStateWithLifecycle()
    var candidates by remember { mutableStateOf<List<Song>>(emptyList()) }
    var results by remember { mutableStateOf<Map<String, List<Song>>>(emptyMap()) }

    LaunchedEffect(context, history, favorites) {
        candidates = withContext(Dispatchers.IO) {
            val local = runCatching { LocalMediaRepository.getLocalMusic(context) }.getOrDefault(emptyList())
            val downloaded = runCatching { LocalMediaRepository.getDownloadedSongs(context) }.getOrDefault(emptyList())
            val listened = runCatching { ListeningStats.trackSnapshot().map { it.song } }.getOrDefault(emptyList())
            (local + downloaded + history + favorites + listened)
                .filter { it.videoId.isNotBlank() }
                .distinctBy { it.videoId }
        }
    }
    LaunchedEffect(definitions, candidates) {
        results = withContext(Dispatchers.IO) {
            val evaluated = SmartPlaylistRepository.evaluateAll(candidates)
            com.music.audify.data.spotify.LocalPlaylistStore.syncSmartPlaylists(
                definitions.map { definition ->
                    com.music.audify.data.spotify.LocalPlaylist(
                        id = "smart_${definition.id}",
                        title = definition.title,
                        songs = evaluated[definition.id].orEmpty(),
                    )
                },
            )
            if (AppSettings.syncSmartPlaylistsToYouTubeMusic.value) AppSettings.enqueueSmartPlaylistSync()
            evaluated
        }
    }
    return remember(definitions, results) {
        definitions.map { definition ->
            val songs = results[definition.id].orEmpty()
            ShelfItem(
                title = definition.title,
                subtitle = "${songs.size} tracks \u00B7 Smart Playlist${if (definition.paused) " \u00B7 Paused" else ""}",
                thumbnailUrl = songs.firstOrNull()?.thumbnailUrl,
                videoId = null,
                browseId = SMART_PLAYLIST_BROWSE_PREFIX + definition.id,
            )
        }
    }
}

/** A rule-backed playlist shelf. Results are recomputed from current local and listening data. */
@Composable
fun SmartPlaylistsScreen(
    contentPadding: PaddingValues,
    onOpenPlaylist: (String) -> Unit = {},
    initialPlaylistId: String? = null,
    initialShowEditor: Boolean = false,
) {
    val context = LocalContext.current
    val definitions by SmartPlaylistStore.definitions.collectAsStateWithLifecycle()
    val history by LocalLibraryStore.history.collectAsStateWithLifecycle()
    val favorites by LocalLibraryStore.favorites.collectAsStateWithLifecycle()
    var loadingLibrary by remember { mutableStateOf(true) }
    var candidates by remember { mutableStateOf<List<Song>>(emptyList()) }
    var results by remember { mutableStateOf<Map<String, List<Song>>>(emptyMap()) }
    var refreshVersion by rememberSaveable { mutableIntStateOf(0) }
    var showEditor by rememberSaveable { mutableStateOf(initialShowEditor) }
    var editing by remember { mutableStateOf<SmartPlaylistDefinition?>(null) }
    var deleting by remember { mutableStateOf<SmartPlaylistDefinition?>(null) }

    LaunchedEffect(context, refreshVersion, history, favorites) {
        loadingLibrary = true
        candidates = withContext(Dispatchers.IO) {
            val local = runCatching { LocalMediaRepository.getLocalMusic(context) }.getOrDefault(emptyList())
            val downloaded = runCatching { LocalMediaRepository.getDownloadedSongs(context) }.getOrDefault(emptyList())
            val listened = runCatching { ListeningStats.trackSnapshot().map { it.song } }.getOrDefault(emptyList())
            (local + downloaded + history + favorites + listened)
                .filter { it.videoId.isNotBlank() }
                .distinctBy { it.videoId }
        }
        loadingLibrary = false
    }
    LaunchedEffect(definitions, candidates, refreshVersion, loadingLibrary) {
        if (loadingLibrary) return@LaunchedEffect
        val evaluated = withContext(Dispatchers.IO) {
            val currentResults = SmartPlaylistRepository.evaluateAll(candidates)
            com.music.audify.data.spotify.LocalPlaylistStore.syncSmartPlaylists(
                definitions.map { definition ->
                    com.music.audify.data.spotify.LocalPlaylist(
                        id = "smart_${definition.id}",
                        title = definition.title,
                        songs = currentResults[definition.id].orEmpty(),
                    )
                },
            )
            if (AppSettings.syncSmartPlaylistsToYouTubeMusic.value) AppSettings.enqueueSmartPlaylistSync()
            currentResults
        }
        results = evaluated
    }
    LaunchedEffect(initialPlaylistId) {
        initialPlaylistId?.let(onOpenPlaylist)
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(contentPadding).padding(horizontal = 20.dp),
    ) {
        Text(
            "Smart Playlists",
            style = MaterialTheme.typography.displayLarge,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(top = 8.dp, bottom = 2.dp),
        )
        Text(
            "Your music, organised automatically.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 14.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                "${definitions.size} PLAYLISTS  ·  ${candidates.size} TRACKS IN LIBRARY",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            TextButton(onClick = { refreshVersion++ }, enabled = !loadingLibrary) {
                Text(if (loadingLibrary) "Loading…" else "Refresh")
            }
        }
        Button(
            onClick = { editing = null; showEditor = true },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Create smart playlist") }
        if (!loadingLibrary && candidates.isEmpty()) {
            Text(
                "No tracks found yet. Play music, like tracks or download music, then refresh.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
        if (definitions.isEmpty()) {
            Card(Modifier.fillMaxWidth().padding(top = 18.dp), shape = RoundedCornerShape(22.dp)) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text("A playlist that keeps itself fresh", style = MaterialTheme.typography.titleLarge)
                    Text(
                        if (loadingLibrary) "Loading your library and listening history..."
                        else "Create a playlist that stays fresh using liked tracks, recent listening, new additions or music you want to rediscover.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    TextButton(
                        onClick = { editing = null; showEditor = true },
                        enabled = !loadingLibrary || candidates.isNotEmpty(),
                    ) { Text("Set up your first playlist") }
                }
            }
        } else {
            LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(top = 12.dp, bottom = 28.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(definitions, key = { it.id }) { definition ->
                        val songs = results[definition.id].orEmpty()
                        Card(
                            modifier = Modifier.fillMaxWidth().clickable { onOpenPlaylist(definition.id) },
                            shape = RoundedCornerShape(20.dp),
                        ) {
                            Column(Modifier.padding(14.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                Box(
                                    Modifier.size(112.dp)
                                        .clip(RoundedCornerShape(14.dp)),
                                ) {
                                    PlaylistArtwork(songs.take(4), Modifier.fillMaxSize())
                                    Text(
                                        "AI MIX",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.align(Alignment.BottomStart).padding(6.dp)
                                            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.92f), RoundedCornerShape(50))
                                            .padding(horizontal = 7.dp, vertical = 4.dp),
                                    )
                                }
                                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            definition.title,
                                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold),
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f),
                                        )
                                        Box {
                                            var menuExpanded by remember(definition.id) { mutableStateOf(false) }
                                            TextButton(onClick = { menuExpanded = true }) { Text("More") }
                                            DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                                                DropdownMenuItem(text = { Text("Edit") }, onClick = { menuExpanded = false; editing = definition; showEditor = true })
                                                DropdownMenuItem(
                                                    text = { Text(if (definition.paused) "Resume" else "Pause") },
                                                    onClick = { menuExpanded = false; SmartPlaylistStore.setPaused(definition.id, !definition.paused) },
                                                )
                                                DropdownMenuItem(text = { Text("Delete") }, onClick = { menuExpanded = false; deleting = definition })
                                            }
                                        }
                                    }
                                    Text(
                                        if (definition.paused) "Paused" else "${songs.size} tracks \u00B7 Auto-updating",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    Text(
                                        definition.summaryLabel(),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                                }
                            }
                        }
                    }
                }
        }
    }

    if (showEditor) {
        SmartPlaylistEditorDialog(
            initial = editing,
            candidates = candidates,
            onDismiss = { showEditor = false },
            onSave = { definition ->
                SmartPlaylistStore.save(definition)
                showEditor = false
            },
        )
    }

    deleting?.let { target ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("Delete Smart Playlist?") },
            text = {
                Text(
                    "Delete ${target.title}? This removes only its smart-playlist rules and saved additions. Your songs and ordinary playlists are not changed.",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        SmartPlaylistStore.delete(target.id)
                        deleting = null
                    },
                ) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { deleting = null }) { Text("Cancel") }
            },
        )
    }
}


private fun SmartPlaylistDefinition.summaryLabel(): String = buildList {
    if (likedOnly) add("Liked tracks")
    addedWithinDays?.let { add("Added in ${it}d") }
    playedWithinDays?.let { add("Played in ${it}d") }
    notPlayedWithinDays?.let { add("Not played in ${it}d") }
    minimumPlays?.let { add("At least $it plays") }
    if (excludeRecentHistory) add("Hides recent tracks")
}.take(2).joinToString(" \u00B7 ").ifBlank { "Based on your library" }

@Composable
private fun PlaylistArtwork(songs: List<Song>, modifier: Modifier = Modifier) {
    if (songs.isEmpty()) {
        ArtworkPlaceholder(modifier)
        return
    }
    Row(modifier) {
        Column(Modifier.weight(1f).fillMaxHeight()) {
            SongArtwork(songs[0], Modifier.weight(1f).fillMaxWidth())
            if (songs.size > 2) {
                SongArtwork(songs[2], Modifier.weight(1f).fillMaxWidth())
            } else {
                ArtworkPlaceholder(Modifier.weight(1f).fillMaxWidth())
            }
        }
        Column(Modifier.weight(1f).fillMaxHeight()) {
            if (songs.size > 1) {
                SongArtwork(songs[1], Modifier.weight(1f).fillMaxWidth())
            } else {
                ArtworkPlaceholder(Modifier.weight(1f).fillMaxWidth())
            }
            if (songs.size > 3) {
                SongArtwork(songs[3], Modifier.weight(1f).fillMaxWidth())
            } else {
                ArtworkPlaceholder(Modifier.weight(1f).fillMaxWidth())
            }
        }
    }
}

@Composable
private fun ArtworkPlaceholder(modifier: Modifier = Modifier) {
    Box(
        modifier.background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        Text("\u266B", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun SongArtwork(song: Song, modifier: Modifier = Modifier) {
    Box(modifier.background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
        val artwork = if (song.isVideo) song.thumbnailUrl else song.artworkAt(ROW_ART_PX)
        if (!artwork.isNullOrBlank()) {
            AsyncImage(
                model = artwork,
                contentDescription = "Artwork for ${song.title}",
                contentScale = if (song.isVideo) ContentScale.Fit else ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Text("\u266B", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun SmartPlaylistEditorDialog(
    initial: SmartPlaylistDefinition?,
    candidates: List<Song>,
    onDismiss: () -> Unit,
    onSave: (SmartPlaylistDefinition) -> Unit,
) {
    var title by remember(initial?.id) { mutableStateOf(initial?.title.orEmpty()) }
    var advancedExpanded by remember(initial?.id) { mutableStateOf(false) }
    var likedOnly by remember(initial?.id) { mutableStateOf(initial?.likedOnly ?: false) }
    var excludeRecent by remember(initial?.id) { mutableStateOf(initial?.excludeRecentHistory ?: false) }
    var addedDays by remember(initial?.id) { mutableStateOf(initial?.addedWithinDays?.toString().orEmpty()) }
    var playedDays by remember(initial?.id) { mutableStateOf(initial?.playedWithinDays?.toString().orEmpty()) }
    var notPlayedDays by remember(initial?.id) { mutableStateOf(initial?.notPlayedWithinDays?.toString().orEmpty()) }
    var minimumPlays by remember(initial?.id) { mutableStateOf(initial?.minimumPlays?.toString().orEmpty()) }
    var maxTracks by remember(initial?.id) { mutableIntStateOf(initial?.maxTracks ?: 100) }
    var sortBy by remember(initial?.id) { mutableStateOf(initial?.sortBy ?: SmartPlaylistSort.MOST_PLAYED) }
    var sortMenu by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var previewCount by remember { mutableStateOf<Int?>(null) }
    var previewing by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    fun resetPresetFilters() {
        likedOnly = false
        excludeRecent = false
        addedDays = ""
        playedDays = ""
        notPlayedDays = ""
        minimumPlays = ""
    }

    fun buildDefinition(): SmartPlaylistDefinition? {
        val rawNumbers = listOf(addedDays, playedDays, notPlayedDays, minimumPlays)
        if (rawNumbers.any { it.isNotBlank() && it.toIntOrNull() == null }) {
            error = "Rule values must be whole numbers."
            return null
        }
        val definition = SmartPlaylistDefinition(
            id = initial?.id ?: UUID.randomUUID().toString(),
            title = title.trim(),
            artists = initial?.artists.orEmpty(),
            likedOnly = likedOnly,
            addedWithinDays = addedDays.toIntOrNull(),
            excludeRecentHistory = excludeRecent,
            playedWithinDays = playedDays.toIntOrNull(),
            notPlayedWithinDays = notPlayedDays.toIntOrNull(),
            minimumPlays = minimumPlays.toIntOrNull(),
            sortBy = sortBy,
            maxTracks = maxTracks,
            paused = initial?.paused ?: false,
            includedSongIds = initial?.includedSongIds.orEmpty(),
        )
        error = SmartPlaylistEngine.validate(definition)
        return definition.takeIf { error == null }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = RectangleShape,
        containerColor = MaterialTheme.colorScheme.background,
        dragHandle = null,
        contentWindowInsets = { WindowInsets(0, 0, 0, 0) },
    ) {
        Column(Modifier.fillMaxSize().statusBarsPadding().imePadding()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 8.dp, end = 18.dp, top = 4.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onDismiss) {
                    Icon(SfIcons.Close, contentDescription = "Close", tint = MaterialTheme.colorScheme.onBackground)
                }
                Column(Modifier.weight(1f)) {
                    Text(
                        if (initial == null) "Create Smart Playlist" else "Edit Smart Playlist",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                    Text(
                        "Saved on this device · updates as your library changes",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Column(
                modifier = Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it; previewCount = null },
                    label = { Text("Playlist name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text("Start with", style = MaterialTheme.typography.titleSmall)
                Text(
                    "Choose a starting point. You can fine-tune it below.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        TextButton(onClick = {
                            resetPresetFilters()
                            likedOnly = true
                            sortBy = SmartPlaylistSort.MOST_PLAYED
                            previewCount = null
                        }) { Text("Liked") }
                        TextButton(onClick = {
                            resetPresetFilters()
                            playedDays = "30"
                            sortBy = SmartPlaylistSort.RECENTLY_PLAYED
                            previewCount = null
                        }) { Text("Recently played") }
                        TextButton(onClick = {
                            resetPresetFilters()
                            notPlayedDays = "30"
                            sortBy = SmartPlaylistSort.MOST_PLAYED
                            previewCount = null
                        }) { Text("Rediscover") }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        TextButton(onClick = {
                            resetPresetFilters()
                            addedDays = "30"
                            sortBy = SmartPlaylistSort.RECENTLY_ADDED
                            previewCount = null
                        }) { Text("New additions") }
                        TextButton(onClick = {
                            resetPresetFilters()
                            sortBy = SmartPlaylistSort.MOST_PLAYED
                            previewCount = null
                        }) { Text("Most played") }
                    }
                }
                RuleCheckbox("Liked tracks only", likedOnly) { likedOnly = it; previewCount = null }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Order: ${sortBy.label()}", modifier = Modifier.weight(1f))
                    TextButton(onClick = { sortMenu = true }) { Text("Change") }
                    DropdownMenu(expanded = sortMenu, onDismissRequest = { sortMenu = false }) {
                        SmartPlaylistSort.entries.forEach { option ->
                            DropdownMenuItem(
                                text = { Text(option.label()) },
                                onClick = { sortBy = option; sortMenu = false; previewCount = null },
                            )
                        }
                    }
                }
                TextButton(onClick = { advancedExpanded = !advancedExpanded }) {
                    Text(if (advancedExpanded) "Fewer controls" else "More controls")
                }
                if (advancedExpanded) {
                    RuleCheckbox("Hide recently played tracks", excludeRecent) { excludeRecent = it; previewCount = null }
                    NumberRuleField("Added in the last (days)", addedDays) { addedDays = it; previewCount = null }
                    NumberRuleField("Played in the last (days)", playedDays) { playedDays = it; previewCount = null }
                    NumberRuleField("Not played in the last (days)", notPlayedDays) { notPlayedDays = it; previewCount = null }
                    NumberRuleField("Minimum number of plays", minimumPlays) { minimumPlays = it; previewCount = null }
                }
                Text("Playlist size: $maxTracks tracks", style = MaterialTheme.typography.bodyMedium)
                Slider(
                    value = maxTracks.toFloat(),
                    onValueChange = { maxTracks = (it / 10f).toInt() * 10; previewCount = null },
                    valueRange = 10f..500f,
                    steps = 48,
                )
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                previewCount?.let { Text("Preview: $it matching tracks", color = MaterialTheme.colorScheme.primary) }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Row(
                modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 18.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(
                    enabled = !previewing,
                    onClick = {
                        val draft = buildDefinition() ?: return@TextButton
                        scope.launch {
                            previewing = true
                            previewCount = withContext(Dispatchers.Default) {
                                SmartPlaylistRepository.evaluate(draft, candidates).size
                            }
                            previewing = false
                        }
                    },
                ) { Text(if (previewing) "Checking…" else "Preview") }
                Button(
                    onClick = { buildDefinition()?.let(onSave) },
                    enabled = title.isNotBlank() && !previewing,
                    modifier = Modifier.weight(1f).height(50.dp),
                ) { Text(if (initial == null) "Create Smart Playlist" else "Save changes") }
            }
        }
    }
}

@Composable
private fun RuleCheckbox(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked = checked, onCheckedChange = onChange)
        Text(label, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun NumberRuleField(label: String, value: String, onValueChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = Modifier.fillMaxWidth(),
    )
}

private fun SmartPlaylistSort.label(): String = when (this) {
    SmartPlaylistSort.LIBRARY_ORDER -> "Library order"
    SmartPlaylistSort.MOST_PLAYED -> "Most played"
    SmartPlaylistSort.RECENTLY_PLAYED -> "Recently played"
    SmartPlaylistSort.RECENTLY_ADDED -> "Recently added"
    SmartPlaylistSort.TITLE -> "Title"
}
