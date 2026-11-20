package com.music.audify.ui.components

import android.os.Build
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitHorizontalTouchSlopOrCancellation
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.gestures.horizontalDrag
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.runtime.withFrameNanos
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.music.audify.R
import com.music.audify.data.settings.AppSettings
import com.music.audify.data.settings.ThemeMode
import com.music.audify.ui.components.backdrop.backdrops.emptyBackdrop
import com.music.audify.ui.components.backdrop.backdrops.layerBackdrop
import com.music.audify.ui.components.backdrop.backdrops.rememberLayerBackdrop
import com.music.audify.ui.components.backdrop.drawBackdrop
import com.music.audify.ui.components.backdrop.effects.colorControls
import com.music.audify.ui.components.backdrop.effects.lens
import com.music.audify.ui.components.backdrop.highlight.Highlight
import com.music.audify.ui.components.backdrop.shadow.InnerShadow
import com.music.audify.ui.components.backdrop.shadow.Shadow
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.roundToInt
import kotlinx.coroutines.launch
import kotlin.math.sin

/**
 * Pemilih tema System / Light / Dark: kapsul kaca dengan satu pill kaca mengambang.
 *
 * State sepenuhnya dikontrol parent lewat [selectedTheme]; komponen ini tidak
 * menyimpan pilihan sendiri. Pill berpindah dengan spring yang sedikit
 * overshoot, melebar mengikuti kecepatan lalu kembali ("liquid"), dan
 * membiaskan track beserta label di bawahnya lewat lens.
 *
 * Mengikuti aturan glass app: tanpa blur/lens bila [isGlassSupported] false
 * atau "Reduce dynamic blur" aktif (fill solid + hairline), snap() bila
 * "Reduce animation" aktif. Track direkam ke backdrop lokal, bukan
 * LocalAppBackdrop, supaya tidak terjadi feedback loop di dalam halaman Settings.
 */
