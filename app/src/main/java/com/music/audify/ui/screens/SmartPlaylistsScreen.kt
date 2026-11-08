// Copyright (c) 2026 Sachit. All rights reserved.
// Project: AUDIFY
package com.music.audify.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import com.music.audify.ui.components.AudifyAlertDialog as AlertDialog
import com.music.audify.ui.components.ExplicitSongTitle
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.music.audify.data.LocalMediaRepository
import com.music.audify.data.model.Song
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
    var expandedId by rememberSaveable { mutableStateOf<String?>(null) }
    var refreshVersion by rememberSaveable { mutableIntStateOf(0) }
    var showEditor by rememberSaveable { mutableStateOf(false) }
    var editing by remember { mutableStateOf<SmartPlaylistDefinition?>(null) }

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
                else "No smart playlists yet. Create rules from played tracks, liked songs, artists or downloaded music.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 24.dp),
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(top = 8.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(definitions, key = { it.id }) { definition ->
                    val songs = results[definition.id].orEmpty()
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(
                                    modifier = Modifier.weight(1f).clickable {
                                        expandedId = if (expandedId == definition.id) null else definition.id
                                    },
                                ) {
                                    Text(definition.title, style = MaterialTheme.typography.titleMedium)
                                    Text(
                                        "${songs.size} tracks · ${if (definition.paused) "Paused" else "Updates automatically"}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                TextButton(onClick = { editing = definition; showEditor = true }) { Text("Edit") }
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                TextButton(
                                    enabled = songs.isNotEmpty() && !definition.paused,
                                    onClick = { onPlaySongs(definition.title, songs, 0) },
                                ) { Text("Play") }
                                TextButton(onClick = {
                                    SmartPlaylistStore.setPaused(definition.id, !definition.paused)
                                }) { Text(if (definition.paused) "Resume" else "Pause") }
                                TextButton(onClick = { SmartPlaylistStore.delete(definition.id) }) { Text("Delete") }
                            }
                            if (expandedId == definition.id) {
                                Spacer(Modifier.height(8.dp))
                                if (songs.isEmpty()) {
                                    Text("No tracks match these rules yet.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                                } else {
                                    songs.take(30).forEach { song ->
                                        Column(Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
                                            Text(song.title, style = MaterialTheme.typography.bodyMedium, maxLines = 1)
                                            Text(song.artist, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                                        }
                                    }
                                    if (songs.size > 30) {
                                        Text("Showing 30 of ${songs.size} tracks.", style = MaterialTheme.typography.labelSmall)
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
}

@Composable
private fun SmartPlaylistEditorDialog(
    initial: SmartPlaylistDefinition?,
    candidates: List<Song>,
    onDismiss: () -> Unit,
    onSave: (SmartPlaylistDefinition) -> Unit,
) {
    var title by remember(initial?.id) { mutableStateOf(initial?.title.orEmpty()) }
    var artists by remember(initial?.id) { mutableStateOf(initial?.artists?.joinToString(", ").orEmpty()) }
    var likedOnly by remember(initial?.id) { mutableStateOf(initial?.likedOnly ?: false) }
    var excludeRecent by remember(initial?.id) { mutableStateOf(initial?.excludeRecentHistory ?: false) }
    var addedDays by remember(initial?.id) { mutableStateOf(initial?.addedWithinDays?.toString().orEmpty()) }
    var playedDays by remember(initial?.id) { mutableStateOf(initial?.playedWithinDays?.toString().orEmpty()) }
    var notPlayedDays by remember(initial?.id) { mutableStateOf(initial?.notPlayedWithinDays?.toString().orEmpty()) }
    var minimumPlays by remember(initial?.id) { mutableStateOf(initial?.minimumPlays?.toString().orEmpty()) }
    var maxTracks by remember(initial?.id) { mutableStateOf((initial?.maxTracks ?: 100).toString()) }
    var sortBy by remember(initial?.id) { mutableStateOf(initial?.sortBy ?: SmartPlaylistSort.MOST_PLAYED) }
    var sortMenu by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var previewCount by remember { mutableStateOf<Int?>(null) }
    var previewing by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    fun buildDefinition(): SmartPlaylistDefinition? {
        val rawNumbers = listOf(addedDays, playedDays, notPlayedDays, minimumPlays, maxTracks)
        if (rawNumbers.any { it.isNotBlank() && it.toIntOrNull() == null }) {
            error = "Rule values must be whole numbers."
            return null
        }
        val definition = SmartPlaylistDefinition(
            id = initial?.id ?: UUID.randomUUID().toString(),
            title = title.trim(),
            artists = artists.split(',').map(String::trim).filter(String::isNotBlank).distinct(),
            likedOnly = likedOnly,
            addedWithinDays = addedDays.toIntOrNull(),
            excludeRecentHistory = excludeRecent,
            playedWithinDays = playedDays.toIntOrNull(),
            notPlayedWithinDays = notPlayedDays.toIntOrNull(),
            minimumPlays = minimumPlays.toIntOrNull(),
            sortBy = sortBy,
            maxTracks = maxTracks.toIntOrNull() ?: 100,
            paused = initial?.paused ?: false,
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
                OutlinedTextField(
                    artists,
                    { artists = it; previewCount = null },
                    label = { Text("Artists (comma-separated)") },
                    supportingText = { Text("Matches the lead or credited artist") },
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Sort by: ${sortBy.label()}", modifier = Modifier.weight(1f))
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
                RuleCheckbox("Liked tracks only", likedOnly) { likedOnly = it; previewCount = null }
                RuleCheckbox("Exclude recent history", excludeRecent) { excludeRecent = it; previewCount = null }
                NumberRuleField("Added within days", addedDays) { addedDays = it; previewCount = null }
                NumberRuleField("Played within days", playedDays) { playedDays = it; previewCount = null }
                NumberRuleField("Not played within days", notPlayedDays) { notPlayedDays = it; previewCount = null }
                NumberRuleField("Minimum play count", minimumPlays) { minimumPlays = it; previewCount = null }
                NumberRuleField("Maximum tracks", maxTracks) { maxTracks = it; previewCount = null }
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
