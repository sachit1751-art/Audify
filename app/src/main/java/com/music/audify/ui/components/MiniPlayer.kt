package com.music.audify.ui.components

import com.music.audify.R

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.ui.draw.drawBehind
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.input.pointer.util.addPointerInputChange
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.music.audify.data.model.ROW_ART_PX
import com.music.audify.data.model.Song
import com.music.audify.data.model.artworkAt
import com.music.audify.playback.MusicOnlyPlayback
import com.music.audify.data.settings.AppSettings
import com.music.audify.sharedui.resources.Res as SharedRes
import com.music.audify.sharedui.resources.ic_player_next
import com.music.audify.ui.components.thumbnailBorder
import com.music.audify.ui.haptics.Haptic
import com.music.audify.ui.haptics.rememberHaptics
import com.music.audify.ui.player.PlayerDock
import com.music.audify.ui.player.AndroidMusicVideo
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.animation.core.animateDpAsState
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.materials.ExperimentalHazeMaterialsApi
import dev.chrisbanes.haze.materials.HazeMaterials
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.painterResource
import kotlin.math.abs
import kotlin.math.sqrt
import kotlin.math.tanh

/**
 * The transport buttons' touch target. Material's default 48dp is what a bar
 * this slim is really made of, so it sets the height on its own.
 */
private val GLYPH_SLOT = 40.dp

/**
 * The play and skip glyphs themselves.
 *
 * Deliberately grown inside [GLYPH_SLOT] rather than by growing the slot: the
 * slot is level with the 40dp artwork opposite it, and it is the taller of the
 * two that sets the row's height — so a bigger slot would make the whole bar
 * taller, which is not what a bigger glyph is being asked for. At 32 there is
 * still 4dp of clearance to the slot's edge on every side.
 */
private val GLYPH_SIZE = 32.dp

/** The spinner that stands in for the play glyph, kept in proportion to it. */
private val SPINNER_SIZE = 22.dp

/**
 * The gap between the two transport controls.
 *
 * Material asks for at least 8dp between adjacent touch targets, and these had
 * none: two [GLYPH_SLOT] boxes sharing an edge, so the boundary between "pause"
 * and "skip" was a line with nothing either side of it. What space there looked
 * to be was only the margin each glyph keeps inside its own slot, and a thumb
 * lands on a target's edge far more often than it lands on a glyph's.
 *
 * Taken from the title's width rather than the bar's height, so nothing above
 * or below it moves.
 */
private val TRANSPORT_GAP = 8.dp

/**
 * Vertical padding, which with the 40dp artwork sets the bar's height at 56dp
 * and so its pill radius at 28.
 */
private val ROW_PADDING_VERTICAL = 8.dp

/**
 * Horizontal padding, deliberately larger than the vertical.
 *
 * A pill's ends are semicircles, so the edge nearest the artwork is not the
 * one beside it but the one curving away above and below it. At the artwork's
 * top corner that edge has already come 8.4dp in from the left — level with
 * where square corners would have put the whole side. Padding the ends by the
 * vertical figure would leave the artwork touching the curve; 12 clears it
 * with room, and reads as centred rather than jammed into the round.
 */
private val ROW_PADDING_HORIZONTAL = 12.dp

/**
 * The artwork's corner, on the 8dp every other thumbnail in the app carries.
 *
 * It used to be 7, picked so the bar's corner could sit concentric with it.
 * A pill has no corner to be concentric with — its radius is whatever half the
 * height happens to be — so that constraint is gone and the artwork can go
 * back to matching [SongRow].
 */
private val ART_CORNER = 8.dp

/** Distance that makes a horizontal drag an intentional track change. */
private val TRACK_SWIPE_THRESHOLD = 72.dp

/**
 * How much further a drag may go sideways than up and still be a pull. A
 * thumb swiping up from the bottom of the screen travels on an arc, and judged
 * level, 1:1, a good share of real pulls came out sideways — too short to skip
 * a track, so they did nothing at all. Steeper than about 34 degrees above
 * level is a pull.
 */
