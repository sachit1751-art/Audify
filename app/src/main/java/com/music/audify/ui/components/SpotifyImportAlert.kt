// Copyright (c) 2026 Sachit. All rights reserved.
// Project: AUDIFY
package com.music.audify.ui.components

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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.music.audify.R
import com.music.audify.data.model.PlaylistPrivacy
import com.music.audify.data.model.Song
import com.music.audify.data.spotify.SpotifyImportTrack
import com.music.audify.data.spotify.SpotifyImporter
import com.music.audify.data.spotify.SpotifyPlaylistFileFormat
import com.music.audify.data.spotify.SpotifyPlaylistFileParser
import dev.chrisbanes.haze.HazeState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

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
@Composable
fun SpotifyImportAlert(
    hazeState: HazeState,
    signedIn: Boolean,
    /** Called once the songs are matched; [privacy] is meaningful only when signed in. */
    onImported: (title: String, privacy: PlaylistPrivacy, songs: List<Song>) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val fetchingMessage = stringResource(R.string.spotify_import_fetching)
    val fileReadFailedMessage = stringResource(R.string.sachit_spotify_import_file_read_failed)
    val jsonUnsupportedMessage = stringResource(R.string.sachit_spotify_import_json_unsupported)
    val fileUnsupportedMessage = stringResource(R.string.sachit_spotify_import_file_unsupported)
    val defaultFileTitle = stringResource(R.string.sachit_spotify_import_file_default_title)
    val matchingFormat = stringResource(R.string.spotify_import_matching)
    val noSongsMatchedMessage = stringResource(R.string.spotify_import_none)
    val importFailedFormat = stringResource(R.string.spotify_import_failed)
    val invalidLinkMessage = stringResource(R.string.spotify_import_invalid)
    val scope = rememberCoroutineScope()
    var link by remember { mutableStateOf("") }
    var privacy by remember { mutableStateOf(PlaylistPrivacy.PRIVATE) }
    var selectedLanguage by remember { mutableStateOf("No Preference") }
    var customLanguageInput by remember { mutableStateOf("") }
    var working by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }
    var failed by remember { mutableStateOf(false) }
    var unmatched by remember { mutableStateOf<List<SpotifyImportTrack>?>(null) }
    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null && !working) {
            val effectiveLanguage = if (selectedLanguage == "Manual / Other") {
                customLanguageInput.trim().ifBlank { null }
            } else {
                selectedLanguage
            }
            working = true
            failed = false
            status = fetchingMessage
            scope.launch {
                try {
                    val (name, tracks) = withContext(Dispatchers.IO) {
                        val displayName = context.contentResolver.query(
                            uri,
                            arrayOf(OpenableColumns.DISPLAY_NAME),
                            null,
                            null,
                            null,
                        )?.use { cursor ->
                            if (cursor.moveToFirst()) cursor.getString(0) else null
                        }
                        val content = context.contentResolver.openInputStream(uri)
                            ?.bufferedReader(Charsets.UTF_8)
                            ?.use { it.readText() }
                            ?: error(fileReadFailedMessage)
                        val format = SpotifyPlaylistFileParser.detect(content, displayName)
                        val parsed = when (format) {
                            SpotifyPlaylistFileFormat.SPOTIFY_CSV,
                            SpotifyPlaylistFileFormat.SPOTIFY_TSV -> SpotifyPlaylistFileParser.parse(content, format)
                            SpotifyPlaylistFileFormat.JSON_UNSUPPORTED ->
                                error(jsonUnsupportedMessage)
                            SpotifyPlaylistFileFormat.M3U_UNSUPPORTED,
                            SpotifyPlaylistFileFormat.UNKNOWN ->
                                error(fileUnsupportedMessage)
                        }
                        val title = displayName
                            ?.substringAfterLast('/')
                            ?.substringBeforeLast('.', missingDelimiterValue = "")
                            ?.trim()
                            ?.takeIf { it.isNotBlank() }
                            ?: defaultFileTitle
                        title to parsed
                    }
                    val (songs, missed) = SpotifyImporter.resolveToSongs(tracks, effectiveLanguage) { done, total ->
                        status = String.format(Locale.getDefault(), matchingFormat, done, total)
                    }
                    if (songs.isEmpty()) error(noSongsMatchedMessage)
                    onImported(name, privacy, songs)
                    if (missed.isEmpty()) onDismiss() else unmatched = missed
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    status = String.format(Locale.getDefault(), importFailedFormat, e.message.orEmpty())
                    failed = true
                } finally {
                    working = false
                }
            }
        }
    }

    suspend fun resolveTracks(name: String, tracks: List<SpotifyImportTrack>, language: String?) {
        val (songs, missed) = SpotifyImporter.resolveToSongs(tracks, language) { done, total ->
            status = String.format(Locale.getDefault(), matchingFormat, done, total)
        }
        if (songs.isEmpty()) error(noSongsMatchedMessage)
        onImported(name, privacy, songs)
        if (missed.isEmpty()) onDismiss() else unmatched = missed
    }

    fun start() {
        val id = SpotifyImporter.extractPlaylistId(link)
        if (id == null) {
            status = invalidLinkMessage
            failed = true
            return
        }
        val effectiveLanguage = if (selectedLanguage == "Manual / Other") {
            customLanguageInput.trim().ifBlank { null }
        } else {
            selectedLanguage
        }
        working = true
        failed = false
        status = fetchingMessage
        scope.launch {
            try {
                val (name, tracks) = SpotifyImporter.fetchPlaylistTracks(id)
                resolveTracks(name, tracks, effectiveLanguage)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                status = String.format(Locale.getDefault(), importFailedFormat, e.message.orEmpty())
                failed = true
            }
            working = false
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
            val missed = unmatched
            if (missed != null) {
                Text(
                    text = pluralStringResource(
                        R.plurals.spotify_import_unmatched,
                        missed.size,
                        missed.size,
                    ),
                    modifier = Modifier.padding(top = 4.dp, bottom = 10.dp),
                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp, lineHeight = 17.sp),
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                )
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 200.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    missed.forEach { track ->
                        Text(
                            text = if (track.artist.isBlank()) track.title else "${track.title} · ${track.artist}",
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
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
                    text = stringResource(R.string.sachit_spotify_import_file_note),
                    modifier = Modifier.padding(top = 10.dp),
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                if (signedIn) {
                    Text(
                        text = stringResource(R.string.who_can_see_it),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 14.dp, bottom = 6.dp),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        PlaylistPrivacy.entries.forEach { option ->
                            val selected = option == privacy
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(percent = 50))
                                    .background(
                                        if (selected) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.surfaceVariant,
                                    )
                                    .clickable(enabled = !working) { privacy = option }
                                    .padding(vertical = 7.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = option.label,
                                    style = MaterialTheme.typography.labelLarge.copy(fontSize = 12.sp),
                                    color = if (selected) MaterialTheme.colorScheme.onPrimary
                                    else MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        }
        AlertRule()
        if (unmatched != null) {
            AlertAction(label = stringResource(R.string.done), emphasised = true, onClick = onDismiss)
        } else {
            AlertAction(
                label = stringResource(R.string.spotify_import_button),
                emphasised = true,
                onClick = ::start,
                enabled = link.isNotBlank() && !working,
            )
            AlertRule()
            AlertAction(
                label = stringResource(R.string.sachit_spotify_import_file_button),
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
