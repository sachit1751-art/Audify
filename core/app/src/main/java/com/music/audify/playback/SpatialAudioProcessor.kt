package com.music.audify.playback

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import com.music.audify.playback.audio.AudioBlock
import com.music.audify.playback.audio.FloatAudioProcessor
import com.music.audify.playback.audio.PcmBoundary
import java.nio.ByteOrder
import kotlin.math.roundToInt

/**
 * Cheap stand-in for "spatial audio": widens the mid/side image and mixes in
 * a short, low-passed cross-feed between channels — the same trick most
 * consumer virtual-surround plugins use. O(1) per sample, no FFT or
 * convolution, so it costs nothing worth measuring on a phone CPU.
 *
 * Exists because the platform [android.media.audiofx.Virtualizer] produced no
 * audible difference on the reference device — likely swallowed by the OEM's
 * own audio effect chain — so this runs inside ExoPlayer's own audio
 * processor pipeline instead, where nothing else can intercept it.
 */
@UnstableApi
class SpatialAudioProcessor : BaseAudioProcessor() {

    @Volatile
    var enabled: Boolean = false

    /**
     * Wet/dry blend: 0 = untouched, 1 = fully widened. Ramps toward [enabled]
     * over [RAMP_MS] so a mid-song toggle doesn't click. Audio thread only.
     */
    private var mix = 0f

    /** After a flush the blend jumps straight to its target instead of ramping. */
    private var snapMix = true

    /** How much wider the stereo image gets. 1.0 = untouched. */
    private val widthGain = 2.5f

    /** Makeup attenuation after widening, so the wider side energy doesn't clip. */
    private val outputGain = 0.82f

    /** How much of the delayed, low-passed opposite channel gets mixed back in. */
    private val crossfeedGain = 0.2f

    /** One-pole lowpass factor applied to the cross-fed signal — dulls it, like a far ear would. */
    private val lowpassCoeff = 0.3f

    private var sampleRate: Int = 0
    private var channelCount: Int = 0

    private var delayLeft = FloatArray(0)
    private var delayRight = FloatArray(0)
    private var delayIndex = 0
    private var lowpassLeft = 0f
    private var lowpassRight = 0f

    /**
     * Configures the Float32 DSP engine for [sampleRate] and [channelCount].
     */
    fun configure(sampleRate: Int, channelCount: Int) {
        this.sampleRate = sampleRate
        this.channelCount = channelCount
        if (channelCount != 2 || sampleRate <= 0) {
            delayLeft = FloatArray(0)
            delayRight = FloatArray(0)
            delayIndex = 0
            lowpassLeft = 0f
            lowpassRight = 0f
            return
        }
        val delaySamples = (sampleRate * DELAY_MS / 1000f)
            .roundToInt()
            .coerceAtLeast(1)
        if (delayLeft.size != delaySamples) {
            delayLeft = FloatArray(delaySamples)
            delayRight = FloatArray(delaySamples)
        }
        onFlush()
    }

    /**
     * Processes interleaved Float32 audio samples in [block] in-place.
     * Preserves dynamic headroom without clamping to [-1.0f, +1.0f].
     */
    fun process(block: AudioBlock) {
        if (block.frameCount == 0) return
        if (block.channelCount != 2) return // Spatial widening only applies to stereo

        val delaySize = delayLeft.size
        if (delaySize == 0) return

        val target = if (enabled) 1f else 0f
        if (snapMix) {
            mix = target
            snapMix = false
        }
        if (mix == 0f && target == 0f) return
        val step = rampStep()

        val totalSamples = block.frameCount * 2
        var idx = 0
        var dIdx = delayIndex
        var lpL = lowpassLeft
        var lpR = lowpassRight

        while (idx < totalSamples) {
            val left = block.samples[idx]
            val right = block.samples[idx + 1]

            val mid = (left + right) * 0.5f
            val side = (left - right) * 0.5f * widthGain
            var widenedLeft = mid + side
            var widenedRight = mid - side

            val delayedRight = delayRight[dIdx]
            val delayedLeft = delayLeft[dIdx]
            lpL += lowpassCoeff * (delayedRight - lpL)
            lpR += lowpassCoeff * (delayedLeft - lpR)
            widenedLeft += lpL * crossfeedGain
            widenedRight += lpR * crossfeedGain

            delayLeft[dIdx] = left
            delayRight[dIdx] = right
            dIdx = (dIdx + 1) % delaySize

            if (mix < target) mix = minOf(target, mix + step)
            else if (mix > target) mix = maxOf(target, mix - step)
            val wetLeft = widenedLeft * outputGain
            val wetRight = widenedRight * outputGain

            // Headroom is preserved: no clamping to [-1.0f, +1.0f]
            block.samples[idx] = if (mix >= 1f) wetLeft else left + (wetLeft - left) * mix
            block.samples[idx + 1] = if (mix >= 1f) wetRight else right + (wetRight - right) * mix
            idx += 2
        }

        delayIndex = dIdx
        lowpassLeft = lpL
        lowpassRight = lpR
    }

