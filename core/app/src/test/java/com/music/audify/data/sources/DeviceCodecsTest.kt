package com.music.audify.data.sources

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceCodecsTest {
    @Test
    fun positiveMedia3EvidenceEnablesAtmos() {
        assertTrue(hasDolbyAtmosDecoderEvidence(listOf("c2.dolby.eac3.decoder"), null))
    }

    @Test
    fun platformEvidenceIsUsedOnlyWhenMedia3QueryFailed() {
        assertTrue(hasDolbyAtmosDecoderEvidence(null, listOf("vendor.eac3.decoder")))
        assertFalse(hasDolbyAtmosDecoderEvidence(emptyList(), listOf("vendor.eac3.decoder")))
    }

    @Test
    fun absentOrEmptyEvidenceNeverAssumesAtmosSupport() {
        assertFalse(hasDolbyAtmosDecoderEvidence(null, null))
        assertFalse(hasDolbyAtmosDecoderEvidence(null, emptyList()))
        assertFalse(hasDolbyAtmosDecoderEvidence(emptyList(), null))
    }
}