private const val PULL_BIAS = 1.5f

/**
 * The finger distance over which the bar's own rise slows to about half its
 * starting rate. It is the player that follows the finger; the bar gives a
 * little and then less, so the pull visibly takes hold of something before
 * the player has come up over it.
 */
private val PULL_LIFT_EASE = 72.dp

/** The bar's rise per pixel of finger as a pull starts. */
private const val PULL_LIFT_RATE = 0.5f

/** Seberapa jauh bar boleh bergeser ke samping saat di-swipe (batas lembut). */
private val JELLY_MAX_OFFSET = 36.dp

/** Melar horizontal saat ditarik, dan gepeng vertikal. 0.12 = 12% pada tarikan penuh. */
private const val JELLY_STRETCH_X = 0.12f
private const val JELLY_SQUASH_Y = 0.10f

/** Makin kecil makin bergoyang. Dipakai saat bar dilepas dan memantul balik. */
private const val JELLY_DAMPING = 0.3f

/**
 * Goyangan jelly mini player yang dibagikan ke tetangganya di bar inline (tab dan
 * Search). [miniPlayerGestures] menulisnya, tab bar membacanya saat draw untuk
 * mendorong tombol di sebelah pill. Semua isinya hanya dibaca di layer, jadi
 * tidak ada recomposition.
 */
class JellyPush {
    internal val offset = mutableFloatStateOf(0f)
    internal val squish = mutableFloatStateOf(0f)
    internal val phase = mutableFloatStateOf(0f)
    internal val width = mutableFloatStateOf(0f)

    /**
     * Seberapa jauh (px) tepi kiri atau kanan pill maju melewati posisi diamnya,
     * dihitung dari geseran ditambah setengah regangannya. Nol kalau tepi itu
     * justru mundur.
     */
    fun advance(towardLeft: Boolean): Float {
        val p = phase.floatValue
        val s = squish.floatValue * p
        val shift = offset.floatValue * p
        val grow = width.floatValue * JELLY_STRETCH_X * s / 2f
        val edge = if (towardLeft) -shift + grow else shift + grow
        return edge.coerceAtLeast(0f)
    }
}

/**
 * How far the bar rises for [pulled] pixels of finger: [PULL_LIFT_RATE] of the
 * finger to begin with, slowing the further it goes — a rubber band with no
 * end to it, so the bar keeps creeping up for as long as the finger does
 * rather than meeting a ceiling. Its rate is RATE / sqrt(1 + pulled / ease).
 */
private fun pullLift(pulled: Float, ease: Float): Float =
    if (pulled <= 0f || ease <= 0f) {
        0f
    } else {
        PULL_LIFT_RATE * 2f * ease * (sqrt(1f + pulled / ease) - 1f)
    }

/**
 * Pulling the mini player up into the full player, the way the full player is
 * pulled back down into it: the player follows the finger the whole way, and
 * on release carries on to whichever end the gesture meant — open for a flick
 * upwards or for most of the way up, closed for anything less.
 *
 * Implemented by the host, which owns the player's sheet.
 */
interface MiniPlayerPull {
    /** The finger moved this far, in pixels — negative is upwards. */
    fun drag(delta: Float)

    /** The finger lifted, moving this fast in pixels a second. */
    fun release(velocity: Float)
}

/**
 * Both of the bar's drags, for both mini-player materials: sideways through
 * the queue — a left swipe advances, a right one goes back, matching the full
 * player's artwork — and up into the player through [pull].
 *
 * One detector rather than one per axis, so a gesture is decided once, by the
 * way it is heading as it passes the touch slop. Two detectors raced each
 * other to their own axis's slop, and the sideways one was asked first — see
 * [PULL_BIAS] for what that did to pulls. The tap to expand is left to the
 * clickable, and is cancelled by this consuming the drag, as it was before.
 *
 * Under a pull the bar itself rises with it, less and less ([pullLift]), so this goes
 * where the bar's own transforms start: the finger is read outside that lift,
 * so a rising bar doesn't take distance off the finger it is following.
 *
 * Track swipes wait until the finger lifts, so one long gesture never skips
 * more than one item.
 */
