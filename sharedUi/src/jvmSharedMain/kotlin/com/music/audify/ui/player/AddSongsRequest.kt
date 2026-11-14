package com.music.audify.ui.player

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Pintu antara baris "Add Songs to Queue" di panel queue dan host aplikasi:
 * baris itu hanya memanggil [show], host yang menggambar popup pencariannya.
 */
object AddSongsRequest {
    private val state = MutableStateFlow(false)
    val open: StateFlow<Boolean> = state.asStateFlow()
    fun show() { state.value = true }
    fun hide() { state.value = false }
}