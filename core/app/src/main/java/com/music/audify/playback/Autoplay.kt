package com.music.audify.playback

import com.music.audify.data.YtMusicRepository
import com.music.audify.data.listentogether.ListenTogether
import com.music.audify.data.model.SearchFilter
import com.music.audify.data.model.SearchResult
import com.music.audify.data.model.QueueTier
import com.music.audify.data.model.Song
import com.music.audify.data.sources.SourceRegistry
import com.music.audify.data.sources.TrackMatcher
import com.music.audify.playback.QueueCoordinator.asQueueEntry
import com.music.audify.data.settings.AppSettings
import com.music.audify.playback.smart.TrackAnalysis
import com.music.audify.playback.smart.TransitionTier
import com.music.audify.playback.smart.assessTransitionTier
import com.music.audify.playback.smart.incomingRateBetween
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.abs

/** Most AutoPlay-suggested tracks kept queued ahead of the current one at once. */
const val MAX_QUEUED_AUTOPLAY = 10

/**
 * Whether AutoPlay is on for the music this device is playing.
 *
 * In a party it is the party's setting and not this phone's: AutoPlay is shared
 * there, so one member's preference cannot be what decides. [localPreference] is
 * `AppSettings.autoplay` and applies everywhere else.
 *
 * Shared by the playback service and by every surface that draws the toggle,
 * because they went their own ways once and the result was a button that lied:
 * the player read the local preference while the service obeyed the party's, so
 * a listener could sit under "AutoPlay on" getting no suggestions at all, press
 * it, and watch the label stay exactly as it was.
 */
fun autoplayEnabledFor(party: ListenTogether.State, localPreference: Boolean): Boolean =
    if (party.inParty) party.playback.autoplayEnabled else localPreference

/**
 * The one device in a party that tops the queue up, or null if there is none.
 *
 * Everybody runs this over the same membership and gets the same answer, which
 * is what stops five phones appending five different sets of suggestions to one
 * queue. The host supplies while it is connected; otherwise the lowest member id
 * takes over, chosen for being stable rather than for meaning anything.
 *
 * Except in a party locked to its host, where there is no stand-in. The server
 * refuses a `queueAdd` from a listener outright — `host_only`, whatever the
 * listener believes it is doing — so electing one produced a control that was
 * always refused, an error on that listener's phone and suggestions for nobody.
 * Returning null instead lets AutoPlay wait for the host, which is what the
 * party's own policy already says should happen.
 */
fun autoplaySupplierId(party: ListenTogether.State): String? {
    if (!party.inParty) return null
    val host = party.members.firstOrNull { it.isHost && it.connected }?.memberId
    if (party.hostOnlyControl) return host
    return host ?: party.members.asSequence()
        .filter { it.connected }
        .minByOrNull { it.memberId }
        ?.memberId
}

/**
 * Whether AutoPlay should forget its last seed and try the current track again.
 *
 * A completed load normally leaves its seed behind to de-duplicate the several
 * player callbacks caused by one queue edit. If that edit leaves the current
 * track with nothing after it, however, the seed is no longer useful: keeping
 * it would make an enabled AutoPlay queue stay empty forever.
 */
fun autoplayQueueNeedsRefresh(
    enabled: Boolean,
    repeatAll: Boolean,
    currentIndex: Int,
    itemCount: Int,
    loadInProgress: Boolean,
): Boolean = enabled &&
    !repeatAll &&
    !loadInProgress &&
    currentIndex >= 0 &&
    currentIndex == itemCount - 1

/**
 * Finds the YouTube id that should seed AutoPlay for a song. Module tracks do not
 * carry YouTube ids, so they are matched on YouTube before the radio request.
 */
suspend fun youtubeSeedFor(song: Song): String? {
    if (SourceRegistry.parseTrackKey(song.videoId) == null) return song.videoId
    val target = TrackMatcher.targetOf(song)
    val query = TrackMatcher.queries(target).firstOrNull() ?: return null
    return YtMusicRepository.search(query, SearchFilter.SONGS)
        .getOrNull()
        ?.filterIsInstance<SearchResult.Track>()
        ?.map { it.song }
        ?.let { TrackMatcher.best(it, target) }
        ?.videoId
}

/**
 * Jalan pintas ke data analisis Automix untuk AutoPlay. Diisi oleh service
 * pemutaran; null berarti tidak ada data (urutan saran dibiarkan apa adanya).
 * Pencarian boleh menyentuh disk, jadi hanya dipanggil di luar thread utama.
 */
