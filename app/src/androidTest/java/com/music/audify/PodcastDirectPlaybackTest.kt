// Copyright (c) 2026 Sachit. All rights reserved.
// Project: AUDIFY
package com.music.audify

import android.content.Context
import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.TransferListener
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.music.audify.data.model.QueueTier
import com.music.audify.playback.PlayableMedia
import com.music.audify.playback.PodcastEpisodeRef
import com.music.audify.playback.PodcastNetworkPolicy
import com.music.audify.playback.PodcastRoutingDataSource
import com.music.audify.playback.QueueEntryMetadata
import com.music.audify.playback.toMediaItem
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.Dns
import okhttp3.Request
import okhttp3.tls.HandshakeCertificates
import okhttp3.tls.HeldCertificate
import okio.Buffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.IOException
import java.net.InetAddress
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

@RunWith(AndroidJUnit4::class)
class PodcastDirectPlaybackTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun media3PlaysPodcastEnclosureDirectlyWithoutOpeningMusicResolver() {
        val fixture = httpsFixture()
        fixture.server.use { server ->
            server.enqueue(
                MockResponse.Builder()
                    .addHeader("Content-Type", "audio/wav")
                    .body(Buffer().write(silentWav()))
                    .build(),
            )
            val musicOpened = AtomicInteger()
            val routingFactory = PodcastRoutingDataSource.Factory(
                musicFactory = DataSource.Factory { RejectingDataSource(musicOpened) },
                podcastFactory = OkHttpDataSource.Factory(fixture.client),
            )
            val item = PlayableMedia.Podcast(
                episode = PodcastEpisodeRef(
                    feedId = "test-feed",
                    episodeId = "test-episode",
                    enclosureUri = Uri.parse(fixture.url("/episode.wav").toString()),
                    mimeType = "audio/wav",
                    showTitle = "Test show",
                    episodeTitle = "Test episode",
                    description = null,
                    artworkUri = null,
                    durationMs = 500L,
                ),
            ).toMediaItem(QueueEntryMetadata("test-occurrence", QueueTier.USER_QUEUE))

            val ended = CountDownLatch(1)
            val playbackError = arrayOfNulls<Throwable>(1)
            var player: ExoPlayer? = null
            InstrumentationRegistry.getInstrumentation().runOnMainSync {
                player = ExoPlayer.Builder(context)
                    .setMediaSourceFactory(DefaultMediaSourceFactory(routingFactory))
                    .build()
                    .also { exoPlayer ->
                        exoPlayer.addListener(object : Player.Listener {
                            override fun onPlaybackStateChanged(playbackState: Int) {
                                if (playbackState == Player.STATE_ENDED) ended.countDown()
                            }

                            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                                playbackError[0] = error
                                ended.countDown()
                            }
                        })
                        exoPlayer.setMediaItem(item)
                        exoPlayer.prepare()
                        exoPlayer.play()
                    }
            }

            val completed = ended.await(15, TimeUnit.SECONDS)
            InstrumentationRegistry.getInstrumentation().runOnMainSync { player?.release() }

            assertTrue("Media3 did not finish the local HTTPS WAV fixture", completed)
            assertNull("Podcast playback failed: ${playbackError[0]}", playbackError[0])
            assertEquals("The podcast item must bypass the music resolver", 0, musicOpened.get())
            assertEquals(1, server.requestCount)
        }
    }

    @Test
    fun redirectToLoopbackIsRejectedBeforeTheSecondRequest() {
        val fixture = httpsFixture()
        fixture.server.use { server ->
            server.enqueue(
                MockResponse.Builder()
                    .code(302)
                    .addHeader("Location", "https://127.0.0.1:${server.port}/private.wav")
                    .build(),
            )

            val failure = runCatching {
                fixture.client.newCall(Request.Builder().url(fixture.url("/redirect.wav")).build()).execute().use { response ->
                    response.close()
                }
            }.exceptionOrNull()

            assertNotNull("A redirect to a loopback address must be rejected", failure)
            assertTrue(failure is IOException)
            assertEquals("The private redirect target must never receive a request", 1, server.requestCount)
        }
    }

    private data class HttpsFixture(
        val server: MockWebServer,
        val client: okhttp3.OkHttpClient,
    ) {
        fun url(path: String) = server.url(path).newBuilder()
            .scheme("https")
            .host("podcast.test")
            .build()
    }

    private fun httpsFixture(): HttpsFixture {
        val certificate = HeldCertificate.Builder()
            .addSubjectAlternativeName("podcast.test")
            .build()
        val serverTls = HandshakeCertificates.Builder()
            .heldCertificate(certificate)
            .build()
        val clientTls = HandshakeCertificates.Builder()
            .addTrustedCertificate(certificate.certificate)
            .build()
        val server = MockWebServer().apply {
            useHttps(serverTls.sslSocketFactory())
            start()
        }
        val dns = Dns { hostname ->
            if (hostname == "podcast.test") {
                listOf(InetAddress.getByName("127.0.0.1"))
            } else {
                PodcastNetworkPolicy.resolvePublicAddresses(hostname)
            }
        }
        val client = PodcastNetworkPolicy.createClient(
            dns = dns,
            sslSocketFactory = clientTls.sslSocketFactory(),
            trustManager = clientTls.trustManager,
        )
        return HttpsFixture(server, client)
    }

    private fun silentWav(durationMs: Int = 500): ByteArray {
        val sampleRate = 8_000
        val sampleCount = sampleRate * durationMs / 1_000
        val dataBytes = sampleCount * 2
        return ByteBuffer.allocate(44 + dataBytes).order(ByteOrder.LITTLE_ENDIAN).apply {
            put("RIFF".toByteArray(Charsets.US_ASCII))
            putInt(36 + dataBytes)
            put("WAVE".toByteArray(Charsets.US_ASCII))
            put("fmt ".toByteArray(Charsets.US_ASCII))
            putInt(16)
            putShort(1)
            putShort(1)
            putInt(sampleRate)
            putInt(sampleRate * 2)
            putShort(2)
            putShort(16)
            put("data".toByteArray(Charsets.US_ASCII))
            putInt(dataBytes)
            repeat(sampleCount) { putShort(0) }
        }.array()
    }

    private class RejectingDataSource(private val opens: AtomicInteger) : DataSource {
        override fun addTransferListener(transferListener: TransferListener) = Unit
        override fun open(dataSpec: DataSpec): Long {
            opens.incrementAndGet()
            throw IOException("Music resolver must not receive a podcast enclosure")
        }
        override fun read(buffer: ByteArray, offset: Int, length: Int): Int = C.RESULT_END_OF_INPUT
        override fun getUri(): Uri? = null
        override fun getResponseHeaders(): Map<String, List<String>> = emptyMap()
        override fun close() = Unit
    }
}
