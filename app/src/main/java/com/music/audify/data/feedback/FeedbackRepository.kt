// Copyright (c) 2026 Sachit. All rights reserved.
// Project: AUDIFY
package com.music.audify.data.feedback

import android.os.Build
import com.music.audify.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/** Anonymous feedback submission. No Audify account, email address, or user ID is sent. */
object FeedbackRepository {
    data class Receipt(val number: Int, val url: String)

    suspend fun submit(
        kind: String,
        category: String,
        title: String,
        description: String,
        includeDiagnostics: Boolean,
    ): Receipt = withContext(Dispatchers.IO) {
        require(kind == "bug" || kind == "feature")
        val payload = JSONObject()
            .put("kind", kind)
            .put("category", category)
            .put("title", title.trim())
            .put("description", description.trim())
            .put("version", BuildConfig.VERSION_NAME)
            .put("platform", "Android")
            .put("website", "")

        if (includeDiagnostics) {
            payload.put("diagnostics", JSONObject()
                .put("os", "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
                .put("device", "${Build.MANUFACTURER} ${Build.MODEL}".take(120)))
        }

        val base = BuildConfig.LISTEN_TOGETHER_SERVER.trim().trimEnd('/')
        if (!base.startsWith("https://")) throw IOException("Secure feedback service is unavailable.")
        val connection = (URL("$base/api/feedback").openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 12_000
            readTimeout = 12_000
            doOutput = true
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
            setRequestProperty("Accept", "application/json")
        }

        try {
            connection.outputStream.use { it.write(payload.toString().toByteArray(Charsets.UTF_8)) }
            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val responseText = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            val response = runCatching { JSONObject(responseText) }.getOrNull()
            if (status !in 200..299 || response?.optBoolean("ok") != true) {
                val message = response?.optString("message")?.takeIf(String::isNotBlank)
                    ?: when (status) {
                        429 -> "Too many submissions. Please try again later."
                        503 -> "Feedback is temporarily unavailable. Please try again later."
                        else -> "Could not submit feedback. Please try again later."
                    }
                throw IOException(message)
            }
            Receipt(response.optInt("number"), response.optString("url"))
        } finally {
            connection.disconnect()
        }
    }
}
