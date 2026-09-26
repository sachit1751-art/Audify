package com.sachit.music.quicksettings

import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService

/**
 * Quick-settings tile that starts shuffled playback of the whole library.
 *
 * The tile itself carries no playback logic: it launches [com.sachit.music.MainActivity]
 * with [ACTION_SHUFFLE_ALL], and the activity performs the action once its player
 * connection is ready (see [com.sachit.music.MainActivity.handleShuffleAllIntent]).
 * This keeps cold-start behaviour safe — the tile works even when the app process
 * and the playback service are not yet running.
 */
class ShuffleAllTileService : TileService() {
    override fun onStartListening() {
        super.onStartListening()
        qsTile?.apply {
            state = Tile.STATE_INACTIVE
            updateTile()
        }
    }

    override fun onClick() {
        super.onClick()
        val launchIntent =
            Intent(this, com.sachit.music.MainActivity::class.java).apply {
                action = ACTION_SHUFFLE_ALL
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION
            }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val pendingIntent =
                android.app.PendingIntent.getActivity(
                    this,
                    0,
                    launchIntent,
                    android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE,
                )
            startActivityAndCollapse(pendingIntent)
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(launchIntent)
        }
    }

    companion object {
        const val ACTION_SHUFFLE_ALL = "com.sachit.music.action.SHUFFLE_ALL"
    }
}
