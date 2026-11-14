package com.music.audify.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.music.audify.R
import com.music.audify.data.model.ROW_ART_PX
import com.music.audify.data.model.Song
import com.music.audify.data.model.artworkAt
import com.music.audify.ui.components.ExplicitSongTitle
import com.music.audify.ui.components.FadingLine
import com.music.audify.ui.components.TrackTextSwitch
import com.music.audify.ui.components.LocalLiquidGlassEnabled
import com.music.audify.ui.components.MiniPlayerNextButton
import com.music.audify.ui.components.MiniPlayerPlayPauseButton
import com.music.audify.ui.components.glassContentColor
import com.music.audify.ui.components.isGlassSupported
import com.music.audify.ui.components.liquidGlass
import com.music.audify.ui.components.miniPlayerGestures
import com.music.audify.ui.components.thumbnailBorder
import com.music.audify.ui.components.rememberRemoteArtworkUrl
import com.music.audify.ui.haptics.Haptic
import com.music.audify.ui.haptics.rememberHaptics
import kotlin.math.roundToInt

/** Up to this much of the open fraction the mini row is still on the panel. */
private const val MINI_ROW_UNTIL = 0.2f

/**
 * The same two, in a light theme. The pill there is pale glass and the player
 * is dark, so there is no middle that matches either: the less of the drag
 * spent between them, the less of it is a muddy grey with the pill's dark
 * text half-visible on it.
 */
private const val MINI_ROW_UNTIL_LIGHT = 0.1f
private const val PANEL_FILL_UNTIL_LIGHT = 0.1f
private const val PANEL_FILL_UNTIL_DARK = 0.2f

/** What the panel is filled with where the liquid glass is off. */
private val PanelFallbackFill = Color(0xFF151517)

private val MINI_ART_SIZE = 30.4.dp
private val MINI_ART_CORNER = 5.7.dp

/** The pill's own radius, shrinking with the panel's progress — read at draw. */
private class PanelCorner(private val dock: PlayerDock) : CornerSize {
    override fun toPx(shapeSize: Size, density: Density): Float =
        dock.panelCornerPx(22f * density.density)
}

/**
 * The mini player's pill and the full player as ONE surface, in the window the
 * pill is in.
 *
 * The container is a single glass shape — the pill's material — whose width,
 * height and corners follow [PlayerDock.panelBounds]. The pill's own row
 * ([PanelMiniRow]) fades out of it and the full player fades in, both on the
 * container; the real pill underneath stands aside while this is up (see
 * [PlayerDock.panelShown]), so there is never a second panel to see.
 *
 * Without a [dock] ("reduce animation") it is just the player, full window.
 */
