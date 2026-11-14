package com.music.audify.ui.player

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection

/**
 * The mini player the expanded player docks into, for a host that has one.
 *
 * Opening and closing the player is one movement on screen: the sheet's
 * artwork travels between its own place and the mini player's cover while the
 * rest of the player fades — see the sleeve in [NowPlayingScreen]. The two are
 * in different windows on a phone (the player is a modal sheet over the page),
 * so nothing here is shared layout; it is the two ends of the movement, each
 * reported by the side that owns it, and met in screen coordinates.
 *
 * Read in layout and draw only. Nothing in it is worth a recomposition: a
 * frame of the sheet's travel moves the artwork and re-tints a layer, and that
 * is all it does.
 */
@Stable
class PlayerDock {
    /**
     * The mini player's cover, as last laid out. Held as coordinates rather than
     * as a rect, and only turned into one when the player asks: the bar moves
     * on every scroll that folds it, and working out a screen position for each
     * of those frames would be paid for by a player that isn't open.
     */
    private var miniArt: LayoutCoordinates? = null
    private var miniArtCorner: Dp = 8.dp

    /**
     * How far the player sheet sits below fully open, in pixels. Set by the
     * host while the sheet is up, null while it is not. `NaN` before the sheet
     * has been laid out.
     */
    var sheetOffset: (() -> Float)? by mutableStateOf(null)

    /** How far the sheet travels from fully open to gone, in pixels. */
    var sheetTravel by mutableFloatStateOf(0f)

    /**
     * Whether the player on screen takes part. Only its portrait shape does —
     * a landscape player has no sleeve that could become a cover, and keeps
     * the plain slide.
     */
    var attached by mutableStateOf(false)
        internal set

    /** Reported by the mini player's cover whenever it is placed. */
    fun reportMiniArt(coordinates: LayoutCoordinates, corner: Dp) {
        miniArt = coordinates
        miniArtCorner = corner
    }

    /** From the cover leaving composition, so a stale rect is never flown to. */
    fun releaseMiniArt(coordinates: LayoutCoordinates) {
        if (miniArt === coordinates) miniArt = null
    }

    internal fun hasMiniArt(): Boolean = miniArt?.isAttached == true

    /**
     * Both corners mapped through every transform above the cover, not its
     * position plus its laid-out size: the glass bar draws its player scaled,
     * and the artwork landed on a cover a fifth bigger than the one on screen.
     */
    internal fun miniArtOnScreen(): Rect? {
        val coordinates = miniArt?.takeIf { it.isAttached } ?: return null
        val size = coordinates.size.toSize()
        return Rect(
            coordinates.localToScreen(Offset.Zero),
            coordinates.localToScreen(Offset(size.width, size.height)),
        )
    }

    internal fun miniArtCorner(): Dp = miniArtCorner

    // ---- Morph: the mini player's pill and the full player are ONE surface ----
    // The pill's rect (at rest, without the pull lift) is where the player
    // starts and ends; the player is clipped to a rounded rect that grows from
    // that pill to the whole window — see [playerMorph].
    private var miniPill: LayoutCoordinates? = null
    private var originW = -1
    private var originH = -1

    /** Where the player's window starts on screen. Measured once the sheet is laid out. */
    internal var windowOrigin: Offset = Offset.Zero

    // Dozens of layout, graphics-layer and draw blocks ask for the pill every
    // frame of an open or close, and each ask walks the coordinate chain twice.
    // The pill does not move during the trip, so one answer serves the frame.
    private var pillCache: Rect? = null
    private var pillCacheNanos = 0L

    fun reportMiniPill(coordinates: LayoutCoordinates) {
        miniPill = coordinates
        pillCache = null
    }

    fun releaseMiniPill(coordinates: LayoutCoordinates) {
        if (miniPill === coordinates) {
            miniPill = null
            pillCache = null
        }
    }

    /** The pill in the player window's own pixels, or null with no pill on screen. */
    fun miniPillInWindow(): Rect? {
        val c = miniPill?.takeIf { it.isAttached } ?: return null
        val now = System.nanoTime()
        pillCache?.let { if (now - pillCacheNanos < PILL_CACHE_NANOS) return it }
        val size = c.size.toSize()
        return Rect(
            c.localToScreen(Offset.Zero) - windowOrigin,
            c.localToScreen(Offset(size.width, size.height)) - windowOrigin,
        ).also {
            pillCache = it
            pillCacheNanos = now
        }
    }

