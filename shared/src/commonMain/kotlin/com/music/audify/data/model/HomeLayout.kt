package com.music.audify.data.model

/** Key of the Top Picks for You slot on Home. */
const val HOME_SLOT_TOP_PICKS = "slot:top-picks"

/** Key of the Find Your Mood slot on Home. */
const val HOME_SLOT_MOODS = "slot:moods"

/** Keys of the fixed rows of the Library tab. Shelves use [homeShelfKey]. */
const val LIBRARY_SLOT_PINNED = "slot:library-pinned"
const val LIBRARY_SLOT_LINKS = "slot:library-links"
const val LIBRARY_SLOT_REPLAY = "slot:library-replay"
const val LIBRARY_SLOT_SMART_PLAYLISTS = "slot:library-smart-playlists"
const val LIBRARY_SLOT_ON_DEVICE = "slot:library-on-device"

/** Title of the speed dial built from local listening; kept apart from YouTube's Quick picks / Speed dial shelf. */
const val LOCAL_SPEED_DIAL_TITLE = "My speed dial"

private val LISTEN_AGAIN_ALIASES = listOf("Listen again", "Nghe lại", "もう一度聴く", "Dengarkan lagi", "Putar ulang", "다시 듣기")
private val QUICK_PICKS_ALIASES = listOf("Quick picks", "Speed dial", "Panggilan cepat", "Quay số nhanh", "スピードダイヤル", "스피드 다이얼", "Lựa chọn nhanh", "Chọn nhanh đài phát", "クイック ミックス", "Pilihan cepat", "Pilihan singkat", "빠른 선곡", "빠른 선택")

/**
 * A stable key for a feed shelf. The same shelf keeps its key across app
 * languages where its title is known in several, so a saved order survives.
 */
fun homeShelfKey(title: String, stableBrowseId: String? = null): String {
    val clean = title.trim().replace('\n', ' ')
    val known = when {
        LISTEN_AGAIN_ALIASES.any { it.equals(clean, ignoreCase = true) } -> "shelf:listen-again"
        QUICK_PICKS_ALIASES.any { it.equals(clean, ignoreCase = true) } -> "shelf:quick-picks"
        else -> null
    }
    if (known != null) return known
    stableBrowseId?.trim()?.takeIf { it.isNotEmpty() && '\n' !in it && '\r' !in it }?.let {
        return "shelf:browse:$it"
    }
    return "shelf:" + clean.lowercase()
}

/** Provider browse identity keeps shelf layout stable when titles are translated. */
fun homeShelfKey(shelf: HomeShelf): String = homeShelfKey(shelf.title, shelf.moreBrowseId)
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

    /** Whether a slot can move by [offset] without crossing a pinned-group boundary. */
    fun canMove(key: String, offset: Int): Boolean {
        if (offset == 0) return false
        val index = slots.indexOfFirst { it.key == key }
        if (index < 0) return false
        val target = (index.toLong() + offset.toLong())
            .coerceIn(0L, slots.lastIndex.toLong())
            .toInt()
        return target != index && slots[target].pinned == slots[index].pinned
    }

    /** Move one known slot by [offset], leaving the layout unchanged at either boundary. */
    fun move(key: String, offset: Int): HomeLayout {
        if (!canMove(key, offset)) return this
        val index = slots.indexOfFirst { it.key == key }
        val target = (index.toLong() + offset.toLong())
            .coerceIn(0L, slots.lastIndex.toLong())
            .toInt()
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

    /** Remap legacy title-derived shelf keys to stable provider IDs without losing flags or order. */
    fun remapShelfKeys(shelves: List<HomeShelf>): HomeLayout {
        val aliases = shelves.groupBy { homeShelfKey(it.title) }.mapNotNull { (legacy, matches) ->
            val stableKeys = matches.map(::homeShelfKey).distinct()
            stableKeys.singleOrNull()?.takeIf { it != legacy }?.let { legacy to it }
        }.toMap()
        if (aliases.isEmpty()) return this
        val merged = LinkedHashMap<String, HomeSlot>()
        slots.forEach { slot ->
            val key = aliases[slot.key] ?: slot.key
            val existing = merged[key]
            merged[key] = if (existing == null) slot.copy(key = key) else existing.copy(
                hidden = existing.hidden || slot.hidden,
                pinned = existing.pinned || slot.pinned,
            )
        }
        return HomeLayout(merged.values.toList())
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

/** Move a row within its pinned or unpinned group without crossing the priority boundary. */
fun moveHomeSlotWithinPinGroup(order: List<String>, pinned: Set<String>, key: String, delta: Int): List<String> {
    val index = order.indexOf(key)
    if (index < 0 || delta == 0) return order
    val group = order.indices.filter { (order[it] in pinned) == (key in pinned) }
    if (group.isEmpty()) return order
    val target = (index + delta).coerceIn(group.first(), group.last())
    return if (target == index) order else order.toMutableList().apply { add(target, removeAt(index)) }
}