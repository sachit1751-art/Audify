package com.music.audify.data.model

/** Key of the Top Picks for You slot on Home. */
const val HOME_SLOT_TOP_PICKS = "slot:top-picks"

/** Key of the Find Your Mood slot on Home. */
const val HOME_SLOT_MOODS = "slot:moods"

/** Keys of the fixed rows of the Library tab. Shelves use [homeShelfKey]. */
const val LIBRARY_SLOT_PINNED = "slot:library-pinned"
const val LIBRARY_SLOT_LINKS = "slot:library-links"
const val LIBRARY_SLOT_REPLAY = "slot:library-replay"
const val LIBRARY_SLOT_ON_DEVICE = "slot:library-on-device"

/** Title of the speed dial built from local listening; kept apart from YouTube's Quick picks / Speed dial shelf. */
const val LOCAL_SPEED_DIAL_TITLE = "My speed dial"

private val LISTEN_AGAIN_ALIASES = listOf("Listen again", "Nghe lại", "もう一度聴く", "Dengarkan lagi", "Putar ulang", "다시 듣기")
private val QUICK_PICKS_ALIASES = listOf("Quick picks", "Speed dial", "Panggilan cepat", "Quay số nhanh", "スピードダイヤル", "스피드 다이얼", "Lựa chọn nhanh", "Chọn nhanh đài phát", "クイック ミックス", "Pilihan cepat", "Pilihan singkat", "빠른 선곡", "빠른 선택")

/**
 * A stable key for a feed shelf. The same shelf keeps its key across app
 * languages where its title is known in several, so a saved order survives.
 */
fun homeShelfKey(title: String): String {
    val clean = title.trim().replace('\n', ' ')
    return when {
        LISTEN_AGAIN_ALIASES.any { it.equals(clean, ignoreCase = true) } -> "shelf:listen-again"
        QUICK_PICKS_ALIASES.any { it.equals(clean, ignoreCase = true) } -> "shelf:quick-picks"
        else -> "shelf:" + clean.lowercase()
    }
}

/** One place on Home: where it sits in [HomeLayout.slots] is its position, [hidden] takes it off the page. */
data class HomeSlot(
    val key: String,
    val hidden: Boolean = false,
    val pinned: Boolean = false,
)

/**
 * The listener's own arrangement of Home. Empty means "not customised": Home
 * keeps its built-in order. Anything the layout has not seen yet goes after
 * everything it has.
 */
data class HomeLayout(val slots: List<HomeSlot> = emptyList()) {
    val isCustom: Boolean get() = slots.isNotEmpty()

    fun isHidden(key: String): Boolean = slots.any { it.key == key && it.hidden }

    fun isPinned(key: String): Boolean = slots.any { it.key == key && it.pinned }

    /** Toggle a known slot's pin without losing its hidden state or position. */
    fun togglePinned(key: String): HomeLayout {
        val index = slots.indexOfFirst { it.key == key }
        if (index < 0) return this
        return copy(
            slots = slots.mapIndexed { slotIndex, slot ->
                if (slotIndex == index) slot.copy(pinned = !slot.pinned) else slot
            },
        )
    }

    /** Saved rows (including hidden ones) first, followed by newly available rows. */
    fun editorOrder(availableKeys: List<String>): List<String> {
        val saved = slots.map { it.key }.distinct()
        val fresh = availableKeys.filterNot { it in saved }
        return saved + fresh
    }

    /** Move one known slot by [offset], leaving the layout unchanged at either boundary. */
    fun move(key: String, offset: Int): HomeLayout {
        if (offset == 0) return this
        val index = slots.indexOfFirst { it.key == key }
        if (index < 0) return this
        val target = (index.toLong() + offset.toLong())
            .coerceIn(0L, slots.lastIndex.toLong())
            .toInt()
        if (target == index) return this
        val reordered = slots.toMutableList().apply {
            add(target, removeAt(index))
        }
        return copy(slots = reordered)
    }

    /**
     * [items] in the saved order. Items the layout has not seen go last, in the
     * order they came in; saved keys without an item are skipped. Hidden items
     * are dropped unless [dropHidden] is false.
     */
    fun <T> arrange(items: List<T>, dropHidden: Boolean = true, keyOf: (T) -> String): List<T> {
        if (!isCustom) return items
        val position = HashMap<String, Int>()
        slots.forEachIndexed { index, slot -> position.putIfAbsent(slot.key, index) }
        val known = items.filter { keyOf(it) in position }.sortedWith(
            compareBy<T>({ !isPinned(keyOf(it)) }, { position.getValue(keyOf(it)) }),
        )
        val fresh = items.filter { keyOf(it) !in position }
        return (known + fresh).filterNot { dropHidden && isHidden(keyOf(it)) }
    }

    fun encode(): String = slots.joinToString("\n") {
        val flag = when {
            it.hidden && it.pinned -> '3'
            it.pinned -> '2'
            it.hidden -> '1'
            else -> '0'
        }
        flag + it.key
    }

    companion object {
        fun decode(text: String?): HomeLayout = HomeLayout(
            text.orEmpty().lineSequence()
                .filter { it.length > 1 }
                .map {
                    HomeSlot(
                        key = it.substring(1),
                        hidden = it[0] == '1' || it[0] == '3',
                        pinned = it[0] == '2' || it[0] == '3',
                    )
                }
                .distinctBy { it.key }
                .toList(),
        )
    }
}

/** Keep at least one Home/Library section visible so its tab cannot be made empty by layout edits. */
fun canHideLayoutSlot(isVisible: Boolean, visibleCount: Int): Boolean =
    !isVisible || visibleCount > 1
