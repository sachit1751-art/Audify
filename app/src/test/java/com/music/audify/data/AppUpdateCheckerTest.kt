// Copyright (c) 2026 Sachit. All rights reserved.
// Project: AUDIFY
package com.music.audify.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppUpdateCheckerTest {

    @Test
    fun prefersTheMatchingAbiOverTheUniversalApk() {
        val release = release(
            "Audify-v1.8.3.apk" to "https://example.test/universal.apk",
            "Audify-v1.8.3_arm64-v8a.apk" to "https://example.test/arm64.apk",
            "Audify-v1.8.3_x86_64.apk" to "https://example.test/x86.apk",
        )

        assertEquals(
            "https://example.test/arm64.apk",
            AppUpdateChecker.apkAssetUrl(release, listOf("arm64-v8a", "armeabi-v7a")),
        )
    }

    @Test
    fun followsDeviceAbiPreferenceOrder() {
        val release = release(
            "Audify-v1.8.3_arm64-v8a.apk" to "https://example.test/arm64.apk",
            "Audify-v1.8.3_x86_64.apk" to "https://example.test/x86.apk",
        )

        assertEquals(
            "https://example.test/x86.apk",
            AppUpdateChecker.apkAssetUrl(release, listOf("x86_64", "arm64-v8a")),
        )
    }

    @Test
    fun fallsBackToUniversalWhenNoSupportedAbiIsPublished() {
        val release = release(
            "Audify-v1.8.3.apk" to "https://example.test/universal.apk",
            "Audify-v1.8.3_arm64-v8a.apk" to "https://example.test/arm64.apk",
        )

        assertEquals(
            "https://example.test/universal.apk",
            AppUpdateChecker.apkAssetUrl(release, listOf("x86_64")),
        )
    }

    @Test
    fun neverSelectsTheTvApk() {
        val release = release(
            "AudifyTV-v1.8.3.apk" to "https://example.test/tv.apk",
        )

        assertEquals(null, AppUpdateChecker.apkAssetUrl(release, listOf("arm64-v8a")))
    }

    @Test
    fun comparesNumericVersionPartsAndStableOverPrerelease() {
        assertTrue(AppUpdateChecker.isNewer("1.8.10", "1.8.9"))
        assertTrue(AppUpdateChecker.isNewer("1.8.3", "1.8.3-beta2"))
        assertFalse(AppUpdateChecker.isNewer("1.8.3", "1.8.3"))
        assertFalse(AppUpdateChecker.isNewer("1.8.2", "1.8.3"))
    }

    private fun release(vararg assets: Pair<String, String>) = Json.parseToJsonElement(
        """{"assets":[${assets.joinToString(",") { (name, url) ->
            """{"name":"$name","browser_download_url":"$url","state":"uploaded"}"""
        }}]}""",
    ).jsonObject
}
