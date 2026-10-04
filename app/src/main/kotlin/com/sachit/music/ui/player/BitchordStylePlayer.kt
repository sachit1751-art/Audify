/**
 * SachitMusic Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 *
 * Expanded player layout ported from the BitChord project's NowPlayingScreen
 * (GPL-3.0): oversized white transport glyphs, a hairline scrubber with
 * elapsed / remaining timestamps, a volume capsule flanked by speaker icons,
 * and a lyrics / modes-pill / queue bottom row — over an artwork-derived mesh
 * gradient backdrop.
 */

package com.sachit.music.ui.player

import android.content.Context
import android.media.AudioManager
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.C
import androidx.media3.common.Player
import coil3.compose.AsyncImage
import com.sachit.music.R
import com.sachit.music.constants.BitchordStyleHideVolumeKey
import com.sachit.music.constants.PlayerArtworkCornerRadius
import com.sachit.music.constants.PlayerArtworkCornerRadiusKey
import com.sachit.music.constants.ThumbnailCornerRadius
import com.sachit.music.models.MediaMetadata
import com.sachit.music.utils.makeTimeString
import com.sachit.music.utils.rememberEnumPreference
import com.sachit.music.utils.rememberPreference
import kotlin.math.roundToInt

/** The player's side margin; content never touches the screen edge. */
private val PlayerGutter = 30.dp

/** Widest the player's content gets before it centres instead of growing. */
private val PlayerMaxWidth = 560.dp

private val ArtTitleGap = 20.dp

private val TRANSPORT_SIZE = 44.dp
private val PLAY_PAUSE_SIZE = 64.dp
private val PLAY_PAUSE_TOUCH_SIZE = 88.dp

private val BOTTOM_ACTION_SIZE = 44.dp
private val PILL_SEGMENT_WIDTH = 54.dp
private val PILL_ICON_SIZE = 24.dp

/** How wide a capsule of [segments] comes out, dividers included. */
private fun pillWidth(segments: Int): Dp =
    PILL_SEGMENT_WIDTH * segments + 1.dp * (segments - 1)

/**
 * The BitChord-style expanded player: artwork and credits over a mesh
 * gradient, with the hairline scrubber, oversized transport and volume
 * capsule pinned to the foot of the screen.
 *
 * All playback state is passed in from [BottomSheetPlayer] so this layout
 * stays a pure view over the same single source of truth.
 *
 * [bottomPadding] reserves the queue peek's footprint at the foot of the
 * screen so the controls never sit under it; the mesh itself runs edge to
 * edge behind it.
 */
