package com.music.audify.data.settings

import android.content.SharedPreferences
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.lang.reflect.Proxy

class DownloadLikedSongsSettingTest {
    private var stored = false
    private var previousPrefs: SharedPreferences? = null
    private val prefsField = AppSettings::class.java.getDeclaredField("prefs").apply {
        isAccessible = true
    }

    @Before
    fun installPreferences() {
        previousPrefs = runCatching { prefsField.get(AppSettings) as? SharedPreferences }.getOrNull()
        val editor = Proxy.newProxyInstance(
            SharedPreferences.Editor::class.java.classLoader,
            arrayOf(SharedPreferences.Editor::class.java),
        ) { proxy, method, args ->
            if (method.name == "putBoolean" && args?.getOrNull(0) == "download_liked_songs") {
                stored = args[1] as Boolean
            }
            when {
                method.returnType == SharedPreferences.Editor::class.java -> proxy
                method.name == "commit" -> true
                else -> null
            }
        }
        val prefs = Proxy.newProxyInstance(
            SharedPreferences::class.java.classLoader,
            arrayOf(SharedPreferences::class.java),
        ) { _, method, args ->
            when (method.name) {
                "edit" -> editor
                "getBoolean" -> if (args?.getOrNull(0) == "download_liked_songs") {
                    stored
                } else {
                    args?.getOrNull(1) as? Boolean ?: false
                }
                "getFloat" -> args?.getOrNull(1) as? Float ?: 1f
                "getInt" -> args?.getOrNull(1) as? Int ?: 0
                "getLong" -> args?.getOrNull(1) as? Long ?: 0L
                "getString" -> args?.getOrNull(1) as? String
                "contains" -> args?.getOrNull(0) == "download_liked_songs"
                "getAll" -> mapOf("download_liked_songs" to stored)
                else -> null
            }
        } as SharedPreferences
        prefsField.set(AppSettings, prefs)
    }

    @After
    fun restorePreferences() {
        AppSettings.downloadLikedSongs.value = false
        stored = false
        previousPrefs?.let { prefsField.set(AppSettings, it) }
    }

    @Test
    fun `liked song download preference updates state and persists`() {
        AppSettings.setDownloadLikedSongs(true)

        assertEquals(true, AppSettings.downloadLikedSongs.value)
        assertEquals(true, stored)

        AppSettings.setDownloadLikedSongs(false)

        assertEquals(false, AppSettings.downloadLikedSongs.value)
        assertEquals(false, stored)
    }
}
