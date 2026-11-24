// Copyright (c) 2026 Sachit. All rights reserved.
// Project: AUDIFY
package com.music.audify.data.settings

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.util.UUID

/** A portable colour theme. Background image URIs intentionally remain device-local. */
data class ThemePack(
    val id: String,
    val name: String,
    val primaryHex: String,
    val secondaryHex: String,
    val tertiaryHex: String,
    val surfaceStyle: ThemeSurfaceStyle = ThemeSurfaceStyle.DEFAULT,
)

/** Surface token family used when the app is in dark mode. Light mode remains user-selectable. */
enum class ThemeSurfaceStyle { DEFAULT, AMOLED, MIDNIGHT, GRAPHITE, DEEP_PURPLE, WARM, SLATE }

object ThemePacks {
    const val CLASSIC_ID = "classic"
    private val json = Json { ignoreUnknownKeys = true }

    val builtIns = listOf(
        ThemePack(CLASSIC_ID, "Classic", "#FFFFFF", "#A1A1AA", "#FA2D48"),
        ThemePack("graphite", "Graphite", "#D1D5DB", "#9CA3AF", "#93C5FD", ThemeSurfaceStyle.GRAPHITE),
        ThemePack("ocean", "Ocean", "#55C7F2", "#80E0D0", "#3478F6"),
        ThemePack("forest", "Forest", "#80D6A3", "#B5D98C", "#3C9B78"),
        ThemePack("jade", "Jade", "#6EE7B7", "#A3E635", "#67E8F9", ThemeSurfaceStyle.SLATE),
        ThemePack("soft-sage", "Soft Sage", "#66806C", "#9AA58A", "#49675C", ThemeSurfaceStyle.SLATE),
        ThemePack("warm-sand", "Warm Sand", "#92765C", "#B59A7B", "#6D766A", ThemeSurfaceStyle.WARM),
    )

    fun isValidHex(value: String): Boolean = value.matches(Regex("^#[0-9A-Fa-f]{6}$"))

    /** The restrained light canvas used by the original theme implementation. */
    private fun backgroundAccentHex(pack: ThemePack): String =
        if (pack.id == CLASSIC_ID) pack.tertiaryHex else pack.primaryHex

    fun lightBackgroundHex(pack: ThemePack): String = blendHex("#F8F5FA", backgroundAccentHex(pack), 0.11)

    /**
     * Dark canvas tokens from the original theme implementation. Palette accents
     * belong on controls and component surfaces, not across the app background.
     */
    fun darkBackgroundHex(pack: ThemePack): String {
        return when (pack.surfaceStyle) {
            ThemeSurfaceStyle.AMOLED -> "#000000"
            ThemeSurfaceStyle.MIDNIGHT -> "#080D18"
            ThemeSurfaceStyle.GRAPHITE -> "#15171B"
            ThemeSurfaceStyle.DEEP_PURPLE -> "#120D1D"
            ThemeSurfaceStyle.WARM -> "#19130F"
            ThemeSurfaceStyle.SLATE -> "#101820"
            // Classic keeps its original neutral AMOLED-like canvas. Other
            // palettes get a restrained dark tint so selecting Ocean, Rose,
            // Forest, etc. is visible in dark mode too, not only on controls.
            ThemeSurfaceStyle.DEFAULT -> if (pack.id == CLASSIC_ID) {
                "#000000"
            } else {
                blendHex("#101014", backgroundAccentHex(pack), 0.12)
            }
        }
    }

    private fun blendHex(base: String, tint: String, amount: Double): String {
        require(isValidHex(base) && isValidHex(tint))
        val channels = (0..2).map { index ->
            val start = base.substring(1 + index * 2, 3 + index * 2).toInt(16)
            val end = tint.substring(1 + index * 2, 3 + index * 2).toInt(16)
            (start + (end - start) * amount).toInt().coerceIn(0, 255)
        }
        return channels.joinToString(prefix = "#", separator = "") { it.toString(16).padStart(2, '0') }.uppercase()
    }