@Composable
fun BitchordStylePlayerContent(
    mediaMetadata: MediaMetadata,
    isPlaying: Boolean,
    isLoading: Boolean,
    positionMs: Long,
    durationMs: Long,
    sliderPosition: Long?,
    onScrub: (Long) -> Unit,
    onScrubFinished: () -> Unit,
    repeatMode: Int,
    shuffleEnabled: Boolean,
    onToggleShuffle: () -> Unit,
    onToggleRepeat: () -> Unit,
    canSkipPrevious: Boolean,
    canSkipNext: Boolean,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onPlayPause: () -> Unit,
    isListenTogetherGuest: Boolean,
    onOpenQueue: () -> Unit,
    onToggleLyrics: () -> Unit,
    showLyrics: Boolean,
    positionProvider: () -> Long,
    bottomPadding: Dp,
) {
    val context = LocalContext.current
    val audioManager = remember { context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager }
    val maxVolume = remember { audioManager?.getStreamMaxVolume(AudioManager.STREAM_MUSIC) ?: 15 }
    var volume by remember {
        mutableStateOf(
            audioManager?.getStreamVolume(AudioManager.STREAM_MUSIC)
                ?.div(maxVolume.toFloat()) ?: 0.5f,
        )
    }

    val hideVolumeBar by rememberPreference(BitchordStyleHideVolumeKey, false)

    val palette = rememberArtworkColors(mediaMetadata.thumbnailUrl)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface),
    ) {
        MeshGradientBackground(
            palette = palette,
            trackKey = mediaMetadata.id,
            modifier = Modifier.fillMaxSize(),
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .widthIn(max = PlayerMaxWidth)
                .align(Alignment.TopCenter)
                .padding(bottom = bottomPadding),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(8.dp))

            // ---- Artwork (swapped for the lyric pane while lyrics are open) ----
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = PlayerGutter),
                contentAlignment = Alignment.Center,
            ) {
                AnimatedContent(
                    targetState = showLyrics,
                    transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(220)) },
                    label = "bitchordArtworkLyrics",
                ) { lyrics ->
                    if (lyrics) {
                        InlineLyricsView(
                            mediaMetadata = mediaMetadata,
                            showLyrics = true,
                            positionProvider = positionProvider,
                        )
                    } else {
                        BitchordArtwork(mediaMetadata)
                    }
                }
            }

            // ---- Title / artist ----
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = PlayerGutter)
                    .offset(y = ArtTitleGap / 2),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = mediaMetadata.title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .basicMarquee(iterations = 1, initialDelayMillis = 3000, velocity = 30.dp),
                )
                Text(
                    text = mediaMetadata.artists.joinToString(", ") { it.name },
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White.copy(alpha = 0.7f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .basicMarquee(iterations = 1, initialDelayMillis = 3000, velocity = 30.dp),
                )
            }

            Spacer(Modifier.height(28.dp))

            // ---- Scrubber + timestamps ----
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = PlayerGutter),
            ) {
                val dur = if (durationMs == C.TIME_UNSET || durationMs <= 0L) 0L else durationMs
                val scrubFraction = if (dur == 0L) {
                    0f
                } else {
                    (sliderPosition ?: positionMs).div(dur.toFloat())
                }
                ThinSlider(
                    value = scrubFraction.coerceIn(0f, 1f),
                    onValueChange = { fraction ->
                        if (dur > 0L) onScrub((fraction * dur).toLong())
                    },
                    onValueChangeFinished = onScrubFinished,
                    modifier = Modifier.fillMaxWidth(),
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        // The slider's touch target extends well past the drawn
                        // bar, so pull the labels back up under it.
                        .offset(y = (-9).dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text = makeTimeString(sliderPosition ?: positionMs),
                            style = MaterialTheme.typography.labelMedium,
                            color = Color.White.copy(alpha = 0.55f),
                        )
                        Text(
                            text =
                                if (dur > 0L) {
                                    // No minus sign once the track has run out: "-0:00" at the end of
                                    // every song read as a rendering glitch.
                                    val remaining = (dur - (sliderPosition ?: positionMs)).coerceAtLeast(0L)
                                    if (remaining == 0L) makeTimeString(0L) else "-" + makeTimeString(remaining)
                                } else {
                                    ""
                                },
                            style = MaterialTheme.typography.labelMedium,
                            color = Color.White.copy(alpha = 0.55f),
                        )
                    }
                }
            }

            Spacer(Modifier.height(14.dp))

            // ---- Transport ----
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = PlayerGutter),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TransportGlyph(
                    icon = R.drawable.skip_previous,
                    contentDescription = stringResource(R.string.previous),
                    size = TRANSPORT_SIZE,
                    discAlpha = 0.14f,
                    onClick = onPrevious,
                    enabled = canSkipPrevious && !isListenTogetherGuest,
                )
                if (isLoading) {
                    // Same footprint as the play/pause target — a smaller box
                    // here would shunt everything below it on every load.
                    Box(
                        modifier = Modifier.size(PLAY_PAUSE_TOUCH_SIZE),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator(
                            color = Color.White,
                            strokeWidth = 3.dp,
                            modifier = Modifier.size(38.dp),
                        )
                    }
                } else {
                    TransportGlyph(
                        icon = if (isPlaying) R.drawable.pause else R.drawable.play,
                        contentDescription = stringResource(if (isPlaying) R.string.pause else R.string.play),
                        size = PLAY_PAUSE_SIZE,
                        touchSize = PLAY_PAUSE_TOUCH_SIZE,
                        discAlpha = 0.26f,
                        onClick = onPlayPause,
                    )
                }
                TransportGlyph(
                    icon = R.drawable.skip_next,
                    contentDescription = stringResource(R.string.next),
                    size = TRANSPORT_SIZE,
                    discAlpha = 0.14f,
                    onClick = onNext,
                    enabled = canSkipNext && !isListenTogetherGuest,
                )
            }

            Spacer(Modifier.height(18.dp))

            // ---- Volume ----
            if (!hideVolumeBar) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = PlayerGutter),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        painter = painterResource(R.drawable.volume_down),
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.5f),
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(Modifier.width(10.dp))
                    ThinSlider(
                        value = volume,
                        onValueChange = { v ->
                            volume = v
                            audioManager?.setStreamVolume(
                                AudioManager.STREAM_MUSIC,
                                (v * maxVolume).roundToInt(),
                                0,
                            )
                        },
                        idleHeight = 6.dp,
                        activeHeight = 10.dp,
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(10.dp))
                    Icon(
                        painter = painterResource(R.drawable.volume_up),
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.5f),
                        modifier = Modifier.size(20.dp),
                    )
                }
            } else {
                // ThinSlider's fixed touch target: activeHeight (10dp) + 22dp.
                Spacer(Modifier.height(32.dp))
            }

            Spacer(Modifier.height(6.dp))

            // ---- Bottom row: lyrics | modes pill | queue ----
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                val widestRow = BOTTOM_ACTION_SIZE * 2 + pillWidth(2)
                val edgeInset = ((maxWidth - widestRow) / 4).coerceAtLeast(0.dp)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = edgeInset),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    BottomGlyph(
                        icon = painterResource(R.drawable.lyrics),
                        contentDescription = stringResource(if (showLyrics) R.string.close else R.string.lyrics),
                        onClick = onToggleLyrics,
                        highlighted = showLyrics,
                    )
                    ModePill(
                        repeatMode = repeatMode,
                        shuffleEnabled = shuffleEnabled,
                        onToggleShuffle = onToggleShuffle,
                        onToggleRepeat = onToggleRepeat,
                        isListenTogetherGuest = isListenTogetherGuest,
                    )
                    BottomGlyph(
                        icon = painterResource(R.drawable.queue_music),
                        contentDescription = stringResource(R.string.up_next),
                        onClick = onOpenQueue,
                    )
                }
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun BitchordArtwork(mediaMetadata: MediaMetadata) {
    val artworkCornerRadius by rememberEnumPreference(
        key = PlayerArtworkCornerRadiusKey,
        defaultValue = PlayerArtworkCornerRadius.SUBTLE,
    )
    val cornerRadius = when (artworkCornerRadius) {
        PlayerArtworkCornerRadius.NONE -> 0.dp
        PlayerArtworkCornerRadius.SUBTLE -> ThumbnailCornerRadius
        PlayerArtworkCornerRadius.ROUNDED -> 14.dp
    }
    AsyncImage(
        model = mediaMetadata.thumbnailUrl,
        contentDescription = null,
        contentScale = ContentScale.Fit,
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .clip(RoundedCornerShape(cornerRadius)),
    )
}

