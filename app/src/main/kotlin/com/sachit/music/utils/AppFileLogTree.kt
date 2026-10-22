package com.sachit.music.utils

import android.content.Context
import android.util.Log
import timber.log.Timber
import java.io.File

class AppFileLogTree(
    context: Context,
) : Timber.Tree() {
    override fun log(
        priority: Int,
        tag: String?,
        message: String,
        t: Throwable?,
    ) {
        if (priority < Log.WARN) return
        Timber.tag("AppFileLog").d("$tag: $message")
    }
}