@Composable
internal fun Modifier.miniPlayerGestures(
    pull: MiniPlayerPull?,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    /** Listening in a party whose host holds the controls. Swipes say so instead of skipping. */
    locked: Boolean = false,
    onBlocked: () -> Unit = {},
    /** Kalau diisi, goyangan jelly dibagikan agar tab dan Search di sebelah pill ikut terdorong. */
    push: JellyPush? = null,
): Modifier {
    // Playback state updates can recompose the bar while a finger is down.
    // Keep the gesture coroutine alive through those updates while still
    // dispatching to the latest controller callbacks when the drag finishes.
    val currentPull by rememberUpdatedState(pull)
    val currentOnNext by rememberUpdatedState(onNext)
    val currentOnPrevious by rememberUpdatedState(onPrevious)
    val currentLocked by rememberUpdatedState(locked)
    val currentOnBlocked by rememberUpdatedState(onBlocked)
    val scope = rememberCoroutineScope()
    // Read at draw only, in the layer below: a pull moves the bar without
    // recomposing it.
    val lift = remember { mutableFloatStateOf(0f) }
    val settle = remember { arrayOfNulls<Job>(1) }
    // Jelly: geser, gepeng, dan fase pantulan. Hanya dibaca saat draw.
    val jOffset = push?.offset ?: remember { mutableFloatStateOf(0f) }
    val jSquish = push?.squish ?: remember { mutableFloatStateOf(0f) }
    val jPhase = push?.phase ?: remember { mutableFloatStateOf(0f) }
    val jellyJob = remember { arrayOfNulls<Job>(1) }
    return pointerInput(Unit) {
        val trackThreshold = TRACK_SWIPE_THRESHOLD.toPx()
        val liftEase = PULL_LIFT_EASE.toPx()
        val jellyMax = JELLY_MAX_OFFSET.toPx()
        val jellyFollow: (Float) -> Unit = { d ->
            val o = jellyMax * tanh(d / (jellyMax * 2.5f))
            jOffset.floatValue = o
            jSquish.floatValue = abs(o) / jellyMax
        }
        val maxVelocity = viewConfiguration.maximumFlingVelocity
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            val pointer = down.id
            // Up to the slop: which way this is heading, or nothing at all.
            var start: PointerInputChange? = null
            var vertical = false
            while (start == null) {
                val change = awaitPointerEvent().changes.firstOrNull { it.id == pointer }
                    ?: return@awaitEachGesture
                // Lifted inside the slop is a tap, and the tap is the clickable's.
                if (!change.pressed || change.isConsumed) return@awaitEachGesture
                val moved = change.position - down.position
                if (moved.getDistance() < viewConfiguration.touchSlop) continue
                vertical = moved.y < 0f && abs(moved.x) < -moved.y * PULL_BIAS
                // Downwards has nothing to do on the bar, and nor has a pull
                // with no player to pull: left as they always were, a drag that
                // ends inside the bar still counts as its tap.
                if (!vertical && abs(moved.x) < abs(moved.y)) return@awaitEachGesture
                if (vertical && currentPull == null) return@awaitEachGesture
                change.consume()
                start = change
            }
            val slopped = start ?: return@awaitEachGesture

            if (!vertical) {
                jellyJob[0]?.cancel()
                jPhase.floatValue = 1f
                var dragged = slopped.position.x - down.position.x
                jellyFollow(dragged)
                // Read before consuming: a consumed change reports no movement.
                val finished = drag(pointer) { change ->
                    dragged += change.positionChange().x
                    change.consume()
                    jellyFollow(dragged)
                }
                // Dilepas atau dibatalkan: bar memantul balik seperti jelly.
                jPhase.floatValue = 1f
                jellyJob[0] = scope.launch {
                    animate(
                        initialValue = 1f,
                        targetValue = 0f,
                        animationSpec = spring(
                            dampingRatio = JELLY_DAMPING,
                            stiffness = Spring.StiffnessLow,
                        ),
                    ) { value, _ -> jPhase.floatValue = value }
                    jOffset.floatValue = 0f
                    jSquish.floatValue = 0f
                    jPhase.floatValue = 1f
                }
                if (!finished) return@awaitEachGesture
                val crossed = dragged <= -trackThreshold || dragged >= trackThreshold
                when {
                    currentLocked -> if (crossed) currentOnBlocked()
                    dragged <= -trackThreshold -> currentOnNext()
                    dragged >= trackThreshold -> currentOnPrevious()
                }
                return@awaitEachGesture
            }

            val target = currentPull ?: return@awaitEachGesture
            settle[0]?.cancel()
            val tracker = VelocityTracker()
            tracker.addPointerInputChange(down)
            tracker.addPointerInputChange(slopped)
            // The player gets all of it from the touch down, slop included, so
            // it starts out level with the finger. The bar starts from here,
            // from nothing: lifted by the slop as well, it jumped a few pixels
            // in one frame before it began to follow.
            target.drag(slopped.position.y - down.position.y)
            var pulled = 0f
            val finished = drag(pointer) { change ->
                val delta = change.positionChange().y
                change.consume()
                tracker.addPointerInputChange(change)
                pulled -= delta
                target.drag(delta)
                lift.floatValue = pullLift(pulled, liftEase)
            }
            target.release(
                if (finished) tracker.calculateVelocity().y.coerceIn(-maxVelocity, maxVelocity) else 0f,
            )
            // Let go, the bar springs home: under the player on its way up, or
            // in full view if the pull fell short and the player went back.
            val from = lift.floatValue
            settle[0] = scope.launch {
                animate(
                    initialValue = from,
                    targetValue = 0f,
                    animationSpec = spring(
                        dampingRatio = 0.65f,
                        stiffness = Spring.StiffnessMediumLow,
                    ),
                ) { value, _ -> lift.floatValue = value }
            }
        }
    }.then(
        if (push == null) Modifier else Modifier.onSizeChanged { push.width.floatValue = it.width.toFloat() },
    ).graphicsLayer {
        val p = jPhase.floatValue
        val s = jSquish.floatValue * p
        translationX = jOffset.floatValue * p
        scaleX = 1f + JELLY_STRETCH_X * s
        scaleY = 1f - JELLY_SQUASH_Y * s
        translationY = -lift.floatValue
    }
}

