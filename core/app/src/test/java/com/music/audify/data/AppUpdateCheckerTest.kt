// Copyright (c) 2026 Sachit. All rights reserved.
// Project: AUDIFY
package com.music.audify.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
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
    fun stableChannelExcludesPrereleasesAndDrafts() {
        val releases = Json.parseToJsonElement(
            """[
                {"tag_name":"v1.9.4","draft":false,"prerelease":false},
                {"tag_name":"v1.9.9-beta.3","draft":false,"prerelease":true},
                {"tag_name":"v13.6.3+76dee453","draft":false,"prerelease":false},
                {"tag_name":"v2.0.0","draft":true,"prerelease":false}
            ]""",
        ).jsonArray

        assertEquals(
            "v1.9.4",
            AppUpdateChecker.selectRelease(releases, includePrereleases = false)
                ?.get("tag_name")?.jsonPrimitive?.content,
        )
    }

    @Test
    fun stableChannelUsesNewestPublicationDateRatherThanLargestVersionTag() {
        val releases = Json.parseToJsonElement(
            """[
                {"tag_name":"v13.6.3+76dee453","draft":false,"prerelease":false,"published_at":"2024-02-01T10:00:00Z"},
                {"tag_name":"v1.9.4","draft":false,"prerelease":false,"published_at":"2026-10-18T10:00:00Z"},
                {"tag_name":"v1.9.5","draft":false,"prerelease":false,"published_at":"2026-11-20T10:00:00Z"}
            ]""",
        ).jsonArray

        assertEquals(
            "v1.9.5",
            AppUpdateChecker.selectRelease(releases, includePrereleases = false)
                ?.get("tag_name")?.jsonPrimitive?.content,
        )
    }

    @Test
    fun betaChannelUsesNewestPublicationDateWithinPrereleasesOnly() {
        val releases = Json.parseToJsonElement(
            """[
                {"tag_name":"v2.0.0","draft":false,"prerelease":false,"published_at":"2026-11-23T10:00:00Z"},
                {"tag_name":"v1.9.9-beta.2","draft":false,"prerelease":true,"published_at":"2026-11-20T10:00:00Z"},
                {"tag_name":"v1.9.8-beta.10","draft":false,"prerelease":true,"published_at":"2026-11-22T10:00:00Z"}
            ]""",
        ).jsonArray

        assertEquals(
            "v1.9.8-beta.10",
            AppUpdateChecker.selectRelease(releases, includePrereleases = true)
                ?.get("tag_name")?.jsonPrimitive?.content,
        )
    }

    @Test
    fun betaChannelSelectsNewestPrereleaseAndExcludesStableAndDrafts() {
        val releases = Json.parseToJsonElement(
            """[
                {"tag_name":"v1.9.8","draft":false,"prerelease":false},
                {"tag_name":"v1.9.9-beta.10","draft":false,"prerelease":true},
                {"tag_name":"v1.9.9-beta.2","draft":false,"prerelease":true},
                {"tag_name":"v1.10.0-beta.1","draft":true,"prerelease":true}
            ]""",
        ).jsonArray

        assertEquals(
            "v1.9.9-beta.10",
            AppUpdateChecker.selectRelease(releases, includePrereleases = true)
                ?.get("tag_name")?.jsonPrimitive?.content,
        )
    }

    @Test
    fun currentReleaseAssetNamesPreferTheMatchingAbiAndNotTheUniversalApk() {
        val release = release(
            "Audify-prod-universal-release.apk" to "https://example.test/universal.apk",
            "Audify-prod-arm64-v8a-release.apk" to "https://example.test/arm64.apk",
            "AudifyWear-prod-arm64-v8a-release.apk" to "https://example.test/watch.apk",
            "tv-prod-arm64-v8a-release.apk" to "https://example.test/tv.apk",
        )

        assertEquals(
            "https://example.test/arm64.apk",
            AppUpdateChecker.apkAssetUrl(release, listOf("arm64-v8a", "armeabi-v7a")),
        )
    }

    @Test
    fun currentReleaseAssetNamesFallBackToUniversalWhenAbiIsUnavailable() {
        val release = release(
            "Audify-prod-universal-release.apk" to "https://example.test/universal.apk",
            "Audify-prod-arm64-v8a-release.apk" to "https://example.test/arm64.apk",
        )

        assertEquals(
            "https://example.test/universal.apk",
            AppUpdateChecker.apkAssetUrl(release, listOf("x86_64")),
        )
    }

    @Test
    fun releaseAssetCarriesItsPublishedSha256Digest() {
        val release = Json.parseToJsonElement(
            """{"assets":[{"name":"Audify-prod-arm64-v8a-release.apk","browser_download_url":"https://example.test/app.apk","state":"uploaded","digest":"sha256:${"a".repeat(64)}"}]}""",
        ).jsonObject

        assertEquals(
            "a".repeat(64),
            AppUpdateChecker.apkAsset(release, listOf("arm64-v8a"))?.sha256,
        )
    }

    @Test
    fun sha256VerificationRejectsAlteredApkBytes() {
        val file = Files.createTempFile("audify-digest-test", ".apk").toFile()
        try {
            file.writeText("not-the-published-apk")
            assertThrows(IllegalArgumentException::class.java) {
                AppUpdateChecker.validateSha256(file, "0".repeat(64))
            }
        } finally {
            file.delete()
        }
    }

    @Test
    fun versionComparisonOrdersBetaRcAndStableBuildsCorrectly() {
        assertTrue(AppUpdateChecker.isNewer("1.9.6-beta.10", "1.9.6-beta.2"))
        assertTrue(AppUpdateChecker.isNewer("1.9.6-rc.1", "1.9.6-beta.10"))
        assertTrue(AppUpdateChecker.isNewer("1.9.6", "1.9.6-rc.1"))
        assertFalse(AppUpdateChecker.isNewer("1.9.6-beta.2", "1.9.6-beta.10"))
        assertFalse(AppUpdateChecker.isNewer("1.9.5", "1.9.6-beta.1"))
    }

    @Test
    fun channelWithNoMatchingReleaseReturnsNoCandidate() {
        val releases = Json.parseToJsonElement(
            """[{"tag_name":"v1.9.8","draft":false,"prerelease":false}]""",
        ).jsonArray

        assertEquals(null, AppUpdateChecker.selectRelease(releases, includePrereleases = true))
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
    fun oldCertificateInstallationsRequireManualMigrationButNewerOnesDoNot() {
        assertTrue(AppUpdateChecker.requiresManualMigration("1.8.7", "1.8.9"))
        assertTrue(AppUpdateChecker.requiresManualMigration("1.8.6", "1.8.9"))
        assertTrue(AppUpdateChecker.requiresManualMigration("1.8.7", "1.8.10"))
        assertFalse(AppUpdateChecker.requiresManualMigration("1.8.8", "1.8.9"))
        assertFalse(AppUpdateChecker.requiresManualMigration("1.8.9", "1.8.10"))
        assertFalse(AppUpdateChecker.requiresManualMigration("1.8.7", "1.8.8"))
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
            assertFalse("A cancelled download must not leave a partial APK", target.exists())
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
