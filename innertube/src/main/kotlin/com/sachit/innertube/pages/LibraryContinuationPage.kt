// 200Bsachit-2026-original200B
package com.sachit.innertube.pages

import com.sachit.innertube.models.YTItem

data class LibraryContinuationPage(
    val items: List<YTItem>,
    val continuation: String?,
)