    /**
     * How far the player travels between fully open and docked on the pill:
     * the pill's top edge. Falls back to the whole window with no pill.
     */
    fun travel(): Float {
        val top = miniPillInWindow()?.top
        return if (top != null && top > 1f) top else sheetTravel
    }

    internal fun measureWindow(coordinates: LayoutCoordinates) {
        if (originW == coordinates.size.width && originH == coordinates.size.height) return
        val offset = offset() ?: return
        originW = coordinates.size.width
        originH = coordinates.size.height
        windowOrigin = coordinates.localToScreen(Offset.Zero) - Offset(0f, offset.toInt().toFloat())
    }

    /**
     * The player's visible rect this frame, in the player box's own pixels, or
     * null when it is fully open (no clip at all) or there is no pill to grow
     * from. Left and right close in on the pill, the bottom rises to meet it,
     * and the corners round off to the pill's: one shape changing size.
     */
    internal fun morph(size: Size): PlayerMorph? {
        val pill = miniPillInWindow() ?: return null
        val offset = offset() ?: return null
        val travel = pill.top
        if (travel <= 1f) return null
        val progress = (1f - offset / travel).coerceIn(0f, 1f)
        if (progress >= 1f) return null
        // Sama dengan [panelBounds]: lebar menyusul lebih cepat dari naiknya,
        // jadi tidak ada celah di kiri dan kanan layar selagi di-drag.
        val wideX = (progress / PANEL_WIDE_AT).coerceIn(0f, 1f)
        val wide = 1f - (1f - wideX) * (1f - wideX)
        val left = pill.left * (1f - wide)
        val right = size.width - (size.width - pill.right) * (1f - wide)
        val bottom = (pill.bottom + (size.height - pill.bottom) * progress - offset).coerceAtLeast(1f)
        return PlayerMorph(
            rect = Rect(left, 0f, right.coerceAtLeast(left + 1f), bottom),
            corner = pill.height / 2f * (1f - progress),
            progress = progress,
        )
    }

    // ---- The one panel: pill and player as a single container ----

    /**
     * True while the single panel stands where the mini player's pill is, so
     * the pill itself stands aside instead of showing as a second panel.
     */
    var panelShown by mutableStateOf(false)
        private set

    /**
     * Warna solid latar panel selagi tumbuh dari pill: diambil dari cover lagu
     * oleh pemutar penuh, hitam polos sebelum diketahui.
     */
    var panelColor by mutableStateOf(Color(0xFF151517))

    /** From the panel's root, once placed: where its window starts on screen. */
    fun panelPlaced(coordinates: LayoutCoordinates) {
        windowOrigin = coordinates.localToScreen(Offset.Zero)
        // Ukuran window panel sendiri, dipakai panelSideTravel/panelBottomTravel.
        // Sebelumnya hanya diisi playerMorph() yang tidak lagi dipasang, jadi
        // keduanya NaN dan tab bar memakai kurva cadangan yang tidak menempel panel.
        originW = coordinates.size.width
        originH = coordinates.size.height
        panelShown = true
    }

    /** The pill comes back. Called as the panel goes, not after it has. */
    fun releasePanel() {
        panelShown = false
    }

    /**
     * The panel's rect this frame, in the window's own pixels. At the bottom
     * of its travel it is the pill's rect; fully open, the whole window. In
     * between its left and right close in on the pill, its top follows the
     * sheet's offset, and its bottom rises to meet the pill: one shape
     * changing size. With no pill to grow from it is the whole window,
     * sliding as one.
     */
    fun panelBounds(window: Size): Rect {
        val offset = offset() ?: 0f
        val pill = miniPillInWindow()
        if (pill == null || pill.top <= 1f) {
            return Rect(0f, offset, window.width, window.height + offset)
        }
        val progress = (1f - offset / pill.top).coerceIn(0f, 1f)
        // Lebar panel menyusul lebih cepat dari naiknya, seperti Apple Music:
        // sudut layar sudah tertutup sebelum setengah jalan.
        val wideX = (progress / PANEL_WIDE_AT).coerceIn(0f, 1f)
        val wide = 1f - (1f - wideX) * (1f - wideX)
        val left = pill.left * (1f - wide)
        val right = window.width - (window.width - pill.right) * (1f - wide)
        val bottom = pill.bottom + (window.height - pill.bottom) * progress
        return Rect(left, offset, right.coerceAtLeast(left + 1f), bottom.coerceAtLeast(offset + 1f))
    }

