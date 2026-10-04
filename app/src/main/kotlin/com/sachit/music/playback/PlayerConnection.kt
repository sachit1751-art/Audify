/**
 * SachitMusic Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.sachit.music.playback

import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM
import androidx.media3.common.Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM
import androidx.media3.common.Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM
import androidx.media3.common.Player.REPEAT_MODE_OFF
import androidx.media3.common.Player.STATE_ENDED
import androidx.media3.common.Timeline
import androidx.media3.exoplayer.ExoPlayer
import com.sachit.music.db.MusicDatabase
import com.sachit.music.db.entities.Song
import com.sachit.music.extensions.currentMetadata
import com.sachit.music.extensions.getCurrentQueueIndex
import com.sachit.music.extensions.getQueueWindows
import com.sachit.music.extensions.metadata
import com.sachit.music.extensions.togglePlayPause
import com.sachit.music.extensions.withUpdatedMetadata
import com.sachit.music.models.toMediaMetadata
import com.sachit.music.playback.MusicService.MusicBinder
import com.sachit.music.playback.queues.Queue
import com.sachit.music.utils.SettingsProperties
import com.sachit.music.utils.reportException
import com.sachit.music.utils.settings
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import timber.log.Timber
import java.time.LocalDate
import java.time.LocalTime
import kotlin.math.roundToInt

@OptIn(ExperimentalCoroutinesApi::class)
class PlayerConnection(
    context: Context,
    binder: MusicBinder,
    val database: MusicDatabase,
    private val scope: CoroutineScope,
) : Player.Listener {
    private companion object {
        private const val TAG = "PlayerConnection"
    }

    val service = binder.service
    private val playerReadinessFlow = service.isPlayerReady

    private fun getPlayerSafe(): ExoPlayer {
        check(playerReadinessFlow.value) {
            "Player not yet initialized in MusicService; " +
                "service.isPlayerReady=${playerReadinessFlow.value}"
        }
        return try {
            service.player
        } catch (e: UninitializedPropertyAccessException) {
            throw IllegalStateException(
                "MusicService.player field not initialized despite isPlayerReady=true; " +
                    "possible race condition in service startup",
                e,
            )
        }
    }

    private fun getPlayerOrNull(): ExoPlayer? =
        try {
            if (!playerReadinessFlow.value) return null
            service.player
        } catch (_: UninitializedPropertyAccessException) {
            null
        } catch (_: NullPointerException) {
            null
        }

    val player: ExoPlayer
        get() = getPlayerSafe()

    /** Tracks whether player initialization completed successfully */
    private val isPlayerInitialized = MutableStateFlow(service.isPlayerReady.value)

    val playbackState: MutableStateFlow<Int>
    private val playWhenReady: MutableStateFlow<Boolean>
    val isPlaying: kotlinx.coroutines.flow.StateFlow<Boolean>

    private val initialState: Triple<Int, Boolean, Boolean> =
        try {
            val initialPlayer = getPlayerOrNull()
            if (initialPlayer != null) {
                Triple(
                    initialPlayer.playbackState,
                    initialPlayer.playWhenReady,
                    initialPlayer.playWhenReady && initialPlayer.playbackState != STATE_ENDED,
                )
            } else {
                Timber.tag(TAG).w("Player not ready during construction; using safe defaults")
                Triple(Player.STATE_IDLE, false, false)
            }
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Error during PlayerConnection initialization, using defaults")
            Triple(Player.STATE_IDLE, false, false)
        }

    init {
        Timber.tag(TAG).d("PlayerConnection init: playerReady=${playerReadinessFlow.value}")

        playbackState = MutableStateFlow(initialState.first)
        playWhenReady = MutableStateFlow(initialState.second)
        isPlaying =
            combine(playbackState, playWhenReady) { state, ready ->
                ready && state != STATE_ENDED
            }.stateIn(
                scope,
                SharingStarted.Lazily,
                initialState.third,
            )

        // Track service readiness changes in background.
        scope.launch {
            playerReadinessFlow.collect { ready ->
                isPlayerInitialized.value = ready
                if (ready) {
                    Timber.tag(TAG).d("Service player initialization detected by PlayerConnection")
                }
            }
        }

        Timber.tag(TAG).d("PlayerConnection state flows initialized successfully")
    }

    val isEffectivelyPlaying =
        combine(
            isPlaying,
            service.castConnectionHandler?.isCasting ?: MutableStateFlow(false),
            service.castConnectionHandler?.castIsPlaying ?: MutableStateFlow(false),
        ) { localPlaying, isCasting, castPlaying ->
            if (isCasting) castPlaying else localPlaying
        }.stateIn(
            scope,
            SharingStarted.Lazily,
            initialState.third,
        )

    val mediaMetadata = MutableStateFlow(getPlayerOrNull()?.currentMetadata)
    // stateIn so the latest DB result is cached and shared: on resume / re-subscription the value
    // is available immediately instead of re-running the Room query (which delayed now-playing
    // details, format and like-state on every foreground). Lazily keeps it hot across lifecycle
    // pauses, matching isPlaying above. StateFlow is still a Flow, so existing collectors are unaffected.
    val currentSong =
        mediaMetadata.flatMapLatest {
            database.song(it?.id)
        }.stateIn(scope, SharingStarted.Lazily, null)
    val currentLyrics =
        mediaMetadata.flatMapLatest { mediaMetadata ->
            database.lyrics(mediaMetadata?.id)
        }.stateIn(scope, SharingStarted.Lazily, null)
    val currentFormat =
        mediaMetadata.flatMapLatest { mediaMetadata ->
            database.format(mediaMetadata?.id)
        }.stateIn(scope, SharingStarted.Lazily, null)

    val queueTitle = MutableStateFlow<String?>(null)
    val queueWindows = MutableStateFlow<List<Timeline.Window>>(emptyList())
    val currentMediaItemIndex = MutableStateFlow(-1)
    val currentWindowIndex = MutableStateFlow(-1)

    val shuffleModeEnabled = MutableStateFlow(false)
    val repeatMode = MutableStateFlow(REPEAT_MODE_OFF)

    val canSkipPrevious = MutableStateFlow(true)
    val canSkipNext = MutableStateFlow(true)

    val error = MutableStateFlow<PlaybackException?>(null)
    val isMuted = service.isMuted
    val currentStreamClient = service.currentStreamClient

    /** Queue rows jumped over by forward jumps this session (plan 020). */
    val skippedInQueueIds = service.skippedInQueueIds

    /** Stream source health of the current media item (plan 021). */
    val streamHealth = service.streamHealth

    val waitingForNetworkConnection = service.waitingForNetworkConnection

    // Callback to check if playback changes should be blocked (e.g., Listen Together guest)
    var shouldBlockPlaybackChanges: (() -> Boolean)? = null

    // Flag to allow internal sync operations to bypass blocking (set by ListenTogetherManager)
    @Volatile
    var allowInternalSync: Boolean = false

    var onSkipPrevious: (() -> Unit)? = null
    var onSkipNext: (() -> Unit)? = null

    private var attachedPlayer: Player? = null

    init {
        scope.launch {
            service.playerFlow.collect { newPlayer ->
                if (newPlayer != null && newPlayer != attachedPlayer) {
                    updateAttachedPlayer(newPlayer)
                }
            }
        }
        val readyPlayer = getPlayerOrNull()
        if (attachedPlayer == null && readyPlayer != null) {
            updateAttachedPlayer(readyPlayer)
        }

        Timber.tag(TAG).d("PlayerConnection flow observer registered; playerReady=${playerReadinessFlow.value}")
    }

    private fun updateAttachedPlayer(newPlayer: Player) {
        attachedPlayer?.removeListener(this)
        attachedPlayer = newPlayer
        newPlayer.addListener(this)
        // Refresh all state from new player
        playbackState.value = newPlayer.playbackState
        playWhenReady.value = newPlayer.playWhenReady
        mediaMetadata.value = newPlayer.currentMetadata
        queueTitle.value = service.queueTitle
        queueWindows.value = newPlayer.getQueueWindows()
        currentWindowIndex.value = newPlayer.getCurrentQueueIndex()
        currentMediaItemIndex.value = newPlayer.currentMediaItemIndex
        shuffleModeEnabled.value = newPlayer.shuffleModeEnabled
        repeatMode.value = newPlayer.repeatMode
        Timber.tag(TAG).d("Attached to new player instance: $newPlayer")
    }

    fun playQueue(queue: Queue) {
        // Block if Listen Together guest (unless internal sync)
        if (!allowInternalSync && shouldBlockPlaybackChanges?.invoke() == true) {
            Timber.tag("PlayerConnection").d("playQueue blocked - Listen Together guest")
            return
        }
        if (!playerReadinessFlow.value) {
            Timber.tag(TAG).w("playQueue called before player ready; delegating to service")
        }
        try {
            service.playQueue(queue)
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Error in playQueue")
            throw e
        }
    }

    fun refreshSongMetadata(song: Song) {
        val player = getPlayerOrNull() ?: return
        val updatedMetadata = song.toMediaMetadata()
        repeat(player.mediaItemCount) { index ->
            val mediaItem = player.getMediaItemAt(index)
            if (mediaItem.mediaId == song.id) {
                player.replaceMediaItem(index, mediaItem.withUpdatedMetadata(updatedMetadata))
            }
        }
        mediaMetadata.value = player.currentMetadata
    }

    fun startRadioSeamlessly() {
        // Block if Listen Together guest
        if (shouldBlockPlaybackChanges?.invoke() == true) {
            Timber.tag("PlayerConnection").d("startRadioSeamlessly blocked - Listen Together guest")
            return
        }
        if (!playerReadinessFlow.value) {
            Timber.tag(TAG).w("startRadioSeamlessly called before player ready; delegating to service")
        }
        try {
            service.startRadioSeamlessly()
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Error in startRadioSeamlessly")
            throw e
        }
    }

    /**
     * Starts the radio and reports the outcome through [onResult] so the UI can
     * show a snackbar/toast when the radio could not be started (offline etc.).
     */
    fun startRadioWithFeedback(onResult: (MusicService.RadioStartResult) -> Unit = {}) {
        // Block if Listen Together guest
        if (shouldBlockPlaybackChanges?.invoke() == true) {
            Timber.tag("PlayerConnection").d("startRadioWithFeedback blocked - Listen Together guest")
            return
        }
        try {
            service.startRadioAsync(onResult)
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Error in startRadioWithFeedback")
            onResult(MusicService.RadioStartResult.Failed)
        }
    }

    fun playNext(item: MediaItem) = playNext(listOf(item))

    fun playNext(items: List<MediaItem>) {
        // Block if Listen Together guest (unless internal sync)
        if (!allowInternalSync && shouldBlockPlaybackChanges?.invoke() == true) {
            Timber.tag("PlayerConnection").d("playNext blocked - Listen Together guest")
            return
        }
        try {
            service.playNext(items)
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Error in playNext")
            throw e
        }
    }

    fun addToQueue(item: MediaItem) = addToQueue(listOf(item))

    fun addToQueue(items: List<MediaItem>) {
        // Block if Listen Together guest (unless internal sync)
        if (!allowInternalSync && shouldBlockPlaybackChanges?.invoke() == true) {
            Timber.tag("PlayerConnection").d("addToQueue blocked - Listen Together guest")
            return
        }
        try {
            service.addToQueue(items)
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Error in addToQueue")
            throw e
        }
    }

    fun toggleLike() {
        try {
            service.toggleLike()
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Error in toggleLike")
        }
    }

    fun toggleMute() {
        service.toggleMute()
    }

    fun setMuted(muted: Boolean) {
        service.setMuted(muted)
    }

    fun toggleLibrary() {
        try {
            service.toggleLibrary()
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Error in toggleLibrary")
        }
    }

    /**
     * Toggle play/pause - handles Cast when active
     */
    fun togglePlayPause() {
        if (!allowInternalSync && shouldBlockPlaybackChanges?.invoke() == true) return
        try {
            val castHandler = service.castConnectionHandler
            if (castHandler?.isCasting?.value == true) {
                if (castHandler.castIsPlaying.value) {
                    castHandler.pause()
                } else {
                    castHandler.play()
                }
            } else {
                player.togglePlayPause()
            }
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Error in togglePlayPause")
        }
    }

    /**
     * Start playback - handles Cast when active
     */
    fun play() {
        try {
            val castHandler = service.castConnectionHandler
            if (castHandler?.isCasting?.value == true) {
                castHandler.play()
            } else {
                if (player.playbackState == Player.STATE_IDLE) {
                    player.prepare()
                }
                player.playWhenReady = true
            }
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Error in play")
        }
    }

    /**
     * Pause playback - handles Cast when active
     */
    fun pause() {
        try {
            val castHandler = service.castConnectionHandler
            if (castHandler?.isCasting?.value == true) {
                castHandler.pause()
            } else {
                player.playWhenReady = false
            }
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Error in pause")
        }
    }

    /**
     * Seek to position - handles Cast when active
     */
    fun seekTo(position: Long) {
        try {
            val castHandler = service.castConnectionHandler
            if (castHandler?.isCasting?.value == true) {
                castHandler.seekTo(position)
            } else {
                player.seekTo(position)
            }
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Error in seekTo")
        }
    }

    fun seekToNext() {
        try {
            // When casting, use Cast skip instead of local player
            val castHandler = service.castConnectionHandler
            if (castHandler?.isCasting?.value == true) {
                castHandler.skipToNext()
                return
            }
            player.seekToNext()
            if (player.playbackState == Player.STATE_IDLE || player.playbackState == Player.STATE_ENDED) {
                player.prepare()
            }
            player.playWhenReady = true
            onSkipNext?.invoke()
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Error in seekToNext")
        }
    }

    var onRestartSong: (() -> Unit)? = null

    fun seekToPrevious() {
        try {
            // When casting, use Cast skip instead of local player
            val castHandler = service.castConnectionHandler
            if (castHandler?.isCasting?.value == true) {
                castHandler.skipToPrevious()
                return
            }

            // Logic to mimic standard seekToPrevious behavior but with explicit callbacks
            // If we are more than 3 seconds in, just restart the song
            if (player.currentPosition > 3000 || !player.hasPreviousMediaItem()) {
                player.seekTo(0)
                if (player.playbackState == Player.STATE_IDLE || player.playbackState == Player.STATE_ENDED) {
                    player.prepare()
                }
                player.playWhenReady = true
                onRestartSong?.invoke()
            } else {
                // Otherwise go to previous media item
                player.seekToPreviousMediaItem()
                if (player.playbackState == Player.STATE_IDLE || player.playbackState == Player.STATE_ENDED) {
                    player.prepare()
                }
                player.playWhenReady = true
                onSkipPrevious?.invoke()
            }
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Error in seekToPrevious")
        }
    }

    /**
     * Starts the automatic sleep timer if the stored schedule says one should start now.
     *
     * Runs on [scope] rather than blocking: this is called from [onPlayWhenReadyChanged], an ExoPlayer
     * callback on the main thread, and the previous implementation read seven preferences there, each
     * through `runBlocking(Dispatchers.IO)` over a disk-backed DataStore. The rules live in
     * [SleepTimerPolicy] so they are testable without a player, a service or a clock.
     */
    private fun checkAndStartAutomaticSleepTimer() {
        scope.launch {
            try {
                val settings = service.applicationContext.settings()

                if (!settings.read(SettingsProperties.sleepTimerEnabled)) {
                    Timber.tag(TAG).d("\u2717 Sleep Timer disabled - skipping")
                    return@launch
                }

                if (service.sleepTimer?.isActive == true) {
                    Timber.tag(TAG).d("\u2717 Sleep Timer already active - skipping")
                    return@launch
                }

                val schedule =
                    SleepTimerSchedule(
                        repeat = settings.read(SettingsProperties.sleepTimerRepeat),
                        startTime = settings.read(SettingsProperties.sleepTimerStartTime),
                        endTime = settings.read(SettingsProperties.sleepTimerEndTime),
                        defaultMinutes = settings.read(SettingsProperties.sleepTimerDefaultMinutes).roundToInt(),
                        customDays = settings.read(SettingsProperties.sleepTimerCustomDays),
                        dayTimes = settings.read(SettingsProperties.sleepTimerDayTimes),
                    )

                when (
                    val decision =
                        SleepTimerPolicy.evaluate(
                            schedule = schedule,
                            date = LocalDate.now(),
                            time = LocalTime.now(),
                        )
                ) {
                    is SleepTimerDecision.Start -> {
                        Timber.tag(TAG).i("AUTO SLEEP TIMER STARTED: ${decision.minutes} minutes")
                        service.sleepTimer?.start(decision.minutes)
                    }

                    SleepTimerDecision.DayNotAllowed -> Timber.tag(TAG).d("\u2717 Day not allowed for Sleep Timer")
                    SleepTimerDecision.OutsideWindow -> Timber.tag(TAG).d("\u2717 Time not in range")
                    is SleepTimerDecision.InvalidSchedule ->
                        Timber.tag(TAG).w("\u2717 Sleep Timer schedule could not be parsed: $schedule")
                    SleepTimerDecision.NotEnabled,
                    SleepTimerDecision.AlreadyActive,
                    -> Timber.tag(TAG).d("\u2717 Sleep Timer not started")
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.tag(TAG).e(e, "Sleep Timer error")
            }
        }
    }

    /** Dislikes the current track and skips it. */
    fun dislike() {
        service.dislikeCurrentTrack()
    }

    override fun onPlaybackStateChanged(state: Int) {
        playbackState.value = state
        error.value = player.playerError
    }

    override fun onPlayWhenReadyChanged(
        newPlayWhenReady: Boolean,
        reason: Int,
    ) {
        val wasPlaying = playWhenReady.value
        playWhenReady.value = newPlayWhenReady

        // Central sleep timer trigger: fires on every paused -> playing transition,
        if (newPlayWhenReady && !wasPlaying) {
            checkAndStartAutomaticSleepTimer()
        }
    }

    override fun onMediaItemTransition(
        mediaItem: MediaItem?,
        reason: Int,
    ) {
        mediaMetadata.value = mediaItem?.metadata
        currentMediaItemIndex.value = player.currentMediaItemIndex
        currentWindowIndex.value = player.getCurrentQueueIndex()
        updateCanSkipPreviousAndNext()
    }

    override fun onTimelineChanged(
        timeline: Timeline,
        reason: Int,
    ) {
        queueWindows.value = player.getQueueWindows()
        queueTitle.value = service.queueTitle
        currentMediaItemIndex.value = player.currentMediaItemIndex
        currentWindowIndex.value = player.getCurrentQueueIndex()
        updateCanSkipPreviousAndNext()
    }

    override fun onShuffleModeEnabledChanged(enabled: Boolean) {
        shuffleModeEnabled.value = enabled
        queueWindows.value = player.getQueueWindows()
        currentWindowIndex.value = player.getCurrentQueueIndex()
        updateCanSkipPreviousAndNext()
    }

    override fun onRepeatModeChanged(mode: Int) {
        if (mode != player.repeatMode) return
        repeatMode.value = mode
        updateCanSkipPreviousAndNext()
    }

    override fun onPlayerErrorChanged(playbackError: PlaybackException?) {
        if (playbackError != null) {
            reportException(playbackError)
        }
        error.value = playbackError
    }

    private fun updateCanSkipPreviousAndNext() {
        if (!player.currentTimeline.isEmpty) {
            val window =
                player.currentTimeline.getWindow(player.currentMediaItemIndex, Timeline.Window())
            canSkipPrevious.value = player.isCommandAvailable(COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM) ||
                !window.isLive ||
                player.isCommandAvailable(COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM)
            canSkipNext.value = window.isLive &&
                window.isDynamic ||
                player.isCommandAvailable(COMMAND_SEEK_TO_NEXT_MEDIA_ITEM)
        } else {
            canSkipPrevious.value = false
            canSkipNext.value = false
        }
    }

    fun dispose() {
        try {
            attachedPlayer?.removeListener(this)
            attachedPlayer = null
            Timber.tag(TAG).d("PlayerConnection disposed successfully")
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Error during PlayerConnection disposal")
        }
    }
}
