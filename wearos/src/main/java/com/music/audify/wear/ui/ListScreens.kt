// Copyright (c) 2026 Sachit. All rights reserved.
// Project: AUDIFY
package com.music.audify.wear.ui

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.Text
import com.music.audify.wear.R
import com.music.audify.wear.data.NowPlaying
import com.music.audify.wear.data.PhoneOutput
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.platform.LocalContext
import com.music.audify.wear.data.PhoneLink
import com.music.audify.wear.data.Queue
import com.music.audify.wear.data.QueueRow
import com.music.audify.wear.data.PodcastShowRow
import com.music.audify.wear.data.PodcastEpisodeRow
import com.music.audify.wear.data.PodcastCatalogueState
import com.music.audify.wear.data.podcastCatalogueState

/**
 * Up Next. Text first, thumbnails a beat later — see PhoneLink.applyQueue —
 * and asked for again whenever the playing track changes, so the list is never
 * the one from two songs ago.
 */
@Composable
fun QueueScreen(
    queue: Queue?,
    playingId: String?,
    onRequest: () -> Unit,
    onPlay: (Int) -> Unit,
) {
    LaunchedEffect(playingId) { onRequest() }
    val state = rememberScalingLazyListState(initialCenterItemIndex = 1)
    val live = queue?.takeIf { playingId == null || it.mediaId == playingId } ?: queue

    ScreenScaffold(scrollState = state) { padding ->
        ScalingLazyColumn(
            state = state,
            contentPadding = padding,
            verticalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.background(Color.Black),
        ) {
            item(key = "header") { Header(stringResource(R.string.up_next)) }
            when {
                live == null -> item(key = "loading") { Hint(stringResource(R.string.loading)) }
                live.rows.isEmpty() -> item(key = "empty") { Hint(stringResource(R.string.queue_empty)) }
                else -> items(live.rows, key = { it.index }) { row ->
                    QueueCell(
                        row = row,
                        isCurrent = row.index == live.currentIndex,
                        isPast = row.index < live.currentIndex,
                    onClick = { onPlay(row.index) },
                    )
                }
            }
        }
    }
}