    /** Selects whichever neutral (black or white) gives the stronger WCAG contrast. */
    fun contentColorHex(backgroundHex: String): String {
        require(isValidHex(backgroundHex)) { "Expected a six-digit colour" }
        val rgb = backgroundHex.drop(1).chunked(2).map { it.toInt(16) / 255.0 }
        fun linear(channel: Double) = if (channel <= 0.04045) channel / 12.92 else Math.pow((channel + 0.055) / 1.055, 2.4)
        val luminance = 0.2126 * linear(rgb[0]) + 0.7152 * linear(rgb[1]) + 0.0722 * linear(rgb[2])
        val blackContrast = (luminance + 0.05) / 0.05
        val whiteContrast = 1.05 / (luminance + 0.05)
        return if (blackContrast >= whiteContrast) "#000000" else "#FFFFFF"
    }

    /** Keeps light-mode accents legible on white surfaces without changing palette swatches. */
    fun accessibleLightAccentHex(hex: String): String {
        require(isValidHex(hex)) { "Expected a six-digit colour" }
        fun luminance(value: String): Double {
            val rgb = value.drop(1).chunked(2).map { it.toInt(16) / 255.0 }
            fun linear(channel: Double) = if (channel <= 0.04045) channel / 12.92 else Math.pow((channel + 0.055) / 1.055, 2.4)
            return 0.2126 * linear(rgb[0]) + 0.7152 * linear(rgb[1]) + 0.0722 * linear(rgb[2])
        }
        fun contrastOnWhite(value: String) = 1.05 / (luminance(value) + 0.05)
        if (contrastOnWhite(hex) >= 3.0) return hex.uppercase()
        var channels = hex.drop(1).chunked(2).map { it.toInt(16) }
        repeat(100) {
            channels = channels.map { (it * 0.94).toInt().coerceAtLeast(0) }
            val candidate = channels.joinToString(prefix = "#", separator = "") { it.toString(16).padStart(2, '0') }.uppercase()
            if (contrastOnWhite(candidate) >= 3.0) return candidate
        }
        return "#000000"
    }

    fun resolve(id: String, custom: List<ThemePack>): ThemePack =
        builtIns.firstOrNull { it.id == id }
            ?: custom.firstOrNull { it.id == id }
            ?: builtIns.first()

    fun encode(pack: ThemePack): String = buildJsonObject {
        put("format", "audify-theme")
        put("version", 1)
        put("id", pack.id)
        put("name", pack.name)
        put("primary", pack.primaryHex)
        put("secondary", pack.secondaryHex)
        put("tertiary", pack.tertiaryHex)
        put("surfaceStyle", pack.surfaceStyle.name)
    }.toString()

    fun decode(json: String): ThemePack = decode(json, preserveId = false)

    private fun decode(json: String, preserveId: Boolean): ThemePack {
        require(json.length <= 16_384) { "Theme files must be 16 KB or smaller" }
        val root = this.json.parseToJsonElement(json).jsonObject
        fun field(key: String) = root[key]?.jsonPrimitive?.contentOrNull.orEmpty()
        require(field("format") == "audify-theme" && root["version"]?.jsonPrimitive?.intOrNull == 1) {
            "This is not a supported Audify theme file"
        }
        val name = field("name").trim().take(32)
        val primary = field("primary").uppercase()
        val secondary = field("secondary").uppercase()
        val tertiary = field("tertiary").uppercase()
        require(name.isNotBlank()) { "Theme name is missing" }
        require(isValidHex(primary) && isValidHex(secondary) && isValidHex(tertiary)) {
            "Theme colours must be six-digit hex values such as #55C7F2"
        }
        val id = if (preserveId) field("id").takeIf { it.matches(Regex("^[A-Za-z0-9_-]{1,64}$")) } else null
        val surfaceStyle = ThemeSurfaceStyle.entries.firstOrNull { it.name == field("surfaceStyle") }
            ?: ThemeSurfaceStyle.DEFAULT
        return ThemePack(id ?: UUID.randomUUID().toString(), name, primary, secondary, tertiary, surfaceStyle)
    }

    fun encodeLibrary(packs: List<ThemePack>): String = buildJsonArray {
        packs.forEach { add(json.parseToJsonElement(encode(it))) }
    }.toString()

    fun decodeLibrary(json: String?): List<ThemePack> {
        if (json.isNullOrBlank() || json.length > 262_144) return emptyList()
        return runCatching {
            val array = this.json.parseToJsonElement(json).jsonArray
            buildList {
                val seenIds = mutableSetOf<String>()
                array.forEach { element ->
                    val pack = runCatching { decode(element.toString(), preserveId = true) }.getOrNull()
                    if (pack != null && seenIds.add(pack.id)) add(pack)
                }
            }
        }.getOrDefault(emptyList())
    }
}
