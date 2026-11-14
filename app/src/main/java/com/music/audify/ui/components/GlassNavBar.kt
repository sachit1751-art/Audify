/*
 * The bar's structure and its inline/expanded behaviour are
 * EchoMusicApp/Echo-Music's AppFloatingNavBar + FloatingMiniPlayer (GPL-3.0),
 * over the FloatingTabBar vendored in [com.music.audify.ui.components.floatingtabbar].
 * Audify's own tabs, song model, transport and haptics are wired through it in
 * place of Echo's Screens routing and PlayerConnection.
 */
@file:OptIn(ExperimentalSharedTransitionApi::class)

package com.music.audify.ui.components

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.windowInsetsPadding
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.music.audify.R
import com.music.audify.data.model.ROW_ART_PX
import com.music.audify.data.model.Song
import com.music.audify.data.model.artworkAt
import com.music.audify.playback.MusicOnlyPlayback
import com.music.audify.ui.components.floatingtabbar.FloatingTabBar
import com.music.audify.ui.components.floatingtabbar.FloatingTabBarDefaults
import com.music.audify.ui.components.floatingtabbar.FloatingTabBarScrollConnection
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.derivedStateOf
import androidx.compose.ui.geometry.isUnspecified
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.semantics.clearAndSetSemantics
import com.music.audify.ui.components.floatingtabbar.LocalTabPillGeometry
import com.music.audify.ui.haptics.Haptic
import com.music.audify.ui.haptics.rememberHaptics
import com.music.audify.ui.player.PlayerDock
import com.music.audify.ui.player.AndroidMusicVideo
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.music.audify.data.settings.AppSettings
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.layout.fillMaxSize

/**
 * The liquid glass navigation bar: the iOS 26 shape where the now playing
 * controls and the tabs are one component rather than two stacked bars.
 *
 * Expanded, it is a full width now playing pill sitting over the tab pill and a
 * separate circular Search tab. Scrolling down collapses it inline — the tabs
 * fold down to just the selected one, the now playing controls narrow into the
 * gap between it and Search, and the whole thing becomes a single row the width
 * of the screen. Scrolling back up expands it. The fold is driven by
 * [scrollConnection], which the page's scroll has to be dispatched into for any
 * of this to move; see MainActivity's `nestedScroll`.
 *
 * Every surface here samples the app backdrop through [Modifier.liquidGlass], so
 * this is only ever used where that is supported and switched on — off either,
 * MainActivity draws [MiniPlayer] and [FloatingBottomBar] instead.
 *
 * [song] null means nothing is playing, and the accessory is simply absent: the
 * bar is then the tab pill and Search alone, and the collapse still works.
 */