@Composable
fun LiquidThemeSelector(
    selectedTheme: ThemeMode,
    onThemeSelected: (ThemeMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    val modes = remember { ThemeMode.entries.toList() }
    val labelStyle = MaterialTheme.typography.labelMedium.copy(fontSize = 13.5.sp)
    LiquidSegmentedControl(
        count = modes.size,
        selection = modes.indexOf(selectedTheme),
        onSelect = { onThemeSelected(modes[it]) },
        modifier = modifier,
    ) { index, color ->
        SegmentContent(modes[index], color, labelStyle)
    }
}

/**
 * Segmented control liquid generik: track kaca + pill lens yang bisa di-drag.
 * Isi tiap segmen digambar oleh [content] dengan warna yang sudah mengikuti
 * posisi pill. Dipakai LiquidThemeSelector dan GlassSegmentedControl.
 */
@Composable
fun LiquidSegmentedControl(
    count: Int,
    selection: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    draggable: Boolean = true,
    dragOnlyFromPill: Boolean = false,
    /** Untuk track yang bisa digeser: drag pill yang mentok di tepi viewport ikut menggeser track. */
    edgeScroll: ScrollState? = null,
    /** Lebar viewport track (px); dibutuhkan bersama [edgeScroll]. */
    viewportPx: Float = 0f,
    /** False untuk track yang bisa digeser: rim digambar pemanggil di viewport yang diam. */
    drawRim: Boolean = true,
    content: @Composable (index: Int, color: Color) -> Unit,
) {
    val modes = remember(count) { List(count) { it } }
    val selectedIndex = selection.coerceIn(0, (count - 1).coerceAtLeast(0))
    val currentSelected by rememberUpdatedState(selectedIndex)
    val currentOnSelected by rememberUpdatedState(onSelect)

    val reduceDynamicBlur by AppSettings.reduceDynamicBlur.collectAsStateWithLifecycle()
    val reduceAnimation by AppSettings.reduceAnimation.collectAsStateWithLifecycle()
    val glassOn = isGlassSupported() && !reduceDynamicBlur && LocalLiquidGlassEnabled.current

    val light = MaterialTheme.colorScheme.surface.luminance() > 0.5f
    val labelColor = glassContentColor()
    val dimColor = labelColor.copy(alpha = 0.55f)
    val isLtr = LocalLayoutDirection.current == LayoutDirection.Ltr
    val haptics = LocalHapticFeedback.current
    val density = LocalDensity.current
    val shape = remember { RoundedCornerShape(percent = 50) }
    val trackBackdrop = rememberLayerBackdrop()

    // Posisi pill dalam satuan indeks segmen; spring dengan sedikit overshoot.
    val position = remember { Animatable(selectedIndex.toFloat()) }
    LaunchedEffect(selectedIndex, reduceAnimation) {
        val target = selectedIndex.toFloat()
        if (reduceAnimation) {
            position.snapTo(target)
        } else {
            position.animateTo(
                target,
                spring(
                    dampingRatio = 0.62f,
                    stiffness = Spring.StiffnessMediumLow,
                    visibilityThreshold = 0.001f,
                ),
            )
        }
    }

    // State drag: isDragging mengangkat pill (seperti ditekan), dragIndex = segmen
    // di bawah pill selama drag (-1 bila tidak drag) supaya label tajam ikut pill.
    val scope = rememberCoroutineScope()
    var isDragging by remember { mutableStateOf(false) }
    var dragIndex by remember { mutableIntStateOf(-1) }
    LaunchedEffect(selectedIndex) { dragIndex = -1 }

    val sources = remember(count) { List(modes.size) { MutableInteractionSource() } }
    val pressedStates = sources.map { it.collectIsPressedAsState() }
    val anyPressed = pressedStates.any { it.value }
    val pressProgress = animateFloatAsState(
        targetValue = if ((anyPressed || isDragging) && !reduceAnimation) 1f else 0f,
        animationSpec = if (reduceAnimation) snap() else spring(0.7f, 500f),
        label = "themePillPress",
    )
    // 0 diam .. 1 bergerak cepat / ditekan. Dibaca saat draw, bukan saat komposisi.
    val energy = {
        max(pressProgress.value, (abs(position.velocity) / 6f).coerceIn(0f, 1f))
    }

    val trackBrush = Brush.verticalGradient(
        listOf(
            TRACK_FILL.copy(alpha = if (light) 0.20f else 0.34f),
            TRACK_FILL.copy(alpha = if (light) 0.09f else 0.18f),
        ),
    )
    val pillTint = Color.White
    val pillRestAlpha = if (light) 0.72f else 0.18f

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .height(SELECTOR_HEIGHT),
    ) {
        val slot = (maxWidth - SELECTOR_INSET * 2) / modes.size
        val slotPx = with(density) { slot.toPx() }

        // 1) Track + label redup: inilah yang dilihat dan dibiaskan pill.
        Box(
            Modifier
                .matchParentSize()
                .clip(shape)
                .then(
                    if (glassOn) {
                        // Lensa menggambar salinan label dari backdrop; label asli di bawah
                        // pill dilubangi supaya tidak terlihat dobel selama pill bergerak.
                        // Rekaman backdrop tetap utuh, hanya gambar yang terlihat yang dilubangi.
                        Modifier.drawWithContent {
                            val r = liquidPillRect(
                                size = size,
                                slotPx = slotPx,
                                insetPx = SELECTOR_INSET.toPx(),
                                position = position.value,
                                velocity = position.velocity,
                                press = pressProgress.value,
                                isLtr = isLtr,
                            )
                            val hole = Path().apply {
                                addRoundRect(RoundRect(r, CornerRadius(r.height / 2f)))
                            }
                            clipPath(hole, ClipOp.Difference) {
                                this@drawWithContent.drawContent()
                            }
                        }
                    } else {
                        Modifier
                    },
                )
                .layerBackdrop(trackBackdrop)
                .background(trackBrush)
                // Isi pill digambar DI BAWAH label dan ikut terekam ke backdrop, seperti
                // navbar (halaman, bar, isi pill, lalu tab): salinan di dalam lensa sudah
                // lengkap, jadi tidak ada lapisan label tajam kedua yang bisa terlihat dobel.
                .then(
                    if (glassOn) {
                        Modifier.drawBehind {
                            val r = liquidPillRect(
                                size = size,
                                slotPx = slotPx,
                                insetPx = SELECTOR_INSET.toPx(),
                                position = position.value,
                                velocity = position.velocity,
                                press = pressProgress.value,
                                isLtr = isLtr,
                            )
                            val e = energy()
                            drawRoundRect(
                                color = pillTint.copy(alpha = pillRestAlpha * (1f - 0.85f * e)),
                                topLeft = r.topLeft,
                                size = r.size,
                                cornerRadius = CornerRadius(r.height / 2f),
                            )
                        }
                    } else {
                        // Tanpa glass: pill digambar DI BAWAH label (bukan di atasnya),
                        // jadi label tetap terlihat saat pill ditarik.
                        Modifier.drawBehind {
                            val r = liquidPillRect(
                                size = size,
                                slotPx = slotPx,
                                insetPx = SELECTOR_INSET.toPx(),
                                position = position.value,
                                velocity = position.velocity,
                                press = pressProgress.value,
                                isLtr = isLtr,
                            )
                            drawRoundRect(
                                color = if (light) Color.White else FALLBACK_DARK_PILL,
                                topLeft = r.topLeft,
                                size = r.size,
                                cornerRadius = CornerRadius(r.height / 2f),
                            )
                        }
                    },
                )
                .padding(SELECTOR_INSET),
        ) {
            Row(Modifier.fillMaxWidth().fillMaxHeight()) {
                modes.forEachIndexed { index, mode ->
                    // Label di dekat pill ikut direkam tajam ke backdrop, jadi lensa pill
                    // membiaskan ikon + teks aslinya. Terangnya mengikuti posisi pill
                    // (bukan indeks + tween), jadi saat drag cepat tidak ada dua label
                    // terang sekaligus.
                    val near = (1f - abs(position.value - index)).coerceIn(0f, 1f)
                    val trackColor = androidx.compose.ui.graphics.lerp(dimColor, labelColor, near)
                    SegmentBox(Modifier.weight(1f)) {
                        content(index, trackColor)
                    }
                }
            }
        }

        // 2) Pill kaca yang bergerak.
        val pillBase = Modifier
            .align(Alignment.CenterStart)
            .padding(start = SELECTOR_INSET)
            .graphicsLayer {
                translationX = (if (isLtr) 1f else -1f) * position.value * slotPx
            }
        val pill = if (glassOn) {
            pillBase.drawBackdrop(
                backdrop = trackBackdrop,
                shape = { shape },
                effects = {
                    val e = energy()
                    val g = max(e, REST_GLASS)
                    colorControls(saturation = 1f + 0.35f * g)

                    // Lensa hanya menyala saat pill bergerak/ditekan. Saat diam, label tajam
                    // di atas pill sudah menutupi label di bawahnya; kalau lensa tetap
                    // membiaskan, salinan yang bergeser terlihat sebagai teks dan ikon dobel.
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && e > 0.02f) {
                        lens(
                            refractionHeight = 12.dp.toPx() * e,
                            refractionAmount = 14.dp.toPx() * e,
                            depthEffect = true,
                            chromaticAberration = e > 0.5f,
                        )
                    }
                },
                highlight = { Highlight.Ambient.copy(alpha = 0.55f + 0.45f * energy()) },
                shadow = {
                    Shadow(
                        radius = 8.dp,
                        offset = DpOffset(0.dp, 2.dp),
                        color = Color.Black.copy(alpha = if (light) 0.12f else 0.30f),
                    )
                },
                innerShadow = {
                    InnerShadow(
                        radius = 4.dp,
                        offset = DpOffset(0.dp, 1.dp),
                        color = Color.Black.copy(alpha = if (light) 0.10f else 0.25f),
                        alpha = 0.6f,
                    )
                },
                layerBlock = {
                    // Liquid: meregang searah gerak mengikuti kecepatan, lalu kembali via spring.
                    val stretch = (abs(position.velocity) / 8f).coerceIn(0f, 1f)
                    val pop = 1f + 0.05f * pressProgress.value
                    scaleX = pop * (1f + 0.16f * stretch)
                    scaleY = pop * (1f - 0.07f * stretch)
                },
                onDrawSurface = {
                    val e = energy()
                    // Isi pill sudah ada di bawah label (terekam di backdrop track).
                    val w = 1.dp.toPx()
                    drawRoundRect(
                        color = Color.White.copy(alpha = 0.45f * e),
                        topLeft = Offset(w / 2f, w / 2f),
                        size = Size(size.width - w, size.height - w),
                        cornerRadius = CornerRadius(size.height / 2f),
                        style = Stroke(w),
                    )
                },
            )
        } else {
            // Pill fallback sudah digambar di lapisan track (di bawah label).
            pillBase
        }
        // 3) Tepi kaca: rim ambient, bayangan luar, inner shadow tipis, border putih transparan.
        //    Digambar sebelum pill supaya pill yang ditarik tampil di atas rim.
        if (drawRim) SegmentedRim(Modifier.matchParentSize(), shape, light, glassOn)

        Box(pill.size(slot, SELECTOR_HEIGHT - SELECTOR_INSET * 2))

        // 4) Hanya label terpilih yang digambar tajam di atas pill (yang lain sudah ada di track);
        //    memudar sebagian selagi kaca menyala supaya refraksi label di bawah pill terlihat.
        Row(
            Modifier
                .matchParentSize()
                .pointerInput(slotPx, isLtr, reduceAnimation, enabled, draggable, dragOnlyFromPill, edgeScroll, viewportPx) {
                    if (!enabled || !draggable) return@pointerInput
                    val dir = if (isLtr) 1f else -1f
                    val maxIndex = (modes.size - 1).toFloat()
                    val tracker = VelocityTracker()
                    var origin = 0f
                    var travelled = 0f
                    var lastIndex = 0
                    // Posisi jari di dalam viewport (bukan di dalam track yang bergeser).
                    var fingerView = 0f
                    var autoJob: Job? = null
                    val maxSpeedPx = 520.dp.toPx()

                    fun applyTarget() {
                        val target = rubberBand(origin + travelled, maxIndex)
                        val idx = target.roundToInt().coerceIn(0, modes.lastIndex)
                        if (idx != lastIndex) {
                            lastIndex = idx
                            dragIndex = idx
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        }
                        scope.launch {
                            if (reduceAnimation) {
                                position.snapTo(target)
                            } else {
                                // Spring kaku mengejar jari: pill sedikit tertinggal
                                // (liquid) dan velocity-nya memicu regangan.
                                position.animateTo(
                                    target,
                                    spring(
                                        dampingRatio = 0.9f,
                                        stiffness = 1000f,
                                        visibilityThreshold = 0.001f,
                                    ),
                                )
                            }
                        }
                    }

                    // -1..1: seberapa dalam jari masuk zona tepi viewport (negatif = tepi kiri).
                    fun edgePush(): Float {
                        if (edgeScroll == null || !isLtr || viewportPx <= 0f) return 0f
                        val edge = minOf(slotPx * 0.9f, viewportPx * 0.25f)
                        if (edge <= 0f) return 0f
                        return when {
                            fingerView < edge -> -((edge - fingerView) / edge).coerceIn(0f, 1f)
                            fingerView > viewportPx - edge ->
                                ((fingerView - (viewportPx - edge)) / edge).coerceIn(0f, 1f)
                            else -> 0f
                        }
                    }

                    fun stopAuto() {
                        autoJob?.cancel()
                        autoJob = null
                    }

                    // Menggeser track sendiri selama jari berada di zona tepi. Pill ikut maju
                    // sebesar jarak yang digeser, jadi tetap di bawah jari walau jarinya diam.
                    fun startAuto() {
                        val sc = edgeScroll ?: return
                        if (autoJob?.isActive == true) return
                        autoJob = scope.launch {
                            var last = withFrameNanos { it }
                            while (isActive) {
                                val now = withFrameNanos { it }
                                val dt = ((now - last) / 1e9f).coerceAtMost(0.05f)
                                last = now
                                val push = edgePush()
                                if (push == 0f) break
                                val consumed = sc.scrollBy(push * maxSpeedPx * dt)
                                // Track sudah mentok: tidak ada lagi yang bisa digeser.
                                if (consumed == 0f) break
                                travelled += consumed / slotPx
                                applyTarget()
                            }
                            autoJob = null
                        }
                    }

                    fun settleTo(idx: Int) {
                        scope.launch {
                            if (reduceAnimation) {
                                position.snapTo(idx.toFloat())
                            } else {
                                // Velocity dari drag ikut terbawa, jadi pill
                                // "dilempar" lalu overshoot sebelum diam.
                                position.animateTo(
                                    idx.toFloat(),
                                    spring(
                                        dampingRatio = 0.62f,
                                        stiffness = Spring.StiffnessMediumLow,
                                        visibilityThreshold = 0.001f,
                                    ),
                                )
                            }
                        }
                    }

                    detectPillDragGestures(
                        startAllowed = { p ->
                            if (!dragOnlyFromPill) {
                                true
                            } else {
                                val centre = (position.value + 0.5f) * slotPx
                                val centreX = if (isLtr) {
                                    SELECTOR_INSET.toPx() + centre
                                } else {
                                    size.width - SELECTOR_INSET.toPx() - centre
                                }
                                abs(p.x - centreX) <= slotPx / 2f
                            }
                        },
                        onDragStart = {
                            tracker.resetTracking()
                            stopAuto()
                            fingerView = it.x - (edgeScroll?.value ?: 0)
                            origin = position.value
                            travelled = 0f
                            lastIndex = position.value.roundToInt().coerceIn(0, modes.lastIndex)
                            dragIndex = lastIndex
                            isDragging = true
                        },
                        onHorizontalDrag = { change, dragAmount ->
                            change.consume()
                            tracker.addPosition(change.uptimeMillis, change.position)
                            travelled += dragAmount * dir / slotPx
                            fingerView = change.position.x - (edgeScroll?.value ?: 0)
                            applyTarget()
                            // Jari mentok di tepi viewport: track menggeser sendiri dan pill ikut.
                            if (edgePush() != 0f) startAuto()
                        },
                        onDragEnd = {
                            stopAuto()
                            val v = tracker.calculateVelocity().x * dir / slotPx
                            val idx = (position.value + v * 0.12f)
                                .roundToInt()
                                .coerceIn(0, modes.lastIndex)
                            isDragging = false
                            if (idx != modes.indexOf(currentSelected)) {
                                dragIndex = idx
                                currentOnSelected(modes[idx])
                            } else {
                                dragIndex = -1
                            }
                            settleTo(idx)
                        },
                        onDragCancel = {
                            stopAuto()
                            isDragging = false
                            dragIndex = -1
                            settleTo(modes.indexOf(currentSelected).coerceAtLeast(0))
                        },
                    )
                }
                .padding(SELECTOR_INSET)
                // Dengan glass, salinan label di lensa sudah lengkap; lapisan ini hanya
                // menangani klik dan semantik (tidak digambar). Tanpa glass tetap tampil.
                .graphicsLayer {
                    alpha = 0f
                },
        ) {
            modes.forEachIndexed { index, mode ->
                val selected = index == (if (dragIndex >= 0) dragIndex else selectedIndex)
                val color by animateColorAsState(
                    targetValue = if (selected) labelColor else labelColor.copy(alpha = 0f),
                    animationSpec = if (reduceAnimation) snap() else tween(180),
                    label = "themeLabelColor",
                )
                val pressed by pressedStates[index]
                val labelScale = animateFloatAsState(
                    targetValue = if (pressed && !reduceAnimation) 0.94f else 1f,
                    animationSpec = if (reduceAnimation) snap() else spring(0.75f, 700f),
                    label = "themeLabelScale",
                )
                SegmentBox(
                    Modifier
                        .weight(1f)
                        .clip(shape)
                        .selectable(
                            selected = selected,
                            enabled = enabled,
                            interactionSource = sources[index],
                            indication = null,
                            role = Role.RadioButton,
                            onClick = {
                                if (mode != currentSelected) {
                                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    currentOnSelected(mode)
                                }
                            },
                        ),
                ) {
                    Box(
                        Modifier.graphicsLayer {
                            val s = labelScale.value
                            scaleX = s
                            scaleY = s
                        },
                    ) {
                        content(index, color)
                    }
                }
            }
        }

    }
}

