// Copyright (c) 2026 Sachit. All rights reserved.
// Project: AUDIFY
package com.music.audify.playback

import android.net.Uri
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.TransferListener

@androidx.annotation.OptIn(markerClass = [androidx.media3.common.util.UnstableApi::class])
/** Routes marked podcast requests around the music resolver and music cache. */
class PodcastRoutingDataSource private constructor(
    private val music: DataSource,
    private val podcast: DataSource,
) : DataSource {

    private var active: DataSource = music

    override fun addTransferListener(transferListener: TransferListener) {
        music.addTransferListener(transferListener)
        podcast.addTransferListener(transferListener)
    }

    override fun open(dataSpec: DataSpec): Long {
        active = if (dataSpec.key?.startsWith(PODCAST_CACHE_KEY_PREFIX) == true) podcast else music
        return active.open(dataSpec)
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int =
        active.read(buffer, offset, length)

    override fun getUri(): Uri? = active.uri

    override fun getResponseHeaders(): Map<String, List<String>> = active.responseHeaders

    override fun close() = active.close()

    @androidx.annotation.OptIn(markerClass = [androidx.media3.common.util.UnstableApi::class])
    class Factory(
        private val musicFactory: DataSource.Factory,
        private val podcastFactory: DataSource.Factory,
    ) : DataSource.Factory {
        override fun createDataSource(): DataSource = PodcastRoutingDataSource(
            music = musicFactory.createDataSource(),
            podcast = podcastFactory.createDataSource(),
        )
    }
}

const val PODCAST_CACHE_KEY_PREFIX = "audify-podcast:"
