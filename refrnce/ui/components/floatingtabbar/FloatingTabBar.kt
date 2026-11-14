@file:OptIn(ExperimentalSharedTransitionApi::class)

/*
 * FloatingTabBar v1.0.1 by Elyes Mansour
 * https://github.com/elyesmansour/compose-floating-tab-bar
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Vendored from the v1.0.1 tag: the published AAR is compiled against
 * Compose 1.8 and crashes with NoSuchMethodError on
 * SharedTransitionScope.sharedElement under the Compose version this app uses.
 * Compiling the source here keeps it binary-compatible.
 *
 * Local modifications on top of v1.0.1:
 * - Package renamed.
 * - InlineBar/ExpandedBar use Row/Column instead of ConstraintLayout, and the
 *   tab pill + standalone tab cluster is centered (iOS 26 style) instead of
 *   being pinned to opposite screen edges.
 * - The inline/expanded AnimatedContent is center-aligned so the collapse
 *   animation morphs around the center instead of the start edge.
 *
 * Taken from EchoMusicApp/Echo-Music, which carries the three modifications
 * listed above; Audify's own use of it is in
 * [com.music.audify.ui.components.GlassNavBar]. One further change here:
 *
 * - `tabBarContentModifier` is a `@Composable () -> Modifier` factory rather
 *   than a single Modifier value. It is applied to three surfaces of three
 *   different sizes (the tab pill, the circular standalone tab, the accessory),
 *   and a liquid glass Modifier carries a shape-outline cache keyed on the size
 *   it last saw — so one shared instance is invalidated by whichever surface
 *   drew last and re-creates all three outlines every frame. A factory gives
 *   each surface its own.
 * - The expanded tab group's selection pill is a lifted glass lens in motion
 *   (see [GlassSelectionPill]): a tap inflates it past the bar, carries it to
 *   the tab on a spring, squashes it with its acceleration, and sheds the glass
 *   back into a flat fill on landing. Horizontal drags pick it up the same way,
 *   resist at the ends, tick across tab boundaries, and settle on release.
 */

package com.music.audify.ui.components.floatingtabbar

import android.os.SystemClock
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.snap
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Indication
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.music.audify.data.settings.AppSettings
import com.music.audify.ui.components.GlassSpring
import com.music.audify.ui.components.JellyPush
import com.music.audify.ui.components.LocalGlassExport
import com.music.audify.ui.components.PILL_INSET
import com.music.audify.ui.components.backdrop.backdrops.layerBackdrop
import com.music.audify.ui.components.backdrop.backdrops.rememberLayerBackdrop
import com.music.audify.ui.components.isGlassSupported
import com.music.audify.ui.haptics.Haptic
import com.music.audify.ui.haptics.rememberHaptics
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.delay

/** Hoisted so the default is one stable lambda rather than a new one per call. */
/**
 * How much the glass pill covers the tab being drawn right now: `1` with the pill
 * centred on it, `0` once it is a full step away. Read inside a tab's icon/title
 * content to colour it by where the pill is, not by which tab is selected.
 * `NaN` where no pill is involved (the inline bar), so callers fall back to the
 * selected/unselected colour.
 */
val LocalTabPillCoverage = compositionLocalOf<() -> Float> { { Float.NaN } }

/**
 * Where the glass pill is relative to the tab being drawn, in pixels: `x` is the
 * pill centre's signed distance from the tab's centre (already flipped for RTL),
 * `y` is the pill's width. Lets a tab's icon/title be masked by the pill's real
 * horizontal extent, so the part under the pill changes colour at once.
 * [Offset.Unspecified] where no pill is involved (the inline bar).
 */
val LocalTabPillGeometry = compositionLocalOf<() -> Offset> { { Offset.Unspecified } }

private val DefaultTabBarContentModifier: @Composable () -> Modifier = { Modifier }

private val ZeroChromeProgress: () -> Float = { 0f }

/** Bukaan player (0..1) saat bar sudah selesai menyingkir. Sisanya bar sudah di luar layar. */
private const val CHROME_PUSH_DONE_AT = 0.7f

/** Jarak ekstra supaya bar benar-benar lepas dari layar, bukan berhenti di tepinya. */
private val CHROME_PUSH_MARGIN = 64.dp

private fun chromePushEase(progress: Float): Float {
    val t = (progress / CHROME_PUSH_DONE_AT).coerceIn(0f, 1f)
    return t * t * (3f - 2f * t)
}

/**
 * Mode expanded: baris tab turun tepat di bawah tepi bawah panel, menjaga
 * jarak yang sama seperti saat diam. [panelDrop] (px) dari PlayerDock; kalau
 * belum bisa dihitung (NaN) dipakai kurva lama berdasarkan [progress].
 */
private fun Modifier.chromePushDown(progress: () -> Float, panelDrop: (() -> Float)?): Modifier = graphicsLayer {
    val drop = panelDrop?.invoke() ?: Float.NaN
    translationY = if (!drop.isNaN()) {
        drop
    } else {
        val e = chromePushEase(progress())
        (size.height + CHROME_PUSH_MARGIN.toPx()) * e
    }
}

/** Mode inline: tab terdorong ke kiri dan Search ke kanan mengikuti bukaan player. */
private fun Modifier.chromePushSide(
    progress: () -> Float,
    direction: Float,
    edgeTravel: ((Boolean) -> Float)?,
): Modifier = graphicsLayer {
    // Mengikuti tepi panel yang melebar (kiri atau kanan, fisik), jadi tab dan
    // Search tidak pernah menyentuh panel; jarak diamnya tetap terjaga.
    val travel = edgeTravel?.invoke(direction > 0f) ?: Float.NaN
    translationX = if (!travel.isNaN()) {
        direction * travel
    } else {
        val e = chromePushEase(progress())
        direction * (size.width + CHROME_PUSH_MARGIN.toPx()) * e
    }
}

/** Celah yang tetap dijaga antara pill mini player yang bergoyang dan tombol yang didorongnya. */
private val JELLY_PUSH_MARGIN = 10.dp

/**
 * Mode inline: tombol di sisi [direction] (-1 kiri, 1 kanan, sudah dibalik untuk RTL)
 * terdorong menjauh begitu tepi pill mini player, yang bergoyang karena jelly,
 * melewati celah [gap] di antara mereka. Dibaca saat draw saja.
 */
private fun Modifier.jellyPushSide(push: JellyPush?, direction: Float, gap: Dp): Modifier {
    if (push == null) return this
    return graphicsLayer {
        val room = (gap - JELLY_PUSH_MARGIN).toPx().coerceAtLeast(0f)
        val over = (push.advance(towardLeft = direction < 0f) - room).coerceAtLeast(0f)
        translationX = direction * over
    }
}

/**
 * A floating tab bar that can transition between inline and expanded states.
 *
 * @param isInline controls the FloatingTabBar's inline state
 * @param selectedTabKey the key of the currently selected tab
 * @param modifier the modifier to be applied to the tab bar
 * @param colors the colors used by the tab bar components
 * @param shapes the shapes used by the tab bar components
 * @param sizes the sizes and spacing used by the tab bar components
 * @param elevations the elevation values used by the tab bar components
 * @param tabBarContentModifier modifier applied to the tab bar sections containing the grouped tabs and standalone tab.
 * It is applied after the default styling (background, shadow, clip) but before any content padding.
 * @param inlineAccessory the accessory composable that appears in inline state (e.g., compact media player)
 * @param expandedAccessory the accessory composable that appears in expanded state (e.g., full media player)
 * @param contentKey optional key that when changed retriggers the content lambda
 * @param content the content defining the tabs
 */