/**
 * [LiquidSegmentedControl] untuk segmen yang tidak muat sama lebar: tiap segmen
 * selebar [minSegmentWidth] dan track-nya bisa digeser. Selama ada ruang, sama
 * persis dengan versi biasa. Saat menggeser, pill tidak bisa di-drag (drag
 * dipakai untuk scroll); pindah segmen lewat tap, dan segmen terpilih dijaga
 * tetap di tengah.
 */
@Composable
fun LiquidScrollSegmentedControl(
    count: Int,
    selection: Int,
    onSelect: (Int) -> Unit,
    minSegmentWidth: Dp,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable (index: Int, color: Color) -> Unit,
) {
    val density = LocalDensity.current
    val reduceAnimation by AppSettings.reduceAnimation.collectAsStateWithLifecycle()
    val scroll = rememberScrollState()
    val placed = remember { booleanArrayOf(false) }
    val reduceDynamicBlur by AppSettings.reduceDynamicBlur.collectAsStateWithLifecycle()
    val glassOn = isGlassSupported() && !reduceDynamicBlur && LocalLiquidGlassEnabled.current
    val light = MaterialTheme.colorScheme.surface.luminance() > 0.5f
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val natural = minSegmentWidth * count + SELECTOR_INSET * 2
        if (natural <= maxWidth) {
            LiquidSegmentedControl(
                count = count,
                selection = selection,
                onSelect = onSelect,
                enabled = enabled,
                content = content,
            )
            return@BoxWithConstraints
        }
        val segmentPx = with(density) { minSegmentWidth.toPx() }
        val insetPx = with(density) { SELECTOR_INSET.toPx() }
        val viewportPx = with(density) { maxWidth.toPx() }
        LaunchedEffect(selection, segmentPx, viewportPx, reduceAnimation) {
            val maxScroll = (segmentPx * count + insetPx * 2f - viewportPx).coerceAtLeast(0f)
            val target = (insetPx + segmentPx * (selection + 0.5f) - viewportPx / 2f)
                .coerceIn(0f, maxScroll)
                .roundToInt()
            if (placed[0] && !reduceAnimation) scroll.animateScrollTo(target) else scroll.scrollTo(target)
            placed[0] = true
        }
        // Rim di viewport yang diam, digambar di bawah track supaya pill tampil di atasnya.
        SegmentedRim(Modifier.matchParentSize(), RoundedCornerShape(percent = 50), light, glassOn)
        Box(Modifier.clip(RoundedCornerShape(percent = 50)).horizontalScroll(scroll)) {
            LiquidSegmentedControl(
                count = count,
                selection = selection,
                onSelect = onSelect,
                modifier = Modifier.width(natural),
                enabled = enabled,
                dragOnlyFromPill = true,
                edgeScroll = scroll,
                viewportPx = viewportPx,
                drawRim = false,
                content = content,
            )
        }
    }
}