object AutoplayMixRanking {
    @Volatile
    var analysisOf: ((String) -> TrackAnalysis?)? = null
}

/** Bobot terhadap urutan relevansi radio (posisi 1.0 turun ke 0). */
private const val MIX_BONUS_BASE = 0.35
private const val MIX_BONUS_CLOSE = 0.25
private const val MIX_PENALTY = 0.3

/** Seberapa cocok [to] dilanjutkan setelah [from] untuk Automix; 0 bila [to] belum dianalisis. */
private fun mixBonus(from: TrackAnalysis, to: TrackAnalysis?): Double {
    if (to == null) return 0.0
    val verdict = assessTransitionTier(from, to, advanced = true)
    if (verdict.tier != TransitionTier.BEATMATCHED) return -MIX_PENALTY
    val rate = incomingRateBetween(from.bpm, to.bpm)
    val closeness = if (rate.isFinite()) 1.0 - (abs(rate - 1.0) / 0.10).coerceIn(0.0, 1.0) else 0.0
    return MIX_BONUS_BASE + MIX_BONUS_CLOSE * closeness
}

/**
 * Menyusun ulang [candidates] sebagai rantai: tiap lagu dipilih agar enak
 * di-mix dari lagu sebelumnya, tanpa membuang urutan relevansi radio.
 * Lagu yang belum dianalisis tidak diberi bonus maupun penalti; tidak ada
 * yang dibuang, hanya diurutkan.
 */
internal fun orderForMixing(
    seed: TrackAnalysis,
    candidates: List<Song>,
    analysisOf: (String) -> TrackAnalysis?,
): List<Song> {
    if (candidates.size < 2 || !seed.isUsable) return candidates
    val known = HashMap<String, TrackAnalysis?>(candidates.size * 2)
    for (song in candidates) known[song.videoId] = analysisOf(song.videoId)
    if (known.values.none { it != null }) return candidates

    val remaining = candidates.withIndex().toMutableList()
    val out = ArrayList<Song>(candidates.size)
    var previous = seed
    while (remaining.isNotEmpty()) {
        var best = 0
        var bestScore = Double.NEGATIVE_INFINITY
        for (i in remaining.indices) {
            val entry = remaining[i]
            val relevance = 1.0 - entry.index.toDouble() / candidates.size
            val score = relevance + mixBonus(previous, known[entry.value.videoId])
            if (score > bestScore) {
                bestScore = score
                best = i
            }
        }
        val chosen = remaining.removeAt(best)
        out += chosen.value
        known[chosen.value.videoId]?.let { previous = it }
    }
    return out
}

private suspend fun rankForAutomix(seedSong: Song, related: List<Song>): List<Song> {
    val lookup = AutoplayMixRanking.analysisOf ?: return related
    if (!AppSettings.smartFadeEnabled.value) return related
    return try {
        withContext(Dispatchers.IO) {
            val seed = lookup(seedSong.videoId)
            if (seed == null) related else orderForMixing(seed, related, lookup)
        }
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Exception) {
        related
    }
}

/**
 * Loads, de-duplicates and resolves one AutoPlay batch. The playback service is
 * the only caller for the AutoPlay toggle; the player UI's explicit radio start
 * uses this same helper for its initial station batch.
 */
suspend fun loadAutoplayTracks(
    existing: List<Song>,
    seedSong: Song,
    limit: Int = MAX_QUEUED_AUTOPLAY,
): Result<List<Song>> {
    val seed = youtubeSeedFor(seedSong) ?: return Result.success(emptyList())
    // A music video plays on with music videos, as YouTube Music does.
    // A music video plays on with music videos, unless they would only be
    // converted again, when the station's songs are the answer.
    val videos = seedSong.isVideo && seed == seedSong.videoId && !MusicOnlyPlayback.isActive()
    val related = YtMusicRepository.radio(seed, preferVideo = videos).getOrElse { return Result.failure(it) }
    val extra = QueueBuilder.extend(existing, rankForAutomix(seedSong, related), limit, preferVideos = videos)
    if (extra.isEmpty()) return Result.success(emptyList())

    return Result.success(extra.map {
        it.copy(
            radioName = seedSong.radioName,
            playbackSource = seedSong.playbackSource,
            playbackSourceType = seedSong.playbackSourceType,
            playbackSourceId = seedSong.playbackSourceId,
        ).asQueueEntry(QueueTier.AUTOPLAY)
    })
}
