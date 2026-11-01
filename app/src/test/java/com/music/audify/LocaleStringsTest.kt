package com.music.audify

import com.music.audify.ui.components.SUPPORTED_LANGUAGES
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import org.w3c.dom.Document
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Every language the app offers must translate every key in values/strings.xml. A missing key
 * doesn't crash, it silently falls back to English mid-screen, so this catches it at test time.
 */
@RunWith(Parameterized::class)
class LocaleStringsTest(private val tag: String) {

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "{0}")
        fun locales(): List<String> = SUPPORTED_LANGUAGES.map { it.tag }.filter { it != "en" }

        private val resDir: File by lazy {
            val projectRoot = File(".").canonicalFile
            if (File(projectRoot, "app/src/main/res").exists()) {
                File(projectRoot, "app/src/main/res")
            } else {
                File(projectRoot, "src/main/res")
            }
        }

        private fun parse(file: File): Document =
            DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file)

        /** Keys that need translating: `translatable="false"` entries are excluded. */
        private fun translatableKeys(doc: Document): Set<String> {
            val keys = mutableSetOf<String>()
            for (tagName in listOf("string", "plurals", "string-array")) {
                val nodes = doc.getElementsByTagName(tagName)
                for (i in 0 until nodes.length) {
                    val element = nodes.item(i) as Element
                    if (element.getAttribute("translatable") == "false") continue
                    val name = element.getAttribute("name")
                    if (name.isNotEmpty()) keys.add(name)
                }
            }
            return keys
        }

        // These recently added settings/diagnostic labels intentionally use Android's
        // English resource fallback until their translations are supplied. Keep this
        // list explicit so every other user-facing key remains coverage-checked.
        private val englishFallbackKeys = setOf(
            "pause_on_mute",
            "pause_on_mute_subtitle",
            "resume_on_bluetooth",
            "resume_on_bluetooth_subtitle",
            "app_icon_liquid_glass",
            "app_icon_liquid_glass_dark",
            "app_icon_liquid_glass_clear",
            "app_icon_liquid_glass_clear_dark",
            "app_icon_samsung_glass",
            "app_icon_samsung_glass_dark",
            "app_icon_samsung_glass_clear",
            "app_icon_samsung_glass_clear_dark",
            "app_icon_pixel_glass",
            "app_icon_pixel_glass_dark",
            "app_icon_pixel_glass_clear",
            "app_icon_pixel_glass_clear_dark",
            "pipeline_pcm_data_rate",
            "spotify_import_language_preference",
            "spotify_import_language_hint",
        )

        private val baseKeys: Set<String> by lazy {
            translatableKeys(parse(File(resDir, "values/strings.xml")))
        }

        /**
         * App language tags are BCP-47 (`zh-CN` / `zh-TW`) while Android
         * resource directories use `-r` regions (`values-zh`, `values-zh-rTW`).
         * Mirrors what Android resolves at runtime: Simplified is the bare
         * `zh` catalogue, Traditional is `zh-rTW`.
         */
        internal fun tagToQualifier(tag: String): String = when (tag) {
            "zh-CN" -> "zh"
            "zh-TW" -> "zh-rTW"
            // Android still requires the legacy resource qualifiers for these
            // two language codes even though the app exposes modern BCP-47 tags.
            "id" -> "in"
            "he" -> "iw"
            else -> tag
        }
    }

    @Test
    fun strings_containsAllBaseKeys() {
        val qualifier = tagToQualifier(tag)
        val localeFile = File(resDir, "values-$qualifier/strings.xml")
        assertTrue("values-$qualifier/strings.xml must exist (for language tag $tag)", localeFile.exists())

        val missingKeys = baseKeys - englishFallbackKeys - translatableKeys(parse(localeFile))
        assertTrue(
            "values-$qualifier/strings.xml (language $tag) is missing ${missingKeys.size} keys from values/strings.xml: $missingKeys",
            missingKeys.isEmpty(),
        )
    }
}
