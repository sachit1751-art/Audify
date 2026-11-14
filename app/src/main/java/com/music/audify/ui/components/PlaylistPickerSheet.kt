package com.music.audify.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.ime
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLocale
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import kotlinx.coroutines.delay
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.rounded.AddPhotoAlternate
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import com.music.audify.ui.icons.SfIcons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ModalBottomSheetProperties
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.music.audify.R
import com.music.audify.data.model.PlaylistPrivacy
import com.music.audify.data.model.ROW_ART_PX
import com.music.audify.data.model.Song
import com.music.audify.data.model.UserPlaylist
import com.music.audify.data.model.artworkAt
import com.music.audify.ui.components.thumbnailBorder
import com.music.audify.ui.icons.AudifyIcons
import java.util.Locale
import kotlinx.coroutines.launch

/**
 * Where a track goes: one of the account's playlists, or a new one.
 *
 * Two panels in one sheet rather than a sheet that opens a dialog. Creating a
 * playlist here is nearly always in service of adding the track that opened
 * this — so the form comes back to the same place, and the create request
 * carries the track with it instead of leaving a new empty playlist behind
 * for the user to add to a second time. The sheet is full height from the
 * start, so swapping panels never moves the window.
 *
 * [song] is null when the flow started from the Library tab rather than from a
 * track, which is the one case where the header has no track to draw and
 * "New playlist" is the whole point of the sheet.
 *
 * Playlists are ticked rather than picked: one track often belongs in more
 * than one list, and a sheet that closed on the first tap meant opening it
 * again for each. [onAdd] gets every ticked playlist at once.
 *
 * [offline] is the same sheet over the device's own playlists, for a track
 * that is a file on it: nothing is saved to the account, so the new-playlist
 * form says where it goes instead and has no visibility to pick.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistPickerSheet(
    playlists: List<UserPlaylist>,
    loading: Boolean,
    onAdd: (List<UserPlaylist>) -> Unit,
    onCreate: (String, PlaylistPrivacy, Uri?) -> Unit,
    onDismiss: () -> Unit,
    song: Song? = null,
    startCreating: Boolean = false,
    offline: Boolean = false,
) {
    var creating by remember { mutableStateOf(startCreating) }
    // By id rather than by value: the list is re-fetched under an open sheet,
    // and a refreshed row must stay ticked.
    var selected by remember { mutableStateOf(emptySet<String>()) }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    // Once only: the sheet is still up, and tappable, while it slides away, and
    // a second tap on the tick would file the track a second time.
    var closing by remember { mutableStateOf(false) }
    val closeAfter: (() -> Unit) -> Unit = { action ->
        if (!closing) {
            closing = true
            action()
            scope.launch { sheetState.hide() }.invokeOnCompletion { onDismiss() }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        // It reaches the top of the screen, where corners would only cut two
        // notches out of the status bar's background.
        shape = RectangleShape,
        containerColor = MaterialTheme.colorScheme.background,
        dragHandle = null,
        contentWindowInsets = { WindowInsets(0, 0, 0, 0) },
        // Back is this sheet's to carry out, below: left to M3 it closes the whole
        // sheet from either panel, and takes the ticks with it.
        properties = ModalBottomSheetProperties(shouldDismissOnBackPress = false),
    ) {
        BackHandler {
            if (creating && !startCreating) creating = false else closeAfter {}
        }

        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding(),
        ) {
            if (creating) {
                NewPlaylistForm(
                    // Nowhere to go back to when the sheet opened straight onto
                    // the form, so that bar closes it instead.
                    onBack = if (startCreating) null else ({ creating = false }),
                    onClose = { closeAfter {} },
                    onCreate = { name, privacy, cover -> closeAfter { onCreate(name, privacy, cover) } },
                    offline = offline,
                )
            } else {
                SheetTopBar(
                    leadingIcon = SfIcons.Close,
                    leadingDescription = stringResource(R.string.close),
                    onLeading = { closeAfter {} },
                    confirmDescription = if (selected.isEmpty()) {
                        stringResource(R.string.add_to_playlist)
                    } else {
                        pluralStringResource(R.plurals.add_to_playlists_count, selected.size, selected.size)
                    },
                    confirmEnabled = selected.isNotEmpty(),
                    onConfirm = { closeAfter { onAdd(playlists.filter { it.playlistId in selected }) } },
                    title = {
                        Text(
                            text = stringResource(
                                if (song != null) R.string.add_to_playlist else R.string.your_playlists,
                            ),
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onBackground,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .weight(1f)
                                .padding(start = 8.dp),
                        )
                    },
                )
                // Lagu yang sedang ditambahkan, sebagai kartu.
                if (song != null) {
                    SheetTrackHeader(
                        song,
                        modifier = Modifier
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)),
                    )
                }

                NewPlaylistRow(onClick = { creating = true })

                when {
                    // Only while there is nothing to show: re-fetching under a
                    // list that is already up would replace it with a spinner
                    // for no gain.
                    loading && playlists.isEmpty() -> Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(120.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator(
                            strokeWidth = 2.5.dp,
                            modifier = Modifier.size(26.dp),
                        )
                    }

                    playlists.isEmpty() -> Text(
                        text = stringResource(R.string.no_playlists_yet),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 22.dp, vertical = 18.dp),
                    )

                    else -> {
                        LazyColumn(
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(top = 4.dp, bottom = 8.dp),
                        ) {
                            items(playlists, key = { it.playlistId }) { playlist ->
                                val ticked = playlist.playlistId in selected
                                PlaylistRow(
                                    playlist = playlist,
                                    selected = ticked,
                                    onClick = {
                                        selected = if (ticked) selected - playlist.playlistId else selected + playlist.playlistId
                                    },
                                )
                            }
                        }
                        // Tombol di bawah: dekat jempol, muncul begitu ada yang dipilih.
                        if (selected.isNotEmpty()) {
                            Button(
                                onClick = { closeAfter { onAdd(playlists.filter { it.playlistId in selected }) } },
                                shape = RoundedCornerShape(26.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 22.dp, vertical = 12.dp)
                                    .height(52.dp),
                            ) {
                                Text(
                                    text = pluralStringResource(
                                        R.plurals.add_to_playlists_count,
                                        selected.size,
                                        selected.size,
                                    ),
                                    style = MaterialTheme.typography.titleMedium,
                                )
                            }
                        }
                        Spacer(Modifier.navigationBarsPadding())
                    }
                }
            }
        }
    }
}

/**
 * Way out on the left, tick on the right, and whatever the panel wants to say
 * in between: the sheet's two panels share these, so the tick stays put when
 * one swaps for the other.
 */