@Composable
private fun SegmentBox(modifier: Modifier, content: @Composable () -> Unit) {
    Box(modifier.fillMaxHeight(), contentAlignment = Alignment.Center) { content() }
}

/**
 * Rim track: tepi kaca (atau border biasa tanpa glass). Dipisah supaya track yang
 * bisa digeser menggambarnya di viewport yang diam, bukan ikut bergeser bersama track.
 */
@Composable
private fun SegmentedRim(
    modifier: Modifier,
    shape: RoundedCornerShape,
    light: Boolean,
    glassOn: Boolean,
) {
    if (glassOn) {
        Box(
            modifier
                .drawBackdrop(
                    backdrop = emptyBackdrop(),
                    shape = { shape },
                    effects = {},
                    highlight = { Highlight.Ambient },
                    shadow = {
                        Shadow(
                            radius = 10.dp,
                            color = Color.Black.copy(alpha = if (light) 0.06f else 0.25f),
                        )
                    },
                    innerShadow = {
                        InnerShadow(
                            radius = 6.dp,
                            offset = DpOffset(0.dp, 2.dp),
                            color = Color.Black.copy(alpha = if (light) 0.14f else 0.35f),
                            alpha = 0.6f,
                        )
                    },
                    onDrawFront = {
                        val w = 0.75f.dp.toPx()
                        drawRoundRect(
                            color = Color.White.copy(alpha = if (light) 0.55f else 0.16f),
                            topLeft = Offset(w / 2f, w / 2f),
                            size = Size(size.width - w, size.height - w),
                            cornerRadius = CornerRadius((size.height - w) / 2f),
                            style = Stroke(w),
                        )
                    },
                ),
        )
    } else {
        Box(modifier.border(GLASS_EDGE_WIDTH, GLASS_EDGE_COLOR, shape))
    }
}

