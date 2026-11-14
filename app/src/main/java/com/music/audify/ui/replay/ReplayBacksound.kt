package com.music.audify.ui.replay

import android.util.Log
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.platform.LocalContext
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import com.music.audify.data.model.Song
import com.music.audify.data.stats.ReplaySummary
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * The Replay's backing track: a clip of each of the listener's top songs, one
 * after another, faded in and out the way a recap reel is scored.
 *
 * Each clip starts at its hook rather than at 0:00, plays for [SEGMENT_MS] and
 * fades out before the next one fades in. Holding the story, or the share
 * sheet covering it, ducks the sound to nothing and brings it back when it
 * lets go — the same moments the story's own clock stops.
 *
 * It has a player of its own, so the app's queue is untouched. Whatever the
 * main player was doing is paused while the Replay is up and resumed after the
 * last fade-out, so the two never play over each other.
 */
@Composable
fun ReplayBacksound(
    summary: ReplaySummary,
    /** True while the story is held or something covers it: fade to silence. */
    silenced: Boolean,
    /** The app's own player, paused for as long as the Replay plays its clips. */
    mainPlayer: Player?,
) {
    val tracks = remember(summary) {
        summary.songs.map { it.song }
            .filter { !it.localUri.isNullOrBlank() || it.videoId.isNotBlank() }
            .take(MAX_TRACKS)
    }
    if (tracks.isNotEmpty()) BacksoundPlayer(tracks, silenced, mainPlayer)
}

@Composable
private fun BacksoundPlayer(
    tracks: List<Song>,
    silenced: Boolean,
    mainPlayer: Player?,
) {
    val context = LocalContext.current.applicationContext
    val silencedNow by rememberUpdatedState(silenced)
    val main by rememberUpdatedState(mainPlayer)
    // Two independent gains multiplied together: [level] is each clip's own
    // fade in and out, [duck] is the hold/share silence over the top of it.
    val level = remember { Animatable(0f) }
    val duck = remember { Animatable(1f) }
    val session = remember { ReplayClipCache.openSession() }
    val player = remember {
        ExoPlayer.Builder(context)
            .setMediaSourceFactory(DefaultMediaSourceFactory(ReplayClipCache.playbackFactory(context)))
            // Focus is not taken: the main player is paused by hand below, and
            // a second focus request would only make the system fight over it.
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                false,
            )
            .build()
            .apply {
                volume = 0f
                playWhenReady = false
            }
    }

    DisposableEffect(player) {
        val resumeMain = main?.isPlaying == true
        if (resumeMain) main?.pause()
        onDispose {
            val last = player.volume
            val resume = if (resumeMain) main else null
            // Outlives the composition on purpose: leaving the Replay must
            // fade the sound out rather than cut it.
            CoroutineScope(Dispatchers.Main.immediate).launch {
                for (step in FADE_STEPS downTo 1) {
                    val fraction = (step - 1f) / FADE_STEPS
                    player.volume = last * fraction * fraction
                    delay((FADE_OUT_MS / FADE_STEPS).toLong())
                }
                player.release()
                resume?.play()
                // The clips were only ever for this Replay: none of them stays on disk.
                withContext(Dispatchers.IO) { ReplayClipCache.closeSession(session) }
            }
        }
    }

    LaunchedEffect(player) {
        // Squared: loudness is not linear in amplitude, so a straight ramp
        // sounds like it jumps up at the start and does nothing at the end.
        snapshotFlow { level.value * duck.value }
            .collect { player.volume = (it * it * MAX_VOLUME).coerceIn(0f, 1f) }
    }

    LaunchedEffect(silenced) {
        if (silenced) {
            // A tap is a press too and must not dip the music: only a hold does.
            delay(HOLD_GRACE_MS)
            duck.animateTo(0f, tween(FADE_OUT_MS, easing = LinearEasing))
            player.pause()
        } else {
            if (player.mediaItemCount > 0) player.play()
            duck.animateTo(1f, tween(FADE_IN_MS, easing = LinearEasing))
        }
    }

    LaunchedEffect(tracks) {
        // Each clip is resolved and stored on disk ahead of its turn, so it is
        // already there when it is due instead of being fetched live while the
        // story waits in silence.
        val prepared = HashMap<String, Deferred<String?>>()
        fun prepare(song: Song, graceMs: Long = ReplayClipCache.STORE_GRACE_MS): Deferred<String?> =
            prepared.getOrPut(song.clipKey()) {
                async(Dispatchers.IO) { ReplayClipCache.prepare(context, song, graceMs) }
            }

        var index = 0
        var failures = 0
        var rounds = 0
        while (isActive) {
            val song = tracks[index % tracks.size]
            val next = tracks[(index + 1) % tracks.size]
            // Klip pertama ada di jalur kritis: story sudah berjalan, jadi ia
            // hanya menunggu salinan sebentar lalu diputar lewat streaming.
            val grace = if (index == 0) FIRST_CLIP_GRACE_MS else ReplayClipCache.STORE_GRACE_MS
            index++
            val source = withTimeoutOrNull(PREPARE_TIMEOUT_MS) { prepare(song, grace).await() }
            // The next song is made ready while this one plays.
            if (next.clipKey() != song.clipKey()) prepare(next)
            if (source == null) Log.w("Audify", "Replay backsound: ${song.clipKey()} could not be prepared")
            val played = source != null &&
                playSegment(player, level, { silencedNow }, song, source)
            // This song is done with: its copy goes now, not when the Replay closes.
            prepared.remove(song.clipKey())?.cancel()
            withContext(Dispatchers.IO) { ReplayClipCache.evict(song) }
            if (played) {
                failures = 0
                rounds = 0
            } else if (++failures >= tracks.size) {
                // A whole round with nothing playable: wait and try again a few
                // times rather than staying silent for the rest of the Replay.
                failures = 0
                if (++rounds >= MAX_RETRY_ROUNDS) return@LaunchedEffect
                delay(RETRY_PAUSE_MS)
            }
        }
    }
}