@Composable
fun GlassNavBar(
    tabs: List<BottomTab>,
    selectedIndex: Int,
    onTabSelected: (Int) -> Unit,
    scrollConnection: FloatingTabBarScrollConnection,
    song: Song?,
    isPlaying: Boolean,
    isLoading: Boolean,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onExpand: () -> Unit,
    /** @see com.music.audify.data.listentogether.ListenTogether.State.controlsLocked */
    controlsLocked: Boolean = false,
    onBlockedControl: () -> Unit = {},
    /** Where the player's artwork lands when it closes into this bar. */
    dock: PlayerDock? = null,
    /** Dragging the bar's player up into the full player. */
    pull: MiniPlayerPull? = null,
    /** true = Search a circle of its own beside the pill; false = the last of the pill's tabs. */
    searchSeparate: Boolean = true,
    modifier: Modifier = Modifier,
) {
    // Held for the same reason [tabs] is. The glass factory below closes over
    // this shape, and a fresh RoundedCornerShape each pass means a fresh factory
    // lambda, which the tab bar sees as a changed argument and recomposes on.
    val pillShape = remember { RoundedCornerShape(percent = 50) }
    val contentColor = glassContentColor()
// Tema gelap: aksen dicerahkan supaya tetap terbaca di atas glass yang memantulkan
// cover cerah. Tema terang tidak berubah.
val isDarkGlass = contentColor == Color.White
val selectedColor = if (isDarkGlass) {
    lerp(MaterialTheme.colorScheme.primary, Color.White, 0.06f)
} else {
    MaterialTheme.colorScheme.primary
}
val unselectedColor = contentColor.copy(alpha = 0.88f)
val haptics = rememberHaptics()
    // Goyangan jelly pill now playing, dibagikan ke tab dan Search di sebelahnya.
    val jellyPush = remember { JellyPush() }

    // The tab content lambdas below are captured once per contentKey and held
    // until it changes, so a click handler that reached back to this call's
    // `onTabSelected` would go stale the moment anything it closes over moved.
    // Held through a state that is always current instead, which also keeps the
    // handler out of the key.
    val currentOnTabSelected by rememberUpdatedState(onTabSelected)

    // Search is the odd one out in the iOS 26 layout: a circle of its own beside
    // the pill rather than a quarter of it. It is the last tab in Audify's
    // order, which is where the shape wants it anyway.
    val standaloneIndex = if (searchSeparate) tabs.lastIndex else -1

    // A factory, not a value — see the note in FloatingTabBar's header. Each of
    // the three surfaces gets its own glass modifier and so its own shape cache.
    // The theme's own glass tint, as the liquid top bar has it — no white wash.
    val glassSurface: @Composable () -> Modifier = { Modifier.liquidGlass(shape = pillShape) }

    // Seberapa terbuka player (0..1), dibaca saat draw oleh tab bar untuk
    // mendorong tab ke bawah (expanded) atau ke kiri/kanan (inline).
    val chromeProgress = remember(dock) { { dock?.openFraction() ?: 0f } }
    // Tab/Search (inline) dan baris tab (expanded) mengikuti tepi panel yang
    // sebenarnya, bukan kurva sendiri, supaya tidak menyentuh panel dan tidak
    // terdorong lebih jauh dari posisi panel.
    val chromeSidePush = remember(dock) {
        dock?.let { d -> { toRight: Boolean -> d.panelSideTravel(toRight) } }
    }
    val chromeDownPush = remember(dock) {
        dock?.let { d -> { d.panelBottomTravel() } }
    }

    FloatingTabBar(
        selectedTabKey = selectedIndex,
        scrollConnection = scrollConnection,
        chromeProgress = chromeProgress,
        chromeSidePush = chromeSidePush,
        chromeDownPush = chromeDownPush,
        jellyPush = jellyPush,
        inlineSplitLast = !searchSeparate,
        modifier = modifier
            .windowInsetsPadding(floatingBarInsets)
            .padding(horizontal = NAV_BAR_GUTTER)
            .padding(bottom = NAV_BAR_LIFT)
            .fillMaxWidth(),
        tabBarContentModifier = glassSurface,
        inlineAccessory = song?.let { current ->
            { accessoryModifier, _ ->
                GlassNowPlaying(
                    song = current,
                    isInline = true,
                    jellyPush = jellyPush,
                    isPlaying = isPlaying,
                    isLoading = isLoading,
                    contentColor = contentColor,
                    onPlayPause = onPlayPause,
                    onNext = onNext,
                    onPrevious = onPrevious,
                    onExpand = onExpand,
                    controlsLocked = controlsLocked,
                    onBlockedControl = onBlockedControl,
                    dock = dock,
                    pull = pull,
                    modifier = accessoryModifier.then(glassSurface()),
                )
            }
        },
        expandedAccessory = song?.let { current ->
            { accessoryModifier, _ ->
                GlassNowPlaying(
                    song = current,
                    isInline = false,
                    isPlaying = isPlaying,
                    isLoading = isLoading,
                    contentColor = contentColor,
                    onPlayPause = onPlayPause,
                    onNext = onNext,
                    onPrevious = onPrevious,
                    onExpand = onExpand,
                    controlsLocked = controlsLocked,
                    onBlockedControl = onBlockedControl,
                    dock = dock,
                    pull = pull,
                    modifier = accessoryModifier.fillMaxWidth().then(glassSurface()),
                )
            }
        },
        // Transparent: the glass surface underneath is the background, and a
        // colour over it would be the thing you saw instead of the backdrop.
        colors = FloatingTabBarDefaults.colors(
            backgroundColor = Color.Transparent,
            accessoryBackgroundColor = Color.Transparent,
            // Tema gelap: indikator digelapkan seperti Apple Music, bukan diangkat
            // putih, supaya ikon dan teks aktif yang cerah menonjol darinya.
            indicatorColor = if (isDarkGlass) Color.Black.copy(alpha = 0.30f) else glassIndicatorColor(),
            selectedContentColor = selectedColor,
        ),
        // Flat, because the glass is not. Every surface here already draws its
        // own [Shadow.Default] as part of the backdrop pass, and the library's
        // Modifier.shadow on top of that is a second offscreen layer and a
        // second shadow render for each of them — three at rest, six mid-fold,
        // paying twice for a shadow you can only see once.
        elevations = FloatingTabBarDefaults.elevations(
            inlineElevation = 0.dp,
            expandedElevation = 0.dp,
        ),
        // Expanded, this is meant to be the plain [FloatingBottomBar] with a
        // different material — same outer width, same pill inset, same tab
        // padding, same glyph — so the two bars measure identically and the
        // toggle changes the surface rather than the layout. The horizontal tab
        // padding is gone with it: the tabs divide the pill by weight now, the
        // way the plain bar's do, so a per-tab horizontal padding would only
        // inset the ripple.
        sizes = FloatingTabBarDefaults.sizes(
            tabBarContentPadding = PaddingValues(PILL_INSET),
            tabExpandedContentPadding = PaddingValues(vertical = TAB_VERTICAL_PADDING),
            // Collapsed, Apple's row is narrower and more spaced: 48pt circles
            // 28pt in from the screen's sides, 14pt apart, centred on the line
            // the expanded bar's middle runs along.
            inlineComponentSpacing = INLINE_SPACING,
            inlineBarPadding = PaddingValues(
                start = INLINE_GUTTER - NAV_BAR_GUTTER,
                end = INLINE_GUTTER - NAV_BAR_GUTTER,
                bottom = INLINE_LIFT,
            ),
        ),
        // Held too: this is declared `Any?`, so a fresh list every pass is a
        // changed argument by identity and defeats skipping on its own.
        contentKey = remember(selectedIndex, tabs, contentColor, selectedColor, searchSeparate) {
            listOf(selectedIndex, tabs, contentColor, selectedColor, searchSeparate)
        },
    ) {
        tabs.forEachIndexed { index, tab ->
            val isSelected = index == selectedIndex
            val onClick = {
                if (!isSelected) haptics.play(Haptic.Select)
                currentOnTabSelected(index)
            }
            if (index == standaloneIndex) {
                standaloneTab(
                    key = index,
                    icon = {
                        PillTinted(isSelected, selectedColor, unselectedColor) { tint ->
                            Icon(
                                imageVector = tab.icon,
                                contentDescription = tab.label,
                                tint = tint,
                                modifier = Modifier.size(TAB_ICON_SIZE),
                            )
                        }
                    },
                    onClick = onClick,
                )
            } else {
                tab(
                    key = index,
                    icon = {
                        PillTinted(isSelected, selectedColor, unselectedColor) { tint ->
                            Icon(
                                imageVector = tab.icon,
                                contentDescription = tab.label,
                                tint = tint,
                                modifier = Modifier.size(TAB_ICON_SIZE),
                            )
                        }
                    },
                    title = {
                        PillTinted(isSelected, selectedColor, unselectedColor) { tint ->
                            Text(
                                text = tab.label,
                                style = tabLabelStyle(),
                                color = tint,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            // The library's Tab stacks the glyph and the label
                            // with nothing between them; the plain bar spaces
                                // them, and this is where that gap goes.
                                modifier = Modifier.padding(top = TAB_ICON_LABEL_GAP),
                            )
                        }
                    },
                    onClick = onClick,
                )
            }
        }
    }
}