@Composable
private fun SegmentContent(
    mode: ThemeMode,
    color: Color,
    style: androidx.compose.ui.text.TextStyle,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ThemeGlyph(mode, color)
        Text(
            text = stringResource(
                when (mode) {
                    ThemeMode.SYSTEM -> R.string.system
                    ThemeMode.LIGHT -> R.string.light
                    ThemeMode.DARK -> R.string.dark
                },
            ),
            style = style,
            color = color,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Clip,
        )
    }
}

/** Ikon garis minimalis monitor / sun / moon pada grid 24 unit (~18dp), tanpa dependency ikon. */
@Composable
private fun ThemeGlyph(mode: ThemeMode, color: Color) {
    Canvas(Modifier.size(18.dp)) {
        val u = size.minDimension / 24f
        val sw = 1.8f * u
        val stroke = Stroke(width = sw, cap = StrokeCap.Round, join = StrokeJoin.Round)
        when (mode) {
            ThemeMode.SYSTEM -> {
                drawRoundRect(
                    color = color,
                    topLeft = Offset(2f * u, 3f * u),
                    size = Size(20f * u, 14f * u),
                    cornerRadius = CornerRadius(2f * u),
                    style = stroke,
                )
                drawLine(color, Offset(12f * u, 17f * u), Offset(12f * u, 21f * u), sw, StrokeCap.Round)
                drawLine(color, Offset(8f * u, 21f * u), Offset(16f * u, 21f * u), sw, StrokeCap.Round)
            }

            ThemeMode.LIGHT -> {
                val c = Offset(12f * u, 12f * u)
                drawCircle(color, radius = 4f * u, center = c, style = stroke)
                for (i in 0 until 8) {
                    val a = i * Math.PI / 4.0
                    val dx = cos(a).toFloat()
                    val dy = sin(a).toFloat()
                    drawLine(
                        color,
                        Offset(c.x + dx * 7.6f * u, c.y + dy * 7.6f * u),
                        Offset(c.x + dx * 9.8f * u, c.y + dy * 9.8f * u),
                        sw,
                        StrokeCap.Round,
                    )
                }
            }

            ThemeMode.DARK -> {
                val outer = Path().apply {
                    addOval(Rect(Offset(4f * u, 4f * u), Size(16f * u, 16f * u)))
                }
                val cutter = Path().apply {
                    addOval(Rect(Offset(9.7f * u, 1.7f * u), Size(13.6f * u, 13.6f * u)))
                }
                drawPath(Path.combine(PathOperation.Difference, outer, cutter), color, style = stroke)
            }
        }
    }
}

