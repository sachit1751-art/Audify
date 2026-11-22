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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlin.math.abs
import java.util.Locale

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

/** Explicit, session-scoped steering for the existing radio/AutoPlay queue. */
enum class RadioListeningIntent {
    OFF,
    FAMILIAR,
    DISCOVERY,
    ENERGETIC,
    CALM,
    ;

    fun next(): RadioListeningIntent = entries[(ordinal + 1) % entries.size]
}

/** Shared by the player menu and the existing queue owner; never persisted as a taste profile. */
object RadioListeningIntentState {
    private val _current = MutableStateFlow(RadioListeningIntent.OFF)
    val current: StateFlow<RadioListeningIntent> = _current.asStateFlow()

    fun advance(): RadioListeningIntent = _current.value.next().also { _current.value = it }

    fun clear() {
        _current.value = RadioListeningIntent.OFF
        RadioRecommendationExplanationState.clear()
    }
}

/** A factual, signal-backed explanation for one AutoPlay candidate. */
enum class RadioRecommendationReason {
    FAMILIAR_ARTIST,
    NEW_ARTIST,
    HIGHER_MEASURED_ENERGY,
    LOWER_MEASURED_ENERGY,
    PROVIDER_ORDER,
}

/** Session-only explanations; bounded so a long radio session cannot grow this map forever. */
object RadioRecommendationExplanationState {
    private const val MAX_EXPLANATIONS = 100
    private val _byVideoId = MutableStateFlow<Map<String, RadioRecommendationReason>>(emptyMap())
    val byVideoId: StateFlow<Map<String, RadioRecommendationReason>> = _byVideoId.asStateFlow()

    fun publish(explanations: Map<String, RadioRecommendationReason>) {
        if (explanations.isEmpty()) return
        val updated = LinkedHashMap(_byVideoId.value)
        explanations.forEach { (videoId, reason) ->
            // Refresh recency when a track appears in a later radio batch.
            updated.remove(videoId)
            updated[videoId] = reason
        }
        while (updated.size > MAX_EXPLANATIONS) {
            updated.remove(updated.keys.first())
        }
        _byVideoId.value = updated
    }

    fun clear() {
        _byVideoId.value = emptyMap()
    }
}

/**
 * Reorders a provider's radio candidates using only explicit session intent
 * and evidence already available locally. Stable provider order breaks ties;
 * missing artist/analysis metadata is never guessed.
 */
internal fun orderForListeningIntent(
    intent: RadioListeningIntent,
    seed: Song,
    existing: List<Song>,
    candidates: List<Song>,
    analysisOf: (String) -> TrackAnalysis?,
): List<Song> {
    if (intent == RadioListeningIntent.OFF || candidates.size < 2) return candidates

    fun artistKeys(song: Song): Set<String> = buildSet {
        song.artists.mapNotNullTo(this) { it.name.trim().lowercase(Locale.ROOT).takeIf(String::isNotEmpty) }
        song.artist.split(',', '&').mapTo(this) { it.trim().lowercase(Locale.ROOT) }
        remove("")
    }

    val familiarArtists = (existing + seed).flatMapTo(HashSet(), ::artistKeys)
    val scored = candidates.mapIndexedNotNull { index, song ->
        val score = when (intent) {
            RadioListeningIntent.OFF -> null
            RadioListeningIntent.FAMILIAR -> artistKeys(song).takeIf { it.isNotEmpty() }
                ?.let { keys -> if (keys.any { it in familiarArtists }) 1.0 else 0.0 }
            RadioListeningIntent.DISCOVERY -> artistKeys(song).takeIf { it.isNotEmpty() }
                ?.let { keys -> if (keys.none { it in familiarArtists }) 1.0 else 0.0 }
            RadioListeningIntent.ENERGETIC,
            RadioListeningIntent.CALM,
            -> analysisOf(song.videoId)
                ?.takeIf { it.status.isBlank() || it.status == TrackAnalysis.STATUS_READY }
                ?.energyCurve
                ?.map { it.energy }
                ?.filter(Double::isFinite)
                ?.takeIf { it.isNotEmpty() }
                ?.average()
        }
        score?.let { Triple(index, song, it) }
    }
    if (scored.isEmpty()) return candidates

    val knownOrder = when (intent) {
        RadioListeningIntent.FAMILIAR,
        RadioListeningIntent.DISCOVERY,
        RadioListeningIntent.ENERGETIC,
        -> compareByDescending<Triple<Int, Song, Double>> { it.third }
        RadioListeningIntent.CALM -> compareBy<Triple<Int, Song, Double>> { it.third }
        RadioListeningIntent.OFF -> return candidates
    }
    val rankedKnown = scored.sortedWith(knownOrder.thenBy { it.first })
    // Preserve the intent score as the primary ordering, but avoid stacking
    // several tracks by the same artist when equally ranked alternatives exist.
    // This is deliberately a tie-breaker: explicit intent always outranks variety.
    val diversifiedKnown = rankedKnown.groupBy { it.third }.values.flatMap { tied ->
        val remaining = tied.toMutableList()
        val ordered = ArrayList<Triple<Int, Song, Double>>(tied.size)
        val recentArtists = ArrayDeque<Set<String>>()
        while (remaining.isNotEmpty()) {
            val diverseIndex = remaining.indexOfFirst { candidate ->
                val keys = artistKeys(candidate.second)
                keys.isEmpty() || recentArtists.none { previous -> keys.any(previous::contains) }
            }
            val chosen = remaining.removeAt(if (diverseIndex >= 0) diverseIndex else 0)
            ordered += chosen
            val keys = artistKeys(chosen.second)
            if (keys.isNotEmpty()) {
                recentArtists.addLast(keys)
                while (recentArtists.size > 2) recentArtists.removeFirst()
            }
        }
        ordered
    }
    val knownIndices = diversifiedKnown.mapTo(HashSet()) { it.first }
    return buildList(candidates.size) {
        addAll(diversifiedKnown.map { it.second })
        candidates.forEachIndexed { index, song -> if (index !in knownIndices) add(song) }
    }
}

