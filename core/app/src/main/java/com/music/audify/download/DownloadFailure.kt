package com.music.audify.download

import java.util.Collections
import java.util.IdentityHashMap
import java.util.Locale

/** Turns storage exhaustion into an actionable message instead of a network error. */
internal fun friendlyDownloadFailure(failure: Throwable): String {
    val seen = Collections.newSetFromMap(IdentityHashMap<Throwable, Boolean>())
    var current: Throwable? = failure
    while (current != null && seen.add(current)) {
        val message = current.message.orEmpty().lowercase(Locale.ROOT)
        val type = current.javaClass.simpleName
        if (
            type == "SQLiteFullException" ||
            type == "DiskFullException" ||
            "enospc" in message ||
            "no space left on device" in message ||
            "not enough space" in message ||
            "insufficient storage" in message ||
            "storage is full" in message ||
            "disk full" in message ||
            "quota exceeded" in message
        ) {
            return "Not enough storage space to save this download. Free up space and try again."
        }
        current = current.cause
    }

    return when (failure) {
        is IllegalStateException, is IllegalArgumentException ->
            failure.message?.takeIf(String::isNotBlank) ?: "Download failed — check your connection"
        else -> "Download failed — check your connection"
    }
}