@Composable
fun FloatingTabBar(
    isInline: Boolean,
    selectedTabKey: Any?,
    modifier: Modifier = Modifier,
    tabBarContentModifier: @Composable () -> Modifier = DefaultTabBarContentModifier,
    inlineAccessory: (@Composable SharedTransitionScope.(Modifier, AnimatedVisibilityScope) -> Unit)? = null,
    expandedAccessory: (@Composable SharedTransitionScope.(Modifier, AnimatedVisibilityScope) -> Unit)? = null,
    colors: FloatingTabBarColors = FloatingTabBarDefaults.colors(),
    shapes: FloatingTabBarShapes = FloatingTabBarDefaults.shapes(),
    sizes: FloatingTabBarSizes = FloatingTabBarDefaults.sizes(),
    elevations: FloatingTabBarElevations = FloatingTabBarDefaults.elevations(),
    contentKey: Any? = null,
    content: FloatingTabBarScope.() -> Unit
) {
    val scrollConnection = rememberFloatingTabBarScrollConnection(
        initialIsInline = isInline,
        inlineBehavior = FloatingTabBarInlineBehavior.Never
    )

    LaunchedEffect(isInline) {
        if (isInline) scrollConnection.inline() else scrollConnection.expand()
    }

    FloatingTabBar(
        selectedTabKey = selectedTabKey,
        scrollConnection = scrollConnection,
        modifier = modifier,
        tabBarContentModifier = tabBarContentModifier,
        inlineAccessory = inlineAccessory,
        expandedAccessory = expandedAccessory,
        colors = colors,
        shapes = shapes,
        sizes = sizes,
        elevations = elevations,
        contentKey = contentKey,
        content = content
    )
}

/**
 * A floating tab bar that transitions between inline and expanded states based on scroll behavior.
 *
 * @param selectedTabKey the key of the currently selected tab
 * @param scrollConnection the scroll connection that handles state transitions
 * @param modifier the modifier to be applied to the tab bar
 * @param colors the colors used by the tab bar components
 * @param shapes the shapes used by the tab bar components
 * @param sizes the sizes and spacing used by the tab bar components
 * @param elevations the elevation values used by the tab bar components
 * @param tabBarContentModifier modifier applied to the tab bar sections containing the grouped tabs and standalone tab.
 * It is applied after the default styling (background, shadow, clip) but before any content padding.
 * @param inlineAccessory the accessory composable that appears in inline state (e.g., compact media player)
 * @param expandedAccessory the accessory composable that appears in expanded state (e.g., full media player)
 * @param contentKey optional key that when changed retriggers the content lambda
 * @param content the content defining the tabs
 */
@Composable
fun FloatingTabBar(
    selectedTabKey: Any?,
    scrollConnection: FloatingTabBarScrollConnection,
    modifier: Modifier = Modifier,
    /** 0 = bar diam, 1 = player terbuka penuh. Dibaca saat draw saja. */
    chromeProgress: () -> Float = ZeroChromeProgress,
    /** Jarak (px) tepi kiri/kanan panel menjauh dari pill; NaN = belum diketahui. */
    chromeSidePush: ((Boolean) -> Float)? = null,
    /** Jarak (px) tepi bawah panel turun dari pill; NaN = belum diketahui. */
    chromeDownPush: (() -> Float)? = null,
    /** Goyangan jelly pill accessory inline; tab dan Search terdorong olehnya. */
    jellyPush: JellyPush? = null,
    /** true = tab terakhir (Search) dipisah jadi lingkaran sendiri hanya saat bar inline. */
    inlineSplitLast: Boolean = false,
    tabBarContentModifier: @Composable () -> Modifier = DefaultTabBarContentModifier,
    inlineAccessory: (@Composable SharedTransitionScope.(Modifier, AnimatedVisibilityScope) -> Unit)? = null,
    expandedAccessory: (@Composable SharedTransitionScope.(Modifier, AnimatedVisibilityScope) -> Unit)? = null,
    colors: FloatingTabBarColors = FloatingTabBarDefaults.colors(),
    shapes: FloatingTabBarShapes = FloatingTabBarDefaults.shapes(),
    sizes: FloatingTabBarSizes = FloatingTabBarDefaults.sizes(),
    elevations: FloatingTabBarElevations = FloatingTabBarDefaults.elevations(),
    contentKey: Any? = null,
    content: FloatingTabBarScope.() -> Unit
) {
    val scope = remember(contentKey, inlineSplitLast) {
        FloatingTabBarScopeImpl(inlineSplitLast).apply { content() }
    }

    val isAccessoryShared = inlineAccessory != null && expandedAccessory != null

    // Saat mengecil, bar ditarik sedikit ke atas seolah terseret halaman, lalu
    // mengendap ke bentuk inline dengan pegas. Dibaca di graphicsLayer saja.
    val pull = remember { androidx.compose.animation.core.Animatable(0f) }
    val pullPx = with(LocalDensity.current) { 28.dp.toPx() }
    // Bentuk yang ditampilkan menyusul scrollConnection: mini player ditarik dulu,
    // baru saat dilepas bentuknya berganti dan memantul masuk ke pil tengah.
    var shownInline by remember { mutableStateOf(scrollConnection.isInline) }
    LaunchedEffect(scrollConnection.isInline) {
        val target = scrollConnection.isInline
        if (target == shownInline) {
            // Berbalik sebelum sempat dilepas: karet kembali diam, tidak tertahan tegang.
            pull.animateTo(
                0f,
                androidx.compose.animation.core.spring(dampingRatio = 0.6f, stiffness = 300f),
            )
            return@LaunchedEffect
        }
        // Mengecil: player penuh ditarik ke atas. Membesar: pil ditarik ke bawah.
        val amplitude = if (target) 1f else -0.7f
        pull.animateTo(
            amplitude,
            androidx.compose.animation.core.tween(190, easing = FastOutSlowInEasing),
        )
        // Dilepas: berganti bentuk, lalu memantul lewat titik awal dan mengendap.
        shownInline = target
        pull.animateTo(
            0f,
            androidx.compose.animation.core.spring(dampingRatio = 0.42f, stiffness = 210f),
        )
    }

    // Hanya accessory (mini player) yang tertarik; tab dan Search tetap diam.
    val inlineWithPull: (@Composable SharedTransitionScope.(Modifier, AnimatedVisibilityScope) -> Unit)? =
        remember(inlineAccessory, pullPx) {
            inlineAccessory?.let { acc ->
                val wrapped: @Composable SharedTransitionScope.(Modifier, AnimatedVisibilityScope) -> Unit =
                    { m, s -> acc(Modifier.rubberStretch(pullPx) { pull.value }.then(m), s) }
                wrapped
            }
        }
    val expandedWithPull: (@Composable SharedTransitionScope.(Modifier, AnimatedVisibilityScope) -> Unit)? =
        remember(expandedAccessory, pullPx) {
            expandedAccessory?.let { acc ->
                val wrapped: @Composable SharedTransitionScope.(Modifier, AnimatedVisibilityScope) -> Unit =
                    { m, s -> acc(Modifier.rubberStretch(pullPx) { pull.value }.then(m), s) }
                wrapped
            }
        }

    SharedTransitionLayout(modifier = modifier) {
        AnimatedContent(
            targetState = shownInline,
            transitionSpec = {
                // The container's own size follows the same spring, so the bar
                // grows and shrinks with the pieces inside it.
                (fadeIn() togetherWith fadeOut()).using(
                    androidx.compose.animation.SizeTransform(clip = false) { _, _ ->
                        androidx.compose.animation.core.spring(
                            dampingRatio = 0.62f,
                            stiffness = 320f,
                            visibilityThreshold = IntSize(1, 1),
                        )
                    },
                )
            },
            contentAlignment = Alignment.BottomCenter
        ) { isInline ->
            if (isInline) {
                InlineBar(
                    scope = scope,
                    chromeProgress = chromeProgress,
                    chromeSidePush = chromeSidePush,
                    jellyPush = jellyPush,
                    selectedTabKey = selectedTabKey,
                    accessory = inlineWithPull,
                    isAccessoryShared = isAccessoryShared,
                    onInlineTabClick = { scrollConnection.expand() },
                    neighbourPull = { pull.value },
                    colors = colors,
                    shapes = shapes,
                    sizes = sizes,
                    elevations = elevations,
                    tabBarContentModifier = tabBarContentModifier,
                    animatedVisibilityScope = this@AnimatedContent
                )
            } else {
                ExpandedBar(
                    scope = scope,
                    chromeProgress = chromeProgress,
                    chromeDownPush = chromeDownPush,
                    selectedTabKey = selectedTabKey,
                    accessory = expandedWithPull,
                    isAccessoryShared = isAccessoryShared,
                    colors = colors,
                    shapes = shapes,
                    sizes = sizes,
                    elevations = elevations,
                    tabBarContentModifier = tabBarContentModifier,
                    animatedVisibilityScope = this@AnimatedContent
                )
            }
        }
    }
}

