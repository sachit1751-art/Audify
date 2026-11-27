package com.music.audify.ui.components

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.music.audify.R
import com.music.audify.data.YtMusicRepository
import com.music.audify.data.model.PlaylistPrivacy
import com.music.audify.data.model.SearchFilter
import com.music.audify.data.model.SearchResult
import com.music.audify.data.model.Song
import com.music.audify.data.spotify.LocalPlaylistStore
import com.music.audify.data.spotify.PlaylistFileImporter
import com.music.audify.data.spotify.PlaylistImportEntry
import com.music.audify.data.spotify.PlaylistImportConfidence
import com.music.audify.data.spotify.PlaylistImportMatcher
import com.music.audify.data.spotify.PlaylistImportRow
import com.music.audify.data.spotify.PlaylistImportRowStatus
import com.music.audify.data.spotify.SpotifyImporter
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.materials.ExperimentalHazeMaterialsApi
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets

private val IMPORT_LANGUAGES = listOf(
    "Tamil",
    "Telugu",
    "Kannada",
    "Malayalam",
    "Hindi",
    "English",
    "Multilingual",
    "No Preference",
    "Manual / Other",
)

/**
 * Paste a Spotify playlist link, match its songs on YouTube Music, keep the result.
 *
 * The same frosted card as the addon editor, rather than a Material dialog.
 * The "who can see it" row exists only for [signedIn]: it is a YouTube Music
 * setting, and a playlist kept on this device has no audience to choose.
 * Songs that matched nothing are listed once the playlist is made, instead of
 * being dropped without a word.
 */
