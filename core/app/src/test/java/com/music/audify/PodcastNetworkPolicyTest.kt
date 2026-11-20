package com.music.audify

import com.music.audify.playback.PodcastNetworkPolicy
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.InetAddress

class PodcastNetworkPolicyTest {
    @Test fun privateAndLoopbackIpv4AreRejected() {
        listOf("127.0.0.1", "10.0.0.2", "172.16.0.1", "192.168.1.1", "169.254.1.1", "100.64.0.1")
            .forEach { assertFalse("$it must be blocked", PodcastNetworkPolicy.isPublicAddress(InetAddress.getByName(it))) }
    }

    @Test fun publicIpv4IsAccepted() {
        assertTrue(PodcastNetworkPolicy.isPublicAddress(InetAddress.getByName("8.8.8.8")))
    }

    @Test fun localIpv6IsRejectedAndGlobalIpv6IsAccepted() {
        assertFalse(PodcastNetworkPolicy.isPublicAddress(InetAddress.getByName("::1")))
        assertTrue(PodcastNetworkPolicy.isPublicAddress(InetAddress.getByName("2606:4700:4700::1111")))
    }
}