/**
 * The move every piece of the bar (tab pill, tab icon, standalone tab, mini
 * player) makes when it flips between inline and expanded: a soft spring that
 * overshoots a little and settles, so the glass reads as liquid.
 */
private val LiquidBounds = androidx.compose.animation.BoundsTransform { _, _ ->
    androidx.compose.animation.core.spring(
        dampingRatio = 0.62f,
        stiffness = 320f,
        visibilityThreshold = androidx.compose.ui.geometry.Rect(0.5f, 0.5f, 0.5f, 0.5f),
    )
}

/**
 * A [NestedScrollConnection] that handles scroll events to transition between inline and expanded states.
 *
 * @param initialIsInline Initial state of the tab bar (inline or expanded).
 * @param scrollThresholdPx The minimum scroll distance in pixels required to trigger a state change.
 * @param inlineBehavior Defines when the tab bar should transition to inline state.
 */
class FloatingTabBarScrollConnection(
    initialIsInline: Boolean = false,
    private val scrollThresholdPx: Float,
    private val inlineBehavior: FloatingTabBarInlineBehavior = FloatingTabBarInlineBehavior.OnScrollDown,
    private val expandOnlyAtTop: Boolean = false,
    private val expandThresholdPx: Float = scrollThresholdPx
) : NestedScrollConnection {
    var isInline by mutableStateOf(initialIsInline)
        private set

    /** Naik setiap kali halaman menyentuh batas atas saat bar masih inline; dibaca untuk menunda expand. */
    var topSignal by mutableIntStateOf(0)
        private set

    private var accumulatedScroll = 0f

    fun expand() {
        isInline = false
        accumulatedScroll = 0f
    }

    fun inline() {
        isInline = true
        accumulatedScroll = 0f
    }

    override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
        // If behavior is Never, don't change state
        if (inlineBehavior == FloatingTabBarInlineBehavior.Never) {
            return Offset.Zero
        }

        val scrollDelta = available.y

        // Reset accumulated scroll if changing direction
        if ((accumulatedScroll > 0 && scrollDelta < 0) || (accumulatedScroll < 0 && scrollDelta > 0)) {
            accumulatedScroll = 0f
        }

        // Accumulate scroll
        accumulatedScroll += scrollDelta

        when (inlineBehavior) {
            FloatingTabBarInlineBehavior.OnScrollDown -> {
                // Check if we've scrolled enough to trigger state change
                if (accumulatedScroll <= -scrollThresholdPx && !isInline) {
                    // Scrolling down enough - transition to inline mode
                    isInline = true
                    accumulatedScroll = 0f // Reset after state change
                } else if (accumulatedScroll >= expandThresholdPx && isInline && !expandOnlyAtTop) {
                    // Scrolling up enough - transition to expanded mode
                    isInline = false
                    accumulatedScroll = 0f // Reset after state change
                }
            }

            FloatingTabBarInlineBehavior.OnScrollUp -> {
                // Check if we've scrolled enough to trigger state change
                if (accumulatedScroll >= scrollThresholdPx && !isInline) {
                    // Scrolling up enough - transition to inline mode
                    isInline = true
                    accumulatedScroll = 0f // Reset after state change
                } else if (accumulatedScroll <= -scrollThresholdPx && isInline) {
                    // Scrolling down enough - transition to expanded mode
                    isInline = false
                    accumulatedScroll = 0f // Reset after state change
                }
            }

            FloatingTabBarInlineBehavior.Never -> {
                // Already handled above, but included for completeness
            }
        }

        return Offset.Zero // Don't consume the scroll, let it pass through
    }

    override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
        // Scrolling up with nothing left to scroll: the page has reached its top.
        if (expandOnlyAtTop &&
            inlineBehavior == FloatingTabBarInlineBehavior.OnScrollDown &&
            isInline &&
            available.y > 0f
        ) {
            topSignal++
        }
        return Offset.Zero
    }
}

/**
 * Creates and remembers a [FloatingTabBarScrollConnection] instance.
 *
 * @param initialIsInline Initial state of the tab bar (inline or expanded). Default is false.
 * @param scrollThreshold The minimum scroll distance required to trigger a state change. Default is 50.dp.
 * @param inlineBehavior Defines when the tab bar should transition to inline state. Default is [FloatingTabBarInlineBehavior.OnScrollDown].
 * @return A remembered [FloatingTabBarScrollConnection] instance.
 */
@Composable
fun rememberFloatingTabBarScrollConnection(
    initialIsInline: Boolean = false,
    scrollThreshold: Dp = 50.dp,
    inlineBehavior: FloatingTabBarInlineBehavior = FloatingTabBarInlineBehavior.OnScrollDown,
    expandOnlyAtTop: Boolean = false,
    expandThreshold: Dp = scrollThreshold
): FloatingTabBarScrollConnection = with(LocalDensity.current) {
    val scrollThresholdPx = scrollThreshold.toPx()
    val expandThresholdPx = expandThreshold.toPx()
    remember(scrollThresholdPx, expandThresholdPx, inlineBehavior, initialIsInline, expandOnlyAtTop) {
        FloatingTabBarScrollConnection(
            initialIsInline,
            scrollThresholdPx,
            inlineBehavior,
            expandOnlyAtTop,
            expandThresholdPx,
        )
    }
}

/**
 * Defines when the floating tab bar should transition to inline state.
 */
enum class FloatingTabBarInlineBehavior {
    /** Never transition to inline - it stays in expanded state */
    Never,

    /** Transition to inline when scrolling down */
    OnScrollDown,

    /** Transition to inline when scrolling up */
    OnScrollUp
}

interface FloatingTabBarScope {
    /**
     * Adds a regular tab to the floating tab bar.
     *
     * @param key Unique identifier for the tab
     * @param title Composable content for the tab title
     * @param icon Composable content for the tab icon
     * @param onClick Callback invoked when the tab is clicked
     * @param indication Optional indication provider for touch feedback, defaults to LocalIndication.current
     */
    fun tab(
        key: Any,
        title: @Composable () -> Unit,
        icon: @Composable () -> Unit,
        onClick: () -> Unit,
        indication: (@Composable () -> Indication)? = { LocalIndication.current }
    )

    /**
     * Adds a standalone tab to the floating tab bar.
     *
     * Note: Calling this method more than once will override the previous standalone tab value.
     *
     * @param key Unique identifier for the standalone tab
     * @param icon Composable content for the tab icon
     * @param onClick Callback invoked when the tab is clicked
     * @param indication Optional indication provider for touch feedback, defaults to LocalIndication.current
     */
    fun standaloneTab(
        key: Any,
        icon: @Composable () -> Unit,
        onClick: () -> Unit,
        indication: (@Composable () -> Indication)? = { LocalIndication.current }
    )
}