/**
 * The now playing controls docked into [GlassNavBar] as its accessory.
 *
 * Two densities of the same row rather than two components, so the shared
 * element carrying it between the bar's states has one thing to interpolate:
 * [isInline] drops the skip button and draws the play button smaller. The
 * artwork and the type are the same at both, as they are in Apple's accessory,
 * which only changes width between its two placements.
 *
 * The content is [MiniPlayer]'s — same artwork, same transport, same haptics —
 * and not Echo's, which reaches into a player connection this app doesn't have.
 * The press response is Echo's, and belongs to the glass rather than the row:
 * a surface you can push on is the whole point of the material.
 */
@Composable
private fun GlassNowPlaying(
    song: Song,
    isInline: Boolean,
    isPlaying: Boolean,
    isLoading: Boolean,
    contentColor: Color,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onExpand: () -> Unit,
    controlsLocked: Boolean,
    onBlockedControl: () -> Unit,
    dock: PlayerDock?,
    pull: MiniPlayerPull?,
    jellyPush: JellyPush? = null,
    modifier: Modifier = Modifier,
) {
    val haptics = rememberHaptics()
    val pressSource = remember { MutableInteractionSource() }
    val isPressed by pressSource.collectIsPressedAsState()
    val pressScale by animateFloatAsState(
        targetValue = if (isPressed) 1.04f else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "accessoryPressScale",
    )

    val titleStyle = MaterialTheme.typography.titleMedium.copy(
        fontSize = ACCESSORY_TITLE_SIZE,
        fontWeight = ACCESSORY_TITLE_WEIGHT,
        letterSpacing = ACCESSORY_TITLE_TRACKING,
    )
    val artistStyle = MaterialTheme.typography.bodyMedium.copy(
        fontSize = ACCESSORY_ARTIST_SIZE,
        letterSpacing = ACCESSORY_ARTIST_TRACKING,
    )
    val glyphSlot = if (isInline) 32.dp else 40.dp
    val glyphSize = if (isInline) 24.dp else EXPANDED_GLYPH_SIZE

    Box(
        // Inline, the accessory is stretched to the row's height by the tab bar
        // (`fillMaxHeight` on a row measured at IntrinsicSize.Max), and this row
        // is shorter than that — the tab pill beside it, with a 28dp glyph in
        // 10dp of padding, is the tallest thing in the row and sets the height.
        // A Box defaults to TopStart, so the artwork and the transport sat a
        // couple of dp above the pill's centre line. Expanded the Box wraps its
        // content, so centring is a no-op there.
        contentAlignment = Alignment.Center,
        // The gestures and the tap belong to the whole glass surface, not the
        // row centred in it. Inline, that row is shorter than the pill it sits
        // in, and a pull started in the strip above or below it went nowhere —
        // on a bar only 48dp tall to begin with.
        modifier = Modifier
            .playerDockPill(dock)
            // Outside the press scale, which it lifts along with everything else.
            .miniPlayerGestures(
                pull = pull,
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
                push = jellyPush,
            )
            .graphicsLayer {
                // Level while the panel is up: the player's cover flies to and
                // from the artwork laid out inside this, and a press still
                // easing back would move that target from under it.
                val factor = if (dock?.panelShown == true) 1f else pressScale
                scaleX = factor
                scaleY = factor
            }
            .then(modifier)
            .clickable(
                interactionSource = pressSource,
                indication = null,
                onClick = onExpand,
            ),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    // The artwork sits in the end of a capsule, which curves
                    // away from it; at 8dp its corner met the curve.
                    start = 16.dp,
                    end = if (isInline) 8.dp else 12.dp,
                    // Expanded, the 40dp play button and these come to the 48dp
                    // Apple's accessory is.
                    top = 4.dp,
                    bottom = 4.dp,
                ),
        ) {
            // A music video plays in the cover's place at its own 16:9, as in
            // the floating mini player; the full player takes it over when up.
            val artWidth by animateDpAsState(
                targetValue = if (song.isVideo && !VideoThumbnails.square && MusicOnlyPlayback.showsPicture(song.videoId)) ACCESSORY_ART_SIZE * (16f / 9f) else ACCESSORY_ART_SIZE,
                label = "glassMiniArtWidth",
            )
            Box(
                modifier = Modifier
                    .size(width = artWidth, height = ACCESSORY_ART_SIZE)
                    // Whichever of the two densities is on screen is the one
                    // the player flies to: only one is composed at rest.
                    .playerDockArt(dock, ACCESSORY_ART_CORNER)
                    .clip(RoundedCornerShape(ACCESSORY_ART_CORNER))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
            ) {
                AsyncImage(
                    model = rememberRemoteArtworkUrl(song)?.artworkAt(ROW_ART_PX),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
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
            Spacer(Modifier.width(if (isInline) 8.dp else 9.dp))
            // Title and artist at both densities, as Apple Music has them, in its
            // small, openly spaced type (see [ACCESSORY_TITLE_SIZE]). The inline
            // row shares its width with the tab pill and the Search circle, so
            // a long line fades out at its end, as Apple's does.
            TrackTextSwitch(song, Modifier.weight(1f)) { shown ->
                Column {
                    val secondary = contentColor.copy(alpha = 0.7f)
                    ExplicitSongTitle(
                        song = shown,
                        style = titleStyle,
                        color = contentColor,
                        badgeColor = secondary,
                        fadeOverflow = true,
                    )
                    FadingLine(
                        text = shown.artist,
                        style = artistStyle,
                        color = secondary,
                    )
                }
            }
            if (isLoading) {
                Box(Modifier.size(glyphSlot), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(
                        color = contentColor,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(if (isInline) 18.dp else 20.dp),
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
            // Dropped inline: the collapsed row is sharing its width with the
            // tab pill and the Search circle, and the title is what has to
            // survive that, not a second transport button.
            if (!isInline) {
                // Glyph centres 44dp apart, as Apple's are.
                Spacer(Modifier.width(4.dp))
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

/** The collapsed row's margin at either side of the screen, and its items' spacing. */
private val INLINE_GUTTER = 28.dp
private val INLINE_SPACING = 14.dp

/**
 * Half the difference between the expanded tab row, 62dp, and the collapsed
 * one, 48 — a 28dp glyph in 10dp of padding — so the one folds onto the
 * other's centre line.
 */
private val INLINE_LIFT = 7.dp

/**
 * The accessory's type, measured off Apple's, collapsed and expanded alike:
 * 13pt over 12pt, set looser than the scale's 16 and 14 as SF Pro Text spaces
 * these sizes — the title less so than measured, tuned by eye on device: in
 * Semibold +0.4 read loose and the cut's own spacing tight.
 */
private val ACCESSORY_TITLE_SIZE = 13.sp

/**
 * W700, which in this app's family draws the Semibold cut (see SFProDisplay):
 * Apple's accessory sets its title in Semibold, and the scale's W600 — the
 * Medium cut — read thin at 13sp.
 */
private val ACCESSORY_TITLE_WEIGHT = FontWeight.W700
private val ACCESSORY_TITLE_TRACKING = 0.15.sp
private val ACCESSORY_ARTIST_SIZE = 12.sp
private val ACCESSORY_ARTIST_TRACKING = 0.35.sp

/** The accessory's artwork, about Apple's 30pt at both densities, and its corner. */
private val ACCESSORY_ART_SIZE = 30.4.dp
private val ACCESSORY_ART_CORNER = 5.7.dp

/** The expanded play glyph: its triangle about Apple's 15pt tall. */
private val EXPANDED_GLYPH_SIZE = 29.dp

/**
 * A tab's content drawn in the unselected colour, with the selected colour masked
 * in over exactly the part the glass pill covers: the pixels under the pill's edge
 * switch colour at once as it slides, with no blend. Without a pill (inline bar)
 * it is just the selected/unselected colour.
 */
@Composable
private fun PillTinted(
    isSelected: Boolean,
    selected: Color,
    unselected: Color,
    content: @Composable (Color) -> Unit,
) {
    val geometry = LocalTabPillGeometry.current
    val hasPill by remember(geometry) { derivedStateOf { !geometry().isUnspecified } }
    if (!hasPill) {
        content(if (isSelected) selected else unselected)
        return
    }
    Box {
        content(unselected)
        Box(
            Modifier
                .matchParentSize()
                .clearAndSetSemantics {}
                .drawWithContent {
                    val g = geometry()
                    if (g.isUnspecified) return@drawWithContent
                    val centre = size.width / 2f + g.x
                    val left = centre - g.y / 2f
                    val right = centre + g.y / 2f
                    if (right <= 0f || left >= size.width) return@drawWithContent
                    clipRect(left = left, top = -size.height, right = right, bottom = size.height * 2f) {
                        this@drawWithContent.drawContent()
                    }
                },
        ) {
            content(selected)
        }
    }
}