@Composable
private fun SheetTopBar(
    leadingIcon: ImageVector,
    leadingDescription: String,
    onLeading: () -> Unit,
    confirmDescription: String,
    confirmEnabled: Boolean,
    onConfirm: () -> Unit,
    title: @Composable RowScope.() -> Unit = { Spacer(Modifier.weight(1f)) },
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onLeading) {
            Icon(
                leadingIcon,
                contentDescription = leadingDescription,
                tint = MaterialTheme.colorScheme.onBackground,
            )
        }
        title()
        IconButton(onClick = onConfirm, enabled = confirmEnabled) {
            Icon(
                SfIcons.Check,
                contentDescription = confirmDescription,
                tint = if (confirmEnabled) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                },
            )
        }
    }
}

/** "New playlist": tile plus sebesar thumbnail playlist, sejajar dengan daftar di bawahnya. */
@Composable
private fun NewPlaylistRow(onClick: () -> Unit) {
    val primary = MaterialTheme.colorScheme.primary
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 2.dp)
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(52.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(primary.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = AudifyIcons.Plus,
                contentDescription = null,
                tint = primary,
                modifier = Modifier.size(26.dp),
            )
        }
        Spacer(Modifier.width(14.dp))
        Text(
            text = stringResource(R.string.new_playlist),
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onBackground,
        )
    }
}