@Composable
private fun SharedTransitionScope.InlineBar(
    scope: FloatingTabBarScopeImpl,
    chromeProgress: () -> Float,
    chromeSidePush: ((Boolean) -> Float)?,
    jellyPush: JellyPush?,
    selectedTabKey: Any?,
    accessory: (@Composable SharedTransitionScope.(Modifier, AnimatedVisibilityScope) -> Unit)?,
    isAccessoryShared: Boolean,
    onInlineTabClick: () -> Unit,
    colors: FloatingTabBarColors,
    shapes: FloatingTabBarShapes,
    sizes: FloatingTabBarSizes,
    elevations: FloatingTabBarElevations,
    tabBarContentModifier: @Composable () -> Modifier,
    animatedVisibilityScope: AnimatedVisibilityScope,
    neighbourPull: () -> Float = { 0f }
) {
    val inlineTab = scope.getInlineTab(selectedTabKey)
    val standaloneTab = scope.inlineStandaloneTab
    val hasInlineTab = inlineTab != null
    // Tab menyingkir ke sisi awalnya, Search ke sisi akhirnya.
    val startSign = if (LocalLayoutDirection.current == LayoutDirection.Rtl) 1f else -1f

    // With an accessory the row spans the full width ([tab][accessory][standalone],
    // iOS 26 Apple Music style); without one, the cluster is packed and centered.
    Row(
        horizontalArrangement = Arrangement.spacedBy(sizes.inlineComponentSpacing),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(sizes.inlineBarPadding)
            .then(if (accessory == null) Modifier.wrapContentWidth() else Modifier)
            .height(IntrinsicSize.Max)
    ) {
        if (hasInlineTab) {
            InlineTab(
                inlineTab = inlineTab,
                onInlineTabClick = onInlineTabClick,
                shapes = shapes,
                sizes = sizes,
                colors = colors,
                elevations = elevations,
                animatedVisibilityScope = animatedVisibilityScope,
                tabBarContentModifier = tabBarContentModifier,
                modifier = Modifier
                    .chromePushSide(chromeProgress, startSign, chromeSidePush)
                    .jellyPushSide(jellyPush, startSign, sizes.inlineComponentSpacing)
                    .neighbourPull(neighbourPull, startSign)
            )
        }

        if (accessory != null) {
            InlineAccessory(
                accessory = accessory,
                isAccessoryShared = isAccessoryShared,
                shapes = shapes,
                colors = colors,
                elevations = elevations,
                animatedVisibilityScope = animatedVisibilityScope,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
            )
        }

        if (standaloneTab != null) {
            InlineStandaloneTab(
                standaloneTab = standaloneTab,
                shapes = shapes,
                colors = colors,
                elevations = elevations,
                animatedVisibilityScope = animatedVisibilityScope,
                tabBarContentModifier = tabBarContentModifier,
                modifier = Modifier
                    .fillMaxHeight()
                    .aspectRatio(1f)
                    .chromePushSide(chromeProgress, -startSign, chromeSidePush)
                    .jellyPushSide(jellyPush, -startSign, sizes.inlineComponentSpacing)
                    .neighbourPull(neighbourPull, -startSign)
            )
        }
    }
}

@Composable
private fun SharedTransitionScope.InlineTab(
    inlineTab: FloatingTabBarTab,
    onInlineTabClick: () -> Unit,
    shapes: FloatingTabBarShapes,
    sizes: FloatingTabBarSizes,
    colors: FloatingTabBarColors,
    elevations: FloatingTabBarElevations,
    animatedVisibilityScope: AnimatedVisibilityScope,
    modifier: Modifier,
    tabBarContentModifier: @Composable () -> Modifier
) {
    Box(
        modifier = modifier
            .sharedElement(
                sharedContentState = rememberSharedContentState("tabGroup"),
                boundsTransform = LiquidBounds,
                animatedVisibilityScope = animatedVisibilityScope,
                zIndexInOverlay = 1f
            )
            .shadow(
                shape = shapes.tabBarShape,
                elevation = elevations.inlineElevation
            )
            .background(
                color = colors.backgroundColor,
                shape = shapes.tabBarShape
            )
            .clip(shapes.tabBarShape)
            .then(tabBarContentModifier())
            .clickable(
                // Sentuhan pertama hanya membuka bar ke bentuk expanded (mini
                // player kembali di atas). Pindah tab baru terjadi di sentuhan
                // berikutnya, lewat pil tab yang sudah terbuka.
                onClick = { onInlineTabClick() },
                indication = inlineTab.indication?.invoke(),
                interactionSource = remember { MutableInteractionSource() }
            )
            .padding(sizes.tabInlineContentPadding)
    ) {
        Tab(
            icon = {
                Box(
                    Modifier.sharedElement(
                        sharedContentState = rememberSharedContentState("tab#${inlineTab.key}-icon"),
                        boundsTransform = LiquidBounds,
                        animatedVisibilityScope = animatedVisibilityScope,
                        zIndexInOverlay = 1f
                    )
                ) {
                    inlineTab.icon()
                }
            },
            title = { inlineTab.title() },
            isInline = true
        )
    }
}

@Composable
private fun SharedTransitionScope.InlineStandaloneTab(
    standaloneTab: FloatingTabBarTab,
    shapes: FloatingTabBarShapes,
    colors: FloatingTabBarColors,
    elevations: FloatingTabBarElevations,
    animatedVisibilityScope: AnimatedVisibilityScope,
    modifier: Modifier,
    tabBarContentModifier: @Composable () -> Modifier
) {
    StandaloneTabBody(
        standaloneTab = standaloneTab,
        isInline = true,
        shapes = shapes,
        colors = colors,
        elevation = elevations.inlineElevation,
        animatedVisibilityScope = animatedVisibilityScope,
        tabBarContentModifier = tabBarContentModifier,
        modifier = modifier,
    )
}

/** Minimum time a tap keeps the lens lifted, so a quick tap still shows it rising. */
private const val MIN_LIFT_HOLD_MS = 260L

/** How much the whole circle swells while the lens is lifted. */
private const val STANDALONE_SWELL = 0.05f

/**
 * The standalone tab (Search) in both bar states. A press lifts the same glass
 * lens the tab pill uses: it inflates past the circle on springs, refracts the
 * circle's own glass and glyph, and sheds back to nothing on release. The
 * circle swells with it, riding the lens's own lift spring.
 */
@Composable
private fun SharedTransitionScope.StandaloneTabBody(
    standaloneTab: FloatingTabBarTab,
    isInline: Boolean,
    shapes: FloatingTabBarShapes,
    colors: FloatingTabBarColors,
    elevation: Dp,
    animatedVisibilityScope: AnimatedVisibilityScope,
    tabBarContentModifier: @Composable () -> Modifier,
    modifier: Modifier,
) {
    val reduceAnimation by AppSettings.reduceAnimation.collectAsStateWithLifecycle()
    val reduceDynamicBlur by AppSettings.reduceDynamicBlur.collectAsStateWithLifecycle()
    val glassPill = isGlassSupported() && !reduceDynamicBlur &&
        com.music.audify.ui.components.LocalLiquidGlassEnabled.current
    val coroutineScope = rememberCoroutineScope()
    // Slower and softer than the tab pill: a Search tap lifts, holds and settles
    // over a longer, rounder spring rather than a quick pop.
    val pill = remember { GlassPillMotion(coroutineScope, 0, slow = 1.6) }
    val glassIndicator by AppSettings.glassIndicator.collectAsStateWithLifecycle()
    pill.reduceMotion = reduceAnimation
    pill.squashScale = glassIndicator.squash
    val barGlass = rememberLayerBackdrop()
    val iconLayer = rememberLayerBackdrop()
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val pressedAt = remember { LongArray(1) }

    LaunchedEffect(pressed, reduceAnimation) {
        if (reduceAnimation) return@LaunchedEffect
        if (pressed) {
            pressedAt[0] = SystemClock.uptimeMillis()
            pill.startDrag()
        } else {
            val held = SystemClock.uptimeMillis() - pressedAt[0]
            if (held < MIN_LIFT_HOLD_MS) delay(MIN_LIFT_HOLD_MS - held)
            pill.release(0)
        }
    }

    var boxSize by remember { mutableStateOf(IntSize.Zero) }
    val density = LocalDensity.current
    val insetPx = with(density) { PILL_INSET.toPx() }
    val growPx = with(density) { PILL_GROW_HEIGHT.toPx() } * glassIndicator.lift
    val w = boxSize.width.toFloat()
    val h = boxSize.height.toFloat()
    val restSize = Size((w - 2f * insetPx).coerceAtLeast(1f), (h - 2f * insetPx).coerceAtLeast(1f))
    val liftedSize = Size(w + growPx, h + growPx)

    Box(
        modifier = modifier
            .sharedElement(
                sharedContentState = rememberSharedContentState("standaloneTab"),
                boundsTransform = LiquidBounds,
                animatedVisibilityScope = animatedVisibilityScope,
                zIndexInOverlay = 1f
            )
            .graphicsLayer {
                val swell = if (reduceAnimation) 0f else pill.liftY.coerceAtLeast(0f)
                scaleX = 1f + STANDALONE_SWELL * swell
                scaleY = 1f + STANDALONE_SWELL * swell
            }
            .onSizeChanged { boxSize = it }
    ) {
        CompositionLocalProvider(LocalGlassExport provides barGlass) {
            Tab(
                icon = {
                    Box(Modifier.layerBackdrop(iconLayer)) { standaloneTab.icon() }
                },
                title = standaloneTab.title,
                isInline = isInline,
                isStandalone = true,
                modifier = Modifier
                    .fillMaxSize()
                    .shadow(
                        shape = shapes.standaloneTabShape,
                        elevation = elevation
                    )
                    .background(
                        color = colors.backgroundColor,
                        shape = shapes.standaloneTabShape
                    )
                    .clip(shapes.standaloneTabShape)
                    .then(tabBarContentModifier())
                    .clickable(
                        onClick = standaloneTab.onClick,
                        indication = standaloneTab.indication?.invoke(),
                        interactionSource = interactionSource
                    )
            )
        }

        if (w > 0f && !pill.isFlat) {
            val pillModifier = Modifier
                .matchParentSize()
                .pillPlacement(
                    center = { Offset(w / 2f, h / 2f) },
                    size = { pill.liveSize(restSize, liftedSize) },
                )
            if (glassPill) {
                GlassSelectionPill(
                    motion = pill,
                    fillColor = Color.Transparent,
                    barGlass = barGlass,
                    tabRow = iconLayer,
                    modifier = pillModifier,
                    iconTint = colors.selectedContentColor,
                )
            } else {
                FlatSelectionPill(
                    color = colors.indicatorColor,
                    modifier = pillModifier.graphicsLayer { alpha = pill.material },
                )
            }
        }
    }
}

