package com.music.audify.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.unit.IntSize
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * Keadaan tekan dan geser untuk thumb kaca pada toggle dan slider (mengikuti
 * DampedDragAnimation di katalog Kyant0/backdrop): nilai dikejar spring, tekan
 * membesarkan thumb 1.5x, dan kecepatan dipakai untuk memipihkan thumb.
 *
 * Sentuhan dimulai begitu jari turun ([onDragStarted]); geseran baru dihitung
 * setelah melewati touch slop. Geseran vertikal dilepas ke induk (scroll) dan
 * dianggap batal, bukan ketukan.
 */
@Stable
class DampedDragAnimation(
    private val animationScope: CoroutineScope,
    initialValue: Float,
    val valueRange: ClosedFloatingPointRange<Float>,
    visibilityThreshold: Float,
    val initialScale: Float,
    val pressedScale: Float,
    /** True: jari turun dan angkat diambil, supaya baris di sekitarnya tidak ikut mengetuk. */
    private val consumeDown: Boolean = false,
    private val onDragStarted: DampedDragAnimation.(position: Offset) -> Unit,
    private val onDragStopped: DampedDragAnimation.() -> Unit,
    private val onDrag: DampedDragAnimation.(size: IntSize, dragAmount: Offset) -> Unit,
    private val onDragCancelled: DampedDragAnimation.() -> Unit = {},
) {
    private val valueSpec = spring<Float>(dampingRatio = 1f, stiffness = 1000f, visibilityThreshold = visibilityThreshold)
    private val pressSpec = spring<Float>(dampingRatio = 1f, stiffness = 1000f, visibilityThreshold = 0.001f)
    private val scaleXSpec = spring<Float>(dampingRatio = 0.6f, stiffness = 250f, visibilityThreshold = 0.001f)
    private val scaleYSpec = spring<Float>(dampingRatio = 0.7f, stiffness = 250f, visibilityThreshold = 0.001f)

    private val valueAnim = Animatable(initialValue, visibilityThreshold)
    private val pressProgressAnim = Animatable(0f, 0.001f)
    private val scaleXAnim = Animatable(initialScale, 0.001f)
    private val scaleYAnim = Animatable(initialScale, 0.001f)

    private var pressed by mutableStateOf(false)

    /** Nilai terakhir yang diminta, langsung (tanpa menunggu coroutine-nya jalan). */
    var requestedValue: Float = initialValue
        private set

    val value: Float get() = valueAnim.value
    val targetValue: Float get() = valueAnim.targetValue
    val pressProgress: Float get() = pressProgressAnim.value
    val scaleX: Float get() = scaleXAnim.value
    val scaleY: Float get() = scaleYAnim.value

    val progress: Float
        get() {
            val span = valueRange.endInclusive - valueRange.start
            return if (span > 0f) ((value - valueRange.start) / span).coerceIn(0f, 1f) else 0f
        }

    /** Laju gerak nilai, per rentang nilai per detik (tanpa tanda). */
    val velocity: Float
        get() {
            // Membaca [value] membuat pemanggil ikut digambar ulang tiap frame spring.
            if (valueAnim.value.isNaN()) return 0f
            val span = (valueRange.endInclusive - valueRange.start).coerceAtLeast(1e-6f)
            return abs(valueAnim.velocity) / span
        }

    /** Ada yang sedang bergerak: kaca perlu dikomposisikan. Diam, thumb cukup gambar putih biasa. */
    val isActive: Boolean
        get() = pressed ||
            pressProgressAnim.isRunning ||
            scaleXAnim.isRunning ||
            scaleYAnim.isRunning ||
            valueAnim.isRunning

    val modifier: Modifier = Modifier.pointerInput(Unit) {
        val slop = viewConfiguration.touchSlop
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            if (consumeDown) down.consume()
            press()
            onDragStarted.invoke(this@DampedDragAnimation, down.position)
            var total = Offset.Zero
            var dragging = false
            var cancelled = false
            while (true) {
                val event = awaitPointerEvent()
                val change = event.changes.firstOrNull { it.id == down.id }
                if (change == null || change.isConsumed) {
                    cancelled = true
                    break
                }
                if (!change.pressed) {
                    if (consumeDown) change.consume()
                    break
                }
                val delta = change.positionChange()
                total += delta
                if (!dragging) {
                    if (abs(total.x) > slop && abs(total.x) >= abs(total.y)) {
                        dragging = true
                        onDrag.invoke(this@DampedDragAnimation, size, total)
                        change.consume()
                    } else if (abs(total.y) > slop) {
                        // Geseran vertikal: biarkan induk men-scroll.
                        cancelled = true
                        break
                    }
                } else if (delta != Offset.Zero) {
                    onDrag.invoke(this@DampedDragAnimation, size, delta)
                    change.consume()
                }
            }
            if (cancelled) {
                release()
                onDragCancelled.invoke(this@DampedDragAnimation)
            } else {
                onDragStopped.invoke(this@DampedDragAnimation)
                release()
            }
        }
    }

    fun press() {
        pressed = true
        animationScope.launch { pressProgressAnim.animateTo(1f, pressSpec) }
        animationScope.launch { scaleXAnim.animateTo(pressedScale, scaleXSpec) }
        animationScope.launch { scaleYAnim.animateTo(pressedScale, scaleYSpec) }
    }

    fun release() {
        pressed = false
        animationScope.launch { pressProgressAnim.animateTo(0f, pressSpec) }
        animationScope.launch { scaleXAnim.animateTo(initialScale, scaleXSpec) }
        animationScope.launch { scaleYAnim.animateTo(initialScale, scaleYSpec) }
    }

    /** Nilai dikejar spring, tanpa mengangkat thumb. */
    fun updateValue(value: Float) {
        val target = value.coerceIn(valueRange.start, valueRange.endInclusive)
        requestedValue = target
        animationScope.launch { valueAnim.animateTo(target, valueSpec) }
    }

    /** Thumb terangkat selama menempuh jarak ke [value], lalu turun lagi. */
    fun animateToValue(value: Float) {
        val target = value.coerceIn(valueRange.start, valueRange.endInclusive)
        requestedValue = target
        animationScope.launch {
            press()
            try {
                valueAnim.animateTo(target, valueSpec)
            } finally {
                release()
            }
        }
    }
}

/** Skala thumb: tekan ditambah pipih/memanjang mengikuti laju gerak. */
internal fun GraphicsLayerScope.dampedThumbScale(anim: DampedDragAnimation, velocityDivisor: Float) {
    scaleX = anim.scaleX
    scaleY = anim.scaleY
    val v = anim.velocity / velocityDivisor
    scaleX /= 1f - (v * 0.75f).coerceIn(-0.2f, 0.2f)
    scaleY *= 1f - (v * 0.25f).coerceIn(-0.2f, 0.2f)
}