@Composable
private fun ModePill(
    repeatMode: Int,
    shuffleEnabled: Boolean,
    onToggleShuffle: () -> Unit,
    onToggleRepeat: () -> Unit,
    isListenTogetherGuest: Boolean,
) {
    Row(
        modifier = Modifier
            .height(BOTTOM_ACTION_SIZE)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.12f)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PillSegment(
            icon = painterResource(R.drawable.shuffle),
            contentDescription = stringResource(
                if (shuffleEnabled) R.string.action_shuffle_on else R.string.action_shuffle_off,
            ),
            onClick = onToggleShuffle,
            enabled = !isListenTogetherGuest,
            highlighted = shuffleEnabled,
        )
        PillDivider()
        PillSegment(
            icon = if (repeatMode == Player.REPEAT_MODE_ONE) null else painterResource(R.drawable.repeat),
            label = if (repeatMode == Player.REPEAT_MODE_ONE) "1" else null,
            contentDescription = when (repeatMode) {
                Player.REPEAT_MODE_ONE -> stringResource(R.string.repeat_mode_one)
                Player.REPEAT_MODE_ALL -> stringResource(R.string.repeat_mode_all)
                else -> stringResource(R.string.repeat_mode_off)
            },
            onClick = onToggleRepeat,
            enabled = !isListenTogetherGuest,
            highlighted = repeatMode != Player.REPEAT_MODE_OFF,
        )
    }
}