@Composable
private fun SharedTransitionScope.InlineAccessory(
    accessory: (@Composable SharedTransitionScope.(Modifier, AnimatedVisibilityScope) -> Unit)?,
    isAccessoryShared: Boolean,
    colors: FloatingTabBarColors,
    shapes: FloatingTabBarShapes,
    elevations: FloatingTabBarElevations,
    animatedVisibilityScope: AnimatedVisibilityScope,
    modifier: Modifier
) {
    accessory?.let { accessory ->
        Box(
            modifier = modifier
                .then(
                    if (isAccessoryShared) {
                        Modifier.sharedElement(
                            sharedContentState = rememberSharedContentState("accessory"),
                            boundsTransform = LiquidBounds,
                            animatedVisibilityScope = animatedVisibilityScope
                        )
                    } else {
                        Modifier.animateEnterExitAccessory(
                            sharedTransitionScope = this,
                            animatedVisibilityScope = animatedVisibilityScope
                        )
                    }
                )
        ) {
            accessory(
                Modifier
                    .fillMaxSize()
                    .shadow(
                        shape = shapes.accessoryShape,
                        elevation = elevations.inlineElevation
                    )
                    .background(color = colors.accessoryBackgroundColor, shapes.accessoryShape)
                    .clip(shapes.accessoryShape),
                animatedVisibilityScope
            )
        }
    }
}

@Composable
private fun SharedTransitionScope.ExpandedBar(
    scope: FloatingTabBarScopeImpl,
    chromeProgress: () -> Float,
    chromeDownPush: (() -> Float)?,
    selectedTabKey: Any?,
    accessory: (@Composable SharedTransitionScope.(Modifier, AnimatedVisibilityScope) -> Unit)?,
    isAccessoryShared: Boolean,
    colors: FloatingTabBarColors,
    shapes: FloatingTabBarShapes,
    sizes: FloatingTabBarSizes,
    elevations: FloatingTabBarElevations,
    tabBarContentModifier: @Composable () -> Modifier,
    animatedVisibilityScope: AnimatedVisibilityScope
) {
    val standaloneTab = scope.standaloneTab
    val hasTabGroup = scope.tabs.isNotEmpty()

    // The accessory spans the full width above the tab row; the tab pill and the
    // standalone tab are packed together and centered (iOS 26 style).
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(sizes.componentSpacing),
        modifier = Modifier.fillMaxWidth()
    ) {
        if (accessory != null) {
            ExpandedAccessory(
                accessory = accessory,
                isAccessoryShared = isAccessoryShared,
                shapes = shapes,
                colors = colors,
                elevations = elevations,
                animatedVisibilityScope = animatedVisibilityScope,
                modifier = Modifier.fillMaxWidth()
            )
        }

        // Full width, and the tab pill takes whatever the standalone tab leaves,
        // so the expanded bar measures the same as the app's plain nav bar
        // instead of shrinking to a narrow centred cluster. It also removes the
        // overflow this row used to be able to hit: the pill is now given a
        // width to divide rather than growing past the screen to fit its labels.
        Row(
            horizontalArrangement = Arrangement.spacedBy(sizes.componentSpacing),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Max)
                .chromePushDown(chromeProgress, chromeDownPush)
        ) {
            if (hasTabGroup) {
                ExpandedTabs(
                    scope = scope,
                    selectedTabKey = selectedTabKey,
                    shapes = shapes,
                    sizes = sizes,
                    colors = colors,
                    elevations = elevations,
                    animatedVisibilityScope = animatedVisibilityScope,
                    tabBarContentModifier = tabBarContentModifier,
                    modifier = Modifier.weight(1f)
                )
            }

            if (standaloneTab != null) {
                ExpandedStandaloneTab(
                    standaloneTab = standaloneTab,
                    shapes = shapes,
                    colors = colors,
                    elevations = elevations,
                    animatedVisibilityScope = animatedVisibilityScope,
                    tabBarContentModifier = tabBarContentModifier,
                    modifier = Modifier
                        .fillMaxHeight()
                        .aspectRatio(1f)
                )
            }
        }
    }
}

