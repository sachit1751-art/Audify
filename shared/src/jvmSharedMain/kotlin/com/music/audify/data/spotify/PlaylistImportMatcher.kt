// Copyright (c) 2026 Sachit. All rights reserved.
// Project: AUDIFY
package com.music.audify.data.spotify

import com.music.audify.data.model.Song
import com.music.audify.data.sources.TrackMatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.text.Normalizer
import java.util.Locale

enum class PlaylistImportRowStatus { MATCHED, AMBIGUOUS, UNMATCHED, DUPLICATE_OCCURRENCE, INVALID }
enum class PlaylistImportConfidence { STRONG, REVIEW }

data class PlaylistImportCandidate(
    val song: Song,
    val confidence: PlaylistImportConfidence,
    val evidence: String,
)

data class PlaylistImportRow(
    val source: PlaylistImportEntry,
    val status: PlaylistImportRowStatus,
    val candidates: List<PlaylistImportCandidate> = emptyList(),
    val duplicateOfSourceRow: Int? = null,
    val reason: String? = null,
)

/** Bounded, deterministic matching. Search is injected so ranking is testable without network access. */
object PlaylistImportMatcher {
    private const val MAX_CONCURRENT_SEARCHES = 4
    private const val AMBIGUITY_SCORE_MARGIN = 10

    suspend fun match(
        entries: List<PlaylistImportEntry>,
        search: suspend (query: String) -> List<Song>,
        onProgress: (completed: Int, total: Int) -> Unit = { _, _ -> },
    ): List<PlaylistImportRow> = coroutineScope {
        require(entries.size <= PlaylistFileImporter.MAX_TRACKS) {
            "Playlist contains more than ${PlaylistFileImporter.MAX_TRACKS} tracks"
        }
        val valid = entries.filter { it.title.isNotBlank() }
        val unique = valid.distinctBy(::identityKey)
        // A source may repeat the same title/artist with different album or
        // runtime metadata. Those rows need independent scoring, not another
        // identical catalogue request.
        val uniqueQueries = valid.distinctBy(::queryKey)
        val semaphore = Semaphore(MAX_CONCURRENT_SEARCHES)
        val completed = java.util.concurrent.atomic.AtomicInteger()
        val searchResults = uniqueQueries.map { entry ->
            async(Dispatchers.IO) {
                semaphore.withPermit {
                    val result = try {
                        Result.success(search(queryOf(entry)))
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (failure: Exception) {
                        Result.failure(failure)
                    }
                    onProgress(completed.incrementAndGet(), uniqueQueries.size)
                    queryKey(entry) to result
                }
            }
        }.awaitAll().toMap()
        val resolved = unique.associate { entry ->
            identityKey(entry) to resolve(entry, searchResults.getValue(queryKey(entry)))
        }

        val firstOccurrence = mutableMapOf<String, Int>()
        entries.map { entry ->
            if (entry.title.isBlank()) {
                PlaylistImportRow(entry, PlaylistImportRowStatus.INVALID, reason = "Track title is missing")
            } else {
                val key = identityKey(entry)
                val firstRow = firstOccurrence.putIfAbsent(key, entry.sourceRow)
                val base = resolved.getValue(key)
                if (firstRow != null) {
                    base.copy(
                        source = entry,
                        status = PlaylistImportRowStatus.DUPLICATE_OCCURRENCE,
                        duplicateOfSourceRow = firstRow,
                        reason = "Repeated source entry from row $firstRow; occurrence order is preserved",
                    )
                } else {
                    base.copy(source = entry)
                }
            }
        }
    }

    private fun resolve(
        entry: PlaylistImportEntry,
        searchResult: Result<List<Song>>,
    ): PlaylistImportRow {
        val target = TrackMatcher.Target(
            title = entry.title,
            artist = entry.artist,
            durationSec = entry.durationMs?.div(1_000L)?.toInt(),
            album = entry.album,
        )
        val searchResults = searchResult.getOrElse { failure ->
            return PlaylistImportRow(
                entry,
                PlaylistImportRowStatus.UNMATCHED,
                reason = "Catalogue search failed: ${failure.message ?: "unknown error"}",
            )
        }
        val candidates = searchResults
            .distinctBy { it.videoId }
            .mapNotNull { song -> TrackMatcher.score(song, target)?.let { song to it } }
            .sortedWith(compareByDescending<Pair<Song, Int>> { it.second }.thenBy { it.first.videoId })
        if (candidates.isEmpty()) {
            return PlaylistImportRow(
                entry,
                PlaylistImportRowStatus.UNMATCHED,
                reason = "No catalogue result matched the title, artist and available release metadata",
            )
        }

        val topScore = candidates.first().second
        val close = candidates.takeWhile { topScore - it.second <= AMBIGUITY_SCORE_MARGIN }
        val ambiguous = close.size > 1
        val options = (if (ambiguous) candidates else candidates.take(1)).take(3).map { (song, _) ->
            val sharesArtist = entry.artist.isNotBlank() && TrackMatcher.sharesArtist(entry.artist, song.artist)
            val confidence = if (sharesArtist) PlaylistImportConfidence.STRONG else PlaylistImportConfidence.REVIEW
            val evidence = when {
                sharesArtist && entry.album != null && entry.album.equals(song.albumName, ignoreCase = true) ->
                    "Title, artist and album agree"
                sharesArtist -> "Title and credited artist agree"
                entry.artist.isBlank() -> "Title matches; source has no artist credit"
                else -> "Title/runtime match; artist credit needs review"
            }
            PlaylistImportCandidate(song, confidence, evidence)
        }
        return PlaylistImportRow(
            source = entry,
            status = if (ambiguous) PlaylistImportRowStatus.AMBIGUOUS else PlaylistImportRowStatus.MATCHED,
            candidates = options,
            reason = if (ambiguous) "Several catalogue results have near-equal match evidence" else null,
        )
    }

    private fun queryOf(entry: PlaylistImportEntry): String =
        listOf(entry.title, entry.artist).filter(String::isNotBlank).joinToString(" ")

    private fun queryKey(entry: PlaylistImportEntry): String = normalizeKey(queryOf(entry))

    private fun identityKey(entry: PlaylistImportEntry): String = listOf(
        entry.title,
        entry.artist,
        entry.album.orEmpty(),
        entry.durationMs?.toString().orEmpty(),
    ).joinToString("\u0000", transform = ::normalizeKey)

    private fun normalizeKey(value: String): String =
        Normalizer.normalize(value.trim(), Normalizer.Form.NFKC)
            .lowercase(Locale.ROOT)
            .replace(Regex("\\s+"), " ")
}
