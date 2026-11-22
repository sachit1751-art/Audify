package com.music.audify.playback

import androidx.media3.common.C
import androidx.media3.common.Player

/** Pure lifecycle gate for advertising this player's session to system effects. */
internal fun shouldAdvertiseAudioEffectSession(
    playWhenReady: Boolean,
    playbackState: Int,
    sessionId: Int,
): Boolean = playWhenReady &&
    (playbackState == Player.STATE_BUFFERING || playbackState == Player.STATE_READY) &&
    sessionId != C.AUDIO_SESSION_ID_UNSET && sessionId > 0
