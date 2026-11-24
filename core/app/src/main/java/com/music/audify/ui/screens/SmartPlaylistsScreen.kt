// Copyright (c) 2026 Sachit. All rights reserved.
// Project: AUDIFY
package com.music.audify.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import com.music.audify.ui.components.AudifyAlertDialog as AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Slider
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

/** A rule-backed playlist shelf. Results are recomputed from current local and listening data. */
@Composable
fun SmartPlaylistsScreen(
    contentPadding: PaddingValues,
    onPlaySongs: (title: String, songs: List<Song>, startIndex: Int) -> Unit,
) {
    val context = LocalContext.current
    val definitions by SmartPlaylistStore.definitions.collectAsStateWithLifecycle()
    val history by LocalLibraryStore.history.collectAsStateWithLifecycle()
    val favorites by LocalLibraryStore.favorites.collectAsStateWithLifecycle()
    var loadingLibrary by remember { mutableStateOf(true) }
    var candidates by remember { mutableStateOf<List<Song>>(emptyList()) }
    var results by remember { mutableStateOf<Map<String, List<Song>>>(emptyMap()) }
    var recommendations by remember { mutableStateOf<Map<String, List<Song>>>(emptyMap()) }
    var expandedId by rememberSaveable { mutableStateOf<String?>(null) }
    var refreshVersion by rememberSaveable { mutableIntStateOf(0) }
    var showEditor by rememberSaveable { mutableStateOf(false) }
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
    LaunchedEffect(definitions, candidates, refreshVersion) {
        results = SmartPlaylistRepository.evaluateAll(candidates)
    }
    LaunchedEffect(definitions, candidates, results) {
        recommendations = SmartPlaylistRepository.recommendAll(
            definitions = definitions,
            currentSongsByPlaylist = results,
            library = candidates,
        )
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(contentPadding).padding(horizontal = 20.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(Modifier.weight(1f)) {
                Text("Smart Playlists", style = MaterialTheme.typography.headlineMedium)
                Text(
                    "Rules update the tracks whenever you open a playlist.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            TextButton(onClick = { refreshVersion++ }) { Text(if (loadingLibrary) "Loading..." else "Refresh") }
            Button(onClick = { editing = null; showEditor = true }) { Text("Create") }
        }
        if (loadingLibrary) {
            Text(
                "Refreshing your music sources...",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
        } else if (candidates.isNotEmpty()) {
            Text(
                "${candidates.size} tracks available to match",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
        } else {
            Text(
                "No tracks found yet. Play music, like tracks or download music, then refresh.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
        if (definitions.isEmpty()) {
            Text(
                if (loadingLibrary) "Loading your library and listening history..."
                else "No smart playlists yet. Start with liked tracks, recent listening, new additions or music you want to rediscover.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 24.dp),
            )
        } else {
            BoxWithConstraints(Modifier.fillMaxSize()) {
                val columns = if (maxWidth >= 340.dp) 2 else 1
                LazyVerticalGrid(
                    columns = GridCells.Fixed(columns),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(top = 10.dp, bottom = 28.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(definitions, key = { it.id }) { definition ->
                        val songs = results[definition.id].orEmpty()
                        val expanded = expandedId == definition.id
                        Card(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(12.dp)) {
                                Box(
                                    Modifier.fillMaxWidth().height(if (columns == 1) 184.dp else 132.dp)
                                        .clip(RoundedCornerShape(14.dp))
                                        .clickable { expandedId = if (expanded) null else definition.id },
                                ) {
                                    PlaylistArtwork(songs.take(4), Modifier.fillMaxSize())
                                    if (definition.paused) {
                                        Text(
                                            "PAUSED",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            modifier = Modifier.align(Alignment.TopStart).padding(8.dp)
                                                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.88f), RoundedCornerShape(50))
                                                .padding(horizontal = 9.dp, vertical = 5.dp),
                                        )
                                    }
                                    Text(
                                        "${songs.size} TRACKS",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.align(Alignment.BottomStart).padding(8.dp)
                                            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.88f), RoundedCornerShape(50))
                                            .padding(horizontal = 9.dp, vertical = 5.dp),
                                    )
                                }
                                Spacer(Modifier.height(10.dp))
                                Text(definition.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(
                                    if (definition.paused) "Paused" else "Updates automatically",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                )
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                                ) {
                                    TextButton(
                                        enabled = songs.isNotEmpty() && !definition.paused,
                                        onClick = { onPlaySongs(definition.title, songs, 0) },
                                        modifier = Modifier.weight(1f),
                                    ) { Text("Play") }
                                    TextButton(
                                        enabled = songs.isNotEmpty() && !definition.paused,
                                        onClick = { onPlaySongs(definition.title, songs.shuffled(), 0) },
                                        modifier = Modifier.weight(1f),
                                    ) { Text("Shuffle") }
                                    Box {
                                        var menuExpanded by remember(definition.id) { mutableStateOf(false) }
                                        TextButton(onClick = { menuExpanded = true }) { Text("More") }
                                        DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                                            DropdownMenuItem(
                                                text = { Text("Edit") },
                                                onClick = { menuExpanded = false; editing = definition; showEditor = true },
                                            )
                                            DropdownMenuItem(
                                                text = { Text(if (definition.paused) "Resume" else "Pause") },
                                                onClick = {
                                                    menuExpanded = false
                                                    SmartPlaylistStore.setPaused(definition.id, !definition.paused)
                                                },
                                            )
                                            DropdownMenuItem(
                                                text = { Text("Delete") },
                                                onClick = { menuExpanded = false; deleting = definition },
                                            )
                                        }
                                    }
                                }
                                TextButton(
                                    onClick = { expandedId = if (expanded) null else definition.id },
                                    modifier = Modifier.fillMaxWidth(),
                                ) { Text(if (expanded) "Hide tracks" else "View tracks") }
                                if (expanded) {
                                    androidx.compose.material3.HorizontalDivider(
                                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f),
                                    )
                                    Spacer(Modifier.height(8.dp))
                                    if (songs.isEmpty()) {
                                        Text("No tracks match these rules yet.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    } else {
                                        songs.forEachIndexed { index, song ->
                                            TrackArtworkRow(song) { onPlaySongs(definition.title, songs, index) }
                                        }
                                    }
                                    val suggested = recommendations[definition.id].orEmpty()
                                    if (suggested.isNotEmpty()) {
                                        Spacer(Modifier.height(12.dp))
                                        Text("Recommended", style = MaterialTheme.typography.titleSmall)
                                        Text(
                                            "Add a suggestion to keep it in this playlist.",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                        suggested.forEach { suggestion ->
                                            Row(
                                                modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                            ) {
                                                SongArtwork(suggestion, Modifier.size(42.dp))
                                                Spacer(Modifier.size(9.dp))
                                                Column(Modifier.weight(1f)) {
                                                    Text(suggestion.title, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                                    Text(suggestion.artist, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                                }
                                                TextButton(onClick = {
                                                    SmartPlaylistStore.save(
                                                        definition.copy(
                                                            includedSongIds = (definition.includedSongIds + suggestion.videoId)
                                                                .distinct().take(SmartPlaylistEngine.MAX_TRACKS),
                                                        ),
                                                    )
                                                }) { Text("Add") }
                                            }
                                        }
                                    }
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
                expandedId = definition.id
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
                        if (expandedId == target.id) expandedId = null
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


@Composable
private fun PlaylistArtwork(songs: List<Song>, modifier: Modifier = Modifier) {
    if (songs.isEmpty()) {
        Box(modifier.background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
            Text("♫", style = MaterialTheme.typography.displayMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }
    Row(modifier) {
        songs.take(2).forEach { song -> SongArtwork(song, Modifier.weight(1f).fillMaxHeight()) }
        if (songs.size == 1) {
            Box(
                Modifier.weight(1f).fillMaxHeight().background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) {
                Text("♫", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (songs.size > 2) {
            Column(Modifier.weight(1f).fillMaxHeight()) {
                songs.drop(2).take(2).forEach { song -> SongArtwork(song, Modifier.weight(1f).fillMaxWidth()) }
            }
        }
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
            Text("♫", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun TrackArtworkRow(song: Song, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SongArtwork(song, Modifier.size(44.dp).clip(RoundedCornerShape(8.dp)))
        Spacer(Modifier.size(10.dp))
        Column(Modifier.weight(1f)) {
            Text(song.title, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(song.artist, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
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

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "Create Smart Playlist" else "Edit Smart Playlist") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(title, { title = it; previewCount = null }, label = { Text("Name") }, singleLine = true)
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
        },
        confirmButton = {
            TextButton(onClick = { buildDefinition()?.let(onSave) }) { Text("Save") }
        },
        dismissButton = {
            Row {
                TextButton(
                    enabled = !previewing,
                    onClick = {
                        val draft = buildDefinition() ?: return@TextButton
                        scope.launch {
                            previewing = true
                            previewCount = SmartPlaylistRepository.evaluate(draft, candidates).size
                            previewing = false
                        }
                    },
                ) { Text(if (previewing) "Checking…" else "Preview") }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        },
    )
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
