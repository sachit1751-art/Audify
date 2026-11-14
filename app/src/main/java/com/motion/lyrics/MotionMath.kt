package com.motion.lyrics

import kotlin.math.exp

internal object MotionTuning {
    const val RiseCompleteAt = 0.85f
    const val LetterRiseShare = 0.55f
    const val LetterRiseLeadChars = 0.5f
    const val LetterRiseSpanChars = 2.5f
    const val LetterSwellLeadChars = 0.6f
    const val LetterSwellSpanChars = 3.4f
    const val SwellAttackFraction = 0.3f
    const val SwellReleaseFraction = 0.55f
    const val RiseFollowSec = 0.16f
    const val SwellFollowSec = 0.22f
    const val SwellTailCharsPerSec = 5f
    const val BloomFollowSec = 0.18f
    const val RiseDistanceEm = 0.14f
    const val SwellScale = 0.05f
    const val BloomStrength = 1.6f
    const val BloomRadiusEm = 0.45f
    const val BloomLagChars = 1.2f
    const val BloomSpanChars = 7f
    const val CharWidthEm = 0.55f
    const val NearGapMs = 80L
    const val WordReachFraction = 0.86f
    const val SpaceShare = 0.15f
    const val MaxSpaceShareMs = 150f
    const val MaxTravelMs = 450L
    const val HoldMinMs = 450f
    const val HoldRangeMs = 700f
    const val GlowSpanChars = 5f
    const val SweepFeatherEm = 0.18f
    const val GlowRadiusEm = 0.40f
    // Glow tidak boleh lebih lebar dari area bleed agar tepinya tidak terpotong.
    const val GlowMaxBleedShare = 0.8f
    const val GlowAlphaScale = 0.30f
    const val ParticleCount = 3
    const val ParticleSpeed = 0.55f
    const val ParticleRiseEm = 1.0f
    const val BleedXDp = 36
    const val BleedYDp = 36
    const val NoLigatures = "liga 0, clig 0"
}

internal fun smoothstep(t: Float): Float {
    val x = t.coerceIn(0f, 1f)
    return x * x * (3f - 2f * x)
}

/** Critically damped follow (solusi eksak, stabil untuk dt apa pun). */
internal fun dampedStep(
    values: FloatArray,
    vels: FloatArray,
    i: Int,
    target: Float,
    followSec: Float,
    dt: Float
) {
    val w = 4f / followSec.coerceAtLeast(0.02f)
    val x0 = values[i] - target
    val v0 = vels[i]
    val e = exp(-w * dt)
    val k = v0 + w * x0
    values[i] = target + (x0 + k * dt) * e
    vels[i] = (v0 - w * k * dt) * e
}