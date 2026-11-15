// Copyright (c) 2026 Sachit. All rights reserved.
// Project: AUDIFY
package com.music.audify.data.feedback

import android.content.Context
import android.net.Uri
import android.os.Build
import com.music.audify.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.IOException
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL

/** Anonymous feedback submission. No Audify account, email address, or user ID is sent. */
object FeedbackRepository {
    data class Receipt(val number: Int, val url: String)
    data class Attachment(val bytes: ByteArray, val mimeType: String)

    suspend fun readAttachment(context: Context, uri: Uri): Attachment = withContext(Dispatchers.IO) {
        val mime = context.contentResolver.getType(uri)?.lowercase()?.substringBefore(';')
        require(mime in setOf("image/jpeg", "image/png", "image/webp")) {
            "Choose a JPEG, PNG or WebP screenshot."
        }
        val input = context.contentResolver.openInputStream(uri)
            ?: throw IOException("Could not read the selected screenshot.")
        val output = ByteArrayOutputStream()
        input.use { stream ->
            val buffer = ByteArray(16 * 1024)
            while (true) {
                val count = stream.read(buffer)
                if (count < 0) break
                require(output.size() + count <= 5 * 1024 * 1024) { "Screenshot must be 5 MB or smaller." }
                output.write(buffer, 0, count)
            }
        }
        Attachment(output.toByteArray(), mime!!)
    }

    suspend fun submit(
        kind: String,
        category: String,
        title: String,
        description: String,
        includeDiagnostics: Boolean,
        diagnosticDetails: Map<String, String> = emptyMap(),
        attachmentBytes: ByteArray? = null,
        attachmentMimeType: String? = null,
        attachmentName: String? = null,
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
            val diagnostics = JSONObject()
                .put("os", "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
                .put("device", "${Build.MANUFACTURER} ${Build.MODEL}".take(120))
            diagnosticDetails.forEach { (key, value) ->
                if (key.matches(Regex("[a-zA-Z0-9_]{1,40}")) && value.isNotBlank()) {
                    diagnostics.put(key, value.take(240))
                }
            }
            payload.put("diagnostics", diagnostics)
        }
        if (attachmentBytes != null) {
            require(attachmentBytes.size in 1..(5 * 1024 * 1024)) { "Screenshot must be 5 MB or smaller." }
            val mime = attachmentMimeType?.lowercase()?.substringBefore(';')
            require(mime in setOf("image/jpeg", "image/png", "image/webp")) {
                "Choose a JPEG, PNG or WebP screenshot."
            }
            val base = BuildConfig.LISTEN_TOGETHER_SERVER.trim().trimEnd('/')
            if (!base.startsWith("https://")) throw IOException("Secure feedback service is unavailable.")
            val upload = (URL("$base/api/feedback/attachment").openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 12_000
                readTimeout = 20_000
                doOutput = true
                setRequestProperty("Content-Type", mime!!)
                setRequestProperty("Accept", "application/json")
            }
            try {
                upload.outputStream.use { it.write(attachmentBytes) }
                val status = upload.responseCode
                val stream = if (status in 200..299) upload.inputStream else upload.errorStream
                val responseText = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
                val response = runCatching { JSONObject(responseText) }.getOrNull()
                val imageUrl = response?.optString("url")?.takeIf { it.startsWith("https://") }
                if (status !in 200..299 || response?.optBoolean("ok") != true || imageUrl == null) {
                    throw IOException(response?.optString("message")?.takeIf(String::isNotBlank)
                        ?: "Could not upload screenshot. The report was not submitted.")
                }
                payload.put("attachmentUrl", imageUrl)
                payload.put("attachmentName", attachmentName.orEmpty().take(120))
            } finally {
                upload.disconnect()
            }
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
