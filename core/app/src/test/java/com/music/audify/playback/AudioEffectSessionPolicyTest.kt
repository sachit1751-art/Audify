package com.music.audify.playback

import androidx.media3.common.C
import androidx.media3.common.Player
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AudioEffectSessionPolicyTest {

    @Test
    fun advertisesAValidSessionWhilePreparingOrReadyToPlay() {
        assertTrue(shouldAdvertiseAudioEffectSession(true, Player.STATE_BUFFERING, 7))
        assertTrue(shouldAdvertiseAudioEffectSession(true, Player.STATE_READY, 7))
    }

    @Test
    fun closesSessionWhenPausedOrOutsidePlayableStates() {
        assertFalse(shouldAdvertiseAudioEffectSession(false, Player.STATE_READY, 7))
        assertFalse(shouldAdvertiseAudioEffectSession(true, Player.STATE_IDLE, 7))
        assertFalse(shouldAdvertiseAudioEffectSession(true, Player.STATE_ENDED, 7))
    }

    @Test
    fun refusesUnsetOrInvalidSessionIds() {
        assertFalse(shouldAdvertiseAudioEffectSession(true, Player.STATE_READY, C.AUDIO_SESSION_ID_UNSET))
        assertFalse(shouldAdvertiseAudioEffectSession(true, Player.STATE_READY, 0))
        assertFalse(shouldAdvertiseAudioEffectSession(true, Player.STATE_READY, -2))
    }
}