@Composable
private fun SharedTransitionScope.ExpandedAccessory(
    accessory: @Composable SharedTransitionScope.(Modifier, AnimatedVisibilityScope) -> Unit,
    isAccessoryShared: Boolean,
    colors: FloatingTabBarColors,
    shapes: FloatingTabBarShapes,
    elevations: FloatingTabBarElevations,
    animatedVisibilityScope: AnimatedVisibilityScope,
    modifier: Modifier
) {
    Box(
        modifier = modifier
            .then(
                if (isAccessoryShared) {
                    Modifier.sharedElement(
                        sharedContentState = rememberSharedContentState("accessory"),
                        boundsTransform = LiquidBounds,
                        animatedVisibilityScope = animatedVisibilityScope
                    )
                } else {
                    Modifier.animateEnterExitAccessory(
                        sharedTransitionScope = this,
                        animatedVisibilityScope = animatedVisibilityScope
                    )
                }
            )
    ) {
        accessory(
            Modifier
                .shadow(
                    shape = shapes.accessoryShape,
                    elevation = elevations.expandedElevation
                )
                .background(color = colors.accessoryBackgroundColor, shapes.accessoryShape)
                .clip(shapes.accessoryShape),
            animatedVisibilityScope
        )
    }
}
@Composable
private fun SharedTransitionScope.ExpandedTabs(
    scope: FloatingTabBarScopeImpl,
    selectedTabKey: Any?,
    shapes: FloatingTabBarShapes,
    sizes: FloatingTabBarSizes,
    colors: FloatingTabBarColors,
    elevations: FloatingTabBarElevations,
    animatedVisibilityScope: AnimatedVisibilityScope,
    modifier: Modifier,
    tabBarContentModifier: @Composable () -> Modifier
) {
    val inlineTab = scope.getInlineTab(selectedTabKey)
    val selectedTabIndex = scope.tabs.indexOfFirst { it.key == selectedTabKey }
    val currentSelectedTabIndex by rememberUpdatedState(selectedTabIndex)
    val reduceAnimation by AppSettings.reduceAnimation.collectAsStateWithLifecycle()
    val reduceDynamicBlur by AppSettings.reduceDynamicBlur.collectAsStateWithLifecycle()
    val selectionSpec: AnimationSpec<Float> = if (reduceAnimation) {
        snap()
    } else {
        GlassSpring
    }
    val haptics = rememberHaptics()

    val coroutineScope = rememberCoroutineScope()
    val pill = remember { GlassPillMotion(coroutineScope, selectedTabIndex.coerceAtLeast(0)) }
    val glassIndicator by AppSettings.glassIndicator.collectAsStateWithLifecycle()
    pill.reduceMotion = reduceAnimation
    pill.squashScale = glassIndicator.squash
    // The lens samples the recorded page, and "reduce dynamic blur" stops that
    // recording. There the flat pill carries the whole motion on its own.
    val glassPill = isGlassSupported() && !reduceDynamicBlur &&
        com.music.audify.ui.components.LocalLiquidGlassEnabled.current
    val barGlass = rememberLayerBackdrop()
    val tabRow = rememberLayerBackdrop()

    var rowSize by remember { mutableStateOf(IntSize.Zero) }
    var lastHapticTab by remember { mutableIntStateOf(selectedTabIndex) }
    var lastSelectedTabIndex by remember { mutableIntStateOf(selectedTabIndex) }
    val density = LocalDensity.current
    val layoutDirection = LocalLayoutDirection.current
    val isRtl = layoutDirection == LayoutDirection.Rtl
    val tabCount = scope.tabs.size
    val tabSpacingPx = with(density) { sizes.tabSpacing.toPx() }
    val tabWidthPx = if (rowSize.width > 0 && tabCount > 0) {
        (rowSize.width - tabSpacingPx * (tabCount - 1)) / tabCount
    } else {
        0f
    }
    val tabStepPx = if (rowSize.width > 0 && tabCount > 0) {
        (rowSize.width + tabSpacingPx) / tabCount
    } else {
        0f
    }
    pill.stepDp = tabStepPx / density.density
    // For the row's drag, which runs in one coroutine for the life of the row
    // and would otherwise keep the step from its first touch.
    val currentTabStepPx by rememberUpdatedState(tabStepPx)
    val currentIsRtl by rememberUpdatedState(isRtl)
    val currentTabWidthPx by rememberUpdatedState(tabWidthPx)

    // The pill rests in its tab's cell and lifts to stand [PILL_GROW_HEIGHT]
    // taller than the whole bar, keeping the cell's proportions as it grows.
    val contentPadding = sizes.tabBarContentPadding
    val padLeftPx = with(density) { contentPadding.calculateLeftPadding(layoutDirection).toPx() }
    val padTopPx = with(density) { contentPadding.calculateTopPadding().toPx() }
    val padBottomPx = with(density) { contentPadding.calculateBottomPadding().toPx() }
    val rowHeightPx = rowSize.height.toFloat()
    val restSize = Size(tabWidthPx, rowHeightPx)
    val liftedHeightPx = rowHeightPx + padTopPx + padBottomPx +
        with(density) { PILL_GROW_HEIGHT.toPx() * glassIndicator.lift }
    val liftedSize = if (rowHeightPx > 0f) {
        Size(liftedHeightPx * tabWidthPx / rowHeightPx, liftedHeightPx)
    } else {
        restSize
    }
    // Where the pill's centre is in the row, for a position in tab steps.
    val pillCenterInRow: (Float) -> Offset = { position ->
        val x = position * tabStepPx + tabWidthPx / 2f
        Offset(if (isRtl) rowSize.width - x else x, rowHeightPx / 2f)
    }
    val showPill = selectedTabIndex >= 0 && tabWidthPx > 0f

    LaunchedEffect(selectedTabIndex) {
        if (selectedTabIndex >= 0) {
            // Back from the standalone tab (Search): the pill flies in from where
            // that tab sits, one step past the last tab, instead of just appearing.
            if (lastSelectedTabIndex < 0) {
                pill.enterFrom(tabCount.toDouble(), selectedTabIndex)
            } else {
                pill.animateTo(selectedTabIndex)
            }
        }
        lastSelectedTabIndex = selectedTabIndex
        lastHapticTab = selectedTabIndex
    }

    // Unclipped: the lifted pill stands past the bar's edges, so it is drawn
    // here, over the bar, rather than inside the bar's clip.
    Box(
        modifier = modifier
            .sharedElement(
                sharedContentState = rememberSharedContentState("tabGroup"),
                boundsTransform = LiquidBounds,
                animatedVisibilityScope = animatedVisibilityScope,
                zIndexInOverlay = 1f
            )
            // The whole bar swells a little while the pill is lifted (a drag, or a
            // tap carrying it to another tab). It rides the pill's own lift spring,
            // so the two grow together and the bar eases back as the pill lands.
            .graphicsLayer {
                val swell = if (reduceAnimation) 0f else pill.liftY.coerceAtLeast(0f)
                scaleX = 1f + 0.03f * swell
                scaleY = 1f + 0.06f * swell
            }
    ) {
        // The bar's glass is exported for the lens to refract.
        CompositionLocalProvider(LocalGlassExport provides barGlass) {
            Box(
                modifier = Modifier
                    .shadow(
                        shape = shapes.tabBarShape,
                        elevation = elevations.expandedElevation,
                        clip = false
                    )
                    .background(
                        color = colors.backgroundColor,
                        shape = shapes.tabBarShape
                    )
                    .then(tabBarContentModifier())
                    .padding(contentPadding)
            ) {
                if (showPill && (!glassPill || pill.isFlat || pill.blurMix > 0f)) {
                    FlatSelectionPill(
                        color = colors.indicatorColor,
                        barGlass = barGlass,
                        modifier = Modifier
                            .matchParentSize()
                            .pillPlacement(
                                center = { pillCenterInRow(pill.position) },
                                size = { pill.flatSize(restSize, liftedSize) },
                            ),
                    )
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(sizes.tabSpacing),
                    modifier = Modifier
                        .animateContentSize()
                        .fillMaxWidth()
                        .onSizeChanged { rowSize = it }
                        .layerBackdrop(tabRow)
                        .pointerInput(Unit) {
                            var totalDrag = 0f
                            var dragStartIndex = currentSelectedTabIndex
                            var direction = 1f
                            var step = 0f
                            // Decided per drag, not once for the coroutine: on the
                            // standalone tab there is no pill in this row to pick up,
                            // but that is no reason to stop listening for good.
                            var holding = false
                            detectHorizontalDragGestures(
                                onDragStart = {
                                    step = currentTabStepPx
                                    holding = currentSelectedTabIndex >= 0 && step > 0f
                                    if (!holding) return@detectHorizontalDragGestures
                                    // In tab order, whichever way the row runs.
                                    direction = if (currentIsRtl) -1f else 1f
                                    totalDrag = 0f
                                    dragStartIndex = currentSelectedTabIndex
                                    pill.startDrag()
                                },
                                onDragCancel = {
                                    if (holding) pill.release(currentSelectedTabIndex)
                                    holding = false
                                },
                                onDragEnd = {
                                    if (!holding) return@detectHorizontalDragGestures
                                    holding = false
                                    val absoluteIndexFloat = (dragStartIndex + totalDrag / step)
                                        .coerceIn(0f, scope.tabs.lastIndex.toFloat())
                                    val newIndex = absoluteIndexFloat.roundToInt()
                                    pill.release(newIndex)
                                    if (newIndex != currentSelectedTabIndex) {
                                        scope.tabs[newIndex].onClick()
                                    }
                                },
                                onHorizontalDrag = { _, delta ->
                                    if (!holding) return@detectHorizontalDragGestures
                                    totalDrag += delta * direction
                                    val absoluteIndexFloat = (dragStartIndex + totalDrag / step)
                                        .coerceIn(0f, scope.tabs.lastIndex.toFloat())
                                    pill.dragTo(absoluteIndexFloat)

                                    val approximateTab = absoluteIndexFloat.roundToInt()
                                    if (approximateTab != lastHapticTab) {
                                        haptics.play(Haptic.Tick)
                                        lastHapticTab = approximateTab
                                    }
                                }
                            )
                        }
                ) {
                    scope.tabs.forEachIndexed { tabIndex, tab ->
                        val isSelected = tab.key == selectedTabKey
                        val tabCoverage = remember(tabIndex) {
                            {
                                // Tanpa tab terpilih (mis. Search, tab standalone) pill
                                // disembunyikan, tapi posisi terakhirnya tetap di tab
                                // sebelumnya. Tanpa pengecekan ini ikon tab itu tetap
                                // berwarna aktif.
                                if (currentSelectedTabIndex < 0) {
                                    0f
                                } else {
                                    (1f - abs(pill.position - tabIndex)).coerceIn(0f, 1f)
                                }
                            }
                        }
                        val tabPillGeometry = remember(tabIndex) {
                            {
                                if (currentSelectedTabIndex < 0) {
                                    // Pill hidden: far enough away to cover nothing.
                                    Offset(1_000_000f, 0f)
                                } else {
                                    val dx = (pill.position - tabIndex) * currentTabStepPx
                                    Offset(if (currentIsRtl) -dx else dx, currentTabWidthPx)
                                }
                            }
                        }
                        val iconScale by animateFloatAsState(
                            targetValue = if (isSelected) 1.08f else 1f,
                            animationSpec = selectionSpec,
                            label = "glassTabScale"
                        )
                        Tab(
                            icon = {
                                Box(
                                    modifier = (if (tab.key == inlineTab?.key) {
                                        Modifier.sharedElement(
                                            sharedContentState = rememberSharedContentState("tab#${tab.key}-icon"),
                                            boundsTransform = LiquidBounds,
                                            animatedVisibilityScope = animatedVisibilityScope,
                                            zIndexInOverlay = 1f
                                        )
                                    } else {
                                        Modifier.animateEnterExitTab(
                                            sharedTransitionScope = this@ExpandedTabs,
                                            animatedVisibilityScope = animatedVisibilityScope
                                        )
                                    }).graphicsLayer {
                                        scaleX = iconScale
                                        scaleY = iconScale
                                    }
                                ) {
                                    CompositionLocalProvider(
                                        LocalTabPillCoverage provides tabCoverage,
                                        LocalTabPillGeometry provides tabPillGeometry,
                                    ) {
                                        tab.icon()
                                    }
                                }
                            },
                            title = {
                                Box(
                                    Modifier.animateEnterExitTab(
                                        sharedTransitionScope = this@ExpandedTabs,
                                        animatedVisibilityScope = animatedVisibilityScope
                                    )
                                ) {
                                    CompositionLocalProvider(
                                        LocalTabPillCoverage provides tabCoverage,
                                        LocalTabPillGeometry provides tabPillGeometry,
                                    ) {
                                        tab.title()
                                    }
                                }
                            },
                            isInline = false,
                            // An equal share of the pill each, the way the plain nav bar
                            // divides its own width, rather than each tab being as wide as
                            // its label happens to be.
                            modifier = Modifier
                                .weight(1f)
                                .skipToLookaheadSize()
                                .clip(shapes.tabShape)
                                .clickable(
                                    onClick = tab.onClick,
                                    indication = tab.indication?.invoke(),
                                    interactionSource = remember { MutableInteractionSource() }
                                )
                                .padding(sizes.tabExpandedContentPadding)
                        )
                    }
                }
            }
        }

        if (glassPill && showPill && !pill.isFlat) {
            GlassSelectionPill(
                motion = pill,
                fillColor = colors.indicatorColor,
                barGlass = barGlass,
                tabRow = tabRow,
                iconTint = colors.selectedContentColor,
                modifier = Modifier
                    .matchParentSize()
                    .pillPlacement(
                        center = { pillCenterInRow(pill.position) + Offset(padLeftPx, padTopPx) },
                        size = { pill.liveSize(restSize, liftedSize) },
                    ),
            )
        }
    }
}