@Composable
fun PodcastCatalogueScreen(
    shows: List<PodcastShowRow>?,
    phoneDisconnected: Boolean = false,
    onRequest: () -> Unit,
    onOpenShow: (PodcastShowRow) -> Unit,
) {
    LaunchedEffect(Unit) { onRequest() }
    val state = rememberScalingLazyListState(initialCenterItemIndex = 1)
    val catalogueState = podcastCatalogueState(shows, phoneDisconnected)
    ScreenScaffold(scrollState = state) { padding ->
        ScalingLazyColumn(
            state = state,
            contentPadding = padding,
            verticalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.background(Color.Black),
        ) {
            item(key = "podcast-header") { Header("Podcasts") }
            item(key = "podcast-subtitle") {
                Hint(if (phoneDisconnected) "Offline catalogue · reconnect your phone to refresh or play" else "Your phone subscriptions")
            }
            when {
                catalogueState == PodcastCatalogueState.OFFLINE_EMPTY -> item(key = "podcast-offline-empty") {
                    Hint("Phone disconnected. No cached subscriptions are available yet.")
                }
                catalogueState == PodcastCatalogueState.LOADING -> item(key = "podcast-loading") { Hint("Loading subscriptions…") }
                catalogueState == PodcastCatalogueState.EMPTY -> item(key = "podcast-empty") { Hint("No RSS / Atom subscriptions on your phone") }
                catalogueState == PodcastCatalogueState.CONTENT -> shows.orEmpty().groupBy { it.category }.forEach { (category, categoryShows) ->
                    item(key = "podcast-category:$category") { Header(category) }
                    items(categoryShows, key = { it.id }) { show ->
                        Cell(onClick = { onOpenShow(show) }) {
                            Image(painterResource(R.drawable.ic_headphones), contentDescription = null,
                                modifier = Modifier.size(30.dp), colorFilter = null)
                            Spacer(Modifier.width(8.dp))
                            Column(Modifier.weight(1f)) {
                                Text(show.title, style = WearType.row, color = Color.White, maxLines = 1,
                                    overflow = TextOverflow.Ellipsis)
                                Text("${show.episodes.size} episodes", style = WearType.rowSub, color = Secondary)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PodcastEpisodesScreen(
    show: PodcastShowRow,
    phoneDisconnected: Boolean = false,
    onPlay: (PodcastEpisodeRow) -> Unit,
) {
    val state = rememberScalingLazyListState(initialCenterItemIndex = 1)
    ScreenScaffold(scrollState = state) { padding ->
        ScalingLazyColumn(
            state = state,
            contentPadding = padding,
            verticalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.background(Color.Black),
        ) {
            item(key = "episode-header") { Header(show.title) }
            item(key = "episode-subtitle") {
                Hint(if (phoneDisconnected) "Offline catalogue · reconnect your phone to play" else "Choose an episode to play on your phone")
            }
            if (show.episodes.isEmpty()) item(key = "episode-empty") { Hint("No cached episodes. Refresh this show on your phone.") }
            else items(show.episodes, key = { it.episodeId }) { episode ->
                Cell(
                    onClick = { onPlay(episode) },
                    dimmed = phoneDisconnected,
                    enabled = !phoneDisconnected,
                    stateDescription = when {
                        phoneDisconnected -> "Reconnect phone to play episode"
                        episode.completed -> "Played"
                        else -> "Play episode"
                    },
                ) {
                    Column(Modifier.fillMaxWidth()) {
                        Text(episode.title, style = WearType.row, color = Color.White, maxLines = 2,
                            overflow = TextOverflow.Ellipsis)
                        Text(
                            text = when {
                                episode.completed -> "Played"
                                episode.positionMs > 0L -> "Resume · ${episode.published}"
                                else -> episode.published
                            },
                            style = WearType.rowSub, color = Secondary, maxLines = 1,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun QueueCell(row: QueueRow, isCurrent: Boolean, isPast: Boolean, onClick: () -> Unit) {
    Cell(
        onClick = onClick,
        dimmed = isPast,
        stateDescription = when {
            isCurrent -> "Playing now"
            isPast -> "Previously played"
            else -> "Up next"
        },
    ) {
        Box(
            Modifier
                .size(34.dp)
                .clip(RoundedCornerShape(7.dp))
                .background(Color.White.copy(alpha = 0.10f)),
        ) {
            row.art?.let {
                Image(it, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.size(34.dp))
            }
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            if (isCurrent) {
                Text(
                    text = stringResource(R.string.playing_now).uppercase(),
                    style = WearType.caption,
                    color = AccentRed,
                    maxLines = 1,
                )
            }
            Text(
                text = row.title,
                style = WearType.row,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = row.artist,
                style = WearType.rowSub,
                color = Secondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** Shuffle, repeat and love, with the state each is in on the phone. */
@Composable
fun MoreScreen(
    np: NowPlaying?,
    link: PhoneLink,
    onOpenOutput: () -> Unit,
    onOpenPodcasts: () -> Unit = {},
    onReplayOnboarding: () -> Unit = {},
) {
    val state = rememberScalingLazyListState(initialCenterItemIndex = 1)
    val onWatch by link.watchSpeaker.collectAsStateWithLifecycle()
    ScreenScaffold(scrollState = state) { padding ->
        ScalingLazyColumn(
            state = state,
            contentPadding = padding,
            verticalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.background(Color.Black),
        ) {
            item(key = "header") { Header(stringResource(R.string.more)) }
            item(key = "output") {
                ToggleCell(
                    icon = if (onWatch) R.drawable.ic_watch else R.drawable.ic_phone,
                    label = stringResource(R.string.output),
                    value = stringResource(if (onWatch) R.string.this_watch else R.string.on_phone_output),
                    active = onWatch,
                    stateDescription = if (onWatch) "This watch selected" else "Phone output selected",
                    onClick = onOpenOutput,
                )
            }
            item(key = "setup-guide") {
                ToggleCell(
                    icon = R.drawable.ic_watch,
                    label = "Setup guide",
                    value = "Replay introduction",
                    active = false,
                    stateDescription = "Replay setup guide",
                    onClick = onReplayOnboarding,
                )
            }
            item(key = "podcasts") {
                ToggleCell(
                    icon = R.drawable.ic_headphones,
                    label = "Podcasts",
                    value = "Browse subscriptions",
                    active = false,
                    stateDescription = "Browse podcasts",
                    onClick = onOpenPodcasts,
                )
            }
            if (np == null) {
                item(key = "none") { Hint(stringResource(R.string.nothing_playing)) }
                return@ScalingLazyColumn
            }
            item(key = "shuffle") {
                ToggleCell(
                    icon = R.drawable.ic_shuffle,
                    label = stringResource(R.string.shuffle),
                    value = stringResource(if (np.shuffle) R.string.on else R.string.off),
                    active = np.shuffle,
                    stateDescription = if (np.shuffle) "On" else "Off",
                    onClick = link::toggleShuffle,
                )
            }
            item(key = "repeat") {
                ToggleCell(
                    icon = R.drawable.ic_repeat,
                    label = stringResource(R.string.repeat),
                    value = stringResource(
                        when (np.repeatMode) {
                            PhoneLink.REPEAT_ALL -> R.string.repeat_all
                            PhoneLink.REPEAT_ONE -> R.string.repeat_one
                            else -> R.string.repeat_off
                        },
                    ),
                    active = np.repeatMode != PhoneLink.REPEAT_OFF,
                    stateDescription = stringResource(
                        when (np.repeatMode) {
                            PhoneLink.REPEAT_ALL -> R.string.repeat_all
                            PhoneLink.REPEAT_ONE -> R.string.repeat_one
                            else -> R.string.repeat_off
                        },
                    ),
                    badge = if (np.repeatMode == PhoneLink.REPEAT_ONE) "1" else null,
                    onClick = link::cycleRepeat,
                )
            }
            if (np.canLike) item(key = "love") {
                ToggleCell(
                    icon = if (np.liked) R.drawable.ic_heart_filled else R.drawable.ic_heart,
                    label = stringResource(if (np.liked) R.string.loved else R.string.love),
                    value = null,
                    active = np.liked,
                    stateDescription = if (np.liked) "Liked" else "Not liked",
                    activeTint = AccentRed,
                    onClick = link::toggleLike,
                )
            }
        }
    }
}

@Composable
private fun ToggleCell(
    @DrawableRes icon: Int,
    label: String,
    value: String?,
    active: Boolean,
    onClick: () -> Unit,
    stateDescription: String? = null,
    activeTint: Color? = null,
    badge: String? = null,
) {
    Cell(onClick = onClick, stateDescription = stateDescription, semanticRole = Role.Switch) {
        // On: a filled disc, as the phone's player highlights shuffle and repeat.
        val disc = when {
            !active -> Color.White.copy(alpha = 0.12f)
            activeTint != null -> activeTint.copy(alpha = 0.20f)
            else -> Color.White
        }
        val glyph = when {
            !active -> Color.White
            activeTint != null -> activeTint
            else -> Color.Black
        }
        Box(
            Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(disc),
            contentAlignment = Alignment.Center,
        ) {
            Icon(painterResource(icon), contentDescription = null, tint = glyph, modifier = Modifier.size(18.dp))
            if (badge != null) {
                Text(
                    text = badge,
                    style = WearType.caption.copy(fontSize = WearType.caption.fontSize * 0.8f),
                    color = glyph,
                    modifier = Modifier.align(Alignment.Center),
                )
            }
        }
        Spacer(Modifier.width(10.dp))
        Text(
            text = label,
            style = WearType.row,
            color = Color.White,
            maxLines = 1,
            modifier = Modifier.weight(1f),
        )
        if (value != null) {
            Text(text = value, style = WearType.rowSub, color = Secondary, maxLines = 1)
        }
    }
}

/** A grouped cell: the phone's settings rows, rounded for a round screen. */
@Composable
private fun Cell(
    onClick: () -> Unit,
    dimmed: Boolean = false,
    enabled: Boolean = true,
    stateDescription: String? = null,
    semanticRole: Role = Role.Button,
    content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .graphicsLayer { alpha = if (dimmed) 0.5f else 1f }
            .clip(RoundedCornerShape(22.dp))
            .background(if (pressed) Color(0xFF2C2C2E) else Cell)
            .clickable(interactionSource = interaction, indication = null, enabled = enabled, role = Role.Button, onClick = onClick)
            .semantics {
                role = semanticRole
                if (stateDescription != null) this.stateDescription = stateDescription
            }
            .padding(PaddingValues(horizontal = 12.dp, vertical = 9.dp)),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

@Composable
private fun Header(text: String) {
    Text(
        text = text,
        style = WearType.header,
        color = Color.White,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 4.dp),
    )
}

@Composable
private fun Hint(text: String) {
    Text(
        text = text,
        style = WearType.subtitle,
        color = Secondary,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
    )
}

/**
 * Where the music plays: this watch, or any output the phone has. Picking the
 * watch hands the queue over from the phone; picking a phone output while the
 * watch plays hands it back, through that output.
 */
@Composable
fun OutputScreen(link: PhoneLink, onDone: () -> Unit) {
    LaunchedEffect(Unit) { link.requestOutputs() }
    val outputs by link.outputs.collectAsStateWithLifecycle()
    val onWatch by link.watchSpeaker.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val state = rememberScalingLazyListState(initialCenterItemIndex = 1)
    ScreenScaffold(scrollState = state) { padding ->
        ScalingLazyColumn(
            state = state,
            contentPadding = padding,
            verticalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.background(Color.Black),
        ) {
            item(key = "header") { Header(stringResource(R.string.output)) }
            item(key = "watch") {
                OutputCell(
                    icon = R.drawable.ic_watch,
                    label = stringResource(R.string.this_watch),
                    detail = stringResource(R.string.watch_speaker),
                    selected = onWatch,
                    onClick = {
                        link.playOnWatch(context)
                        onDone()
                    },
                )
            }
            val list = outputs
            if (list == null) {
                item(key = "loading") { Hint(stringResource(R.string.loading)) }
            } else {
                items(list, key = { "out-${it.id}" }) { output ->
                    OutputCell(
                        icon = when (output.kind) {
                            "PHONE" -> R.drawable.ic_speaker
                            "WIRED", "BLUETOOTH", "USB" -> R.drawable.ic_headphones
                            else -> R.drawable.ic_phone
                        },
                        label = outputLabel(output),
                        detail = stringResource(R.string.on_phone_output),
                        selected = output.active && !onWatch,
                        onClick = {
                            link.playOnPhone(output)
                            onDone()
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun outputLabel(output: PhoneOutput): String = output.name.takeIf(String::isNotBlank) ?: stringResource(
    when (output.kind) {
        "PHONE" -> R.string.phone_speaker
        "WIRED" -> R.string.wired_headphones
        "USB" -> R.string.usb_audio
        "HDMI" -> R.string.hdmi
        else -> R.string.other_output
    },
)

@Composable
private fun OutputCell(
    @DrawableRes icon: Int,
    label: String,
    detail: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Cell(
        onClick = onClick,
        stateDescription = if (selected) "Selected" else "Not selected",
        semanticRole = Role.RadioButton,
    ) {
        Box(
            Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(if (selected) Color.White else Color.White.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painterResource(icon),
                contentDescription = null,
                tint = if (selected) Color.Black else Color.White,
                modifier = Modifier.size(18.dp),
            )
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(label, style = WearType.row, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(detail, style = WearType.rowSub, color = Secondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (selected) {
            Icon(
                painterResource(R.drawable.ic_check),
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(16.dp),
            )
        }
    }
}