/**
 * The mini player's cover as one end of the player opening and closing — see
 * [PlayerDock]. Shared by both mini-player materials.
 *
 * Reports where it is on being placed, and nothing more: the coordinates are
 * the same object for as long as the cover is on screen, and the player turns
 * them into a position only on the frames it is actually flying to them, so a
 * bar folding and unfolding on every scroll pays nothing for this.
 *
 * Stands aside while the player's own artwork is on its way to or from here.
 * Read at draw, off the sheet's offset — a layer property per frame of the
 * sheet's travel, never a recomposition of the bar.
 */
@Composable
internal fun Modifier.playerDockArt(dock: PlayerDock?, corner: Dp): Modifier {
    if (dock == null) return this
    val placed = remember { arrayOfNulls<LayoutCoordinates>(1) }
    DisposableEffect(dock) {
        onDispose { placed[0]?.let(dock::releaseMiniArt) }
    }
    return this
        .onPlaced { coordinates ->
            placed[0] = coordinates
            dock.reportMiniArt(coordinates, corner)
        }
        .graphicsLayer { alpha = if (dock.coversMiniArt) 0f else 1f }
}

/**
 * The mini player's whole pill as the START of the player's morph — see
 * [PlayerDock.morph]. Placed ahead of the pull lift on purpose, so it reports
 * the pill at rest rather than the bar rising under the finger.
 */