@Composable
private fun SharedTransitionScope.ExpandedStandaloneTab(
    standaloneTab: FloatingTabBarTab,
    shapes: FloatingTabBarShapes,
    colors: FloatingTabBarColors,
    elevations: FloatingTabBarElevations,
    animatedVisibilityScope: AnimatedVisibilityScope,
    modifier: Modifier,
    tabBarContentModifier: @Composable () -> Modifier
) {
    StandaloneTabBody(
        standaloneTab = standaloneTab,
        isInline = false,
        shapes = shapes,
        colors = colors,
        elevation = elevations.expandedElevation,
        animatedVisibilityScope = animatedVisibilityScope,
        tabBarContentModifier = tabBarContentModifier,
        modifier = modifier,
    )
}

@Composable
private fun Tab(
    icon: @Composable () -> Unit,
    title: @Composable () -> Unit,
    isInline: Boolean,
    modifier: Modifier = Modifier,
    isStandalone: Boolean = false
) {
    Column(
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
    ) {
        icon()
        if (!isStandalone && !isInline) {
            title()
        }
    }
}

/**
 * A custom modifier that provides smooth enter/exit animations without clipping shadows or other content.
 * This is an alternative to animateEnterExit that uses renderInSharedTransitionScopeOverlay to prevent clipping.
 */
@Composable
private fun Modifier.animateEnterExitAccessory(
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope
): Modifier = with(sharedTransitionScope) {
    with(animatedVisibilityScope) {
        val animatedAlpha by transition.animateFloat { targetState ->
            when (targetState) {
                EnterExitState.Visible -> 1f
                else -> 0f
            }
        }

        this@animateEnterExitAccessory
            .renderInSharedTransitionScopeOverlay()
            .graphicsLayer(
                compositingStrategy = CompositingStrategy.ModulateAlpha,
                alpha = animatedAlpha
            )
    }
}

/**
 * A custom modifier that provides smooth enter/exit animations with fade and blur effects.
 */
@Composable
private fun Modifier.animateEnterExitTab(
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope
): Modifier = with(sharedTransitionScope) {
    with(animatedVisibilityScope) {
        val enterStartFraction = 0.5f
        val enterEndFraction = 0.8f
        val durationMs = 150

        val animatedAlpha by transition.animateFloat(
            transitionSpec = {
                keyframes {
                    durationMillis = durationMs
                    if (targetState == EnterExitState.Visible) {
                        0f atFraction enterStartFraction using FastOutSlowInEasing
                        1f atFraction enterEndFraction
                    }
                }
            }
        ) { targetState ->
            when (targetState) {
                EnterExitState.Visible -> 1f
                else -> 0f
            }
        }

        // 12dp, down from the library's 50. A tab glyph is 28dp across, so a 50dp
        // radius is twice the thing it is blurring — everything past about half
        // the element's width has already dissolved it to nothing and is spent
        // on a wider and wider sample of empty space. 12 reaches the same nothing
        // over a quarter of the area, and there are six of these running at once
        // in the middle of the fold, over glass that is sampling the screen.
        val blurRadius = with(LocalDensity.current) { 12.dp.toPx() }
        val animatedBlur by transition.animateFloat(
            transitionSpec = {
                keyframes {
                    durationMillis = durationMs
                    if (targetState == EnterExitState.Visible) {
                        blurRadius atFraction enterStartFraction using FastOutSlowInEasing
                        0f atFraction enterEndFraction
                    }
                }
            }
        ) { targetState ->
            when (targetState) {
                EnterExitState.Visible -> 0f
                else -> blurRadius
            }
        }

        graphicsLayer {
            alpha = animatedAlpha
            // Null at rest, rather than a zero-radius BlurEffect. A layer holding
            // any render effect at all is composited offscreen for as long as it
            // holds one, so the library's unconditional BlurEffect(0, 0) bought
            // every tab glyph and every tab label a permanent offscreen buffer to
            // pay for a blur of nothing — on a bar that is on screen the whole
            // time the app is.
            renderEffect =
                if (animatedBlur > 0f) BlurEffect(animatedBlur, animatedBlur) else null
        }
    }
}

