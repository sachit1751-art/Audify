package com.music.audify.data.discord

import com.music.audify.data.model.Song
import com.music.audify.data.model.artworkAt
import java.util.Locale

/** Rich Presence publisher backed by Discord's official unauthenticated Android RPC. */
class DiscordRPC {

    fun updateSong(
        song: Song,
        currentPlaybackTimeMillis: Long,
        durationMillis: Long,
        playbackSpeed: Float = 1.0f,
        useDetails: Boolean = false,
        button1Text: String = "",
        button1Visible: Boolean = true,
        button2Text: String = "",
        button2Visible: Boolean = true,
        activityType: String = "listening",
        audioQuality: String? = null,
        showArtwork: Boolean = true,
        showElapsedTime: Boolean = true,
        showTrackTitle: Boolean = true,
        showArtist: Boolean = true,
        showAlbum: Boolean = false,
    ): Result<Unit> = runCatching {
        val now = System.currentTimeMillis()
        val safeSpeed = playbackSpeed.takeIf { it.isFinite() && it > 0f } ?: 1f
        val position = (currentPlaybackTimeMillis.coerceAtLeast(0L) / safeSpeed).toLong()
        val remaining = (durationMillis.coerceAtLeast(0L) - currentPlaybackTimeMillis.coerceAtLeast(0L))
            .coerceAtLeast(0L)
        val title = if (safeSpeed != 1f) {
            "${song.title} [${String.format(Locale.ROOT, "%.2fx", safeSpeed)}]"
        } else song.title

        val button1 = if (button1Visible) {
            resolveVariables(button1Text.ifEmpty { DEFAULT_BUTTON_1 }, song) to watchUrl(song)
        } else null
        val button2 = if (button2Visible) {
            resolveVariables(button2Text.ifEmpty { DEFAULT_BUTTON_2 }, song) to PROJECT_URL
        } else null

        // Keep the music metadata on one compact line, with comma-separated
        // fields, rather than splitting title, artist and album across lines.
        // The Social SDK uses Playing for third-party app presence.
        val activityDetails = buildList {
            if (showTrackTitle) title.takeIf(String::isNotBlank)?.let(::add)
            if (showArtist) song.artist.takeIf(String::isNotBlank)?.let(::add)
            if (showAlbum) song.albumName?.takeIf(String::isNotBlank)?.let(::add)
        }.joinToString(", ").take(128).takeIf { it.length >= 2 }

        DiscordSocialPresence.update(
            details = activityDetails,
            state = null,
            imageUrl = song.artworkAt(ART_PX)?.takeIf { showArtwork && (it.startsWith("https://") || it.startsWith("http://")) },
            imageTooltip = audioQuality?.take(128),
            detailsUrl = watchUrl(song),
            // Send both timestamps so Discord has the full track interval for
            // its progress indicator. Discord may render this as remaining time.
            startMs = if (showElapsedTime) (now - position) / 1_000L else 0L,
            endMs = if (showElapsedTime && durationMillis > 0L && remaining > 0L) {
                (now + (remaining / safeSpeed).toLong()) / 1_000L
            } else 0L,
            showArtwork = showArtwork,
            showElapsedTime = showElapsedTime,
            // Discord's Social SDK documents Playing as the supported
            // Rich Presence activity type for third-party app integrations.
            activityType = 0,
            useDetails = true,
            button1Label = button1?.first?.take(32),
            button1Url = button1?.second,
            button2Label = button2?.first?.take(32),
            button2Url = button2?.second,
        )
    }

    fun close() = DiscordSocialPresence.clear()

    /** No separate socket lifecycle is exposed by the Android RPC transport. */
    fun closeRPC() = Unit

    companion object {
        const val PROJECT_URL = "https://github.com/sachit1751-art/Audify"
        const val DEFAULT_BUTTON_1 = "Listen on YouTube Music"
        const val DEFAULT_BUTTON_2 = "Visit Audify"
        private const val ART_PX = 480

        fun watchUrl(song: Song): String = "https://music.youtube.com/watch?v=${song.videoId}"

        fun resolveVariables(text: String, song: Song): String = text
            .replace("{song_name}", song.title)
            .replace("{artist_name}", song.artist)
            .replace("{album_name}", song.albumName ?: "")
    }
}
