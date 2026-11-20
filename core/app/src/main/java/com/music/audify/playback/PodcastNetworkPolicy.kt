// Copyright (c) 2026 Sachit. All rights reserved.
// Project: AUDIFY
package com.music.audify.playback

import okhttp3.Dns
import okhttp3.OkHttpClient
import okhttp3.HttpUrl
import java.io.IOException
import java.net.Inet4Address
import java.net.InetAddress
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLSocketFactory
import javax.net.ssl.X509TrustManager

/** Network policy for untrusted RSS enclosure URLs and every HTTP redirect hop. */
object PodcastNetworkPolicy {

    private val safeDns = Dns(::resolvePublicAddresses)

    internal fun resolvePublicAddresses(hostname: String): List<InetAddress> {
        val addresses = try {
            Dns.SYSTEM.lookup(hostname)
        } catch (error: Exception) {
            throw IOException("Could not resolve podcast enclosure host", error)
        }
        if (addresses.isEmpty() || addresses.any { !isPublicAddress(it) }) {
            throw IOException("Podcast enclosure host resolves to a non-public address")
        }
        return addresses
    }

    /** Dedicated client: redirects are followed only after each hop passes DNS and URL checks. */
    val client: OkHttpClient = createClient()

    /** Internal TLS/DNS injection seam for the local HTTPS playback and redirect tests. */
    internal fun createClient(
        dns: Dns = safeDns,
        sslSocketFactory: SSLSocketFactory? = null,
        trustManager: X509TrustManager? = null,
    ): OkHttpClient {
        require((sslSocketFactory == null) == (trustManager == null)) {
            "A test TLS socket factory and trust manager must be supplied together"
        }
        return OkHttpClient.Builder()
            .dns(dns)
            .apply {
                if (sslSocketFactory != null && trustManager != null) {
                    sslSocketFactory(sslSocketFactory, trustManager)
                }
            }
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(false)
            .retryOnConnectionFailure(false)
            .addNetworkInterceptor { chain ->
                validateUrl(chain.request().url)
                chain.proceed(chain.request())
            }
            .build()
    }

    internal fun validateUrl(url: HttpUrl) {
        if (url.scheme != "https" || url.host.isBlank() ||
            url.username.isNotEmpty() || url.password.isNotEmpty()
        ) {
            throw IOException("Podcast enclosure URL is not an allowed HTTPS URL")
        }
        if (url.host.equals("localhost", ignoreCase = true) ||
            url.host.endsWith(".localhost", ignoreCase = true) ||
            url.host.endsWith(".local", ignoreCase = true) ||
            url.host.endsWith(".internal", ignoreCase = true)
        ) {
            throw IOException("Podcast enclosure host is not publicly routable")
        }
    }

    internal fun isPublicAddress(address: InetAddress): Boolean {
        if (address.isAnyLocalAddress || address.isLoopbackAddress ||
            address.isLinkLocalAddress || address.isSiteLocalAddress || address.isMulticastAddress
        ) return false

        val bytes = address.address.map { it.toInt() and 0xff }
        if (address is Inet4Address) {
            val a = bytes[0]
            val b = bytes[1]
            val c = bytes[2]
            return when {
                a == 0 || a == 10 || a == 127 || a >= 224 -> false
                a == 100 && b in 64..127 -> false // Shared address space (RFC 6598).
                a == 169 && b == 254 -> false
                a == 172 && b in 16..31 -> false
                a == 192 && (b == 0 || b == 168) -> false
                a == 192 && b == 88 && c == 99 -> false // Deprecated 6to4 relay anycast.
                a == 198 && (b in 18..19 || (b == 51 && c == 100)) -> false
                a == 203 && b == 0 && c == 113 -> false
                else -> true
            }
        }

        // IPv6 global-unicast allocation is 2000::/3. Exclude documentation and
        // protocol-assignment blocks even though they sit inside that prefix.
        val globalUnicast = (bytes[0] and 0xe0) == 0x20
        val documentation = (bytes[0] == 0x20 && bytes[1] == 0x01 &&
            bytes[2] == 0x0d && bytes[3] == 0xb8) ||
            (bytes[0] == 0x3f && bytes[1] == 0xff && (bytes[2] and 0xf0) == 0x00)
        val protocolAssignments = bytes[0] == 0x20 && bytes[1] == 0x01 &&
            (bytes[2] and 0xfe) == 0x00 // 2001:0000::/23, including Teredo and ORCHID ranges.
        val sixToFour = bytes[0] == 0x20 && bytes[1] == 0x02 // 2002::/16 embeds arbitrary IPv4 routes.
        return globalUnicast && !documentation && !protocolAssignments && !sixToFour
    }
}