/** Identifies a song's clip: the local file, or else the videoId. */
private fun Song.clipKey(): String = localUri?.takeIf { it.isNotBlank() } ?: videoId

/** One clip, faded in, held and faded out. False when the track could not be played. */
private suspend fun playSegment(
    player: ExoPlayer,
    level: Animatable<Float, *>,
    silenced: () -> Boolean,
    song: Song,
    source: String,
): Boolean {

    val ready = CompletableDeferred<Boolean>()
    val listener = object : Player.Listener {
        override fun onPlaybackStateChanged(playbackState: Int) {
            if (playbackState == Player.STATE_READY) ready.complete(true)
        }

        override fun onPlayerError(error: PlaybackException) {
            Log.w("Audify", "Replay backsound: ${error.errorCodeName}: ${error.message}")
            ready.complete(false)
        }
    }
    player.addListener(listener)
    try {
        level.snapTo(0f)
        player.playWhenReady = false
        // Durasi dari metadata lagu: pemutar langsung memuat dari titik hook,
        // tanpa buffering dari 0:00 lalu seek ulang.
        val plannedStart = song.durationText?.let(::parseClockMs)?.let(::clipStartFor)
        player.setMediaItem(
            MediaItem.Builder()
                .setUri(source)
                // The key the clip was stored under, so it plays from disk.
                .apply { if (song.localUri.isNullOrBlank()) setCustomCacheKey(song.videoId) }
                .build(),
            plannedStart ?: 0L,
        )
        player.prepare()
        if (withTimeoutOrNull(READY_TIMEOUT_MS) { ready.await() } != true) return false

        val duration = player.duration.takeIf { it != C.TIME_UNSET && it > 0L } ?: 0L
        val computed = if (duration > 0L) clipStartFor(duration) else 0L
        // Titik mulai yang direncanakan dipakai selama dekat dengan durasi sebenarnya.
        val start = if (plannedStart != null && kotlin.math.abs(computed - plannedStart) <= START_TOLERANCE_MS) {
            plannedStart
        } else {
            computed
        }
        if (start != plannedStart) player.seekTo(start)
        player.playWhenReady = !silenced()
        // The fade starts when the sound does, not when it was asked for: a seek
        // into a stream can take longer than the fade itself, and a fade run
        // against the clock in that time is over before anything is audible,
        // so the music arrives at full volume out of nowhere.
        if (!silenced()) awaitState(START_TIMEOUT_MS) { player.isPlaying || silenced() }
        level.animateTo(1f, tween(FADE_IN_MS, easing = LinearEasing))

        val stop = (start + SEGMENT_MS).let { if (duration > 0L) minOf(it, duration) else it }
        var lastPosition = player.currentPosition
        var stalledMs = 0L
        // Position stops advancing while held, so the clip's length is only
        // ever counted in audible time.
        while (
            player.playbackState != Player.STATE_ENDED &&
            player.playbackState != Player.STATE_IDLE &&
            player.currentPosition < stop - FADE_OUT_MS
        ) {
            delay(POLL_MS)
            // A clip that stops moving with nothing holding it has lost its stream.
            val position = player.currentPosition
            if (silenced() || position != lastPosition) {
                lastPosition = position
                stalledMs = 0L
            } else if (stalledMs + POLL_MS >= STALL_MS) {
                Log.w("Audify", "Replay backsound: ${song.clipKey()} stalled")
                return false
            } else {
                stalledMs += POLL_MS
            }
        }
        level.animateTo(0f, tween(FADE_OUT_MS, easing = LinearEasing))
        return true
    } finally {
        player.removeListener(listener)
    }
}

