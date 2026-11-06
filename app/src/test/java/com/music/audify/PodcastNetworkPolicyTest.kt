// Copyright (c) 2026 Sachit. All rights reserved.
// Project: AUDIFY
package com.music.audify

import com.music.audify.playback.PodcastNetworkPolicy
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.net.InetAddress

class PodcastNetworkPolicyTest {

    @Test
    fun `only globally routable IPv4 and IPv6 addresses pass`() {
        assertTrue(PodcastNetworkPolicy.isPublicAddress(InetAddress.getByName("8.8.8.8")))
        assertTrue(PodcastNetworkPolicy.isPublicAddress(InetAddress.getByName("2606:4700:4700::1111")))
        listOf(
            "127.0.0.1",
            "10.0.0.8",
            "172.16.0.2",
            "192.168.1.1",
            "169.254.10.20",
            "100.64.0.1",
            "198.18.0.1",
            "192.88.99.1",
            "203.0.113.5",
            "224.0.0.1",
            "::1",
            "fc00::1",
            "fe80::1",
            "2001:db8::1",
            "2001:1::1",
            "2001:2::1",
            "2001:20::1",
            "2002::1",
            "3fff::1",
        ).forEach { address ->
            assertFalse("$address must be rejected", PodcastNetworkPolicy.isPublicAddress(InetAddress.getByName(address)))
        }
    }

    @Test
    fun `only credential-free HTTPS public hostnames pass URL validation`() {
        PodcastNetworkPolicy.validateUrl("https://cdn.example.org/audio.mp3".toHttpUrl())
        listOf(
            "http://cdn.example.org/audio.mp3",
            "https://localhost/audio.mp3",
            "https://speaker.local/audio.mp3",
            "https://nas.internal/audio.mp3",
            "https://user:pass@cdn.example.org/audio.mp3",
        ).forEach { url ->
            try {
                PodcastNetworkPolicy.validateUrl(url.toHttpUrl())
                throw AssertionError("URL must be rejected: $url")
            } catch (_: IOException) {
                // Expected: an enclosure cannot downgrade, target local hosts, or embed credentials.
            }
        }
    }
}
