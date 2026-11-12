// Copyright (c) 2026 Sachit. All rights reserved.
// Project: AUDIFY
package com.music.audify

import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.SystemClock
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaBrowser
import androidx.media3.session.SessionToken
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.music.audify.data.model.Song
import com.music.audify.playback.PlaybackService
import com.music.audify.playback.toMediaItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.StandardCharsets
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@OptIn(UnstableApi::class)
@RunWith(AndroidJUnit4::class)
class AndroidAutoMediaLibraryTest {

    private lateinit var mediaBrowser: MediaBrowser

    @Before
    fun setup() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        val latch = CountDownLatch(1)
        var browserInstance: MediaBrowser? = null

        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val future = MediaBrowser.Builder(context, token).buildAsync()
            future.addListener(
                {
                    browserInstance = future.get()
                    latch.countDown()
                },
                { command -> command.run() },
            )
        }

        assertTrue("MediaBrowser connection timed out", latch.await(10, TimeUnit.SECONDS))
        assertNotNull("MediaBrowser instance must not be null", browserInstance)
        mediaBrowser = browserInstance!!
    }

    @After
    fun tearDown() {
        if (::mediaBrowser.isInitialized) {
            InstrumentationRegistry.getInstrumentation().runOnMainSync {
                mediaBrowser.release()
            }
        }
    }

    @Test
    fun testGetLibraryRoot() = runBlocking {
        val rootResult = withContext(Dispatchers.Main) {
            mediaBrowser.getLibraryRoot(/* params = */ null).get(10, TimeUnit.SECONDS)
        }
        assertNotNull("Library root result must not be null", rootResult)
        assertNotNull("Library root item must not be null", rootResult.value)
        assertEquals("root", rootResult.value?.mediaId)
        assertTrue("Root must be browsable", rootResult.value?.mediaMetadata?.isBrowsable == true)
    }

    @Test
    fun transportControlsAndMetadataStaySynchronizedAcrossTrackTransitions() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val firstFile = createSilentWav(context.cacheDir.resolve("android-auto-first.wav"))
        val secondFile = createSilentWav(context.cacheDir.resolve("android-auto-second.wav"))
        val first = Song(
            videoId = Uri.fromFile(firstFile).toString(),
            title = "Android Auto first track",
            artist = "Audify test",
            thumbnailUrl = null,
        ).toMediaItem()
        val second = Song(
            videoId = Uri.fromFile(secondFile).toString(),
            title = "Android Auto second track",
            artist = "Audify test",
            thumbnailUrl = null,
        ).toMediaItem()

        withContext(Dispatchers.Main) {
            mediaBrowser.setMediaItems(listOf(first, second), 0, 0L)
            mediaBrowser.prepare()
        }

        awaitPlayerState("first local test track to become ready") {
            mediaBrowser.currentMediaItem?.mediaId == first.mediaId &&
                mediaBrowser.playbackState == Player.STATE_READY
        }
        withContext(Dispatchers.Main) {
            assertEquals("Android Auto first track", mediaBrowser.mediaMetadata.title?.toString())
            assertTrue(mediaBrowser.isCommandAvailable(Player.COMMAND_PLAY_PAUSE))
            assertTrue(mediaBrowser.isCommandAvailable(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM))
        }

        withContext(Dispatchers.Main) { mediaBrowser.seekToNextMediaItem() }
        awaitPlayerState("next-track command and metadata to synchronize") {
            mediaBrowser.currentMediaItem?.mediaId == second.mediaId
        }
        withContext(Dispatchers.Main) {
            assertEquals("Android Auto second track", mediaBrowser.mediaMetadata.title?.toString())
        }

        withContext(Dispatchers.Main) { mediaBrowser.play() }
        awaitPlayerState("play command to reach the session") { mediaBrowser.playWhenReady }
        withContext(Dispatchers.Main) { mediaBrowser.pause() }
        awaitPlayerState("pause command to reach the session") { !mediaBrowser.playWhenReady }
    }

    @Test
    fun automotiveMediaDescriptorIsRegistered() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val appInfo = context.packageManager.getApplicationInfo(
            context.packageName,
            PackageManager.GET_META_DATA,
        )
        assertNotNull("Application metadata must be present", appInfo.metaData)
        assertEquals(
            "Android Auto must be told this is a media app",
            R.xml.automotive_app_desc,
            appInfo.metaData?.getInt("com.google.android.gms.car.application"),
        )

        val parser = context.resources.getXml(R.xml.automotive_app_desc)
        try {
            var foundMediaCapability = false
            while (parser.next() != org.xmlpull.v1.XmlPullParser.END_DOCUMENT) {
                if (parser.eventType == org.xmlpull.v1.XmlPullParser.START_TAG && parser.name == "uses") {
                    foundMediaCapability = parser.getAttributeValue(null, "name") == "media"
                    if (foundMediaCapability) break
                }
            }
            assertTrue("Automotive descriptor must declare the media capability", foundMediaCapability)
        } finally {
            parser.close()
        }
    }

    @Test
    fun mediaLibraryServiceIsDiscoverableToAndroidAuto() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val packageManager = context.packageManager
        val mediaLibraryIntent = Intent("androidx.media3.session.MediaLibraryService")
            .setPackage(context.packageName)
        val serviceInfo = packageManager.resolveService(
            mediaLibraryIntent,
            0,
        )?.serviceInfo

        assertNotNull("MediaLibraryService action must resolve inside the app", serviceInfo)
        assertEquals(PlaybackService::class.java.name, serviceInfo?.name)
        assertTrue("Media browsing service must be exported for Android Auto", serviceInfo?.exported == true)
        assertEquals(
            "android.permission.BIND_MEDIA_BROWSER_SERVICE",
            serviceInfo?.permission,
        )

        val legacyBrowserIntent = Intent("android.media.browse.MediaBrowserService")
            .setPackage(context.packageName)
        assertNotNull(
            "Legacy MediaBrowserService discovery must remain available",
            packageManager.resolveService(legacyBrowserIntent, 0),
        )
    }

    @Test
    fun testGetTopLevelChildren() = runBlocking {
        val childrenResult = withContext(Dispatchers.Main) {
            mediaBrowser.getChildren("root", /* page = */ 0, /* pageSize = */ 20, /* params = */ null)
                .get(10, TimeUnit.SECONDS)
        }
        assertNotNull("Children result must not be null", childrenResult)
        val items = childrenResult.value
        assertNotNull("Children list must not be null", items)
        assertTrue("Top-level children must not be empty", items!!.isNotEmpty())

        val expectedRootOrder = listOf("quick_picks", "recents", "playlists", "liked", "more")
        assertEquals("Root must expose the pinned Android Auto folders", expectedRootOrder, items.map { it.mediaId })
        items.forEach {
            assertTrue("Root child ${it.mediaId} must be browsable", it.mediaMetadata.isBrowsable == true)
        }
    }

    @Test
    fun testMoreFolderExposesOfflineCollections() = runBlocking {
        val children = withContext(Dispatchers.Main) {
            mediaBrowser.getChildren("more", 0, 20, null).get(10, TimeUnit.SECONDS)
        }
        assertNotNull(children.value)
        assertEquals(listOf("downloads", "local_music", "albums"), children.value!!.map { it.mediaId })
        children.value!!.forEach { assertTrue(it.mediaMetadata.isBrowsable == true) }
    }

    @Test
    fun testAlbumsFolderReturnsARecoverableSignedOutState() = runBlocking {
        val children = withContext(Dispatchers.Main) {
            mediaBrowser.getChildren("albums", 0, 20, null).get(10, TimeUnit.SECONDS)
        }
        assertNotNull(children.value)
        assertEquals("msg:login_required", children.value!!.single().mediaId)
    }

    @Test
    fun testReconnectCanBrowseTheSameLibraryRoot() = runBlocking {
        withContext(Dispatchers.Main) { mediaBrowser.release() }
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        val latch = CountDownLatch(1)
        var reconnected: MediaBrowser? = null
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val future = MediaBrowser.Builder(context, token).buildAsync()
            future.addListener(
                {
                    reconnected = future.get()
                    latch.countDown()
                },
                { command -> command.run() },
            )
        }
        assertTrue("MediaBrowser reconnect timed out", latch.await(10, TimeUnit.SECONDS))
        val browser = reconnected ?: throw AssertionError("MediaBrowser did not reconnect")
        try {
            val root = withContext(Dispatchers.Main) {
                browser.getLibraryRoot(null).get(10, TimeUnit.SECONDS)
            }
            assertEquals("root", root.value?.mediaId)
        } finally {
            InstrumentationRegistry.getInstrumentation().runOnMainSync { browser.release() }
        }
    }

    @Test
    fun testGetItem() = runBlocking {
        for (id in listOf("quick_picks", "recents", "playlists", "liked", "more", "albums")) {
            val itemResult = withContext(Dispatchers.Main) {
                mediaBrowser.getItem(id).get(10, TimeUnit.SECONDS)
            }
            assertNotNull("Item result for $id must not be null", itemResult)
            assertNotNull("Item for $id must not be null", itemResult.value)
            assertEquals(id, itemResult.value?.mediaId)
        }
    }

    @Test
    fun testPaginationBoundaries() = runBlocking {
        val firstPage = withContext(Dispatchers.Main) {
            mediaBrowser.getChildren("root", 0, 2, null).get(10, TimeUnit.SECONDS)
        }
        assertNotNull(firstPage.value)
        assertTrue(firstPage.value!!.size <= 2)

        val outOfRange = withContext(Dispatchers.Main) {
            mediaBrowser.getChildren("root", 99, 20, null).get(10, TimeUnit.SECONDS)
        }
        assertNotNull(outOfRange.value)
        assertTrue(outOfRange.value!!.isEmpty())

        val overflowAttempt = withContext(Dispatchers.Main) {
            mediaBrowser.getChildren("root", Int.MAX_VALUE, 50, null).get(10, TimeUnit.SECONDS)
        }
        assertNotNull(overflowAttempt.value)
        assertTrue(overflowAttempt.value!!.isEmpty())
    }

    @Test
    fun testSearchAndGetSearchResult() = runBlocking {
        withContext(Dispatchers.Main) {
            mediaBrowser.search("Adele", null).get(10, TimeUnit.SECONDS)
        }

        val results = withContext(Dispatchers.Main) {
            mediaBrowser.getSearchResult("Adele", 0, 10, null).get(10, TimeUnit.SECONDS)
        }
        assertNotNull("Search result should not be null", results)
        assertNotNull("Search result items should not be null", results.value)
    }

    private fun createSilentWav(file: File): File {
        val sampleRate = 8_000
        val dataSize = sampleRate * 5 * 2 // five seconds, mono, 16-bit PCM
        val bytes = ByteBuffer.allocate(44 + dataSize).order(ByteOrder.LITTLE_ENDIAN)
        bytes.put("RIFF".toByteArray(StandardCharsets.US_ASCII))
        bytes.putInt(36 + dataSize)
        bytes.put("WAVE".toByteArray(StandardCharsets.US_ASCII))
        bytes.put("fmt ".toByteArray(StandardCharsets.US_ASCII))
        bytes.putInt(16)
        bytes.putShort(1) // PCM
        bytes.putShort(1) // mono
        bytes.putInt(sampleRate)
        bytes.putInt(sampleRate * 2)
        bytes.putShort(2)
        bytes.putShort(16)
        bytes.put("data".toByteArray(StandardCharsets.US_ASCII))
        bytes.putInt(dataSize)
        while (bytes.hasRemaining()) bytes.put(0)
        file.writeBytes(bytes.array())
        return file
    }

    private fun awaitPlayerState(description: String, timeoutMs: Long = 5_000L, condition: () -> Boolean) {
        val deadline = SystemClock.elapsedRealtime() + timeoutMs
        var satisfied = false
        while (!satisfied) {
            InstrumentationRegistry.getInstrumentation().runOnMainSync {
                satisfied = condition()
            }
            if (satisfied) break
            if (SystemClock.elapsedRealtime() >= deadline) {
                throw AssertionError("Timed out waiting for $description")
            }
            Thread.sleep(20L)
        }
    }
}
