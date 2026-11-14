package com.music.audify.ui.screens

import com.music.audify.ui.icons.SfIcons
import com.music.audify.auth.clearSpotifyWebSession
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.HorizontalDivider
import com.music.audify.sharedui.resources.Res
import com.music.audify.sharedui.resources.spotify_logo
import org.jetbrains.compose.resources.painterResource
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.SwitchAccount
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.MaterialTheme
import com.music.audify.ui.components.IosSwitch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.music.audify.R
import com.music.audify.data.model.Account
import com.music.audify.data.settings.AppSettings
import kotlin.math.roundToInt
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.TextButton
import com.music.audify.ui.components.AlertAction
import com.music.audify.ui.components.AlertRule
import com.music.audify.ui.components.GlassAlertDialog
import com.music.audify.ui.components.PillTextField
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import coil3.compose.AsyncImage
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val PROFILE_PHOTO_PX = 512
private const val PROFILE_NAME_MAX = 40

/**
 * Memotong foto pilihan jadi persegi tengah, mengikuti rotasi EXIF, mengecilkannya
 * ke [PROFILE_PHOTO_PX], lalu menyimpannya di penyimpanan aplikasi dengan nama
 * unik (supaya cache gambar tidak menampilkan foto lama). Null bila gagal.
 */
private suspend fun saveProfilePhoto(context: Context, uri: Uri): String? = withContext(Dispatchers.IO) {
    runCatching {
        val resolver = context.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= 1024 && bounds.outHeight / (sample * 2) >= 1024) sample *= 2
        val decoded = resolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
        } ?: return@runCatching null
        val turn = resolver.openInputStream(uri)?.use {
            when (ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                else -> 0f
            }
        } ?: 0f
        val side = minOf(decoded.width, decoded.height)
        val matrix = Matrix().apply {
            postRotate(turn)
            val scale = PROFILE_PHOTO_PX.toFloat() / side
            if (scale < 1f) postScale(scale, scale)
        }
        val square = Bitmap.createBitmap(
            decoded,
            (decoded.width - side) / 2,
            (decoded.height - side) / 2,
            side,
            side,
            matrix,
            true,
        )
        val file = File(context.filesDir, "profile_photo_${System.currentTimeMillis()}.jpg")
        file.outputStream().use { square.compress(Bitmap.CompressFormat.JPEG, 90, it) }
        file.absolutePath
    }.getOrNull()
}