internal fun explainListeningIntentCandidate(
    intent: RadioListeningIntent,
    seed: Song,
    existing: List<Song>,
    candidate: Song,
    analysisOf: (String) -> TrackAnalysis?,
): RadioRecommendationReason {
    fun artistKeys(song: Song): Set<String> = buildSet {
        song.artists.mapNotNullTo(this) { it.name.trim().lowercase(Locale.ROOT).takeIf(String::isNotEmpty) }
        song.artist.split(',', '&').mapTo(this) { it.trim().lowercase(Locale.ROOT) }
        remove("")
    }
    val knownArtists = (existing + seed).flatMapTo(HashSet(), ::artistKeys)
    val candidateArtists = artistKeys(candidate)
    return when (intent) {
        RadioListeningIntent.FAMILIAR -> if (candidateArtists.any { it in knownArtists }) {
            RadioRecommendationReason.FAMILIAR_ARTIST
        } else RadioRecommendationReason.PROVIDER_ORDER
        RadioListeningIntent.DISCOVERY -> if (candidateArtists.isNotEmpty() && candidateArtists.none { it in knownArtists }) {
            RadioRecommendationReason.NEW_ARTIST
        } else RadioRecommendationReason.PROVIDER_ORDER
        RadioListeningIntent.ENERGETIC,
        RadioListeningIntent.CALM,
        -> {
            val energy = analysisOf(candidate.videoId)
                ?.takeIf { it.status.isBlank() || it.status == TrackAnalysis.STATUS_READY }
                ?.energyCurve?.map { it.energy }?.filter(Double::isFinite)?.takeIf { it.isNotEmpty() }
                ?.average()
            if (energy == null) RadioRecommendationReason.PROVIDER_ORDER
            else if (intent == RadioListeningIntent.ENERGETIC) RadioRecommendationReason.HIGHER_MEASURED_ENERGY
            else RadioRecommendationReason.LOWER_MEASURED_ENERGY
        }
        RadioListeningIntent.OFF -> RadioRecommendationReason.PROVIDER_ORDER
    }
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

private suspend fun rankForAutomix(
    seedSong: Song,
    existing: List<Song>,
    related: List<Song>,
    intent: RadioListeningIntent,
): List<Song> {
    val lookup = AutoplayMixRanking.analysisOf
    val mixed = try {
        withContext(Dispatchers.IO) {
            if (lookup != null && AppSettings.smartFadeEnabled.value) {
                val seed = lookup(seedSong.videoId)
                if (seed == null) related else orderForMixing(seed, related, lookup)
            } else related
        }
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Exception) {
        related
    }
    // A failed optional Automix analysis must not silently discard the user's
    // explicit listening intent. Ranking still uses whatever evidence remains.
    return try {
        withContext(Dispatchers.IO) {
            val ordered = orderForListeningIntent(
                intent = intent,
                seed = seedSong,
                existing = existing,
                candidates = mixed,
                analysisOf = lookup ?: { null },
            )
            RadioRecommendationExplanationState.publish(
                ordered.associate { song ->
                    song.videoId to explainListeningIntentCandidate(
                        intent = intent,
                        seed = seedSong,
                        existing = existing,
                        candidate = song,
                        analysisOf = lookup ?: { null },
                    )
                },
            )
            ordered
        }
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Exception) {
        mixed
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
    intent: RadioListeningIntent = RadioListeningIntentState.current.value,
): Result<List<Song>> {
    val seed = youtubeSeedFor(seedSong) ?: return Result.success(emptyList())
    // A music video plays on with music videos, as YouTube Music does.
    // A music video plays on with music videos, unless they would only be
    // converted again, when the station's songs are the answer.
    val videos = seedSong.isVideo && seed == seedSong.videoId && !MusicOnlyPlayback.isActive()
    val related = YtMusicRepository.radio(seed, preferVideo = videos).getOrElse { return Result.failure(it) }
    val extra = QueueBuilder.extend(
        existing,
        rankForAutomix(seedSong, existing, related, intent),
        limit,
        preferVideos = videos,
    )
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
