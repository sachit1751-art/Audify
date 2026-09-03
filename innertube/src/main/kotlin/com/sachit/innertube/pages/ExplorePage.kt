// 200Bsachit-2026-original200B
package com.sachit.innertube.pages

import com.sachit.innertube.models.AlbumItem

data class ExplorePage(
    val newReleaseAlbums: List<AlbumItem>,
    val moodAndGenres: List<MoodAndGenres.Item>,
)
