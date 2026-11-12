// Derived from Zenith's GPL-3.0 HomeLayout model.
// Original project: https://github.com/rezapranata089-bit/Zenith
// Copyright and licence terms are retained under GPL-3.0; see THIRD_PARTY_NOTICES.md.
package com.music.audify.data.model

const val HOME_SLOT_MOODS = "slot:moods"

private val LISTEN_AGAIN_ALIASES = listOf("Listen again", "Nghe lại", "もう一度聴く", "Dengarkan lagi", "Putar ulang", "다시 듣기")
private val QUICK_PICKS_ALIASES = listOf("Quick picks", "Speed dial", "Panggilan cepat", "Quay số nhanh", "スピードダイヤル", "스피드 다이얼", "Lựa chọn nhanh", "クイック ミックス", "Pilihan cepat", "빠른 선곡")

/** Stable, language-independent key for a Home shelf. */
fun homeShelfKey(title: String): String {
    val clean = title.trim().replace('\n', ' ')
    return when {
        LISTEN_AGAIN_ALIASES.any { it.equals(clean, ignoreCase = true) } -> "shelf:listen-again"
        QUICK_PICKS_ALIASES.any { it.equals(clean, ignoreCase = true) } -> "shelf:quick-picks"
        else -> "shelf:" + clean.lowercase()
    }
}

/** One Home row; list position is its order and [hidden] removes it from Home. */
data class HomeSlot(val key: String, val hidden: Boolean = false)

/** Saved Home arrangement. Unknown future rows are appended without losing saved rows. */
data class HomeLayout(val slots: List<HomeSlot> = emptyList()) {
    val isCustom: Boolean get() = slots.isNotEmpty()
    fun isHidden(key: String): Boolean = slots.any { it.key == key && it.hidden }

    /** Includes saved hidden rows so the editor can always make them visible again. */
    fun editorOrder(availableKeys: List<String>): List<String> =
        (slots.map(HomeSlot::key) + availableKeys).distinct()

    fun <T> arrange(items: List<T>, dropHidden: Boolean = true, keyOf: (T) -> String): List<T> {
        if (!isCustom) return items
        val position = HashMap<String, Int>()
        slots.forEachIndexed { index, slot -> position.putIfAbsent(slot.key, index) }
        val known = items.filter { keyOf(it) in position }.sortedBy { position.getValue(keyOf(it)) }
        val fresh = items.filter { keyOf(it) !in position }
        return (known + fresh).filterNot { dropHidden && isHidden(keyOf(it)) }
    }

    fun encode(): String = slots.joinToString("\n") { (if (it.hidden) "1" else "0") + it.key }

    companion object {
        fun decode(text: String?): HomeLayout = HomeLayout(
            text.orEmpty().lineSequence()
                .filter { it.length > 1 }
                .map { HomeSlot(key = it.substring(1), hidden = it[0] == '1') }
                .distinctBy { it.key }
                .toList(),
        )
    }
}
