package com.music.audify.ui.screens

import java.text.Normalizer
import java.util.Locale

private val SETTINGS_SEARCH_SYNONYMS = mapOf(
    "dark" to listOf("theme", "appearance", "night"),
    "light" to listOf("theme", "appearance", "day"),
    "night" to listOf("dark", "theme", "appearance"),
    "theme" to listOf("appearance", "dark", "light", "display"),
    "battery" to listOf("power", "performance", "background"),
    "lag" to listOf("performance", "smooth", "stutter"),
    "slow" to listOf("performance", "lag", "startup"),
    "fast" to listOf("performance", "speed", "startup"),
    "crash" to listOf("error", "recovery", "diagnostics"),
    "bug" to listOf("error", "diagnostics", "feedback"),
    "wifi" to listOf("network", "streaming", "connection"),
    "internet" to listOf("network", "streaming", "connection"),
    "offline" to listOf("download", "local", "storage"),
    "space" to listOf("storage", "cache", "download"),
    "memory" to listOf("cache", "performance", "storage"),
    "sound" to listOf("audio", "volume", "equalizer", "output"),
    "loud" to listOf("volume", "audio", "normalization"),
    "quiet" to listOf("volume", "audio", "normalization"),
    "headphone" to listOf("bluetooth", "output", "audio", "earphone"),
    "earbuds" to listOf("bluetooth", "output", "audio", "headphone"),
    "font" to listOf("text", "display", "accessibility"),
    "bigger" to listOf("text", "display", "accessibility"),
    "language" to listOf("translation", "locale", "region"),
    "backup" to listOf("restore", "export", "import", "data"),
    "restore" to listOf("backup", "import", "data"),
    "privacy" to listOf("data", "permissions", "tracking"),
    "account" to listOf("login", "sign in", "profile", "integration"),
    "login" to listOf("account", "sign in", "authentication"),
    "spotify" to listOf("integration", "provider", "playlist"),
    "youtube" to listOf("music", "provider", "streaming"),
    "icon" to listOf("launcher", "logo", "app appearance"),
    "colour" to listOf("color", "accent", "theme", "appearance"),
    "color" to listOf("colour", "accent", "theme", "appearance"),
)

internal fun normalizeSettingsSearch(value: String): String = Normalizer
    .normalize(value.lowercase(Locale.ROOT), Normalizer.Form.NFD)
    .replace(Regex("\\p{Mn}+"), "")
    .replace(Regex("[^\\p{L}\\p{N}]+"), " ")
    .trim()

internal fun settingsSearchMatches(query: String, vararg keywords: String?): Boolean {
    val terms = normalizeSettingsSearch(query).split(' ').filter(String::isNotBlank).distinct()
    if (terms.isEmpty()) return true
    val corpus = normalizeSettingsSearch(keywords.filterNotNull().joinToString(" "))
    val compactCorpus = corpus.replace(" ", "")
    val corpusTerms = corpus.split(' ').filter(String::isNotBlank)
    return terms.all { term ->
        val normalizedTerm = term.removeSuffix("s").takeIf { term.length > 4 } ?: term
        (listOf(term, normalizedTerm) + SETTINGS_SEARCH_SYNONYMS[term].orEmpty() +
            SETTINGS_SEARCH_SYNONYMS[normalizedTerm].orEmpty()).distinct().any { candidate ->
            corpus.contains(candidate) || compactCorpus.contains(candidate.replace(" ", "")) ||
                (candidate.length >= 5 && corpusTerms.any { word ->
                    word.length >= 5 && hasSingleEditDistance(candidate, word)
                })
        }
    }
}

/** True only for one insertion, deletion or substitution; avoids broad fuzzy matches. */
private fun hasSingleEditDistance(left: String, right: String): Boolean {
    if (kotlin.math.abs(left.length - right.length) > 1 || left == right) return false
    var i = 0
    var j = 0
    var edits = 0
    while (i < left.length && j < right.length) {
        if (left[i] == right[j]) {
            i++
            j++
        } else {
            edits++
            if (edits > 1) return false
            when {
                left.length > right.length -> i++
                right.length > left.length -> j++
                else -> { i++; j++ }
            }
        }
    }
    if (i < left.length || j < right.length) edits++
    return edits == 1
}
