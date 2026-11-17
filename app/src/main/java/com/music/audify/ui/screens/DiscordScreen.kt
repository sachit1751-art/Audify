package com.music.audify.ui.screens

import androidx.core.net.toUri
import com.music.audify.ui.icons.SfIcons
import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.automirrored.rounded.Label
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.RadioButtonChecked
import androidx.compose.material.icons.rounded.SmartButton
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.TextButton
import com.music.audify.ui.components.IosSwitch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.music.audify.R
import com.music.audify.data.NerdStats
import com.music.audify.data.discord.DiscordRPC
import com.music.audify.data.discord.discordAudioQualityLine
import com.music.audify.data.model.CARD_ART_PX
import com.music.audify.data.model.Song
import com.music.audify.data.model.artworkAt
import com.music.audify.data.settings.AppSettings
import com.music.audify.ui.components.ChoiceAlert
import com.music.audify.ui.components.TextValueAlert
import com.music.audify.ui.components.thumbnailBorder
import dev.chrisbanes.haze.HazeState
import java.util.Locale

/**
 * The verb on the profile. All four render the same card; only the line above it
 * changes, so this is purely how you'd rather have it read.
 */
enum class DiscordActivityKind(val value: String, val verb: String, val label: String) {
    LISTENING("listening", "Listening to", "Listening"),
    PLAYING("playing", "Playing", "Playing"),
    WATCHING("watching", "Watching", "Watching"),
    COMPETING("competing", "Competing in", "Competing"),
}

/**
 * The dialogs the Discord screen opens. Hoisted out to an enum because they are
 * rendered by the activity, above the tab bar and mini player, rather than
 * inside the scrolling screen where a full-screen scrim would be trapped.
 */
enum class DiscordDialog { ACTIVITY_TYPE, BUTTON_1, BUTTON_2 }

private enum class DiscordSettingsPage(val title: String) {
    OVERVIEW("Discord Rich Presence"),
    INTEGRATION("Discord Integration"),
    DISPLAY("Display Preferences"),
    ACTIVITY("Activity Style"),
    PRIVACY("Privacy Controls"),
}

private fun kindOf(value: String) =
    DiscordActivityKind.entries.firstOrNull { it.value == value } ?: DiscordActivityKind.LISTENING

@Composable
private fun localizedActivityCopy(): Map<DiscordActivityKind, Pair<String, String>> = mapOf(
    DiscordActivityKind.LISTENING to (
        stringResource(R.string.activity_listening) to stringResource(R.string.verb_listening)
        ),
    DiscordActivityKind.PLAYING to (
        stringResource(R.string.activity_playing) to stringResource(R.string.verb_playing)
        ),
    DiscordActivityKind.WATCHING to (
        stringResource(R.string.activity_watching) to stringResource(R.string.verb_watching)
        ),
    DiscordActivityKind.COMPETING to (
        stringResource(R.string.activity_competing) to stringResource(R.string.verb_competing)
        ),
)

@Composable
private fun DiscordActivityKind.localizedLabel(): String =
    localizedActivityCopy().getValue(this).first

@Composable
private fun DiscordActivityKind.localizedVerb(): String =
    localizedActivityCopy().getValue(this).second

/**
 * Discord Rich Presence: the account it posts as, what the card says, and a
 * live preview of it.
 *
 * The preview is the point of the screen. Every field here changes something
 * about a card the user cannot see from inside this app — so it draws the card
 * as Discord will, from the track actually playing, and updates as they type.
 */