/** Baris playlist: seluruh baris ter-highlight saat dipilih, bukan hanya lingkarannya. */
@Composable
private fun PlaylistRow(playlist: UserPlaylist, selected: Boolean, onClick: () -> Unit) {
    val primary = MaterialTheme.colorScheme.primary
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 2.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(if (selected) primary.copy(alpha = 0.12f) else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AsyncImage(
            model = playlist.thumbnailUrl.artworkAt(ROW_ART_PX),
            contentDescription = null,
            modifier = Modifier
                .size(52.dp)
                .clip(RoundedCornerShape(10.dp))
                .thumbnailBorder(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
        )
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = playlist.title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (playlist.subtitle.isNotBlank()) {
                Text(
                    text = playlist.subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Icon(
            imageVector = if (selected) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked,
            contentDescription = null,
            tint = if (selected) primary else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(26.dp),
        )
    }
}

/**
 * Name and visibility, and nothing else. YouTube also takes a description,
 * which nobody fills in from a phone at the moment of saving a song.
 */
@Composable
private fun NewPlaylistForm(
    onBack: (() -> Unit)?,
    onClose: () -> Unit,
    onCreate: (String, PlaylistPrivacy, Uri?) -> Unit,
    offline: Boolean,
) {
    var name by remember { mutableStateOf("") }
    var privacy by remember { mutableStateOf(PlaylistPrivacy.PRIVATE) }
    var cover by remember { mutableStateOf<Uri?>(null) }
    var nameFocused by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    val pickCover = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { picked ->
        if (picked != null) cover = picked
    }

    // The form exists to be typed into; opening it with the keyboard already
    // up saves the tap that would otherwise always follow.
    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    val submit: () -> Unit = {
        if (name.isNotBlank()) {
            focusManager.clearFocus()
            onCreate(name, privacy, if (offline) null else cover)
        }
    }

    Column(
        Modifier
            .fillMaxWidth()
            .imePadding()
            .verticalScroll(rememberScrollState()),
    ) {
        SheetTopBar(
            leadingIcon = if (onBack != null) SfIcons.ArrowBack else SfIcons.Close,
            leadingDescription = stringResource(if (onBack != null) R.string.back else R.string.close),
            onLeading = {
                focusManager.clearFocus()
                (onBack ?: onClose)()
            },
            confirmDescription = stringResource(R.string.create_playlist),
            confirmEnabled = name.isNotBlank(),
            onConfirm = submit,
            title = {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.new_playlist),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                    Text(
                        text = stringResource(
                            if (offline) R.string.saved_on_this_device else R.string.saved_to_youtube_music_account,
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
        )
        HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outline)

        // Cover dari galeri, dengan pratinjaunya langsung di kotak ini.
        if (!offline) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 24.dp),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier
                        .size(176.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .clickable {
                            focusManager.clearFocus()
                            pickCover.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                            )
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    val picked = cover
                    if (picked != null) {
                        AsyncImage(
                            model = picked,
                            contentDescription = stringResource(R.string.playlist_cover_change),
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(),
                        )
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(10.dp)
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(Color.Black.copy(alpha = 0.55f)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                Icons.Rounded.Edit,
                                contentDescription = stringResource(R.string.playlist_cover_change),
                                tint = Color.White,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(10.dp)
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(Color.Black.copy(alpha = 0.55f))
                                .clickable { cover = null },
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                Icons.Rounded.Close,
                                contentDescription = stringResource(R.string.playlist_cover_remove),
                                tint = Color.White,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    } else {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                Icons.Rounded.AddPhotoAlternate,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(40.dp),
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                text = stringResource(R.string.playlist_cover_add),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 22.dp, vertical = 22.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .border(
                    1.5.dp,
                    if (nameFocused) MaterialTheme.colorScheme.primary else Color.Transparent,
                    RoundedCornerShape(14.dp),
                )
                .padding(horizontal = 14.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.weight(1f)) {
                if (name.isEmpty()) {
                    Text(
                        text = stringResource(R.string.playlist_name),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                BasicTextField(
                    value = name,
                    onValueChange = { name = it },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(
                        color = MaterialTheme.colorScheme.onBackground,
                    ),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { submit() }),
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester)
                        .onFocusChanged { nameFocused = it.isFocused },
                )
            }
            if (name.isNotEmpty()) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .clickable { name = "" },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Rounded.Close,
                        contentDescription = stringResource(R.string.clear_name),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }

        // A playlist on the device is seen by whoever holds the device.
        if (!offline) {
            SheetHeading(stringResource(R.string.who_can_see_it).uppercase(LocalLocale.current.platformLocale))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 22.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                PlaylistPrivacy.entries.forEach { option ->
                    PrivacyOptionRow(
                        option = option,
                        selected = option == privacy,
                        onClick = {
                            focusManager.clearFocus()
                            privacy = option
                        },
                    )
                }
            }
        }

        Button(
            onClick = submit,
            enabled = name.isNotBlank(),
            shape = RoundedCornerShape(26.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 22.dp, vertical = 22.dp)
                .height(52.dp),
        ) {
            Text(
                text = stringResource(R.string.create_playlist),
                style = MaterialTheme.typography.titleMedium,
            )
        }
        Spacer(Modifier.navigationBarsPadding())
    }
}

private val PlaylistPrivacy.hint: Int
    get() = when (this) {
        PlaylistPrivacy.PRIVATE -> R.string.playlist_privacy_private_hint
        PlaylistPrivacy.UNLISTED -> R.string.playlist_privacy_unlisted_hint
        PlaylistPrivacy.PUBLIC -> R.string.playlist_privacy_public_hint
    }

/** Satu baris penuh per pilihan: label tidak lagi terpotong ("Publi / c") di layar sempit. */
@Composable
private fun PrivacyOptionRow(option: PlaylistPrivacy, selected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(14.dp)
    val primary = MaterialTheme.colorScheme.primary
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(if (selected) primary.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surfaceVariant)
            .border(1.5.dp, if (selected) primary else Color.Transparent, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = option.icon,
            contentDescription = null,
            tint = if (selected) primary else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(22.dp),
        )
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = option.label,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                text = stringResource(option.hint),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Icon(
            imageVector = if (selected) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked,
            contentDescription = null,
            tint = if (selected) primary else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(22.dp),
        )
    }
}

/**
 * Renaming one of the account's playlists: what [BrowseActionsMenu]'s Rename
 * hands on to, raised by the host as a sheet of its own — a text field and its
 * keyboard have no place on a floating menu. Same reason the create form lives
 * inside [PlaylistPickerSheet].
 */
@Composable
internal fun RenamePlaylistForm(
    playlist: UserPlaylist,
    onBack: () -> Unit,
    onRename: (String) -> Unit,
    modifier: Modifier = Modifier,
    /** Ganti cover dari galeri; Uri null = hapus cover kustom. Null menyembunyikan bagian cover. */
    onChangeCover: ((Uri?) -> Unit)? = null,
) {
    var name by remember { mutableStateOf(playlist.title) }
    var cover by remember { mutableStateOf<Uri?>(null) }
    var coverRemoved by remember { mutableStateOf(false) }
    var nameFocused by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    val pickCover = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { picked ->
        if (picked != null) {
            cover = picked
            coverRemoved = false
        }
    }

    // Tombol simpan ikut digulirkan ke area yang terlihat saat keyboard naik,
    // sehingga kolom nama (tepat di atasnya) tidak tertutup keyboard.
    val saveBring = remember { BringIntoViewRequester() }
    val density = LocalDensity.current
    val imeInsets = WindowInsets.ime
    var imeUp by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
        // Pengaman: tidak memakai delay tetap. Tinggi keyboard dipantau, dan
        // setiap kali berubah (animasi naik/turun, ganti keyboard, gesture
        // insets) scroll dibatalkan lalu diulang. Tombol Save baru
        // digulirkan ke area terlihat setelah tinggi keyboard benar-benar
        // berhenti berubah, sehingga kolom nama di atasnya ikut terlihat.
        snapshotFlow { imeInsets.getBottom(density) }
            .distinctUntilChanged()
            .collectLatest { bottom ->
                imeUp = bottom > 0
                if (bottom > 0) {
                    delay(120)
                    saveBring.bringIntoView()
                }
            }
    }
    // Pratinjau cover mengecil saat keyboard naik supaya kolom nama dan Save
    // selalu muat di area sisa, juga di layar pendek.
    val coverSize by animateDpAsState(
        targetValue = if (imeUp) 80.dp else 128.dp,
        label = "renameCoverSize",
    )

    val changed = cover != null || coverRemoved || name != playlist.title
    val submit: () -> Unit = {
        if (name.isNotBlank() && changed) {
            focusManager.clearFocus()
            val picked = cover
            if (onChangeCover != null) {
                if (picked != null) onChangeCover(picked) else if (coverRemoved) onChangeCover(null)
            }
            onRename(name)
        }
    }

    // The form opens with the keyboard already up, which on a bottom sheet
    // would otherwise sit over the button the form exists to reach.
    Column(
        modifier
            .fillMaxWidth()
            .imePadding()
            .verticalScroll(rememberScrollState()),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 8.dp, end = 22.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    SfIcons.ArrowBack,
                    contentDescription = stringResource(R.string.back),
                    tint = MaterialTheme.colorScheme.onBackground,
                )
            }
            Text(
                text = stringResource(R.string.rename_playlist),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.weight(1f),
            )
        }
        HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outline)

        // Cover: pratinjau langsung, ketuk untuk memilih dari galeri.
        if (onChangeCover != null) {
            val shown: Any? = when {
                cover != null -> cover
                coverRemoved -> null
                else -> playlist.thumbnailUrl
            }
            // Hanya cover kustom (dari galeri) yang bisa dihapus.
            val removable = cover != null ||
                (!coverRemoved && playlist.thumbnailUrl?.startsWith("file:") == true)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 20.dp),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier
                        .size(coverSize)
                        .clip(RoundedCornerShape(18.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .clickable {
                            focusManager.clearFocus()
                            pickCover.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                            )
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    if (shown != null) {
                        AsyncImage(
                            model = shown,
                            contentDescription = stringResource(R.string.playlist_cover_change),
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(),
                        )
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(8.dp)
                                .size(30.dp)
                                .clip(CircleShape)
                                .background(Color.Black.copy(alpha = 0.55f)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                Icons.Rounded.Edit,
                                contentDescription = stringResource(R.string.playlist_cover_change),
                                tint = Color.White,
                                modifier = Modifier.size(16.dp),
                            )
                        }
                    } else {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                Icons.Rounded.AddPhotoAlternate,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(34.dp),
                            )
                            Spacer(Modifier.height(6.dp))
                            Text(
                                text = stringResource(R.string.playlist_cover_add),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    if (removable) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(8.dp)
                                .size(30.dp)
                                .clip(CircleShape)
                                .background(Color.Black.copy(alpha = 0.55f))
                                .clickable {
                                    if (cover != null) cover = null else coverRemoved = true
                                },
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                Icons.Rounded.Close,
                                contentDescription = stringResource(R.string.playlist_cover_remove),
                                tint = Color.White,
                                modifier = Modifier.size(16.dp),
                            )
                        }
                    }
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 22.dp, vertical = 20.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .border(
                    1.5.dp,
                    if (nameFocused) MaterialTheme.colorScheme.primary else Color.Transparent,
                    RoundedCornerShape(14.dp),
                )
                .padding(horizontal = 14.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BasicTextField(
                value = name,
                onValueChange = { name = it },
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge.copy(
                    color = MaterialTheme.colorScheme.onBackground,
                ),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { submit() }),
                modifier = Modifier
                    .weight(1f)
                    .focusRequester(focusRequester)
                    .onFocusChanged { nameFocused = it.isFocused },
            )
            if (name.isNotEmpty()) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .clickable { name = "" },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Rounded.Close,
                        contentDescription = stringResource(R.string.clear_name),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }
        Button(
            onClick = submit,
            enabled = name.isNotBlank() && changed,
            shape = RoundedCornerShape(26.dp),
            modifier = Modifier
                .bringIntoViewRequester(saveBring)
                .fillMaxWidth()
                .padding(horizontal = 22.dp)
                .height(52.dp),
        ) {
            Text(
                text = stringResource(R.string.save_name),
                style = MaterialTheme.typography.titleMedium,
            )
        }
        Spacer(Modifier.height(28.dp))
    }
}

/**
 * The glyph that says what a visibility actually means — a padlock, a shared
 * link, a globe. Three words that all sound like degrees of the same thing
 * read much faster as three different shapes.
 */
private val PlaylistPrivacy.icon: ImageVector
    get() = when (this) {
        PlaylistPrivacy.PRIVATE -> Icons.Rounded.Lock
        PlaylistPrivacy.UNLISTED -> Icons.Rounded.Link
        PlaylistPrivacy.PUBLIC -> Icons.Rounded.Public
    }

/** The search filters' pill, carrying an icon ahead of its label. */
@Composable
private fun PrivacyPill(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val content = if (selected) {
        MaterialTheme.colorScheme.onPrimary
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(percent = 50))
            .background(
                if (selected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.surfaceVariant
                },
            )
            .clickable(onClick = onClick)
            .padding(start = 11.dp, end = 14.dp, top = 7.dp, bottom = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = content,
            modifier = Modifier.size(16.dp),
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.titleMedium,
            color = content,
        )
    }
}
