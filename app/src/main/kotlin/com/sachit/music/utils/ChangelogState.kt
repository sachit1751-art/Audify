package com.sachit.music.utils

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

class ChangelogState {
    var hasSeenChangelog by mutableStateOf(false)
        private set
}
