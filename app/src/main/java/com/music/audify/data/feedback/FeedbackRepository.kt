// Copyright (c) 2026 Sachit. All rights reserved.
// Project: AUDIFY
package com.music.audify.data.feedback

import android.content.Context
import android.os.Build
import com.music.audify.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.json.JSONArray
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/** Anonymous feedback submission. No Audify account, email address, or user ID is sent. */
object FeedbackRepository {
    data class Receipt(val number: Int, val url: String)
    data class TrackedIssue(val number: Int, val url: String, val title: String)
    data class Comment(val author: String, val body: String, val createdAt: String?, val url: String?)
    data class IssueDetail(
        val number: Int, val title: String, val body: String, val url: String, val state: String,
        val status: String, val updatedAt: String?, val labels: List<String>, val comments: List<Comment>,
    )

    private const val PREFS = "audify_feedback_tracking"
    private const val TRACKED = "issues"

    fun trackedIssues(context: Context): List<TrackedIssue> {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(TRACKED, "[]") ?: "[]"
        return runCatching {
            val array = JSONArray(raw)
            (0 until array.length()).mapNotNull { index ->
                val item = array.optJSONObject(index) ?: return@mapNotNull null
                val number = item.optInt("number", 0)
                val url = item.optString("url")
                if (number < 1 || !url.startsWith("https://github.com/sachit1751-art/Audify/issues/")) null
                else TrackedIssue(number, url, item.optString("title", "Feedback #$number"))
            }
        }.getOrDefault(emptyList())
    }

    fun rememberIssue(context: Context, issue: TrackedIssue) {
        val all = (listOf(issue) + trackedIssues(context).filterNot { it.number == issue.number }).take(30)
        val array = JSONArray()
        all.forEach { array.put(JSONObject().put("number", it.number).put("url", it.url).put("title", it.title.take(200))) }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(TRACKED, array.toString()).apply()
    }

    suspend fun fetchIssue(number: Int): IssueDetail = withContext(Dispatchers.IO) {
        require(number > 0)
        val base = BuildConfig.LISTEN_TOGETHER_SERVER.trim().trimEnd('/')
        if (!base.startsWith("https://")) throw IOException("Secure feedback service is unavailable.")
        val connection = (URL("$base/api/feedback/$number").openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 10_000
            readTimeout = 15_000
            setRequestProperty("Accept", "application/json")
        }
        try {
            val statusCode = connection.responseCode
            val stream = if (statusCode in 200..299) connection.inputStream else connection.errorStream
            val response = runCatching { JSONObject(stream?.bufferedReader()?.use { it.readText() }.orEmpty()) }.getOrNull()
            if (statusCode !in 200..299 || response?.optBoolean("ok") != true) {
                throw IOException(response?.optString("message")?.takeIf(String::isNotBlank) ?: "Could not load feedback status. Try again.")
            }
            val commentsJson = response.optJSONArray("comments") ?: JSONArray()
            val comments = (0 until commentsJson.length()).mapNotNull { index ->
                val item = commentsJson.optJSONObject(index) ?: return@mapNotNull null
                Comment(item.optString("author", "GitHub user"), item.optString("body"), item.optString("createdAt").takeIf(String::isNotBlank), item.optString("url").takeIf { it.startsWith("https://github.com/") })
            }
            val issueUrl = response.optString("url")
            if (!issueUrl.startsWith("https://github.com/sachit1751-art/Audify/issues/")) throw IOException("Issue tracker returned an invalid link.")
            val labelsJson = response.optJSONArray("labels") ?: JSONArray()
            IssueDetail(
                number = response.optInt("number", number), title = response.optString("title", "Feedback #$number"),
                body = response.optString("body"),
                url = issueUrl, state = response.optString("state", "open"), status = response.optString("status", "Open"),
                updatedAt = response.optString("updatedAt").takeIf(String::isNotBlank),
                labels = (0 until labelsJson.length()).map { labelsJson.optString(it) }, comments = comments,
            )
        } finally { connection.disconnect() }
    }
    suspend fun submit(
        kind: String,
        category: String,
        title: String,
        description: String,
        includeDiagnostics: Boolean,
        diagnosticDetails: Map<String, String> = emptyMap(),
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
            val issueNumber = response?.optInt("number", 0) ?: 0
            val issueUrl = response?.optString("url")?.takeIf { it.startsWith("https://github.com/sachit1751-art/Audify/issues/") }
            if (status !in 200..299 || response?.optBoolean("ok") != true || issueNumber <= 0 || issueUrl == null) {
                val message = response?.optString("message")?.takeIf(String::isNotBlank)
                    ?: when (status) {
                        429 -> "Too many submissions. Please try again later."
                        503 -> "Feedback is temporarily unavailable. Please try again later."
                        else -> "Could not submit feedback. Please try again later."
                    }
                throw IOException(message)
            }
            Receipt(issueNumber, issueUrl)
        } finally {
            connection.disconnect()
        }
    }
}