/**
 * Seperti detectHorizontalDragGestures, tapi gesture yang tidak lolos [startAllowed]
 * tidak disentuh sama sekali (tidak di-consume), jadi parent yang bisa scroll tetap
 * menggeser track saat sentuhan dimulai di luar pill.
 */
private suspend fun PointerInputScope.detectPillDragGestures(
    startAllowed: (Offset) -> Boolean,
    onDragStart: (Offset) -> Unit,
    onDragEnd: () -> Unit,
    onDragCancel: () -> Unit,
    onHorizontalDrag: (change: PointerInputChange, dragAmount: Float) -> Unit,
) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        if (!startAllowed(down.position)) return@awaitEachGesture
        var overSlop = 0f
        val first = awaitHorizontalTouchSlopOrCancellation(down.id) { change, over ->
            change.consume()
            overSlop = over
        } ?: return@awaitEachGesture
        onDragStart(first.position)
        onHorizontalDrag(first, overSlop)
        val completed = horizontalDrag(first.id) { change ->
            onHorizontalDrag(change, change.positionChange().x)
            change.consume()
        }
        if (completed) onDragEnd() else onDragCancel()
    }
}

/** Tarikan karet di tepi: melewati batas hanya bergeser sedikit (maks ~0.125 segmen). */
private fun rubberBand(x: Float, maxIndex: Float): Float {
    val over = when {
        x < 0f -> -x
        x > maxIndex -> x - maxIndex
        else -> return x
    }
    val eased = over * 0.25f / (1f + 2f * over)
    return if (x < 0f) -eased else maxIndex + eased
}

