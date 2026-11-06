// Copyright (c) 2026 Sachit. All rights reserved.
// Project: AUDIFY
package com.music.audify

import android.content.ComponentName
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaBrowser
import androidx.media3.session.SessionToken
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.music.audify.playback.PlaybackService
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
}