    /** 0 on the pill (all glass), 1 once the plain panel has fully covered the glass. */
    fun panelFill(until: Float = PANEL_FILL_UNTIL): Float = (openFraction() / until).coerceIn(0f, 1f)

    /** The panel's corner radius this frame: the pill's, rounding off to square. */
    fun panelCornerPx(sheetCornerPx: Float = 0f): Float {
        val pill = miniPillInWindow() ?: return 0f
        if (pill.top <= 1f) return 0f
        val progress = (1f - (offset() ?: 0f) / pill.top).coerceIn(0f, 1f)
        val pillCorner = pill.height / 2f * (1f - progress)
        // Seperti sheet Apple Music: sudut atas tetap membulat selagi panel
        // naik, baru rata di ujung ketika panel menutupi seluruh layar.
        val sheetCorner = sheetCornerPx *
            (progress / PANEL_WIDE_AT).coerceIn(0f, 1f) *
            (1f - ((progress - 0.9f) / 0.1f).coerceIn(0f, 1f))
        return maxOf(pillCorner, sheetCorner)
    }

    /** The sheet's offset this frame, or null with no sheet laid out. */
    internal fun offset(): Float? = sheetOffset?.invoke()?.takeUnless { it.isNaN() }

    /**
     * 1 with the player fully open, 0 down on the mini player. 0 too while
     * there is no sheet yet, which is where an opening player starts.
     */
    fun openFraction(): Float {
        val travel = travel()
        val offset = offset() ?: return 0f
        if (travel <= 0f) return 0f
        return (1f - offset / travel).coerceIn(0f, 1f)
    }

    /**
     * Seberapa jauh (px) tepi kiri ([toRight] false) atau kanan panel sudah
     * menjauh dari pill, memakai kurva lebar yang sama dengan [panelBounds].
     * Tab dan Search digeser sebesar ini, jadi jaraknya ke panel tetap sama
     * seperti saat diam. NaN kalau belum bisa dihitung.
     */
    fun panelSideTravel(toRight: Boolean): Float {
        val open = openFraction()
        if (open <= 0f) return 0f
        val pill = miniPillInWindow() ?: return Float.NaN
        if (pill.top <= 1f || originW <= 0) return Float.NaN
        val wide = panelWideEase(open)
        return if (toRight) (originW - pill.right).coerceAtLeast(0f) * wide else pill.left.coerceAtLeast(0f) * wide
    }

    /**
     * Seberapa jauh (px) tepi bawah panel sudah turun dari tepi bawah pill.
     * Baris tab digeser sebesar ini, jadi tetap tepat di bawah panel dengan
     * jarak diamnya. NaN kalau belum bisa dihitung.
     */
    fun panelBottomTravel(): Float {
        val open = openFraction()
        if (open <= 0f) return 0f
        val pill = miniPillInWindow() ?: return Float.NaN
        if (pill.top <= 1f || originH <= 0) return Float.NaN
        return (originH - pill.bottom).coerceAtLeast(0f) * open
    }

    /**
     * Whether the mini player's own cover should stand aside, because the
     * player's is on its way to or from it. Not before the sheet has moved at
     * all: the sheet's window can land a frame after the page's, and a cover
     * hidden in that frame is a hole in the bar.
     */
    val coversMiniArt: Boolean
        get() = coversMiniArtState.value

    // Derived, so a reader hears about it twice a transition — when it flips
    // — rather than on every frame of the sheet's travel. Read straight off
    // the fraction, the cover's layer re-ran every frame of an open or a close,
    // and every one of those invalidated the page's whole window behind the
    // player: a full redraw, liquid glass and all, for a cover that hadn't
    // changed.
    // Also while the panel stands on the pill, even fully down: the panel's own
    // row shows a cover of its own, and at the end of a close — the sheet at
    // zero but the player not yet gone — it was a second cover beside the one
    // flying in.
    private val coversMiniArtState = derivedStateOf { attached && (openFraction() > 0f || panelShown) }
}

