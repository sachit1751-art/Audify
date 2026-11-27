package com.music.audify.ui

import java.net.NoRouteToHostException
import java.net.UnknownHostException
import java.util.IdentityHashMap

/** Recognises failures that reliably mean the device cannot reach the search service. */
internal fun isSearchNetworkUnavailable(error: Throwable): Boolean {
    val seen = IdentityHashMap<Throwable, Boolean>()
    var current: Throwable? = error
    while (current != null && seen.put(current, true) == null) {
        if (current is UnknownHostException || current is NoRouteToHostException) return true
        val message = current.message.orEmpty().lowercase()
        if (listOf(
                "resolve host",
                "unable to resolve",
                "network is unreachable",
                "no route to host",
            ).any(message::contains)
        ) return true
        current = current.cause
    }
    return false
}