/** Polls [condition] until it holds or [timeoutMs] passes. */
private suspend fun awaitState(timeoutMs: Long, condition: () -> Boolean): Boolean =
    withTimeoutOrNull(timeoutMs) {
        while (!condition()) delay(START_POLL_MS)
        true
    } == true

/** How many of the top songs take turns. */
private const val MAX_TRACKS = 3

/** How long each clip plays, counted in audible time. */
private const val SEGMENT_MS = 30_000L

/** Where in the track a clip starts, as a share of its length: about where a hook sits. */
private const val HOOK_FRACTION = 0.30f

private const val FADE_IN_MS = 1_500

/** The first clip waits this long for its disk copy before it streams instead. */
private const val FIRST_CLIP_GRACE_MS = 600L

/** How far the planned start may differ from the real duration's before a seek is made. */
private const val START_TOLERANCE_MS = 2_000L

/** Where a clip of a track [durationMs] long starts: about where its hook sits. */
private fun clipStartFor(durationMs: Long): Long =
    (durationMs * HOOK_FRACTION).toLong().coerceAtMost((durationMs - SEGMENT_MS).coerceAtLeast(0L))

/** "3:45" or "1:02:03" in milliseconds, or null when it is not a clock time. */
private fun parseClockMs(text: String): Long? {
    val parts = text.trim().split(":").map { it.toIntOrNull() ?: return null }
    if (parts.size !in 2..3) return null
    val seconds = parts.fold(0L) { total, part -> total * 60 + part }
    return (seconds * 1000).takeIf { it > 0L }
}
private const val FADE_OUT_MS = 1_800
private const val FADE_STEPS = 28

/** Quiet enough to sit under the story's text rather than over it. */
private const val MAX_VOLUME = 0.85f

private const val POLL_MS = 200L
private const val START_POLL_MS = 50L

/** How long the sound may take to actually start after a seek. */
private const val START_TIMEOUT_MS = 8_000L

/** A clip that has not moved for this long, with nothing holding it, is given up on. */
private const val STALL_MS = 8_000L

/** A press shorter than this is a tap, and leaves the music alone. */
private const val HOLD_GRACE_MS = 250L

/** Resolving a track and storing it on disk, before it is given up on. */
private const val PREPARE_TIMEOUT_MS = 30_000L
private const val READY_TIMEOUT_MS = 12_000L

/** A whole round of tracks that would not play is retried this many times, this far apart. */
private const val MAX_RETRY_ROUNDS = 3
private const val RETRY_PAUSE_MS = 4_000L