package com.music.audify.data.discord

import android.app.Activity
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import com.discord.socialsdk.DiscordSocialSdkInit
import java.lang.ref.WeakReference
import kotlin.concurrent.thread

/** Thin main-thread bridge to Discord Social SDK 1.10's Android RPC transport. */
internal object DiscordSocialPresence {
    private const val APPLICATION_ID = 1571895598062506015L
    private val mainHandler = Handler(Looper.getMainLooper())
    private val nativeLoadLock = Any()
    private val nativeReadyActions = mutableListOf<() -> Unit>()
    @Volatile private var nativeLibraryLoaded = false
    private var nativeLibraryLoading = false
    @Volatile private var currentActivity = WeakReference<Activity>(null)
    private var initialized = false
    private var callbacksScheduled = false
    private var presenceActive = false
    private var callbackGraceDeadline = 0L
    private var requestGeneration = 0L

    private val callbackPump = object : Runnable {
        override fun run() {
            if (!initialized) {
                callbacksScheduled = false
                return
            }
            runCatching { nativeRunCallbacks() }
            if (presenceActive || SystemClock.uptimeMillis() < callbackGraceDeadline) {
                mainHandler.postDelayed(this, CALLBACK_INTERVAL_MS)
            } else {
                callbacksScheduled = false
            }
        }
    }

    /** The SDK's Android RPC binder obtains its Activity from this registration. */
    fun attachActivity(activity: Activity) {
        // Do not load the 30 MB native SDK while Android is creating the first
        // frame. Remember the Activity cheaply and register it only when the
        // listener actually enables/publishes Discord Rich Presence.
        currentActivity = WeakReference(activity)
        if (nativeLibraryLoaded) {
            mainHandler.post { registerCurrentActivity() }
        }
    }

    fun update(
        details: String?,
        state: String?,
        imageUrl: String?,
        imageTooltip: String?,
        detailsUrl: String,
        startMs: Long,
        endMs: Long,
        showArtwork: Boolean,
        showElapsedTime: Boolean,
        activityType: Int,
        useDetails: Boolean,
        button1Label: String?,
        button1Url: String?,
        button2Label: String?,
        button2Url: String?,
    ) {
        mainHandler.post {
            val generation = ++requestGeneration
            if (com.music.audify.BuildConfig.DEBUG) {
                android.util.Log.d(TAG, "Presence update queued (generation=$generation)")
            }
            runWhenNativeReady {
                // An update can be queued while the native SDK is loading. If
                // playback stopped or the track changed before loading finished,
                // never publish that stale queued payload.
                if (generation != requestGeneration) {
                    if (com.music.audify.BuildConfig.DEBUG) {
                        android.util.Log.d(TAG, "Dropped stale presence update (generation=$generation)")
                    }
                    return@runWhenNativeReady
                }
                if (!initialized) {
                    registerCurrentActivity()
                    initialized = runCatching { nativeInitialize(APPLICATION_ID) }
                        .onFailure { android.util.Log.w(TAG, "Discord SDK initialization failed", it) }
                        .getOrDefault(false)
                }
                if (!initialized) return@runWhenNativeReady
                presenceActive = true
                ensureCallbackPump()
                if (com.music.audify.BuildConfig.DEBUG) {
                    android.util.Log.d(TAG, "Dispatching current presence to native RPC")
                }
                runCatching {
                    nativeUpdate(
                        details, state, imageUrl, imageTooltip, detailsUrl,
                        startMs, endMs, showArtwork, showElapsedTime, activityType, useDetails,
                        button1Label, button1Url, button2Label, button2Url,
                    )
                }.onFailure { android.util.Log.w(TAG, "Discord presence update failed", it) }
            }
        }
    }

    fun clear() {
        mainHandler.post {
            // Invalidate any update waiting for the native library to load.
            requestGeneration++
            if (com.music.audify.BuildConfig.DEBUG) {
                android.util.Log.d(TAG, "Presence clear requested")
            }
            if (!nativeLibraryLoaded || !initialized) return@post
            presenceActive = false
            callbackGraceDeadline = SystemClock.uptimeMillis() + CLEAR_CALLBACK_GRACE_MS
            runCatching { nativeClear() }
            ensureCallbackPump()
        }
    }

    private fun ensureCallbackPump() {
        if (callbacksScheduled) return
        callbacksScheduled = true
        mainHandler.post(callbackPump)
    }

    private fun registerCurrentActivity() {
        currentActivity.get()?.let { activity ->
            runCatching { DiscordSocialSdkInit.setEngineActivity(activity) }
                .onFailure { android.util.Log.w(TAG, "Discord Activity registration failed", it) }
        }
    }

    /** Load Discord's large native SDK off the UI thread, on first real use only. */
    private fun runWhenNativeReady(action: () -> Unit) {
        synchronized(nativeLoadLock) {
            if (nativeLibraryLoaded) {
                action()
                return
            }
            nativeReadyActions += action
            if (nativeLibraryLoading) return
            nativeLibraryLoading = true
        }
        thread(name = "discord-native-load", priority = Thread.MIN_PRIORITY) {
            val loaded = runCatching { System.loadLibrary("audify_discord") }
                .onFailure { android.util.Log.w(TAG, "Discord native library load failed", it) }
                .isSuccess
            mainHandler.post {
                val actions = synchronized(nativeLoadLock) {
                    nativeLibraryLoading = false
                    nativeLibraryLoaded = loaded
                    nativeReadyActions.toList().also { nativeReadyActions.clear() }
                }
                if (loaded) actions.forEach { it() }
            }
        }
    }

    private external fun nativeInitialize(applicationId: Long): Boolean
    private external fun nativeUpdate(
        details: String?,
        state: String?,
        imageUrl: String?,
        imageTooltip: String?,
        detailsUrl: String,
        startMs: Long,
        endMs: Long,
        showArtwork: Boolean,
        showElapsedTime: Boolean,
        activityType: Int,
        useDetails: Boolean,
        button1Label: String?,
        button1Url: String?,
        button2Label: String?,
        button2Url: String?,
    )
    private external fun nativeClear()
    private external fun nativeRunCallbacks()

    // RPC callbacks are not frame-rendering work. 10 Hz keeps acknowledgements
    // responsive without waking the UI thread 20 times per second all playback.
    private const val CALLBACK_INTERVAL_MS = 100L
    private const val CLEAR_CALLBACK_GRACE_MS = 2_000L
    private const val TAG = "AudifyDiscord"
}
