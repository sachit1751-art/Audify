/**
 * SachitMusic Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.sachit.music.utils

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import com.sachit.music.constants.InnerTubeCookieKey
import com.sachit.music.discord.DiscordTokenStore.AesKeystore
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * The innertube session cookie, encrypted at rest.
 *
 * The cookie grants full access to a signed-in account, so it is the one preference that must not
 * sit in plaintext. [AesKeystore] keeps the key in the AndroidKeyStore, where it is device-bound and
 * non-exportable, so a copy of the datastore recovered from anywhere is ciphertext with no key
 * beside it.
 *
 * This complements the backup rules, which already keep the datastore out of cloud backup. Neither
 * measure is enough alone: the backup rule stops the upload, this makes a leak inert.
 */
object SecureCookieStore {
    private const val TAG = "SecureCookie"
    private const val PREFIX = "enc:v1:"

    /**
     * The cookie as observable state, encrypted on every write.
     *
     * Drop-in replacement for `rememberPreference(InnerTubeCookieKey, "")`: same
     * [MutableState] shape, so the destructuring call sites read the same.
     */
    @Composable
    fun rememberInnerTubeCookie(): MutableState<String> {
        val context = LocalContext.current
        val scope = rememberCoroutineScope()

        val state =
            context.dataStore.data
                .map { preferences ->
                    val raw = preferences[InnerTubeCookieKey].orEmpty()
                    if (raw.startsWith(PREFIX)) decrypt(raw.removePrefix(PREFIX)) else raw
                }.distinctUntilChanged()
                .collectAsState(initial = "")

        // Upgrade a cookie stored before encryption existed, once, without signing anyone out.
        LaunchedEffect(context) {
            val raw = context.dataStore.data.first()[InnerTubeCookieKey].orEmpty()
            if (raw.isNotBlank() && !raw.startsWith(PREFIX)) write(context, raw)
        }

        return remember(context) {
            object : MutableState<String> {
                override var value: String
                    get() = state.value
                    set(newValue) {
                        scope.launch { write(context, newValue) }
                    }

                override fun component1(): String = value

                override fun component2(): (String) -> Unit = { value = it }
            }
        }
    }

    /**
     * Encodes [cookie] for storage.
     *
     * Falls back to the plaintext value, with a warning, when encryption is unavailable on the
     * device. Returning null instead would assign null to the preference key, which *removes* it
     * and signs the user out — losing a working sign-in is far worse than storing it unencrypted.
     */
    fun encode(cookie: String): String {
        val encrypted = encrypt(cookie) ?: run {
            Timber.tag(TAG).w("Encryption unavailable; storing cookie without it")
            return cookie
        }
        return PREFIX + encrypted
    }

    /**
     * Decodes a stored value, tolerating a cookie written before encryption existed.
     *
     * Synchronous on purpose: AES over a short string, so it stays usable from the existing
     * blocking call sites without adding another thread hop.
     */
    fun decode(stored: String?): String {
        val raw = stored.orEmpty()
        if (raw.isEmpty()) return ""
        return if (raw.startsWith(PREFIX)) decrypt(raw.removePrefix(PREFIX)) else raw
    }

    /** Reads the cookie, decrypting when needed. Empty means signed out. */
    suspend fun read(context: Context): String = decode(context.dataStore.data.first()[InnerTubeCookieKey])

    /** Persists [cookie], encrypted. A blank value clears it. */
    suspend fun write(
        context: Context,
        cookie: String,
    ) {
        if (cookie.isBlank()) {
            clear(context)
            return
        }
        val encrypted = encrypt(cookie)
        if (encrypted == null) {
            // Never lose a working sign-in because encryption is unavailable on this device.
            Timber.tag(TAG).w("Encryption unavailable; storing cookie without it")
            context.safeDataStoreEdit { it[InnerTubeCookieKey] = cookie }
        } else {
            context.safeDataStoreEdit { it[InnerTubeCookieKey] = PREFIX + encrypted }
        }
    }

    /** True when [stored] is already in the encrypted format. */
    fun isEncrypted(stored: String?): Boolean = stored?.startsWith(PREFIX) == true

    /** Removes the cookie, e.g. on sign-out. */
    suspend fun clear(context: Context) {
        context.safeDataStoreEdit { it.remove(InnerTubeCookieKey) }
    }

    private fun encrypt(value: String): String? =
        try {
            AesKeystore.encrypt(value)
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "encrypt failed")
            null
        }

    private fun decrypt(value: String): String =
        try {
            AesKeystore.decrypt(value)
        } catch (e: Exception) {
            // A key that no longer matches - a device restore, say - leaves an unreadable cookie.
            // Report it as signed out instead of crashing; the user signs in again.
            Timber.tag(TAG).e(e, "decrypt failed; treating as signed out")
            ""
        }
}
