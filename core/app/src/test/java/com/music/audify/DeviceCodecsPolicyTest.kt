package com.music.audify

import com.music.audify.data.sources.dolbyAtmosDecoderAvailable
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceCodecsPolicyTest {

    @Test
    fun `positive Media3 renderer evidence enables Dolby Atmos`() {
        assertTrue(dolbyAtmosDecoderAvailable(listOf("c2.dolby.eac3.decoder"), null))
    }

    @Test
    fun `empty Media3 renderer evidence remains authoritative over platform list`() {
        assertFalse(dolbyAtmosDecoderAvailable(emptyList(), listOf("vendor.eac3.decoder")))
    }

    @Test
    fun `platform decoder list is fallback only when Media3 query fails`() {
        assertTrue(dolbyAtmosDecoderAvailable(null, listOf("vendor.eac3.decoder")))
        assertFalse(dolbyAtmosDecoderAvailable(null, emptyList()))
    }

    @Test
    fun `failed decoder probes do not claim unsupported capability`() {
        assertFalse(dolbyAtmosDecoderAvailable(null, null))
    }
}