@Composable
fun PlayerPanel(
    dock: PlayerDock?,
    motion: PlayerSheetMotion?,
    song: Song,
    isPlaying: Boolean,
    isLoading: Boolean,
    controlsLocked: Boolean,
    onBlockedControl: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    scrimAlpha: Float,
    onLaidOut: (IntSize) -> Unit,
    modifier: Modifier = Modifier,
    /** Drawn above the panel in the window, unclipped — the ⋯ menu. */
    overlay: @Composable BoxScope.() -> Unit = {},
    content: @Composable () -> Unit,
) {
    var full by remember { mutableStateOf(IntSize.Zero) }
    // Whatever takes the panel away, the pill comes back with it.
    DisposableEffect(dock) {
        onDispose { dock?.releasePanel() }
    }
    val shape = remember(dock) {
        RoundedCornerShape(if (dock != null) PanelCorner(dock) else CornerSize(0.dp))
    }
    val covered by remember(dock) {
        derivedStateOf { dock != null && dock.attached && dock.openFraction() >= 1f }
    }
    val lightTheme = MaterialTheme.colorScheme.surface.luminance() > 0.5f
    val rowUntil = if (lightTheme) MINI_ROW_UNTIL_LIGHT else MINI_ROW_UNTIL
    val fillUntil = if (lightTheme) PANEL_FILL_UNTIL_LIGHT else PANEL_FILL_UNTIL_DARK
    val miniVisible by remember(dock, rowUntil) {
        derivedStateOf { dock != null && dock.openFraction() < rowUntil }
    }
    val glassOn = LocalLiquidGlassEnabled.current && isGlassSupported()
    // The pill's own liquid glass is the container, so pill and panel are one
    // surface. As it opens, a plain fill comes in over the glass (see
    // [PlayerDock.panelFill]) and the glass is dropped once that is opaque,
    // which is when its blur stops costing anything.
    val glassNeeded by remember(dock, fillUntil) {
        derivedStateOf { dock != null && dock.panelFill(fillUntil) < 1f }
    }
    val surface = when {
        dock == null || covered -> Modifier
        glassOn && glassNeeded -> Modifier.liquidGlass(shape)
        glassOn -> Modifier
        else -> Modifier.background(dock?.panelColor ?: PanelFallbackFill, shape)
    }

    Box(
        modifier
            .fillMaxSize()
            .onSizeChanged {
                full = it
                onLaidOut(it)
            }
            .onGloballyPositioned { dock?.panelPlaced(it) }
            .drawBehind {
                if (dock == null) return@drawBehind
                val open = dock.openFraction()
                // Not under a portrait player that is fully up: its own
                // background is opaque, and a full-screen blend behind it
                // would be paid on every frame for nothing anyone can see.
                if (open > 0f && !(open >= 1f && dock.attached)) {
                    drawRect(Color.Black.copy(alpha = scrimAlpha * open))
                }
            },
    ) {
        Box(
            (if (dock != null) Modifier.panelBounds(dock) else Modifier.fillMaxSize())
                .then(surface)
                .then(if (motion != null) Modifier.playerPullDown(motion) else Modifier),
        ) {
            if (dock != null && !covered) {
                Box(
                    Modifier
                        .matchParentSize()
                        .graphicsLayer { alpha = dock.panelFill(fillUntil) }
                        .background(dock.panelColor, shape),
                )
            }
            Box(Modifier.fillMaxSize().clip(shape)) {
                Box(
                    (if (dock != null) Modifier.windowSized(dock) { full } else Modifier.fillMaxSize())
                        .graphicsLayer {
                            // Once the player takes part in the docking it fades
                            // itself (and flies its cover, title and artist
                            // outside that fade), so the panel must not.
                            alpha = if (dock == null || dock.attached) {
                                1f
                            } else {
                                (dock.openFraction() / rowUntil).coerceIn(0f, 1f)
                            }
                        },
                ) { content() }
                if (dock != null && miniVisible) {
                    val density = LocalDensity.current
                    val windowWidth = LocalWindowInfo.current.containerSize.width
                    val pill = remember(dock) { dock.miniPillInWindow() }
                    val rowHeight = with(density) { (pill?.height ?: 0f).toDp() }.takeIf { it > 0.dp } ?: 52.dp
                    // Inline in the tab bar the pill is narrow and drops its
                    // skip button; the row does the same.
                    // The plain mini player is never inline: it always has its skip button.
                    val showNext = !glassOn || pill == null || pill.width > windowWidth * 0.8f
                    PanelMiniRow(
                        dock = dock,
                        song = song,
                        isPlaying = isPlaying,
                        isLoading = isLoading,
                        showNext = showNext,
                        glass = glassOn,
                        controlsLocked = controlsLocked,
                        onBlockedControl = onBlockedControl,
                        onPlayPause = onPlayPause,
                        onNext = onNext,
                        onPrevious = onPrevious,
                        motion = motion,
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .fillMaxWidth()
                            .height(rowHeight)
                            .graphicsLayer {
                                alpha = (1f - dock.openFraction() / rowUntil).coerceIn(0f, 1f)
                            },
                    )
                }
            }
        }
        overlay()
    }
}

/** The container's place this frame: size and position come from the dock. */
private fun Modifier.panelBounds(dock: PlayerDock): Modifier = layout { measurable, constraints ->
    val window = Size(constraints.maxWidth.toFloat(), constraints.maxHeight.toFloat())
    val rect = dock.panelBounds(window)
    val width = rect.width.roundToInt().coerceAtLeast(1)
    val height = rect.height.roundToInt().coerceAtLeast(1)
    val placeable = measurable.measure(Constraints.fixed(width, height))
    layout(constraints.maxWidth, constraints.maxHeight) {
        placeable.place(rect.left.roundToInt(), rect.top.roundToInt())
    }
}

/**
 * The full player, laid out at the whole window's size whatever the panel is,
 * and put back where the window's left edge is — so the panel reveals it
 * instead of squeezing it.
 */
private fun Modifier.windowSized(dock: PlayerDock, full: () -> IntSize): Modifier =
    layout { measurable, constraints ->
        val size = full()
        val placeable = measurable.measure(Constraints.fixed(size.width, size.height))
        layout(constraints.maxWidth, constraints.maxHeight) {
            val rect = dock.panelBounds(Size(size.width.toFloat(), size.height.toFloat()))
            placeable.place(-rect.left.roundToInt(), 0)
        }
    }

/**
 * The pill's row, on the panel: the same cover, title, artist and transport as
 * the pill, where the pill had them.
 */