/** Ubah nama tampilan dan foto profil; hanya berlaku di aplikasi ini, akun Google tidak berubah. */
@Composable
private fun EditProfileDialog(account: Account?, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val stored by AppSettings.profileOverride.collectAsStateWithLifecycle()
    val owner = AppSettings.profileOwnerOf(account?.email)
    val hasOverride = stored.owner == owner && (stored.name.isNotBlank() || stored.photoPath.isNotBlank())
    var name by remember { mutableStateOf(account?.name.orEmpty()) }
    var photoPath by remember { mutableStateOf(if (stored.owner == owner) stored.photoPath else "") }
    var busy by remember { mutableStateOf(false) }

    // Foto yang dipilih tapi tidak jadi disimpan tidak boleh tertinggal di penyimpanan.
    fun discardUnsavedPhoto() {
        if (photoPath.isNotEmpty() && photoPath != stored.photoPath) runCatching { File(photoPath).delete() }
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            busy = true
            val saved = saveProfilePhoto(context, uri)
            if (saved != null) {
                discardUnsavedPhoto()
                photoPath = saved
            }
            busy = false
        }
    }
    val pickPhoto = {
        picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
    }
    val preview: String? = photoPath.takeIf { it.isNotEmpty() }?.let { "file://$it" } ?: account?.thumbnailUrl

    GlassAlertDialog(
        onDismiss = {
            discardUnsavedPhoto()
            onDismiss()
        },
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 19.dp),
        ) {
            Text(
                text = stringResource(R.string.edit_profile),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(14.dp))
            Box(
                modifier = Modifier
                    .size(96.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.outline)
                    .clickable(onClick = pickPhoto),
                contentAlignment = Alignment.Center,
            ) {
                if (preview != null) {
                    AsyncImage(
                        model = preview,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    Icon(
                        SfIcons.Person,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.height(4.dp))
            TextButton(onClick = pickPhoto, enabled = !busy) {
                Text(
                    text = stringResource(R.string.profile_change_photo),
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Medium,
                )
            }
            Spacer(Modifier.height(4.dp))
            PillTextField(
                value = name,
                onValueChange = { name = it.take(PROFILE_NAME_MAX) },
                placeholder = stringResource(R.string.profile_display_name),
            )
            Spacer(Modifier.height(10.dp))
            Text(
                text = stringResource(R.string.profile_local_only_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
            )
        }
        AlertRule()
        AlertAction(
            label = stringResource(R.string.save),
            emphasised = true,
            enabled = !busy && name.isNotBlank(),
            onClick = {
                AppSettings.setProfileOverride(owner, name.trim(), photoPath)
                onDismiss()
            },
        )
        if (hasOverride) {
            AlertRule()
            AlertAction(
                label = stringResource(R.string.profile_reset),
                emphasised = false,
                destructive = true,
                onClick = {
                    discardUnsavedPhoto()
                    AppSettings.clearProfileOverride()
                    onDismiss()
                },
            )
        }
        AlertRule()
        AlertAction(
            label = stringResource(R.string.cancel),
            emphasised = false,
            onClick = {
                discardUnsavedPhoto()
                onDismiss()
            },
        )
    }
}

@Composable
fun AccountAndScrobblingScreen(
    signedIn: Boolean,
    account: Account?,
    channelName: String?,
    onSignIn: () -> Unit,
    onSwitchChannel: () -> Unit,
    onSignOut: () -> Unit,
    onOpenListenBrainzLogin: () -> Unit,
    onOpenLastfmLogin: () -> Unit,
    onOpenDiscord: () -> Unit,
    onOpenSpotify: () -> Unit,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
) {
    val lastfmEnabled by AppSettings.lastfmEnabled.collectAsStateWithLifecycle()
    val lastfmUsername by AppSettings.lastfmUsername.collectAsStateWithLifecycle()
    val lastfmSessionKey by AppSettings.lastfmSessionKey.collectAsStateWithLifecycle()
    val lastfmScrobbleEnabled by AppSettings.lastfmScrobbleEnabled.collectAsStateWithLifecycle()
    val lastfmNowPlayingEnabled by AppSettings.lastfmNowPlaying.collectAsStateWithLifecycle()
    val lastfmPrimaryArtistOnly by AppSettings.lastfmPrimaryArtistOnly.collectAsStateWithLifecycle()
    val scrobbleMinDuration by AppSettings.scrobbleMinDuration.collectAsStateWithLifecycle()
    val scrobbleDelayPercent by AppSettings.scrobbleDelayPercent.collectAsStateWithLifecycle()
    val scrobbleDelaySeconds by AppSettings.scrobbleDelaySeconds.collectAsStateWithLifecycle()
    val listenBrainzEnabled by AppSettings.listenBrainzEnabled.collectAsStateWithLifecycle()
    val listenBrainzToken by AppSettings.listenBrainzToken.collectAsStateWithLifecycle()
    val listenBrainzPrimaryArtistOnly by AppSettings.listenBrainzPrimaryArtistOnly.collectAsStateWithLifecycle()
    val discordToken by AppSettings.discordToken.collectAsStateWithLifecycle()
    val discordUsername by AppSettings.discordUsername.collectAsStateWithLifecycle()
    val discordRpcEnabled by AppSettings.discordRpcEnabled.collectAsStateWithLifecycle()
    val spotifyConnected by AppSettings.spotifySpdcToken.collectAsStateWithLifecycle()
    var editingProfile by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(contentPadding),
    ) {
        SettingsPageTitle(stringResource(R.string.account_integrations))
        if (editingProfile) EditProfileDialog(account) { editingProfile = false }

        // One card: who is signed in, which profile is listening, and the way
        // out, with the explanation of profiles underneath.
        SettingsGroup(
            footer = if (signedIn) stringResource(R.string.account_profiles_help) else null,
            topSpacing = 0.dp,
        ) {
            AccountCard(
                signedIn = signedIn,
                account = account,
                onSignIn = onSignIn,
                onClick = onSwitchChannel,
                grouped = true,
            )
            if (signedIn) {
                FullWidthDivider()
                SettingsRow(
                    icon = SfIcons.Person,
                    title = stringResource(R.string.edit_profile),
                    onClick = { editingProfile = true },
                )
                FullWidthDivider()
                SettingsRow(
                    icon = SfIcons.SwitchAccount,
                    title = stringResource(R.string.listen_as),
                    subtitle = channelName ?: stringResource(R.string.default_youtube_profile),
                    onClick = onSwitchChannel,
                )
                FullWidthDivider()
                DestructiveRow(label = stringResource(R.string.sign_out), onClick = onSignOut)
            }
        }

        SettingsGroup(
            header = stringResource(R.string.spotify),
            footer = stringResource(R.string.spotify_connect_subtitle),
        ) {
            SettingsRow(
                iconPainter = painterResource(Res.drawable.spotify_logo),
                title = stringResource(R.string.spotify),
                subtitle = stringResource(
                    if (spotifyConnected.isNotBlank()) R.string.connected else R.string.tap_to_connect,
                ),
                onClick = onOpenSpotify,
            )
            if (spotifyConnected.isNotBlank()) {
                FullWidthDivider()
                DestructiveRow(
                    label = stringResource(R.string.spotify_disconnect),
                    onClick = {
                        clearSpotifyWebSession()
                        AppSettings.setSpotifySpdcToken("")
                    },
                )
            }
        }

        SettingsGroup(
            header = stringResource(R.string.rich_presence),
            footer = stringResource(R.string.rich_presence_footer),
        ) {
            SettingsRow(
                icon = ImageVector.vectorResource(R.drawable.ic_discord),
                title = "Discord",
                subtitle = when {
                    discordToken.isEmpty() -> stringResource(R.string.tap_to_connect)
                    !discordRpcEnabled -> stringResource(R.string.connected_presence_off)
                    discordUsername.isNotEmpty() -> stringResource(R.string.sharing_as, discordUsername)
                    else -> stringResource(R.string.sharing_listens)
                },
                onClick = onOpenDiscord,
            )
        }

        if (AppSettings.scrobblingAvailable) {
            SettingsGroup(
                header = stringResource(R.string.scrobbling),
                footer = stringResource(R.string.scrobbling_footer),
            ) {
                SettingsRow(
                    icon = SfIcons.Cloud,
                    title = "ListenBrainz",
                    subtitle = if (listenBrainzEnabled && listenBrainzToken.isNotBlank()) {
                        stringResource(R.string.connected)
                    } else {
                        stringResource(R.string.enter_token_to_enable)
                    },
                    trailing = {
                        IosSwitch(
                            checked = listenBrainzEnabled,
                            onCheckedChange = { checked ->
                                if (checked && listenBrainzToken.isBlank()) {
                                    onOpenListenBrainzLogin()
                                } else {
                                    AppSettings.setListenBrainzEnabled(checked)
                                }
                            },
                        )
                    },
                    onClick = onOpenListenBrainzLogin,
                )
                if (listenBrainzEnabled && listenBrainzToken.isNotBlank()) {
                    RowDivider()
                    SettingsRow(
                        icon = SfIcons.GraphicEq,
                        title = stringResource(R.string.scrobble_primary_artist_only),
                        subtitle = stringResource(R.string.scrobble_primary_artist_only_subtitle),
                        trailing = {
                            IosSwitch(
                                checked = listenBrainzPrimaryArtistOnly,
                                onCheckedChange = AppSettings::setListenBrainzPrimaryArtistOnly,
                            )
                        },
                        onClick = { AppSettings.setListenBrainzPrimaryArtistOnly(!listenBrainzPrimaryArtistOnly) },
                    )
                }
                RowDivider()
                SettingsRow(
                    icon = SfIcons.History,
                    title = "Last.fm",
                    subtitle = if (lastfmSessionKey.isNotBlank()) {
                        stringResource(R.string.signed_in_as, lastfmUsername)
                    } else {
                        stringResource(R.string.tap_to_sign_in)
                    },
                    trailing = {
                        IosSwitch(
                            checked = lastfmEnabled,
                            onCheckedChange = { checked ->
                                if (checked && lastfmSessionKey.isBlank()) {
                                    onOpenLastfmLogin()
                                } else {
                                    AppSettings.setLastfmEnabled(checked)
                                }
                            },
                        )
                    },
                    onClick = {
                        if (lastfmSessionKey.isNotBlank()) {
                            AppSettings.setLastfmSessionKey("")
                            AppSettings.setLastfmUsername("")
                            AppSettings.setLastfmEnabled(false)
                            AppSettings.setLastfmScrobbleEnabled(false)
                            AppSettings.setLastfmNowPlaying(false)
                        } else {
                            onOpenLastfmLogin()
                        }
                    },
                )
                if (lastfmEnabled && lastfmSessionKey.isNotBlank()) {
                    RowDivider()
                    SettingsRow(
                        icon = SfIcons.GraphicEq,
                        title = stringResource(R.string.scrobble_tracks),
                        subtitle = stringResource(R.string.scrobble_tracks_subtitle),
                        trailing = {
                            IosSwitch(
                                checked = lastfmScrobbleEnabled,
                                onCheckedChange = AppSettings::setLastfmScrobbleEnabled,
                            )
                        },
                        onClick = { AppSettings.setLastfmScrobbleEnabled(!lastfmScrobbleEnabled) },
                    )
                    RowDivider()
                    SettingsRow(
                        icon = SfIcons.GraphicEq,
                        title = stringResource(R.string.scrobble_primary_artist_only),
                        subtitle = stringResource(R.string.scrobble_primary_artist_only_subtitle),
                        trailing = {
                            IosSwitch(
                                checked = lastfmPrimaryArtistOnly,
                                onCheckedChange = AppSettings::setLastfmPrimaryArtistOnly,
                            )
                        },
                        onClick = { AppSettings.setLastfmPrimaryArtistOnly(!lastfmPrimaryArtistOnly) },
                    )
                    RowDivider()
                    SettingsRow(
                        icon = SfIcons.GraphicEq,
                        title = stringResource(R.string.now_playing),
                        subtitle = stringResource(R.string.lastfm_now_playing_subtitle),
                        trailing = {
                            IosSwitch(
                                checked = lastfmNowPlayingEnabled,
                                onCheckedChange = AppSettings::setLastfmNowPlaying,
                            )
                        },
                        onClick = { AppSettings.setLastfmNowPlaying(!lastfmNowPlayingEnabled) },
                    )
                }
            }

            if (lastfmEnabled && lastfmSessionKey.isNotBlank()) {
                SettingsGroup(header = stringResource(R.string.scrobble_timing)) {
                    SliderRow(
                        icon = SfIcons.Tune,
                        title = stringResource(R.string.min_song_duration),
                        subtitle = stringResource(R.string.min_song_duration_subtitle),
                        value = "${scrobbleMinDuration}s",
                        sliderValue = scrobbleMinDuration.toFloat(),
                        onSliderValue = { AppSettings.setScrobbleMinDuration(it.roundToInt()) },
                        valueRange = 15f..120f,
                        steps = 20,
                    )
                    RowDivider()
                    SliderRow(
                        icon = SfIcons.Tune,
                        title = stringResource(R.string.scrobble_delay),
                        subtitle = stringResource(R.string.scrobble_delay_subtitle),
                        value = "${(scrobbleDelayPercent * 100).roundToInt()}%",
                        sliderValue = scrobbleDelayPercent,
                        onSliderValue = { AppSettings.setScrobbleDelayPercent(it) },
                        valueRange = 0.1f..1.0f,
                        steps = 8,
                    )
                    RowDivider()
                    SliderRow(
                        icon = SfIcons.Tune,
                        title = stringResource(R.string.max_delay),
                        subtitle = stringResource(R.string.max_delay_subtitle),
                        value = "${scrobbleDelaySeconds}s",
                        sliderValue = scrobbleDelaySeconds.toFloat(),
                        onSliderValue = { AppSettings.setScrobbleDelaySeconds(it.roundToInt()) },
                        valueRange = 30f..300f,
                        steps = 26,
                    )
                }
            }
        } else {
            // Left in place rather than dropped: a section that simply vanishes
            // reads as a feature that never existed, and this one is coming back.
            // The rows are dimmed and inert via `enabled = false`.
            SettingsGroup(
                header = stringResource(R.string.scrobbling),
                footer = stringResource(R.string.scrobbling_paused_footer),
            ) {
                SettingsRow(
                    icon = SfIcons.Cloud,
                    title = "ListenBrainz",
                    subtitle = stringResource(R.string.back_in_future_version),
                    enabled = false,
                )
                RowDivider()
                SettingsRow(
                    icon = SfIcons.History,
                    title = "Last.fm",
                    subtitle = stringResource(R.string.back_in_future_version),
                    enabled = false,
                )
            }
        }

        Spacer(Modifier.height(24.dp))
    }
}

/** A hairline across the whole card, for setting a destructive row apart from the rows above it. */
@Composable
private fun FullWidthDivider() {
    HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outline)
}
