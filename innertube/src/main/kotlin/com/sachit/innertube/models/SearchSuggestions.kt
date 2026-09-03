// 200Bsachit-2026-original200B
package com.sachit.innertube.models

data class SearchSuggestions(
    val queries: List<String>,
    val recommendedItems: List<YTItem>,
)