@Composable
private fun PanelMiniRow(
    song: Song,
    isPlaying: Boolean,
    isLoading: Boolean,
    showNext: Boolean,
    controlsLocked: Boolean,
    onBlockedControl: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    motion: PlayerSheetMotion?,
    modifier: Modifier,
    dock: PlayerDock? = null,
    /** Whether the pill underneath is the glass bar's accessory rather than the plain mini player. */
    glass: Boolean = true,
) {
    val haptics = rememberHaptics()
    val contentColor = if (glass) glassContentColor() else MaterialTheme.colorScheme.onBackground
    val secondary = if (glass) {
        contentColor.copy(alpha = 0.7f)
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    // The same type as the pill's own: the pill comes back when this panel
    // goes, and any difference reads as the title resizing. The glass bar's
    // accessory and the plain mini player are set differently, so this
    // follows whichever of them is underneath.
    val titleStyle = if (glass) {
        MaterialTheme.typography.titleMedium.copy(
            fontSize = 13.sp,
            fontWeight = FontWeight.W700,
            letterSpacing = 0.15.sp,
        )
    } else {
        MaterialTheme.typography.titleMedium
    }
    val artistStyle = if (glass) {
        MaterialTheme.typography.bodyMedium.copy(fontSize = 12.sp, letterSpacing = 0.35.sp)
    } else {
        MaterialTheme.typography.bodyMedium
    }
    val glyphSlot = if (!glass) 40.dp else if (showNext) 40.dp else 32.dp
    // 29dp expanded, as the glass accessory's own play glyph is.
    val glyphSize = if (!glass) 32.dp else if (showNext) 29.dp else 24.dp
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .miniPlayerGestures(
                pull = motion,
                onNext = {
                    haptics.play(Haptic.SkipNext)
                    onNext()
                },
                onPrevious = {
                    haptics.play(Haptic.SkipPrevious)
                    onPrevious()
                },
                locked = controlsLocked,
                onBlocked = onBlockedControl,
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = { motion?.open() },
            ),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    start = if (glass) 16.dp else 12.dp,
                    end = if (!glass) 12.dp else if (showNext) 12.dp else 8.dp,
                    top = if (glass) 4.dp else 8.dp,
                    bottom = if (glass) 4.dp else 8.dp,
                ),
        ) {
            val artSize = if (glass) MINI_ART_SIZE else 40.dp
            val artShape = RoundedCornerShape(if (glass) MINI_ART_CORNER else 8.dp)
            val artWidth = when {
                !song.isVideo -> artSize
                glass -> artSize * (16f / 9f)
                else -> 71.dp
            }
            Box(
                Modifier
                    .size(width = artWidth, height = artSize)
                    // The player's own cover is the one that flies; this one
                    // stands aside as soon as it does.
                    .graphicsLayer { alpha = if (dock != null && dock.coversMiniArt) 0f else 1f }
                    .clip(artShape)
                    .then(if (glass) Modifier else Modifier.thumbnailBorder(artShape))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
            ) {
                AsyncImage(
                    model = rememberRemoteArtworkUrl(song)?.artworkAt(ROW_ART_PX),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            Spacer(Modifier.width(if (!glass) 10.dp else if (showNext) 9.dp else 8.dp))
            TrackTextSwitch(song, Modifier.weight(1f)) { shown ->
                Column {
                    ExplicitSongTitle(
                        song = shown,
                        style = titleStyle,
                        color = contentColor,
                        badgeColor = secondary,
                        fadeOverflow = true,
                    )
                    FadingLine(text = shown.artist, style = artistStyle, color = secondary)
                }
            }
            if (isLoading) {
                Box(Modifier.size(glyphSlot), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(
                        color = contentColor,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(if (!glass) 22.dp else if (showNext) 20.dp else 18.dp),
                    )
                }
            } else {
                MiniPlayerPlayPauseButton(
                    isPlaying = isPlaying,
                    tint = contentColor,
                    slot = glyphSlot,
                    glyphSize = glyphSize,
                    onClick = {
                        haptics.play(if (isPlaying) Haptic.Pause else Haptic.Resume)
                        onPlayPause()
                    },
                )
            }
            if (showNext) {
                Spacer(Modifier.width(if (glass) 4.dp else 8.dp))
                MiniPlayerNextButton(
                    tint = contentColor.copy(alpha = if (controlsLocked) 0.3f else 1f),
                    enabled = !controlsLocked,
                    slot = glyphSlot,
                    onClick = {
                        haptics.play(Haptic.SkipNext)
                        onNext()
                    },
                )
            }
        }
    }
}

/**
 * Drag the open player down to put it away — what the modal sheet used to do
 * for it. A drag nothing inside claims, and the pull a list leaves over when
 * it is already at its top, both move the panel through [PlayerSheetMotion]:
 * the same motion the pill's pull and every close run on.
 */
@Composable
internal fun Modifier.playerPullDown(motion: PlayerSheetMotion): Modifier {
    val connection = remember(motion) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                // Back up first: a pulled panel returns before the list scrolls again.
                if (!motion.pulling || available.y >= 0f) return Offset.Zero
                return Offset(0f, motion.pullBy(available.y))
            }

            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource,
            ): Offset {
                if (source != NestedScrollSource.UserInput || available.y <= 0f) {
                    return Offset.Zero
                }
                return Offset(0f, motion.pullBy(available.y))
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                if (!motion.pulling) return Velocity.Zero
                motion.releasePull(available.y)
                return available
            }
        }
    }
    val dragState = rememberDraggableState { delta -> motion.pullBy(delta) }
    return this
        .nestedScroll(connection)
        .draggable(
            state = dragState,
            orientation = Orientation.Vertical,
            onDragStopped = { velocity -> motion.releasePull(velocity) },
        )
}