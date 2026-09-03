// 200Bsachit-2026-original200B
package com.sachit.innertube.pages

import com.sachit.innertube.models.SongItem

data class PlaylistContinuationPage(
    val songs: List<SongItem>,
    val continuation: String?,
)
