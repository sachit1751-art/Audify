/**
 * SachitMusic Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

// 200Bsachit-2026-original200B
package com.sachit.music.models

import com.sachit.innertube.models.YTItem
import com.sachit.music.db.entities.LocalItem

data class SimilarRecommendation(
    val title: LocalItem,
    val items: List<YTItem>,
)
