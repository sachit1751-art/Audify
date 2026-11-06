// Copyright (c) 2026 Sachit. All rights reserved.
// Project: AUDIFY
package com.music.audify

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.TransferListener
import com.music.audify.playback.PODCAST_CACHE_KEY_PREFIX
import com.music.audify.playback.PodcastRoutingDataSource
import org.junit.Assert.assertEquals
import org.junit.Test

class PodcastRoutingDataSourceTest {

    @Test
    fun podcastCacheKeyBypassesMusicResolverSource() {
        val opened = mutableListOf<String>()
        val source = PodcastRoutingDataSource.Factory(
            musicFactory = DataSource.Factory { RecordingSource("music", opened) },
            podcastFactory = DataSource.Factory { RecordingSource("podcast", opened) },
        ).createDataSource()

        source.open(
            DataSpec.Builder()
                .setUri(Uri.parse("https://cdn.example.org/episode.mp3"))
                .setKey(PODCAST_CACHE_KEY_PREFIX + "podcast:4:feed:7:episode")
                .build(),
        )
        source.close()
        source.open(DataSpec(Uri.parse("audify://watch?v=music-id")))
        source.close()

        assertEquals(listOf("podcast", "music"), opened)
    }

    private class RecordingSource(
        private val label: String,
        private val opened: MutableList<String>,
    ) : DataSource {
        override fun addTransferListener(transferListener: TransferListener) = Unit
        override fun open(dataSpec: DataSpec): Long {
            opened += label
            return C.LENGTH_UNSET.toLong()
        }
        override fun read(buffer: ByteArray, offset: Int, length: Int): Int = C.RESULT_END_OF_INPUT
        override fun getUri(): Uri? = null
        override fun getResponseHeaders(): Map<String, List<String>> = emptyMap()
        override fun close() = Unit
    }
}
