package com.music.audify.data.social

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long

/**
 * Runs `shared/testvectors/social-v1.json`, the examples that the Kotlin rules
 * and the backend must both satisfy (Plan 091, section 11). The file is the
 * contract: when a rule changes, change the file, this code and the backend
 * together. Expected results in the file are written by hand, not generated.
 */
class SocialVectorsTest {

    private val vectors: JsonObject by lazy { load() }

    private fun load(): JsonObject {
        // Gradle runs tests from the module directory; an IDE may run them from the repository root.
        val candidates = listOf(File("testvectors/social-v1.json"), File("shared/testvectors/social-v1.json"))
        val file = candidates.firstOrNull { it.isFile }
            ?: error("social-v1.json not found. Looked in: " + candidates.joinToString { it.absolutePath })
        return Json.parseToJsonElement(file.readText(Charsets.UTF_8)).jsonObject
    }

    private fun cases(section: String): List<JsonObject> =
        vectors.getValue(section).jsonArray.map { it.jsonObject }

    /** An input is a string, null, or {"repeat": unit, "times": n}. */
    private fun JsonElement.input(): String? = when (this) {
        is JsonNull -> null
        is JsonObject -> getValue("repeat").jsonPrimitive.content.repeat(getValue("times").jsonPrimitive.int)
        else -> jsonPrimitive.content
    }

    private fun JsonObject.text(key: String): String = getValue(key).jsonPrimitive.content

    private fun JsonObject.textOrNull(key: String): String? =
        getValue(key).let { if (it is JsonNull) null else it.jsonPrimitive.content }

    private fun JsonObject.longOrNull(key: String): Long? =
        getValue(key).let { if (it is JsonNull) null else it.jsonPrimitive.long }

    @Test
    fun limitsMatchTheConstants() {
        val limits = vectors.getValue("limits").jsonObject
        fun limit(key: String) = limits.getValue(key).jsonPrimitive.int
        assertEquals(limit("usernameMin"), UsernameRules.MIN_LENGTH)
        assertEquals(limit("usernameMax"), UsernameRules.MAX_LENGTH)
        assertEquals(limit("displayNameMax"), ProfileTextRules.DISPLAY_NAME_MAX)
        assertEquals(limit("bioMax"), ProfileTextRules.BIO_MAX)
        assertEquals(limit("pageDefault"), SocialPaging.DEFAULT_PAGE_SIZE)
        assertEquals(limit("pageMax"), SocialPaging.MAX_PAGE_SIZE)
        assertEquals(limit("usernameCooldownDays"), UsernameRules.CHANGE_COOLDOWN_DAYS)
        assertEquals(limit("usernameReleaseHoldDays"), UsernameRules.RELEASED_NAME_HOLD_DAYS)
        assertEquals(limit("searchMinPrefix"), UsernameRules.SEARCH_MIN_PREFIX_LENGTH)
        assertEquals(limit("cursorMax"), SocialPaging.MAX_CURSOR_LENGTH)
    }

    @Test
    fun reservedUsernamesMatch() {
        val expected = vectors.getValue("reservedUsernames").jsonArray.map { it.jsonPrimitive.content }.toSet()
        assertEquals(expected, UsernameRules.RESERVED)
        val prefixes = vectors.getValue("reservedUsernamePrefixes").jsonArray.map { it.jsonPrimitive.content }
        assertEquals(prefixes, UsernameRules.RESERVED_PREFIXES)
    }

    @Test
    fun usernameValidation() {
        cases("usernameValidate").forEach { case ->
            val expected: UsernameCheck = if (case.getValue("valid").jsonPrimitive.boolean) {
                UsernameCheck.Valid(display = case.text("display"), key = case.text("key"))
            } else {
                UsernameCheck.Invalid(UsernameProblem.valueOf(case.text("problem")))
            }
            assertEquals(expected, UsernameRules.validate(case.getValue("input").input()!!), case.text("name"))
        }
    }

    @Test
    fun searchPrefixes() {
        cases("searchPrefix").forEach { case ->
            assertEquals(case.textOrNull("expected"), UsernameRules.searchPrefix(case.getValue("input").input()!!), case.text("name"))
        }
    }

    @Test
    fun displayNames() {
        cases("displayName").forEach { case ->
            assertEquals(case.textOrNull("expected"), ProfileTextRules.cleanDisplayName(case.getValue("input").input()!!), case.text("name"))
        }
    }

    @Test
    fun bios() {
        cases("bio").forEach { case ->
            assertEquals(case.textOrNull("expected"), ProfileTextRules.cleanBio(case.getValue("input").input()!!), case.text("name"))
        }
    }

    @Test
    fun codePointCounts() {
        cases("codePointCount").forEach { case ->
            val expected = case.getValue("expected").jsonPrimitive.int
            assertEquals(expected, ProfileTextRules.codePointCount(case.getValue("input").input()!!), case.text("name"))
        }
    }

    @Test
    fun pageLimitClamping() {
        cases("pageClamp").forEach { case ->
            val limit = case.getValue("limit").jsonPrimitive.int
            assertEquals(case.getValue("expected").jsonPrimitive.int, SocialPaging.clamp(limit), "limit $limit")
        }
    }

    @Test
    fun cursorSyntax() {
        cases("cursor").forEach { case ->
            val expected = case.getValue("wellFormed").jsonPrimitive.boolean
            assertEquals(expected, SocialPaging.isWellFormedCursor(case.getValue("input").input()), case.text("name"))
        }
    }

    @Test
    fun usernameCooldown() {
        cases("usernameCooldown").forEach { case ->
            val last = case.longOrNull("lastChangedAtMillis")
            val now = case.getValue("nowMillis").jsonPrimitive.long
            assertEquals(case.getValue("allowed").jsonPrimitive.boolean, UsernameRules.canChange(last, now), case.text("name"))
            assertEquals(case.longOrNull("nextAllowedAtMillis"), UsernameRules.nextChangeAllowedAt(last), case.text("name"))
        }
    }

    @Test
    fun usernameChanges() {
        cases("usernameChange").forEach { case ->
            val expected: UsernameChange = when (val result = case.text("result")) {
                "Unchanged" -> UsernameChange.Unchanged
                "CapitalizationOnly" -> UsernameChange.CapitalizationOnly(case.text("display"))
                "Rename" -> UsernameChange.Rename(case.text("display"), case.text("key"))
                "CoolingDown" -> UsernameChange.CoolingDown(case.getValue("nextChangeAllowedAtMillis").jsonPrimitive.long)
                "Invalid" -> UsernameChange.Invalid(UsernameProblem.valueOf(case.text("problem")))
                else -> error("Unknown result '$result' in case '${case.text("name")}'")
            }
            val actual = UsernameRules.planChange(
                current = case.text("current"),
                requested = case.text("requested"),
                lastRenamedAtMillis = case.longOrNull("lastRenamedAtMillis"),
                nowMillis = case.getValue("nowMillis").jsonPrimitive.long,
            )
            assertEquals(expected, actual, case.text("name"))
        }
    }

    @Test
    fun releasedUsernameHolds() {
        cases("usernameHold").forEach { case ->
            val released = case.getValue("releasedAtMillis").jsonPrimitive.long
            val now = case.getValue("nowMillis").jsonPrimitive.long
            assertEquals(case.getValue("held").jsonPrimitive.boolean, UsernameRules.isHeld(released, now), case.text("name"))
            assertEquals(case.getValue("holdEndsAtMillis").jsonPrimitive.long, UsernameRules.holdEndsAt(released), case.text("name"))
        }
    }
}