@OptIn(ExperimentalHazeMaterialsApi::class)
@Composable
fun SpotifyImportAlert(
    hazeState: HazeState,
    signedIn: Boolean,
    /** Called once the songs are matched; [privacy] is meaningful only when signed in. */
    onImported: (title: String, privacy: PlaylistPrivacy, songs: List<Song>) -> Unit,
    onMergeIntoLocal: (playlistId: String, songs: List<Song>) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val invalidMessage = stringResource(R.string.spotify_import_invalid)
    val fetchingMessage = stringResource(R.string.spotify_import_fetching)
    val matchingTemplate = stringResource(R.string.spotify_import_matching)
    val failedTemplate = stringResource(R.string.spotify_import_failed)
    var link by remember { mutableStateOf("") }
    var privacy by remember { mutableStateOf(PlaylistPrivacy.PRIVATE) }
    var selectedLanguage by remember { mutableStateOf("No Preference") }
    var customLanguageInput by remember { mutableStateOf("") }
    var working by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }
    var failed by remember { mutableStateOf(false) }
    var previewTitle by remember { mutableStateOf("") }
    var previewRows by remember { mutableStateOf<List<PlaylistImportRow>?>(null) }
    var excludedRows by remember { mutableStateOf<Set<Int>>(emptySet()) }
    var selectedCandidates by remember { mutableStateOf<Map<Int, String>>(emptyMap()) }
    var mergeExisting by remember { mutableStateOf(false) }
    var mergeTargetId by remember { mutableStateOf<String?>(null) }
    var mergeMenuExpanded by remember { mutableStateOf(false) }
    val localPlaylists by LocalPlaylistStore.playlists.collectAsStateWithLifecycle()
    val mergeablePlaylists = localPlaylists.filterNot { it.id == LocalPlaylistStore.FAVORITES_PLAYLIST_ID }

    suspend fun preparePreview(entries: List<PlaylistImportEntry>, title: String) {
        val effectiveLanguage = if (selectedLanguage == "Manual / Other") {
            customLanguageInput.trim().ifBlank { null }
        } else {
            selectedLanguage.takeUnless { it == "No Preference" || it == "Multilingual" }
        }
        val progressJobs = mutableListOf<Job>()
        val progressLock = Any()
        val matched = PlaylistImportMatcher.match(
            entries = entries,
            search = { query ->
                val searchQuery = listOf(query, effectiveLanguage).filterNotNull()
                    .filter(String::isNotBlank).joinToString(" ")
                YtMusicRepository.search(searchQuery, SearchFilter.SONGS).getOrNull()
                    ?.filterIsInstance<SearchResult.Track>()?.map { it.song }.orEmpty()
            },
            onProgress = { done, total ->
                if (done == total || done % 5 == 0) {
                    val job = scope.launch {
                        status = matchingTemplate.replace("%1\$d", done.toString()).replace("%2\$d", total.toString())
                    }
                    synchronized(progressLock) { progressJobs += job }
                }
            },
        )
        synchronized(progressLock) { progressJobs.toList() }.joinAll()
        previewTitle = title.ifBlank { "Imported Playlist" }
        previewRows = matched
        excludedRows = emptySet()
        selectedCandidates = matched.mapNotNull { row ->
            row.candidates.singleOrNull()?.let { row.source.sourceRow to it.song.videoId }
        }.toMap()
        mergeExisting = false
        mergeTargetId = null
        status = null
        failed = false
    }

    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            working = true
            failed = false
            status = "Reading playlist file…"
            scope.launch {
                try {
                    val (name, content) = withContext(Dispatchers.IO) { readPlaylistDocument(context, uri) }
                    val document = PlaylistFileImporter.parse(content, name)
                    status = "Preparing preview…"
                    preparePreview(document.entries, document.playlistName ?: name.substringBeforeLast('.'))
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (failure: Exception) {
                    status = failedTemplate.replace("%1\$s", failure.message.orEmpty())
                    failed = true
                } finally {
                    working = false
                }
            }
        }
    }

    fun start() {
        val id = SpotifyImporter.extractPlaylistId(link)
        if (id == null) {
            status = invalidMessage
            failed = true
            return
        }
        working = true
        failed = false
        status = fetchingMessage
        scope.launch {
            try {
                val (name, tracks) = SpotifyImporter.fetchPlaylistTracks(id)
                val entries = tracks.mapIndexed { index, track ->
                    PlaylistImportEntry(sourceRow = index + 1, title = track.title, artist = track.artist)
                }
                preparePreview(entries, name)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                status = failedTemplate.replace("%1\$s", e.message.orEmpty())
                failed = true
            }
            working = false
        }
    }

    fun commitPreview() {
        val rows = previewRows ?: return
        val songs = rows.mapNotNull { row ->
            if (row.source.sourceRow in excludedRows) return@mapNotNull null
            val videoId = selectedCandidates[row.source.sourceRow] ?: return@mapNotNull null
            row.candidates.firstOrNull { it.song.videoId == videoId }?.song
        }
        if (songs.isEmpty()) {
            status = "Select at least one matched track to continue."
            failed = true
            return
        }
        if (mergeExisting && mergeTargetId == null) {
            status = "Choose a local playlist to merge into."
            failed = true
            return
        }
        try {
            if (mergeExisting) onMergeIntoLocal(mergeTargetId!!, songs)
            else onImported(previewTitle, privacy, songs)
            onDismiss()
        } catch (failure: Exception) {
            status = failedTemplate.replace("%1\$s", failure.message.orEmpty())
            failed = true
        }
    }

    AlertScaffold(hazeState = hazeState, onDismiss = { if (!working) onDismiss() }) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 520.dp)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 19.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = stringResource(R.string.spotify_import_title),
                style = MaterialTheme.typography.bodyLarge.copy(fontSize = 17.sp, fontWeight = FontWeight.W600),
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
            )
            val rows = previewRows
            if (rows != null) {
                val matchedCount = rows.count { it.status == PlaylistImportRowStatus.MATCHED }
                val ambiguousCount = rows.count { it.status == PlaylistImportRowStatus.AMBIGUOUS }
                val duplicateCount = rows.count { it.status == PlaylistImportRowStatus.DUPLICATE_OCCURRENCE }
                val unmatchedCount = rows.count { it.status == PlaylistImportRowStatus.UNMATCHED }
                val invalidCount = rows.count { it.status == PlaylistImportRowStatus.INVALID }
                val selectedCount = rows.count { row ->
                    row.source.sourceRow !in excludedRows && selectedCandidates[row.source.sourceRow] != null
                }
                Text(
                    text = previewTitle,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                )
                if (!mergeExisting) {
                    PillTextField(
                        value = previewTitle,
                        onValueChange = { previewTitle = it.take(PlaylistFileImporter.MAX_FIELD_CHARS) },
                        placeholder = "Playlist name",
                        enabled = !working,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text, imeAction = ImeAction.Done),
                    )
                }
                Text(
                    text = "${rows.size} source rows · $matchedCount matched · $ambiguousCount ambiguous · " +
                        "$duplicateCount duplicates · $unmatchedCount unmatched · $invalidCount invalid",
                    modifier = Modifier.padding(top = 5.dp, bottom = 10.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 230.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    items(rows, key = { it.source.sourceRow }) { row ->
                        val sourceRow = row.source.sourceRow
                        val candidates = row.candidates
                        val selectedId = selectedCandidates[sourceRow]
                        val selectedCandidate = candidates.firstOrNull { it.song.videoId == selectedId }
                        val included = selectedId != null && sourceRow !in excludedRows
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(
                                checked = included,
                                enabled = selectedId != null,
                                onCheckedChange = { checked ->
                                    excludedRows = if (checked) excludedRows - sourceRow else excludedRows + sourceRow
                                },
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = row.source.title.ifBlank { "Untitled source row $sourceRow" },
                                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.W600),
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                                val byline = row.source.artist.takeIf(String::isNotBlank)
                                    ?.let { " · $it" }.orEmpty()
                                Text(
                                    text = when {
                                        sourceRow in excludedRows -> "Excluded$byline"
                                        row.status == PlaylistImportRowStatus.AMBIGUOUS && selectedCandidate != null ->
                                            "Ambiguous · candidate selected$byline"
                                        else -> statusLabel(row.status) + byline
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = when (row.status) {
                                        PlaylistImportRowStatus.MATCHED, PlaylistImportRowStatus.DUPLICATE_OCCURRENCE ->
                                            MaterialTheme.colorScheme.primary
                                        PlaylistImportRowStatus.AMBIGUOUS, PlaylistImportRowStatus.UNMATCHED,
                                        PlaylistImportRowStatus.INVALID -> MaterialTheme.colorScheme.error
                                    },
                                )
                                val detail = selectedCandidate?.let {
                                    "${if (it.confidence == PlaylistImportConfidence.STRONG) "Strong match" else "Review match"} · ${it.evidence}"
                                } ?: row.reason
                                if (detail != null) {
                                    Text(
                                        text = detail,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                val rowReason = row.reason
                                if (selectedCandidate != null && rowReason != null) {
                                    Text(
                                        text = rowReason,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                if (candidates.size > 1) {
                                    Text(
                                        text = selectedCandidate?.let { "Use: ${it.song.title} · tap to change" }
                                            ?: "Choose a match · tap to cycle candidates",
                                        modifier = Modifier.clickable {
                                            val current = candidates.indexOfFirst { it.song.videoId == selectedId }
                                            val next = candidates[(current + 1).mod(candidates.size)]
                                            selectedCandidates = selectedCandidates + (sourceRow to next.song.videoId)
                                            excludedRows = excludedRows - sourceRow
                                        },
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.W600),
                                        color = MaterialTheme.colorScheme.primary,
                                    )
                                }
                            }
                        }
                    }
                }

                Text(
                    text = "Import destination",
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 6.dp),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(
                        modifier = Modifier.weight(1f).clip(RoundedCornerShape(12.dp))
                            .background(if (!mergeExisting) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant)
                            .clickable { mergeExisting = false; mergeTargetId = null }
                            .padding(horizontal = 10.dp, vertical = 9.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("Create new", style = MaterialTheme.typography.labelMedium,
                            color = if (!mergeExisting) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Box(
                        modifier = Modifier.weight(1f).clip(RoundedCornerShape(12.dp))
                            .background(if (mergeExisting) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant)
                            .clickable(enabled = mergeablePlaylists.isNotEmpty()) { mergeExisting = true }
                            .padding(horizontal = 10.dp, vertical = 9.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("Merge into local", style = MaterialTheme.typography.labelMedium,
                            color = if (mergeExisting) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                if (mergeExisting) {
                    val target = mergeablePlaylists.firstOrNull { it.id == mergeTargetId }
                    Box(modifier = Modifier.fillMaxWidth().padding(top = 6.dp)) {
                        Text(
                            text = target?.title ?: "Choose a local playlist",
                            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .clickable { mergeMenuExpanded = true }
                                .padding(12.dp),
                            color = MaterialTheme.colorScheme.onSurface,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        DropdownMenu(expanded = mergeMenuExpanded, onDismissRequest = { mergeMenuExpanded = false }) {
                            mergeablePlaylists.forEach { playlist ->
                                DropdownMenuItem(
                                    text = { Text(playlist.title) },
                                    onClick = { mergeTargetId = playlist.id; mergeMenuExpanded = false },
                                )
                            }
                        }
                    }
                    Text(
                        text = "Merge adds missing tracks once; existing order is retained. Only local playlists are merge targets.",
                        modifier = Modifier.padding(top = 4.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (signedIn && !mergeExisting) {
                    Text(
                        text = stringResource(R.string.who_can_see_it),
                        modifier = Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 6.dp),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        PlaylistPrivacy.entries.forEach { option ->
                            val selected = option == privacy
                            Box(
                                modifier = Modifier.weight(1f).clip(RoundedCornerShape(percent = 50))
                                    .background(if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant)
                                    .clickable { privacy = option }.padding(vertical = 7.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(option.label, style = MaterialTheme.typography.labelLarge.copy(fontSize = 12.sp),
                                    color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
                Text(
                    text = "$selectedCount tracks selected. Ambiguous, unmatched and invalid rows stay out until you resolve or select them.",
                    modifier = Modifier.padding(top = 8.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                if (status != null) {
                    Text(
                        text = status.orEmpty(),
                        modifier = Modifier.padding(top = 4.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (failed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                        textAlign = TextAlign.Center,
                    )
                }
            } else {
                Text(
                    text = status ?: stringResource(
                        if (signedIn) R.string.spotify_import_description
                        else R.string.spotify_import_description_local,
                    ),
                    modifier = Modifier.padding(top = 4.dp, bottom = 14.dp),
                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp, lineHeight = 17.sp),
                    color = when {
                        status == null -> MaterialTheme.colorScheme.onSurface
                        failed -> MaterialTheme.colorScheme.error
                        else -> MaterialTheme.colorScheme.primary
                    },
                    textAlign = TextAlign.Center,
                )
                PillTextField(
                    value = link,
                    onValueChange = {
                        link = it
                        if (failed) {
                            status = null
                            failed = false
                        }
                    },
                    placeholder = stringResource(R.string.spotify_import_hint),
                    enabled = !working,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { if (link.isNotBlank() && !working) start() }),
                )
                Text(
                    text = "Or choose a playlist file (CSV, TSV, M3U, M3U8, Audify JSON)",
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.W600),
                    color = MaterialTheme.colorScheme.primary,
                    textAlign = TextAlign.Center,
                )
                Text(
                    text = stringResource(R.string.spotify_import_language_preference),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 14.dp, bottom = 6.dp),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    IMPORT_LANGUAGES.forEach { lang ->
                        val selected = lang == selectedLanguage
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(percent = 50))
                                .background(
                                    if (selected) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.surfaceVariant,
                                )
                                .clickable(enabled = !working) {
                                    selectedLanguage = lang
                                }
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = lang,
                                style = MaterialTheme.typography.labelLarge.copy(fontSize = 11.sp),
                                color = if (selected) MaterialTheme.colorScheme.onPrimary
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                if (selectedLanguage == "Manual / Other") {
                    Spacer(Modifier.height(8.dp))
                    PillTextField(
                        value = customLanguageInput,
                        onValueChange = { customLanguageInput = it },
                        placeholder = stringResource(R.string.spotify_import_language_hint),
                        enabled = !working,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text, imeAction = ImeAction.Done),
                    )
                }
            }
        }
        AlertRule()
        if (previewRows != null) {
            val rows = previewRows.orEmpty()
            val selectedCount = rows.count { row ->
                row.source.sourceRow !in excludedRows && selectedCandidates[row.source.sourceRow] != null
            }
            AlertAction(
                label = "Import $selectedCount tracks",
                emphasised = true,
                onClick = ::commitPreview,
                enabled = selectedCount > 0 && (!mergeExisting || mergeTargetId != null) && !working,
            )
            AlertRule()
            AlertAction(
                label = "Choose another source",
                emphasised = false,
                onClick = { previewRows = null; status = null; failed = false },
                enabled = !working,
            )
        } else {
            AlertAction(
                label = "Preview Spotify link",
                emphasised = true,
                onClick = ::start,
                enabled = link.isNotBlank() && !working,
            )
            AlertRule()
            AlertAction(
                label = "Choose playlist file",
                emphasised = false,
                onClick = { filePicker.launch(arrayOf("*/*")) },
                enabled = !working,
            )
            AlertRule()
            AlertAction(
                label = stringResource(R.string.cancel),
                emphasised = false,
                onClick = onDismiss,
                enabled = !working,
            )
        }
    }
}

private fun statusLabel(status: PlaylistImportRowStatus): String = when (status) {
    PlaylistImportRowStatus.MATCHED -> "Matched"
    PlaylistImportRowStatus.AMBIGUOUS -> "Ambiguous"
    PlaylistImportRowStatus.UNMATCHED -> "Unmatched"
    PlaylistImportRowStatus.DUPLICATE_OCCURRENCE -> "Duplicate occurrence"
    PlaylistImportRowStatus.INVALID -> "Invalid"
}

/** Reads only the selected SAF document; embedded paths are never opened. */
private fun readPlaylistDocument(context: Context, uri: Uri): Pair<String, String> {
    val displayName = context.contentResolver.query(
        uri,
        arrayOf(OpenableColumns.DISPLAY_NAME),
        null,
        null,
        null,
    )?.use { cursor ->
        if (cursor.moveToFirst()) cursor.getString(0) else null
    }?.takeIf(String::isNotBlank) ?: uri.lastPathSegment?.substringAfterLast('/') ?: "playlist"

    val output = ByteArrayOutputStream()
    context.contentResolver.openInputStream(uri)?.use { input ->
        val buffer = ByteArray(8 * 1024)
        var total = 0
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            total += count
            require(total <= PlaylistFileImporter.MAX_INPUT_BYTES) {
                "Playlist file exceeds the 5 MiB import limit"
            }
            output.write(buffer, 0, count)
        }
    } ?: error("The selected document provider could not open this file")

    val bytes = output.toByteArray()
    val (charset, offset) = when {
        bytes.startsWith(byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte())) -> StandardCharsets.UTF_8 to 3
        bytes.startsWith(byteArrayOf(0xFF.toByte(), 0xFE.toByte())) -> StandardCharsets.UTF_16LE to 2
        bytes.startsWith(byteArrayOf(0xFE.toByte(), 0xFF.toByte())) -> StandardCharsets.UTF_16BE to 2
        else -> StandardCharsets.UTF_8 to 0
    }
    val text = try {
        charset.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
            .decode(ByteBuffer.wrap(bytes, offset, bytes.size - offset)).toString()
    } catch (malformedUtf8: Exception) {
        require(offset == 0 && charset == StandardCharsets.UTF_8) { "Playlist file has invalid text encoding" }
        Charset.forName("windows-1252").newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
            .decode(ByteBuffer.wrap(bytes)).toString()
    }
    require('\u0000' !in text) { "Selected file is not a supported text playlist" }
    return displayName to text
}

private fun ByteArray.startsWith(prefix: ByteArray): Boolean =
    size >= prefix.size && prefix.indices.all { this[it] == prefix[it] }