private class FloatingTabBarScopeImpl(
    private val inlineSplitLast: Boolean = false,
) : FloatingTabBarScope {
    val tabs = mutableStateListOf<FloatingTabBarTab>()
    var standaloneTab: FloatingTabBarTab? by mutableStateOf(null)
        private set
    private var inlineTab: FloatingTabBarTab? = null

    /**
     * Standalone tab untuk bentuk inline. Jika tidak ada standalone asli,
     * tab terakhir dipisah jadi lingkaran (ikon saja) saat [inlineSplitLast] aktif.
     * Bentuk expanded tetap memakai [standaloneTab] (satu pil).
     */
    val inlineStandaloneTab: FloatingTabBarTab?
        get() = standaloneTab ?: if (inlineSplitLast && tabs.size > 1) {
            tabs.last().let { t ->
                FloatingTabBarTab(
                    key = t.key,
                    title = {},
                    icon = t.icon,
                    onClick = t.onClick,
                    indication = t.indication
                )
            }
        } else {
            null
        }

    fun getInlineTab(selectedTabKey: Any?): FloatingTabBarTab? {
        return if (selectedTabKey != inlineStandaloneTab?.key) {
            val selectedTab = tabs.find { it.key == selectedTabKey }
            if (selectedTab != null) {
                inlineTab = selectedTab
                selectedTab
            } else {
                inlineTab ?: tabs.firstOrNull()
            }
        } else {
            inlineTab ?: tabs.firstOrNull()
        }
    }

    override fun tab(
        key: Any,
        title: @Composable () -> Unit,
        icon: @Composable () -> Unit,
        onClick: () -> Unit,
        indication: (@Composable () -> Indication)?
    ) {
        tabs.add(
            FloatingTabBarTab(
                key = key,
                title = title,
                icon = icon,
                onClick = onClick,
                indication = indication
            )
        )
    }
    
    override fun standaloneTab(
        key: Any,
        icon: @Composable () -> Unit,
        onClick: () -> Unit,
        indication: (@Composable () -> Indication)?
    ) {
        standaloneTab = FloatingTabBarTab(
            key = key,
            title = {},
            icon = icon,
            onClick = onClick,
            indication = indication
        )
    }
}

private data class FloatingTabBarTab(
    val key: Any,
    val title: @Composable () -> Unit,
    val icon: @Composable () -> Unit,
    val onClick: () -> Unit,
    val indication: (@Composable () -> Indication)?
)

/**
 * Represents the colors used in [FloatingTabBar].
 */
@Immutable
data class FloatingTabBarColors(
    val backgroundColor: Color,
    val accessoryBackgroundColor: Color,
    val indicatorColor: Color,
    /** Colour the tab row is masked to inside the lifted glass pill; Unspecified keeps each tab's own colour. */
    val selectedContentColor: Color = Color.Unspecified,
)

/**
 * Represents the shapes used in [FloatingTabBar].
 */
@Immutable
data class FloatingTabBarShapes(
    val tabBarShape: Shape,
    val tabShape: Shape,
    val standaloneTabShape: Shape,
    val accessoryShape: Shape,
)

/**
 * Represents the elevations used in [FloatingTabBar].
 */
@Immutable
data class FloatingTabBarElevations(
    val inlineElevation: Dp,
    val expandedElevation: Dp,
)

/**
 * Represents the sizes and spacing used in [FloatingTabBar].
 */
@Immutable
data class FloatingTabBarSizes(
    val tabBarContentPadding: PaddingValues,
    val tabInlineContentPadding: PaddingValues,
    val tabExpandedContentPadding: PaddingValues,
    val componentSpacing: Dp,
    val tabSpacing: Dp,
    val inlineComponentSpacing: Dp = componentSpacing,
    val inlineBarPadding: PaddingValues = PaddingValues(),
)

/**
 * Contains the default values used by [FloatingTabBar].
 */
object FloatingTabBarDefaults {
    /**
     * Creates a [FloatingTabBarColors] that represents the default colors used in a [FloatingTabBar].
     *
     * @param backgroundColor the color used for the tab bar background
     * @param accessoryBackgroundColor the color used for the accessory background
     * @param indicatorColor the color used behind the selected tab in expanded state
     */
    @Composable
    fun colors(
        backgroundColor: Color = MaterialTheme.colorScheme.surfaceContainerHigh,
        accessoryBackgroundColor: Color = MaterialTheme.colorScheme.surfaceContainerHigh,
        indicatorColor: Color = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
        selectedContentColor: Color = Color.Unspecified,
    ): FloatingTabBarColors = FloatingTabBarColors(
        backgroundColor = backgroundColor,
        accessoryBackgroundColor = accessoryBackgroundColor,
        indicatorColor = indicatorColor,
        selectedContentColor = selectedContentColor,
    )

    /**
     * Creates a [FloatingTabBarShapes] that represents the default shapes used in a [FloatingTabBar].
     *
     * @param tabBarShape the shape used to clip the tab bar
     * @param tabShape the shape used to clip individual tabs. Can be useful for example to control the click ripple effect shape
     * @param standaloneTabShape the shape used to clip the standalone tab
     * @param accessoryShape the shape used to clip the accessory container
     */
    @Composable
    fun shapes(
        tabBarShape: Shape = RoundedCornerShape(100),
        tabShape: Shape = RoundedCornerShape(100),
        standaloneTabShape: Shape = CircleShape,
        accessoryShape: Shape = RoundedCornerShape(100),
    ): FloatingTabBarShapes = FloatingTabBarShapes(
        tabBarShape = tabBarShape,
        tabShape = tabShape,
        standaloneTabShape = standaloneTabShape,
        accessoryShape = accessoryShape,
    )

    /**
     * Creates a [FloatingTabBarSizes] that represents the default sizes used in a [FloatingTabBar].
     *
     * @param tabBarContentPadding the padding applied to the tab bar content. This also applies to the standalone tab content.
     * @param tabInlineContentPadding the padding applied to tabs in inline state
     * @param tabExpandedContentPadding the padding applied to tabs in expanded state
     * @param componentSpacing the spacing between components
     * @param tabSpacing the spacing between tabs in expanded state
     * @param inlineComponentSpacing the spacing between components in inline state
     * @param inlineBarPadding padding around the whole bar in inline state, inside the bar's own
     */
    @Composable
    fun sizes(
        tabBarContentPadding: PaddingValues = PaddingValues(vertical = 4.dp, horizontal = 4.dp),
        tabInlineContentPadding: PaddingValues = PaddingValues(10.dp),
        tabExpandedContentPadding: PaddingValues = PaddingValues(vertical = 6.dp, horizontal = 20.dp),
        componentSpacing: Dp = 8.dp,
        tabSpacing: Dp = 0.dp,
        inlineComponentSpacing: Dp = componentSpacing,
        inlineBarPadding: PaddingValues = PaddingValues(),
    ): FloatingTabBarSizes = FloatingTabBarSizes(
        tabBarContentPadding = tabBarContentPadding,
        tabInlineContentPadding = tabInlineContentPadding,
        tabExpandedContentPadding = tabExpandedContentPadding,
        componentSpacing = componentSpacing,
        tabSpacing = tabSpacing,
        inlineComponentSpacing = inlineComponentSpacing,
        inlineBarPadding = inlineBarPadding,
    )

    /**
     * Creates a [FloatingTabBarElevations] that represents the default elevations used in a [FloatingTabBar].
     *
     * @param inlineElevation the elevation used for tabs in inline state
     * @param expandedElevation the elevation used for tabs in expanded state
     */
    @Composable
    fun elevations(
        inlineElevation: Dp = 6.dp,
        expandedElevation: Dp = 12.dp,
    ): FloatingTabBarElevations = FloatingTabBarElevations(
        inlineElevation = inlineElevation,
        expandedElevation = expandedElevation,
    )
}
