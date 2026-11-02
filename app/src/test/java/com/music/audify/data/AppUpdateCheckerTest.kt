// Copyright (c) 2026 Sachit. All rights reserved.
// Project: AUDIFY
package com.music.audify.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files

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
    fun ignoresAssetsThatAreNotUploadedPhoneApks() {
        val release = Json.parseToJsonElement(
            """{"assets":[
                {"name":"Audify-v1.8.4.apk","browser_download_url":"https://example.test/pending.apk","state":"new"},
                {"name":"Audify-v1.8.4.zip","browser_download_url":"https://example.test/archive.zip","state":"uploaded"},
                {"name":"AudifyTV-v1.8.4.apk","browser_download_url":"https://example.test/tv.apk","state":"uploaded"}
            ]}""",
        ).jsonObject

        assertEquals(null, AppUpdateChecker.apkAssetUrl(release, listOf("arm64-v8a")))
    }

    @Test
    fun comparesNumericVersionPartsAndStableOverPrerelease() {
        assertTrue(AppUpdateChecker.isNewer("1.8.10", "1.8.9"))
        assertTrue(AppUpdateChecker.isNewer("1.8.3", "1.8.3-beta2"))
        assertFalse(AppUpdateChecker.isNewer("1.8.3", "1.8.3"))
        assertFalse(AppUpdateChecker.isNewer("1.8.2", "1.8.3"))
    }

    @Test
    fun releaseComparisonCoversFreshInstallCurrentReleaseAndAvailableUpgrade() {
        // A fresh install runs the same comparison against its bundled version.
        assertFalse(AppUpdateChecker.isNewer("1.8.3", "1.8.3"))
        assertFalse(AppUpdateChecker.isNewer("1.8.2", "1.8.3"))
        assertTrue(AppUpdateChecker.isNewer("1.8.4", "1.8.3"))
    }

    @Test
    fun failedDownloadCanBeRetriedAndProducesTheCompleteApk() {
        val server = MockWebServer()
        val directory = Files.createTempDirectory("audify-updater-test").toFile()
        try {
            server.enqueue(MockResponse().setResponseCode(503).setBody("temporarily unavailable"))
            server.enqueue(MockResponse().setResponseCode(200).setBody("valid-apk-payload"))
            server.start()

            val target = directory.resolve("audify-1.8.4.apk")
            val client = OkHttpClient()
            assertThrows(IllegalStateException::class.java) {
                AppUpdateChecker.downloadToFile(server.url("/update.apk").toString(), target, client)
            }
            assertFalse(target.exists())

            assertTrue(
                AppUpdateChecker.downloadToFile(server.url("/update.apk").toString(), target, client),
            )
            assertEquals("valid-apk-payload", target.readText())
            assertEquals(2, server.requestCount)
        } finally {
            server.shutdown()
            directory.deleteRecursively()
        }
    }

    @Test
    fun cancelledDownloadDoesNotReportACompletedFile() {
        val server = MockWebServer()
        val directory = Files.createTempDirectory("audify-updater-cancel-test").toFile()
        try {
            server.enqueue(MockResponse().setBody("payload-that-must-not-complete"))
            server.start()
            val target = directory.resolve("cancelled.apk")

            assertFalse(
                AppUpdateChecker.downloadToFile(
                    url = server.url("/update.apk").toString(),
                    target = target,
                    isCancelled = { true },
                ),
            )
        } finally {
            server.shutdown()
            directory.deleteRecursively()
        }
    }

    private fun release(vararg assets: Pair<String, String>) = Json.parseToJsonElement(
        """{"assets":[${assets.joinToString(",") { (name, url) ->
            """{"name":"$name","browser_download_url":"$url","state":"uploaded"}"""
        }}]}""",
    ).jsonObject
}
