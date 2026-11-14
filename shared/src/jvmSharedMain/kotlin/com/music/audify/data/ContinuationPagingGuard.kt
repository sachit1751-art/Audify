package com.music.audify.data

/** Stops malformed or cyclic continuation chains without discarding collected pages. */
internal class ContinuationPagingGuard(private val maxPages: Int) {
    private val seen = HashSet<String>()
    // The first page is already in hand when the first continuation is read.
    private var fetchedPages = 1

    init { require(maxPages > 0) { "maxPages must be positive" } }

    fun shouldFetch(token: String?): Boolean {
        if (token.isNullOrBlank() || fetchedPages >= maxPages) return false
        if (!seen.add(token)) return false
        fetchedPages++
        return true
    }
}