    /**
     * Stereo 16-bit only: the widening is written in terms of a left and a
     * right sample, and there is no mid/side of a mono voice note or of a 5.1
     * mix to widen.
     *
     * Bowing out with [AudioProcessor.AudioFormat.NOT_SET] rather than an
     * [AudioProcessor.UnhandledAudioFormatException] is what keeps those
     * tracks playable at all.
     * [DefaultAudioSink][androidx.media3.exoplayer.audio.DefaultAudioSink]
     * configures every processor in its chain whether or not the effect is
     * switched on, and a throw from any
     * of them fails the whole sink — the renderer dies with
     * "MediaCodecAudioRenderer error" before a sample is written. NOT_SET
     * means "inactive for this format" and the chain routes around this
     * processor instead.
     *
     * Nothing from YouTube is anything but stereo, so this only ever showed
     * itself on files from the device: every mono or multichannel track in the
     * local library failed to play while downloads were fine.
     */
    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT || inputAudioFormat.channelCount != 2) {
            return AudioProcessor.AudioFormat.NOT_SET
        }
        configure(inputAudioFormat.sampleRate, inputAudioFormat.channelCount)
        return inputAudioFormat
    }

    override fun onFlush() {
        snapMix = true
        delayLeft.fill(0f)
        delayRight.fill(0f)
        delayIndex = 0
        lowpassLeft = 0f
        lowpassRight = 0f
    }

    override fun onReset() {
        delayLeft = FloatArray(0)
        delayRight = FloatArray(0)
        delayIndex = 0
        lowpassLeft = 0f
        lowpassRight = 0f
        channelCount = 0
        sampleRate = 0
    }

    override fun queueInput(inputBuffer: java.nio.ByteBuffer) {
        val frameCount = inputBuffer.remaining() / BYTES_PER_FRAME
        if (frameCount == 0) return
        val outputBuffer = replaceOutputBuffer(frameCount * BYTES_PER_FRAME)

        val target = if (enabled) 1f else 0f
        if (snapMix) {
            mix = target
            snapMix = false
        }
        if (mix == 0f && target == 0f) {
            outputBuffer.put(inputBuffer)
            outputBuffer.flip()
            return
        }
        val step = rampStep()

        inputBuffer.order(ByteOrder.nativeOrder())
        outputBuffer.order(ByteOrder.nativeOrder())

        val delaySize = delayLeft.size
        if (delaySize == 0) {
            outputBuffer.put(inputBuffer)
            outputBuffer.flip()
            return
        }

        var dIdx = delayIndex
        var lpL = lowpassLeft
        var lpR = lowpassRight
        val invScale = 1.0f / 32768.0f

        repeat(frameCount) {
            val left = inputBuffer.short.toFloat() * invScale
            val right = inputBuffer.short.toFloat() * invScale

            val mid = (left + right) * 0.5f
            val side = (left - right) * 0.5f * widthGain
            var widenedLeft = mid + side
            var widenedRight = mid - side

            val delayedRight = delayRight[dIdx]
            val delayedLeft = delayLeft[dIdx]
            lpL += lowpassCoeff * (delayedRight - lpL)
            lpR += lowpassCoeff * (delayedLeft - lpR)
            widenedLeft += lpL * crossfeedGain
            widenedRight += lpR * crossfeedGain

            delayLeft[dIdx] = left
            delayRight[dIdx] = right
            dIdx = (dIdx + 1) % delaySize

            if (mix < target) mix = minOf(target, mix + step)
            else if (mix > target) mix = maxOf(target, mix - step)
            val wetLeft = widenedLeft * outputGain
            val wetRight = widenedRight * outputGain
            val outLeft = if (mix >= 1f) wetLeft else left + (wetLeft - left) * mix
            val outRight = if (mix >= 1f) wetRight else right + (wetRight - right) * mix
            outputBuffer.putShort(PcmBoundary.clamp16FromFloat(outLeft))
            outputBuffer.putShort(PcmBoundary.clamp16FromFloat(outRight))
        }
        delayIndex = dIdx
        lowpassLeft = lpL
        lowpassRight = lpR

        outputBuffer.flip()
    }

    /** Per-sample [mix] increment: a full 0 to 1 swing takes [RAMP_MS]. */
    private fun rampStep(): Float =
        if (sampleRate > 0) 1000f / (sampleRate * RAMP_MS) else 1f

    private companion object {
        const val BYTES_PER_FRAME = 4 // stereo, 16-bit
        const val DELAY_MS = 15
        const val RAMP_MS = 300f
    }
}