@Composable
private fun PillDivider() {
    Box(
        Modifier
            .width(1.dp)
            .height(20.dp)
            .background(Color.White.copy(alpha = 0.20f)),
    )
}

/**
 * One control inside a mode capsule — [BottomGlyph]'s twin, squared off.
 * A glyph's highlight is a circle sized to itself; a segment's fills its
 * share of the capsule edge to edge or the join stops reading as one.
 */
@Composable
private fun PillSegment(
    contentDescription: String,
    onClick: () -> Unit,
    icon: Painter? = null,
    label: String? = null,
    highlighted: Boolean = false,
    enabled: Boolean = true,
) {
    Box(
        modifier = Modifier
            .width(PILL_SEGMENT_WIDTH)
            .height(BOTTOM_ACTION_SIZE)
            .background(if (highlighted) Color.White.copy(alpha = 0.14f) else Color.Transparent)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                enabled = enabled,
            ) {
                onClick()
            },
        contentAlignment = Alignment.Center,
    ) {
        val tint = Color.White.copy(alpha = if (highlighted) 1f else 0.75f)
        if (icon != null) {
            Icon(
                painter = icon,
                contentDescription = contentDescription,
                tint = tint,
                modifier = Modifier.size(PILL_ICON_SIZE),
            )
        } else if (label != null) {
            Text(
                text = label,
                color = tint,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

/**
 * Transport glyph. No ripple: the disc brightening under the finger is the feedback.
 *
 * [discAlpha] gives the control a translucent disc behind it. The transport row used to be bare
 * outline glyphs on the artwork, which left the primary action with no visual weight at all; the
 * discs follow the same language as [BottomGlyph] so the row reads as one set of controls.
 */
@Composable
private fun TransportGlyph(
    icon: Int,
    contentDescription: String,
    size: Dp,
    touchSize: Dp = size,
    discAlpha: Float = 0f,
    onClick: () -> Unit,
    enabled: Boolean = true,
) {
    // Faded rather than hidden: the row keeps its shape at the ends of a queue.
    val alpha by animateFloatAsState(
        targetValue = if (enabled) 1f else 0.3f,
        label = "transportAlpha",
    )
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val pressedScale by animateFloatAsState(
        targetValue = if (isPressed) 1.08f else 1f,
        label = "transportPressScale",
    )

    Box(
        modifier =
            Modifier
                .size(touchSize)
                .then(
                    if (discAlpha > 0f) {
                        Modifier
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = if (isPressed) discAlpha + 0.1f else discAlpha))
                    } else {
                        Modifier
                    },
                ).clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    enabled = enabled,
                ) {
                    onClick()
                },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = contentDescription,
            tint = Color.White.copy(alpha = alpha),
            modifier =
                Modifier
                    .size(size)
                    .scale(pressedScale),
        )
    }
}

@Composable
private fun BottomGlyph(
    icon: Painter,
    contentDescription: String,
    onClick: () -> Unit,
    highlighted: Boolean = false,
) {
    val discColor by animateColorAsState(
        targetValue = if (highlighted) Color.White.copy(alpha = 0.34f) else Color.White.copy(alpha = 0.18f),
        label = "glyphDisc",
    )
    Box(
        modifier = Modifier
            .size(BOTTOM_ACTION_SIZE)
            .clip(CircleShape)
            .background(discColor)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) {
                onClick()
            },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = icon,
            contentDescription = contentDescription,
            tint = Color.White,
            modifier = Modifier.size(22.dp),
        )
    }
}
