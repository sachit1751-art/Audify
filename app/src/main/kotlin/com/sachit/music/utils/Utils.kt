/**
 * SachitMusic Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.sachit.music.utils

import android.content.Context
import android.content.res.Configuration
import com.sachit.music.R
import timber.log.Timber
import java.util.Locale

fun getArtistSeparator(context: Context): String = " ${context.getString(R.string.and)} "

fun <T> List<T>.joinToArtistString(
    conjunction: String,
    transform: (T) -> String,
): String = when (size) {
    0 -> ""
    1 -> transform(this[0])
    2 -> "${transform(this[0])}$conjunction${transform(this[1])}"
    else -> dropLast(1).joinToString(", ") { transform(it) } + "$conjunction${transform(last())}"
}

/**
 * Reports a non-fatal exception.
 *
 * Deliberately avoids [Throwable.printStackTrace]: since Android 4.1 an app's
 * stderr is redirected to the system log, which every other app (and `adb
 * shell`) can read. Timber routes the trace to the app's private log file in
 * release builds and to logcat in debug builds.
 */
fun reportException(throwable: Throwable) {
    Timber.e(throwable, "Unhandled exception")
}

@Suppress("DEPRECATION")
fun setAppLocale(context: Context, locale: Locale) {
    val config = Configuration(context.resources.configuration)
    config.setLocale(locale)
    context.resources.updateConfiguration(config, context.resources.displayMetrics)
}