@Composable
internal fun Modifier.playerDockPill(dock: PlayerDock?): Modifier {
    if (dock == null) return this
    val placed = remember { arrayOfNulls<LayoutCoordinates>(1) }
    DisposableEffect(dock) {
        onDispose { placed[0]?.let(dock::releaseMiniPill) }
    }
    return this
        .onPlaced { coordinates ->
            placed[0] = coordinates
            dock.reportMiniPill(coordinates)
        }
        // The player's panel is standing where this pill is: it is one surface
        // with it, so the pill stands aside rather than show under it.
        .graphicsLayer { alpha = if (dock.panelShown) 0f else 1f }
}

/** The mini player's cover for a music video: 16:9 at the cover's 40dp height. */
private val VIDEO_ART_WIDTH = 71.dp

/** Frosted mini player that rides just above the floating tab bar. */
@OptIn(ExperimentalHazeMaterialsApi::class)
@Composable
fun MiniPlayer(
    song: Song,
    isPlaying: Boolean,
    isLoading: Boolean,
    hazeState: HazeState,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onExpand: () -> Unit,
    /** @see com.music.audify.data.listentogether.ListenTogether.State.controlsLocked */
    controlsLocked: Boolean = false,
    onBlockedControl: () -> Unit = {},
    /** Where the player's artwork lands when it closes into this bar. */
    dock: PlayerDock? = null,
    /** Dragging the bar up into the player. */
    pull: MiniPlayerPull? = null,
    modifier: Modifier = Modifier,
) {
    val reduceDynamicBlur by AppSettings.reduceDynamicBlur.collectAsStateWithLifecycle()
    val haptics = rememberHaptics()
    // percent rather than a dp figure, so the corner stays exactly half the
    // height if the row's contents ever change it — which is what keeps a pill
    // a pill instead of a rounded rectangle. Same idiom as [FloatingBottomBar]
    // directly below it, so the two shapes are the same family.
    val shape = RoundedCornerShape(percent = 50)
    Box(
        modifier = modifier
            .padding(horizontal = BAR_GUTTER)
            .playerDockPill(dock)
            // Ahead of the surface, so a pull lifts the whole of it, frost and all.
            .miniPlayerGestures(
                pull = pull,
                onNext = {
                    haptics.play(Haptic.SkipNext)
                    com.music.audify.ui.components.TrackFlow.hint(true)
                    onNext()
                },
                onPrevious = {
                    haptics.play(Haptic.SkipPrevious)
                    com.music.audify.ui.components.TrackFlow.hint(false)
                    onPrevious()
                },
                locked = controlsLocked,
                onBlocked = onBlockedControl,
            )
            .clip(shape)
            .then(
                if (reduceDynamicBlur) {
                    Modifier.background(MaterialTheme.colorScheme.surface)
                } else {
                    Modifier.optimizedHazeEffect(
                        state = hazeState,
                        style = HazeMaterials.thin(MaterialTheme.colorScheme.surface),
                    )
                },
            )
            .border(0.5.dp, Color.White.copy(alpha = 0.10f), shape)
            // Deliberately silent: the whole bar is the target, so it catches
            // stray taps meant for the page behind it, and the sheet rising is
            // its own confirmation. The glyphs on it still buzz.
            .clickable(onClick = onExpand),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    horizontal = ROW_PADDING_HORIZONTAL,
                    vertical = ROW_PADDING_VERTICAL,
                ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // A music video plays here at its own 16:9, as the web player's
            // mini player does, rather than cropped into the square cover.
            val artWidth by animateDpAsState(
                targetValue = if (song.isVideo && !VideoThumbnails.square && MusicOnlyPlayback.showsPicture(song.videoId)) VIDEO_ART_WIDTH else 40.dp,
                label = "miniArtWidth",
            )
            Box(
                modifier = Modifier
                    .size(width = artWidth, height = 40.dp)
                    .playerDockArt(dock, ART_CORNER)
                    .clip(RoundedCornerShape(ART_CORNER))
                    .thumbnailBorder(RoundedCornerShape(ART_CORNER))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
            ) {
                AsyncImage(
                    model = rememberRemoteArtworkUrl(song)?.artworkAt(ROW_ART_PX),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
                // A music video keeps playing in the cover's place, cropped to
                // it, as the web player's mini player does — the full player,
                // when it is up, takes the picture over.
                if (song.isVideo && MusicOnlyPlayback.showsPicture(song.videoId)) {
                    AndroidMusicVideo(
                        videoId = song.videoId,
                        fit = false,
                        onRenderedChanged = {},
                        modifier = Modifier.fillMaxSize(),
                        priority = 0,
                    )
                }
            }
            Spacer(Modifier.width(10.dp))
            TrackTextSwitch(song, Modifier.weight(1f)) { shown ->
                Column {
                    // The badge in the artist line's colour and a long line faded
                    // out at its end, as on the glass bar's mini player.
                    ExplicitSongTitle(
                        song = shown,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onBackground,
                        badgeColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        fadeOverflow = true,
                    )
                    FadingLine(
                        text = shown.artist,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (isLoading) {
                Box(Modifier.size(GLYPH_SLOT), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(
                        color = MaterialTheme.colorScheme.onBackground,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(SPINNER_SIZE),
                    )
                }
            } else {
                MiniPlayerPlayPauseButton(
                    isPlaying = isPlaying,
                    tint = MaterialTheme.colorScheme.onBackground,
                    slot = GLYPH_SLOT,
                    glyphSize = GLYPH_SIZE,
                    onClick = {
                        haptics.play(if (isPlaying) Haptic.Pause else Haptic.Resume)
                        onPlayPause()
                    },
                )
            }
            Spacer(Modifier.width(TRANSPORT_GAP))
            // Faded and inert rather than removed while the host holds the
            // controls, so the bar keeps its shape — see [controlsLocked].
            MiniPlayerNextButton(
                tint = MaterialTheme.colorScheme.onBackground
                    .copy(alpha = if (controlsLocked) 0.3f else 1f),
                enabled = !controlsLocked,
                slot = GLYPH_SLOT,
                onClick = {
                    haptics.play(Haptic.SkipNext)
                    com.music.audify.ui.components.TrackFlow.hint(true)
                    onNext()
                },
            )
        }
    }
}

/**
 * The full player's skip-forward glyph, at the mini player's scale.
 *
 * Sized against the play glyph beside it the way the full player sizes it
 * against its own play button — about half as wide again and two thirds as
 * tall — and squashed by the same 0.85, so the two players' next buttons
 * read as one glyph at two sizes. Shared with the glass bar's docked player.
 */
@Composable
internal fun MiniPlayerNextGlyph(tint: Color) {
    Icon(
        painter = painterResource(SharedRes.drawable.ic_player_next),
        contentDescription = stringResource(R.string.widget_next),
        tint = tint,
        modifier = Modifier
            .size(NEXT_GLYPH_SIZE)
            .graphicsLayer { scaleY = 0.85f },
    )
}

private val NEXT_GLYPH_SIZE = 25.dp

/**
 * The mini players' play/pause button, with the full player's feel: the glyph
 * shrinks under the finger and springs back, and play and pause morph into
 * each other (scale + fade) instead of swapping in one frame. Shared by the
 * plain bar, the glass bar and the docked player.
 */
@Composable
internal fun MiniPlayerPlayPauseButton(
    isPlaying: Boolean,
    tint: Color,
    slot: Dp,
    glyphSize: Dp,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val pressScale = animateFloatAsState(
        targetValue = if (pressed) 0.72f else 1f,
        animationSpec = spring(dampingRatio = 0.45f, stiffness = Spring.StiffnessMedium),
        label = "miniPlayPausePress",
    )
    val pressDisc by animateFloatAsState(
        targetValue = if (pressed) 0.18f else 0f,
        animationSpec = tween(durationMillis = if (pressed) 70 else 260),
        label = "miniPlayPauseDisc",
    )
    Box(
        modifier = Modifier
            .size(slot)
            .drawBehind {
                if (pressDisc > 0f) {
                    drawCircle(
                        color = tint.copy(alpha = pressDisc),
                        radius = this.size.minDimension / 2f,
                    )
                }
            }
            .clickable(
                interactionSource = interaction,
                indication = null,
                role = androidx.compose.ui.semantics.Role.Button,
            ) { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        AnimatedContent(
            targetState = isPlaying,
            transitionSpec = {
                (
                    fadeIn(spring(stiffness = Spring.StiffnessMedium)) +
                        scaleIn(
                            initialScale = 0.55f,
                            animationSpec = spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessMediumLow),
                        )
                    ) togetherWith (
                    fadeOut(tween(90)) +
                        scaleOut(targetScale = 0.55f, animationSpec = tween(120))
                    )
            },
            contentAlignment = Alignment.Center,
            label = "miniPlayPause",
        ) { playing ->
            Icon(
                imageVector = if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                contentDescription = stringResource(if (playing) R.string.pause else R.string.play),
                tint = tint,
                modifier = Modifier
                    .size(glyphSize)
                    .graphicsLayer {
                        scaleX = pressScale.value
                        scaleY = pressScale.value
                    },
            )
        }
    }
}

/**
 * The mini players' skip-forward button, with the full player's press feel:
 * the glyph shrinks under the finger and springs back, and a tap kicks it
 * forward with a ring. Shared by the plain bar, the glass bar and the docked
 * player so all three answer a tap the same way.
 */
@Composable
internal fun MiniPlayerNextButton(
    tint: Color,
    enabled: Boolean,
    slot: Dp,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val pressScale = animateFloatAsState(
        targetValue = if (pressed && enabled) 0.72f else 1f,
        animationSpec = spring(dampingRatio = 0.45f, stiffness = Spring.StiffnessMedium),
        label = "miniNextPress",
    )
    val pressDisc by animateFloatAsState(
        targetValue = if (pressed && enabled) 0.18f else 0f,
        animationSpec = tween(durationMillis = if (pressed) 70 else 260),
        label = "miniNextDisc",
    )
    val scope = rememberCoroutineScope()
    val kick = remember { Animatable(0f) }
    Box(
        modifier = Modifier
            .size(slot)
            .drawBehind {
                if (pressDisc > 0f) {
                    drawCircle(
                        color = tint.copy(alpha = pressDisc),
                        radius = this.size.minDimension / 2f,
                    )
                }
            }
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = enabled,
                role = androidx.compose.ui.semantics.Role.Button,
            ) {
                scope.launch {
                    kick.snapTo(0f)
                    kick.animateTo(1f, tween(durationMillis = 80, easing = FastOutSlowInEasing))
                    kick.animateTo(0f, spring(dampingRatio = 0.4f, stiffness = Spring.StiffnessMediumLow))
                }
                onClick()
            },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(SharedRes.drawable.ic_player_next),
            contentDescription = stringResource(R.string.widget_next),
            tint = tint,
            modifier = Modifier
                .size(NEXT_GLYPH_SIZE)
                .graphicsLayer {
                    val k = kick.value
                    translationX = k * 6.dp.toPx()
                    scaleX = pressScale.value * (1f + abs(k) * 0.15f)
                    // The glyph's own 0.85 squash, kept so it still matches the play glyph.
                    scaleY = pressScale.value * (1f - abs(k) * 0.06f) * 0.85f
                },
        )
    }
}
