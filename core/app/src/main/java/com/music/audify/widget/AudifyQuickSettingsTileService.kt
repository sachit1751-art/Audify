@file:androidx.annotation.OptIn(markerClass = [androidx.media3.common.util.UnstableApi::class])

package com.music.audify.widget

import android.app.PendingIntent
import android.content.ComponentName
import android.content.Intent
import android.annotation.SuppressLint
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import androidx.core.content.ContextCompat
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.music.audify.MainActivity
import com.music.audify.R
import com.music.audify.playback.PlaybackService
import java.util.concurrent.Executor

/**
 * Quick Settings transport backed by Audify's existing Media3 session.
 *
 * It never owns playback state and never starts a second player. The controller
 * is bound only while the tile is listening, and all displayed state comes from
 * that controller rather than a polling loop or a widget snapshot.
 */
class AudifyQuickSettingsTileService : TileService() {

    private var controllerFuture: com.google.common.util.concurrent.ListenableFuture<MediaController>? = null
    private var controller: MediaController? = null
    private var clickPending = false

    private val mainThreadExecutor: Executor by lazy { ContextCompat.getMainExecutor(this) }

    private val playerListener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) {
            updateTile(player)
        }
    }

    override fun onStartListening() {
        super.onStartListening()
        // Give the tile a useful first frame even before the asynchronous
        // session bind completes. A live controller replaces this immediately.
        updateTileFromSnapshot()
        connectToSession()
    }

    override fun onStopListening() {
        clickPending = false
        releaseController()
        super.onStopListening()
    }

    override fun onClick() {
        super.onClick()
        val active = controller
        if (active != null) {
            handleClick(active)
        } else {
            clickPending = true
            connectToSession()
        }
    }

    override fun onDestroy() {
        releaseController()
        super.onDestroy()
    }

    private fun connectToSession() {
        if (controller != null || controllerFuture != null) return

        val token = SessionToken(this, ComponentName(this, PlaybackService::class.java))
        val future = runCatching { MediaController.Builder(this, token).buildAsync() }.getOrNull()
        if (future == null) {
            if (clickPending) {
                clickPending = false
                openAudify()
            }
            return
        }
        controllerFuture = future
        future.addListener(
            {
                val connected = runCatching { future.get() }.getOrNull()
                if (controllerFuture !== future) {
                    MediaController.releaseFuture(future)
                    return@addListener
                }
                controllerFuture = null
                if (connected == null) {
                    if (clickPending) {
                        clickPending = false
                        openAudify()
                    }
                    updateTileFromSnapshot()
                    return@addListener
                }

                controller = connected
                connected.addListener(playerListener)
                updateTile(connected)
                if (clickPending) {
                    clickPending = false
                    handleClick(connected)
                }
            },
            mainThreadExecutor,
        )
    }

    private fun handleClick(player: MediaController) {
        if (player.mediaItemCount == 0) {
            openAudify()
            return
        }

        if (player.playWhenReady) {
            player.pause()
        } else {
            if (player.playbackState == Player.STATE_ENDED) player.seekTo(0L)
            if (player.playbackState == Player.STATE_IDLE) player.prepare()
            player.play()
        }
        updateTile(player)
    }

    private fun updateTileFromSnapshot() {
        val snapshot = MediaWidgetSnapshot.load(this)
        val presentation = quickSettingsTilePresentation(
            hasTrack = snapshot.hasTrack,
            playWhenReady = snapshot.isPlaying,
            title = snapshot.title,
        )
        applyPresentation(presentation)
    }

    private fun updateTile(player: Player) {
        val hasTrack = player.mediaItemCount > 0
        val title = player.currentMediaItem?.mediaMetadata?.title?.toString()
        applyPresentation(
            quickSettingsTilePresentation(
                hasTrack = hasTrack,
                playWhenReady = player.playWhenReady,
                title = title,
            ),
        )
    }

    private fun applyPresentation(presentation: QuickSettingsTilePresentation) {
        val tile = qsTile ?: return
        tile.label = getString(R.string.quick_settings_tile_label)
        tile.state = if (presentation.isActive) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            tile.subtitle = presentation.subtitle
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            tile.stateDescription = presentation.stateDescription
        }
        tile.updateTile()
    }

    @SuppressLint("StartActivityAndCollapseDeprecated")
    private fun openAudify() {
        val intent = Intent(this, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        if (Build.VERSION.SDK_INT >= 34) {
            val pendingIntent = PendingIntent.getActivity(
                this,
                REQUEST_OPEN_APP,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            startActivityAndCollapse(pendingIntent)
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(intent)
        }
    }

    private fun releaseController() {
        controller?.removeListener(playerListener)
        controller = null
        controllerFuture?.let(MediaController::releaseFuture)
        controllerFuture = null
    }

    companion object {
        private const val REQUEST_OPEN_APP = 701
    }
}