/** The [PlayerDock] around the player, if its host has a mini player to dock into. */
val LocalPlayerDock = staticCompositionLocalOf<PlayerDock?> { null }

/** How long one measurement of the mini pill stands, in nanoseconds: under a frame. */
private const val PILL_CACHE_NANOS = 8_000_000L

/** The share of the opening over which the glass gives way to the plain panel. */
private const val PANEL_FILL_UNTIL = 0.2f

/** Seberapa jauh panel terbuka saat lebarnya sudah penuh selebar layar: bertahap, bukan langsung. */
private const val PANEL_WIDE_AT = 0.8f

/**
 * Kurva lebar panel (0 di pill, 1 saat selebar layar) untuk jarak buka [t].
 * Dipakai juga oleh cover yang terbang ke/dari mini player, supaya cover turun
 * dan naik selaras dengan panel, tidak mendahului atau tertinggal darinya.
 */
internal fun panelWideEase(t: Float): Float {
    val x = (t / PANEL_WIDE_AT).coerceIn(0f, 1f)
    return 1f - (1f - x) * (1f - x)
}

internal class PlayerMorph(val rect: Rect, val corner: Float, val progress: Float)

private class MorphShape(private val rect: Rect, private val corner: Float) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline =
        Outline.Rounded(RoundRect(rect, CornerRadius(corner, corner)))
}

/**
 * Clips the player to a rounded rect that grows from the mini player's pill to
 * the whole window as the sheet opens (and shrinks back as it closes), with a
 * dark fill that comes up quickly so the pill reads as turning into the player.
 * Read in the graphics layer and draw only: no recomposition per frame.
 * No-op without a dock, and once the player is fully open.
 */
fun Modifier.playerMorph(dock: PlayerDock?): Modifier {
    if (dock == null) return this
    return this
        .onGloballyPositioned { dock.measureWindow(it) }
        .graphicsLayer {
            val morph = dock.morph(size)
            if (morph != null) {
                shape = MorphShape(morph.rect, morph.corner)
                clip = true
                // Until the player has placed itself, its first frame would
                // show full-size UI inside the pill.
                alpha = if (!dock.attached && morph.progress < 0.02f) 0f else 1f
            } else {
                clip = false
                alpha = 1f
            }
        }
        .drawBehind {
            val morph = dock.morph(size) ?: return@drawBehind
            drawRect(Color(0xFF151517).copy(alpha = (morph.progress * 4f).coerceIn(0f, 1f)))
        }
}

/** How small the page behind the player gets while the player is fully up. */
private const val PAGE_RECEDE_SCALE = 0.94f

/**
 * Leaves the page undrawn while the portrait player fully covers it: its own
 * background is opaque, so everything drawn under it (glass bars, blur, lists)
 * would be paid for every frame for nobody to see. Layout, composition and
 * input are untouched; only drawing stops.
 *
 * Put on the page, never on a parent of the player panel, or the panel would
 * be skipped with it. The flag is derived, so the draw is invalidated twice
 * per open or close — when it flips — and not on every frame of the travel.
 */
@Composable
fun Modifier.skipDrawWhenPlayerCovers(dock: PlayerDock?): Modifier {
    val covered by remember(dock) {
        derivedStateOf { dock != null && dock.attached && dock.openFraction() >= 1f }
    }
    return drawWithContent { if (!covered) drawContent() }
}

/**
 * The page shrinks a little as the player comes up over it, and grows back as
 * it goes. Read in the graphics layer only, so a frame of the player's travel
 * costs no recomposition and no layout.
 */
fun Modifier.recedeBehindPlayer(dock: PlayerDock?): Modifier {
    if (dock == null) return this
    return graphicsLayer {
        if (dock.attached) {
            val scale = 1f - (1f - PAGE_RECEDE_SCALE) * dock.openFraction()
            scaleX = scale
            scaleY = scale
        }
    }
}