/**
 * Persegi pill pada frame ini (pop + regangan mengikuti kecepatan), dalam koordinat track.
 * Dipakai oleh lubang di track dan oleh isi pill, supaya keduanya selalu persis sama.
 */
private fun liquidPillRect(
    size: Size,
    slotPx: Float,
    insetPx: Float,
    position: Float,
    velocity: Float,
    press: Float,
    isLtr: Boolean,
): Rect {
    val stretch = (abs(velocity) / 8f).coerceIn(0f, 1f)
    val pop = 1f + 0.05f * press
    val w = slotPx * pop * (1f + 0.16f * stretch)
    val h = (size.height - insetPx * 2f) * pop * (1f - 0.07f * stretch)
    val left = if (isLtr) {
        insetPx + position * slotPx
    } else {
        size.width - insetPx - (position + 1f) * slotPx
    }
    val cx = left + slotPx / 2f
    val cy = size.height / 2f
    return Rect(cx - w / 2f, cy - h / 2f, cx + w / 2f, cy + h / 2f)
}

private val SELECTOR_HEIGHT = 50.dp
private val SELECTOR_INSET = 4.dp
private val TRACK_FILL = Color(0xFF767680)
private val FALLBACK_DARK_PILL = Color(0xFF636366)
private const val REST_GLASS = 0.35f