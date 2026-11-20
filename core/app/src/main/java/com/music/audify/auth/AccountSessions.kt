// Copyright (c) 2026 Sachit. All rights reserved.
// Project: AUDIFY
package com.music.audify.auth

import org.json.JSONArray
import org.json.JSONObject
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import java.security.MessageDigest

/** A Google login kept independently from every other captured login. */
data class GoogleAccountSession(
    val accountId: String,
    val cookie: String,
    val name: String = "",
    val email: String = "",
    val profiles: List<YouTubeProfile> = emptyList(),
    val activeProfileId: String? = null,
)

/** One Personal or Brand identity available under a Google login. */
data class YouTubeProfile(
    val profileId: String,
    val name: String,
    val handle: String = "",
    val avatar: String? = null,
    val pageId: String? = null,
    val dataSyncId: String? = null,
    val authUser: String? = null,
    val isBrandAccount: Boolean = false,
)

/** Stable selector/swipe order: account insertion order, then profile order. */
fun flattenedProfiles(accounts: List<GoogleAccountSession>): List<Pair<String, String>> =
    accounts.flatMap { account -> account.profiles.map { account.accountId to it.profileId } }

fun adjacentProfile(
    accounts: List<GoogleAccountSession>, accountId: String?, profileId: String?, forward: Boolean,
): Pair<String, String>? {
    val items = flattenedProfiles(accounts)
    val index = items.indexOf(accountId to profileId)
    return items.getOrNull(index + if (forward) 1 else -1)
}

internal fun sessionId(cookie: String, dataSyncId: String?): String =
    dataSyncId?.takeIf { it.isNotBlank() } ?: sha256(cookie).take(24)

internal fun profileId(pageId: String?, dataSyncId: String?, name: String): String =
    pageId ?: dataSyncId ?: "profile:${sha256(name).take(16)}"

private fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
    .digest(value.toByteArray())
    .joinToString("") { "%02x".format(it) }

internal fun List<GoogleAccountSession>.toJson(): String = JSONArray().apply {
    forEach { session ->
        put(JSONObject().apply {
            put("id", session.accountId); put("cookie", session.cookie)
            put("name", session.name); put("email", session.email)
            put("activeProfileId", session.activeProfileId)
            put("profiles", JSONArray().apply {
                session.profiles.forEach { profile -> put(JSONObject().apply {
                    put("id", profile.profileId); put("name", profile.name)
                    put("handle", profile.handle); put("avatar", profile.avatar)
                    put("pageId", profile.pageId); put("dataSyncId", profile.dataSyncId)
                    put("authUser", profile.authUser); put("brand", profile.isBrandAccount)
                }) }
            })
        })
    }
}.toString()

internal fun sessionsFromJson(raw: String?): List<GoogleAccountSession> {
    val root = runCatching { Json.parseToJsonElement(raw ?: "[]").jsonArray }.getOrNull()
        ?: return emptyList()
    return buildList {
        root.forEach { element ->
            // One damaged account/profile must not make every other saved login
            // disappear. This registry is the only durable source for accounts
            // beyond the legacy single-cookie slot.
            val item = element as? JsonObject ?: return@forEach
            val cookie = item.string("cookie")?.takeIf(String::isNotBlank) ?: return@forEach
            val profiles = item["profiles"] as? JsonArray
            val parsedProfiles = buildList {
                profiles.orEmpty().forEach profileLoop@{ profile ->
                    val value = profile as? JsonObject ?: return@profileLoop
                    val name = value.string("name").orEmpty()
                    add(YouTubeProfile(
                        profileId = value.string("id")
                            ?.takeIf(String::isNotBlank) ?: profileId(null, null, name),
                        name = name,
                        handle = value.string("handle").orEmpty(),
                        avatar = value.string("avatar")?.ifBlank { null },
                        pageId = value.string("pageId")?.ifBlank { null },
                        dataSyncId = value.string("dataSyncId")?.ifBlank { null },
                        authUser = value.string("authUser")?.ifBlank { null },
                        isBrandAccount = value.boolean("brand") ?: false,
                    ))
                }
            }
            val savedActiveProfile = item.string("activeProfileId")?.ifBlank { null }
            val activeProfile = savedActiveProfile?.takeIf { saved ->
                parsedProfiles.any { it.profileId == saved }
            } ?: parsedProfiles.firstOrNull()?.profileId
            add(GoogleAccountSession(
                accountId = item.string("id")?.takeIf(String::isNotBlank)
                    ?: sessionId(cookie, parsedProfiles.firstOrNull()?.dataSyncId),
                cookie = cookie,
                name = item.string("name").orEmpty(),
                email = item.string("email").orEmpty(),
                activeProfileId = activeProfile,
                profiles = parsedProfiles,
            ))
        }
    }
}

private fun JsonObject.string(key: String): String? =
    (this[key] as? kotlinx.serialization.json.JsonPrimitive)?.contentOrNull

private fun JsonObject.boolean(key: String): Boolean? =
    (this[key] as? kotlinx.serialization.json.JsonPrimitive)?.booleanOrNull