@Composable
fun DiscordScreen(
    song: Song?,
    positionMs: Long,
    durationMs: Long,
    onOpenDialog: (DiscordDialog) -> Unit,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var page by remember { mutableStateOf(DiscordSettingsPage.OVERVIEW) }
    var installCheck by remember { mutableStateOf(0) }
    val discordInstalled = remember(context, installCheck) {
        runCatching { context.packageManager.getPackageInfo("com.discord", 0) }.isSuccess
    }
    val rpcEnabled by AppSettings.discordRpcEnabled.collectAsStateWithLifecycle()
    val useDetails by AppSettings.discordUseDetails.collectAsStateWithLifecycle()
    val showAudioQuality by AppSettings.discordShowAudioQuality.collectAsStateWithLifecycle()
    val showTrackTitle by AppSettings.discordShowTrackTitle.collectAsStateWithLifecycle()
    val showArtist by AppSettings.discordShowArtist.collectAsStateWithLifecycle()
    val showAlbum by AppSettings.discordShowAlbum.collectAsStateWithLifecycle()
    val showArtwork by AppSettings.discordShowArtwork.collectAsStateWithLifecycle()
    val showElapsedTime by AppSettings.discordShowElapsedTime.collectAsStateWithLifecycle()
    val hideWhenPaused by AppSettings.discordHideWhenPaused.collectAsStateWithLifecycle()
    val activityType by AppSettings.discordActivityType.collectAsStateWithLifecycle()
    val button1Text by AppSettings.discordButton1Text.collectAsStateWithLifecycle()
    val button1Visible by AppSettings.discordButton1Visible.collectAsStateWithLifecycle()
    val button2Text by AppSettings.discordButton2Text.collectAsStateWithLifecycle()
    val button2Visible by AppSettings.discordButton2Visible.collectAsStateWithLifecycle()
    val nerdStats by NerdStats.current.collectAsStateWithLifecycle()
    val advancedMode by AppSettings.discordAdvancedMode.collectAsStateWithLifecycle()

    BackHandler(enabled = page != DiscordSettingsPage.OVERVIEW) {
        page = DiscordSettingsPage.OVERVIEW
    }

    val discordIcon = ImageVector.vectorResource(R.drawable.ic_discord)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(contentPadding),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (page != DiscordSettingsPage.OVERVIEW) {
                TextButton(onClick = { page = DiscordSettingsPage.OVERVIEW }) { Text("‹") }
            }
            Column(Modifier.weight(1f)) {
                Text(page.title, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onBackground)
                if (page == DiscordSettingsPage.OVERVIEW) {
                    Text("Control how Audify appears on Discord", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Text(
                text = if (rpcEnabled) "● RPC ON" else "○ RPC OFF",
                style = MaterialTheme.typography.labelSmall,
                color = if (rpcEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        when (page) {
            DiscordSettingsPage.OVERVIEW -> {
                SettingsGroup(header = "Status Preview", footer = if (song == null) "Start playback to preview live track information." else "Live preview · updates with playback and display settings") {
                    RichPresencePreview(
                        song = song, positionMs = positionMs, durationMs = durationMs,
                        heading = appName(), verb = "Playing",
                        trackVerb = kindOf(activityType).localizedVerb(),
                        useDetails = useDetails,
                        audioQuality = discordAudioQualityLine(nerdStats).takeIf { showAudioQuality },
                        showTrackTitle = showTrackTitle, showArtist = showArtist,
                        showAlbum = showAlbum, showArtwork = showArtwork,
                        showElapsedTime = showElapsedTime, enabled = rpcEnabled,
                        button1Text = button1Text, button1Visible = button1Visible,
                        button2Text = button2Text, button2Visible = button2Visible,
                    )
                }
                SettingsGroup(header = "Integration") {
                    SettingsRow(
                        icon = discordIcon, title = "Discord connection",
                        subtitle = if (discordInstalled) "Discord app detected · tap for connection details" else "Discord app not detected · setup required",
                        onClick = { page = DiscordSettingsPage.INTEGRATION },
                    )
                }
                SettingsGroup(header = "Presence Settings") {
                    SettingsRow(
                        icon = discordIcon, title = "Enable Rich Presence",
                        subtitle = "Broadcast current playback to your Discord status",
                        trailing = { IosSwitch(checked = rpcEnabled, onCheckedChange = AppSettings::setDiscordRpcEnabled) },
                        onClick = { AppSettings.setDiscordRpcEnabled(!rpcEnabled) },
                    )
                    RowDivider()
                    SettingsRow(icon = SfIcons.Tune, title = "Display Preferences", subtitle = "Track details, artwork and buttons", onClick = { page = DiscordSettingsPage.DISPLAY })
                    RowDivider()
                    SettingsRow(icon = SfIcons.RadioButtonChecked, title = "Activity Style", subtitle = "Listening, Playing or Watching", onClick = { page = DiscordSettingsPage.ACTIVITY })
                    RowDivider()
                    SettingsRow(icon = SfIcons.GraphicEq, title = "Privacy Controls", subtitle = "Control what remains visible when playback stops", onClick = { page = DiscordSettingsPage.PRIVACY })
                }
            }

            DiscordSettingsPage.INTEGRATION -> {
                DiscordAccountCard(
                    installed = discordInstalled, icon = discordIcon,
                    onInstall = {
                        val market = Intent(Intent.ACTION_VIEW, "market://details?id=com.discord".toUri())
                        runCatching { context.startActivity(market) }.onFailure {
                            runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, "https://play.google.com/store/apps/details?id=com.discord".toUri())) }
                        }
                    },
                    onCheckAgain = { installCheck++ },
                )
                SettingsGroup(header = "Connection Status", footer = if (discordInstalled) "Audify uses Discord's local Social SDK. No Discord login token is stored by Audify." else "Install the official Discord app, then use Check Again. Rich Presence cannot be delivered while Discord is unavailable.") {
                    Text(
                        text = if (discordInstalled) "Discord app detected" else "Discord not detected",
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                        style = MaterialTheme.typography.titleMedium,
                        color = if (discordInstalled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                    )
                    Text(
                        text = if (discordInstalled) "The app package is present. Actual RPC connection still depends on Discord's SDK handshake." else "The Discord app package was not found on this device.",
                        modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                SettingsGroup(header = "Broadcast") {
                    SettingsRow(icon = discordIcon, title = "Enable Rich Presence", subtitle = "Allow Audify to publish playback status", trailing = { IosSwitch(checked = rpcEnabled, onCheckedChange = AppSettings::setDiscordRpcEnabled) }, onClick = { AppSettings.setDiscordRpcEnabled(!rpcEnabled) })
                }
            }

            DiscordSettingsPage.DISPLAY -> {
                SettingsGroup(header = "Broadcast Fields", footer = "Changes are saved immediately and reflected in the preview.") {
                    SettingsRow(icon = SfIcons.Label, title = "Track title", subtitle = "Show the current song name", enabled = rpcEnabled, trailing = { IosSwitch(checked = showTrackTitle, onCheckedChange = AppSettings::setDiscordShowTrackTitle) }, onClick = { AppSettings.setDiscordShowTrackTitle(!showTrackTitle) })
                    RowDivider()
                    SettingsRow(icon = SfIcons.Label, title = "Artist name", subtitle = "Show primary and featured artists", enabled = rpcEnabled, trailing = { IosSwitch(checked = showArtist, onCheckedChange = AppSettings::setDiscordShowArtist) }, onClick = { AppSettings.setDiscordShowArtist(!showArtist) })
                    RowDivider()
                    SettingsRow(icon = SfIcons.Label, title = "Album name", subtitle = "Include album or EP title", enabled = rpcEnabled, trailing = { IosSwitch(checked = showAlbum, onCheckedChange = { AppSettings.setDiscordShowAlbum(it) }) }, onClick = { AppSettings.setDiscordShowAlbum(!showAlbum) })
                    RowDivider()
                    SettingsRow(icon = SfIcons.GraphicEq, title = "Album artwork", subtitle = "Show current track artwork when available", enabled = rpcEnabled, trailing = { IosSwitch(checked = showArtwork, onCheckedChange = { AppSettings.setDiscordShowArtwork(it) }) }, onClick = { AppSettings.setDiscordShowArtwork(!showArtwork) })
                    RowDivider()
                    SettingsRow(icon = SfIcons.Tune, title = "Elapsed playback time", subtitle = "Show synced duration and timestamps", enabled = rpcEnabled, trailing = { IosSwitch(checked = showElapsedTime, onCheckedChange = { AppSettings.setDiscordShowElapsedTime(it) }) }, onClick = { AppSettings.setDiscordShowElapsedTime(!showElapsedTime) })
                    RowDivider()
                    SettingsRow(icon = SfIcons.GraphicEq, title = "Audio quality", subtitle = stringResource(R.string.discord_show_audio_quality_subtitle), enabled = rpcEnabled, trailing = { IosSwitch(checked = showAudioQuality, onCheckedChange = AppSettings::setDiscordShowAudioQuality) }, onClick = { AppSettings.setDiscordShowAudioQuality(!showAudioQuality) })
                    RowDivider()
                    SettingsRow(icon = SfIcons.Tune, title = "Lead with track title", subtitle = stringResource(R.string.lead_with_song_subtitle), enabled = rpcEnabled, trailing = { IosSwitch(checked = useDetails, onCheckedChange = AppSettings::setDiscordUseDetails) }, onClick = { AppSettings.setDiscordUseDetails(!useDetails) })
                }
                SettingsGroup(header = "Action Buttons", footer = stringResource(R.string.discord_buttons_footer)) {
                    SettingsRow(icon = SfIcons.SmartButton, title = stringResource(R.string.first_button), subtitle = button1Text.ifEmpty { DiscordRPC.DEFAULT_BUTTON_1 }, trailing = { IosSwitch(checked = button1Visible, onCheckedChange = AppSettings::setDiscordButton1Visible) }, onClick = { onOpenDialog(DiscordDialog.BUTTON_1) })
                    RowDivider()
                    SettingsRow(icon = SfIcons.SmartButton, title = stringResource(R.string.second_button), subtitle = button2Text.ifEmpty { DiscordRPC.DEFAULT_BUTTON_2 }, trailing = { IosSwitch(checked = button2Visible, onCheckedChange = AppSettings::setDiscordButton2Visible) }, onClick = { onOpenDialog(DiscordDialog.BUTTON_2) })
                }
                SettingsGroup(header = "Advanced") {
                    SettingsRow(icon = SfIcons.Tune, title = "Advanced mode", subtitle = "Expose additional presence configuration", trailing = { IosSwitch(checked = advancedMode, onCheckedChange = AppSettings::setDiscordAdvancedMode) }, onClick = { AppSettings.setDiscordAdvancedMode(!advancedMode) })
                }
            }

            DiscordSettingsPage.ACTIVITY -> {
                SettingsGroup(header = "Presence Type", footer = "Choose how your playback status is labelled across Discord.") {
                    DiscordActivityKind.entries.forEach { kind ->
                        Row(
                            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                                .background(if (activityType == kind.value) MaterialTheme.colorScheme.primary.copy(alpha = 0.14f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.42f))
                                .clickable(enabled = rpcEnabled) { AppSettings.setDiscordActivityType(kind.value) }
                                .padding(horizontal = 14.dp, vertical = 13.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(kind.localizedLabel(), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onBackground)
                                Text("${kind.localizedVerb()} ${appName()}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            RadioButton(selected = activityType == kind.value, onClick = { AppSettings.setDiscordActivityType(kind.value) }, enabled = rpcEnabled)
                        }
                        Spacer(Modifier.height(8.dp))
                    }
                }
                Text("The selected style is applied immediately to the next presence update.", modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            DiscordSettingsPage.PRIVACY -> {
                SettingsGroup(header = "Playback Privacy", footer = "These controls affect whether playback details remain visible when audio is paused.") {
                    SettingsRow(icon = SfIcons.RadioButtonChecked, title = "Hide when paused", subtitle = "Clear Discord activity instead of showing a paused indicator", enabled = rpcEnabled, trailing = { IosSwitch(checked = hideWhenPaused, onCheckedChange = AppSettings::setDiscordHideWhenPaused) }, onClick = { AppSettings.setDiscordHideWhenPaused(!hideWhenPaused) })
                }
                SettingsGroup(header = "Privacy Status") {
                    Text("Private-session-aware presence is not available in this app build yet. This page only exposes controls that are currently connected to playback behaviour.", modifier = Modifier.padding(16.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        Spacer(Modifier.height(24.dp))
    }
}

/** Who this posts as. Same frame as [AccountCard], with Discord's status dot. */
@Composable
private fun DiscordAccountCard(
    installed: Boolean,
    icon: ImageVector,
    onInstall: () -> Unit,
    onCheckAgain: () -> Unit,
) {
    val cardColor = MaterialTheme.colorScheme.surfaceVariant
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = GROUP_INSET)
            .clip(GroupShape)
            .background(cardColor)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(52.dp)) {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.outline),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(26.dp),
                )
            }
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.discord_social_sdk_title),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = if (installed) "Discord app detected · account status is managed by Discord" else "Discord app not detected on this device",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            if (!installed) TextButton(onClick = onInstall) { Text("Install Discord") }
            TextButton(onClick = onCheckAgain) { Text("Check again") }
        }
    }
}

/**
 * The card as Discord will draw it: heading, sleeve, an optional quality line, a
 * countdown, and up to two buttons.
 *
 * Deliberately not built from our own row primitives — this is a picture of
 * another app's UI, and the only way it does its job is by looking like one.
 * The buttons work, so it doubles as a way to check the links land.
 */
@Composable
private fun RichPresencePreview(
    song: Song?,
    positionMs: Long,
    durationMs: Long,
    heading: String,
    verb: String,
    trackVerb: String,
    useDetails: Boolean,
    audioQuality: String?,
    showTrackTitle: Boolean,
    showArtist: Boolean,
    showAlbum: Boolean,
    showArtwork: Boolean,
    showElapsedTime: Boolean,
    enabled: Boolean,
    button1Text: String,
    button1Visible: Boolean,
    button2Text: String,
    button2Visible: Boolean,
) {
    val context = LocalContext.current
    val title = song?.title ?: "Nothing playing"
    val artist = song?.artist ?: "Start playback to preview real track details"
    val album = song?.albumName

    Column(Modifier.fillMaxWidth().padding(16.dp)) {
        Text(
            text = "$verb $heading".uppercase(Locale.ROOT),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.W700,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(Modifier.height(12.dp))

        Row(verticalAlignment = Alignment.Top) {
            val artShape = RoundedCornerShape(6.dp)
            if (showArtwork && song?.thumbnailUrl != null) {
                AsyncImage(
                    model = song.artworkAt(CARD_ART_PX),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(84.dp).clip(artShape).thumbnailBorder(artShape),
                )
            } else if (showArtwork) {
                Box(
                    Modifier
                        .size(84.dp)
                        .clip(artShape)
                        .background(MaterialTheme.colorScheme.outline),
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                // Discord bolds whichever line `status_display_type` names, and
                // that is the one it also repeats next to the user's name in a
                // member list — so which of these is emphasised is the whole
                // point of the "Lead with the song" switch.
                if (showTrackTitle) Text(
                    text = "$trackVerb $title",
                    style = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp),
                    fontWeight = if (useDetails) FontWeight.W700 else FontWeight.W400,
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (showArtist && artist.isNotBlank()) Text(
                    text = artist,
                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
                    fontWeight = if (useDetails && showTrackTitle) FontWeight.W400 else FontWeight.W700,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (showAlbum && !album.isNullOrBlank()) Text(
                    text = album,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                audioQuality?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.height(8.dp))
                if (showElapsedTime) ProgressLine(positionMs = positionMs, durationMs = durationMs)
            }
        }

        val resolved = { text: String, fallback: String ->
            val chosen = text.ifEmpty { fallback }
            if (song != null) DiscordRPC.resolveVariables(chosen, song) else chosen
        }
        if (button1Visible) {
            Spacer(Modifier.height(12.dp))
            PresenceButton(
                label = resolved(button1Text, DiscordRPC.DEFAULT_BUTTON_1),
                enabled = song != null && enabled,
                onClick = {
                    song?.let {
                        context.startActivity(
                            Intent(Intent.ACTION_VIEW, DiscordRPC.watchUrl(it).toUri()),
                        )
                    }
                },
            )
        }
        if (button2Visible) {
            Spacer(Modifier.height(8.dp))
            PresenceButton(
                label = resolved(button2Text, DiscordRPC.DEFAULT_BUTTON_2),
                enabled = enabled,
                onClick = {
                    context.startActivity(
                        Intent(Intent.ACTION_VIEW, DiscordRPC.PROJECT_URL.toUri()),
                    )
                },
            )
        }
    }
}

/** Discord's flat, full-width secondary button. */
@Composable
private fun PresenceButton(label: String, enabled: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.outline.copy(alpha = if (enabled) 0.9f else 0.4f))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 9.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp),
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = if (enabled) 1f else 0.5f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * Elapsed and total, either side of a plain track.
 *
 * Deliberately plain: Discord's own bar is a flat 4px rule, and the wavy
 * indicator this is otherwise shaped like would be this app's idiom leaking
 * into a picture of somewhere else.
 */
@Composable
private fun ProgressLine(positionMs: Long, durationMs: Long) {
    val fraction = if (durationMs > 0) {
        (positionMs.toFloat() / durationMs).coerceIn(0f, 1f)
    } else {
        0f
    }
    Column(Modifier.fillMaxWidth()) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(MaterialTheme.colorScheme.outline),
        ) {
            Box(
                Modifier
                    .fillMaxWidth(fraction)
                    .height(4.dp)
                    .background(MaterialTheme.colorScheme.primary),
            )
        }
        Spacer(Modifier.height(4.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = formatClock(positionMs),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = formatClock(durationMs),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun formatClock(ms: Long): String {
    val total = (ms / 1000).coerceAtLeast(0)
    return "%d:%02d".format(Locale.ROOT, total / 60, total % 60)
}

/**
 * The Discord screen's dialogs, rendered by the activity so their scrim covers
 * the tab bar and mini player like every other alert in the app.
 */
@Composable
fun DiscordDialogHost(
    which: DiscordDialog,
    hazeState: HazeState,
    onDismiss: () -> Unit,
) {
    when (which) {
        DiscordDialog.ACTIVITY_TYPE -> {
            val current by AppSettings.discordActivityType.collectAsStateWithLifecycle()
            val copy = localizedActivityCopy()
            val applicationName = appName()
            ChoiceAlert(
                hazeState = hazeState,
                title = stringResource(R.string.activity),
                message = stringResource(R.string.activity_dialog_message),
                options = DiscordActivityKind.entries,
                selected = kindOf(current),
                label = { copy.getValue(it).first },
                detail = { "\"${copy.getValue(it).second} $applicationName\"" },
                onSelect = {
                    AppSettings.setDiscordActivityType(it.value)
                    onDismiss()
                },
                onDismiss = onDismiss,
            )
        }

        DiscordDialog.BUTTON_1, DiscordDialog.BUTTON_2 -> {
            val first = which == DiscordDialog.BUTTON_1
            val flow = if (first) AppSettings.discordButton1Text else AppSettings.discordButton2Text
            val current by flow.collectAsStateWithLifecycle()
            var input by remember { mutableStateOf(current) }
            TextValueAlert(
                hazeState = hazeState,
                title = if (first) stringResource(R.string.first_button)
                else stringResource(R.string.second_button),
                message = stringResource(R.string.discord_button_variables),
                placeholder = if (first) DiscordRPC.DEFAULT_BUTTON_1 else DiscordRPC.DEFAULT_BUTTON_2,
                value = input,
                onValueChange = { input = it },
                onSave = {
                    if (first) {
                        AppSettings.setDiscordButton1Text(input.trim())
                    } else {
                        AppSettings.setDiscordButton2Text(input.trim())
                    }
                    onDismiss()
                },
                onDismiss = onDismiss,
            )
        }
    }
}

/** The app's own label, minus the dev flavor's suffix. Matches [DiscordRPC]. */
@Composable
private fun appName(): String =
    stringResource(R.string.app_name).removeSuffix(" Dev")
