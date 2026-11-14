package com.music.audify.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.music.audify.data.model.HomeShelf
import com.music.audify.data.model.ShelfItem
import com.music.audify.ui.components.PAGE_GUTTER
import com.music.audify.ui.components.SHELF_CARD_WIDTH
import com.music.audify.ui.components.ShelfRow
import com.music.audify.ui.components.contextClick
import com.music.audify.ui.components.longPressMenuClickable
import com.music.audify.ui.components.shelfStagger
import com.music.audify.ui.components.thumbnailBorder
import com.music.audify.ui.player.MeshGradientBackground
import com.music.audify.ui.player.MeshPalette

/*
 * Baris "Mixed for you" bergaya sampul Apple Music: kartu persegi dengan latar
 * cairan berwarna, label kecil "Music", dan nama mix ("Chill" / "Mix") di pojok
 * kiri atas. Satu baris yang digeser ke samping.
 */

/**
 * Latar sampul mix: (palet, boleh bergerak, modifier). Android mengisinya dengan
 * shader AGSL (OrganicFluidBackground); tanpa itu dipakai mesh gradient statis.
 */
val LocalMixCoverBackground =
    staticCompositionLocalOf<(@Composable (List<Color>, Boolean, Modifier) -> Unit)?> { null }

private val MixCorner = RoundedCornerShape(12.dp)

private fun ShelfItem.isMixItem(): Boolean =
    browseId?.removePrefix("VL")?.startsWith("RDTMAK") == true

/** Shelf yang isinya (hampir semuanya) mix: My Supermix, Discover Mix, My Mix 1, dst. */
internal fun HomeShelf.isMixShelf(): Boolean =
    items.isNotEmpty() && items.count { it.isMixItem() } * 2 >= items.size

@Composable
internal fun MixCoverShelf(
    shelf: HomeShelf,
    onItemClick: (ShelfItem) -> Unit,
    onItemLongPress: ((ShelfItem) -> Unit)?,
) {
    Column(Modifier.padding(bottom = 26.dp)) {
        SectionHeader(shelf.title, shelf.subtitle)
        ShelfRow(
            state = rememberShelfListState("mix:${shelf.title}"),
            contentPadding = PaddingValues(horizontal = PAGE_GUTTER),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            itemsIndexed(shelf.items) { index, item ->
                Box(Modifier.shelfStagger(index)) {
                    MixCoverCard(
                        item = item,
                        onClick = { onItemClick(item) },
                        onLongPress = onItemLongPress?.let { { it(item) } },
                    )
                }
            }
        }
    }
}

@Composable
private fun MixCoverCard(
    item: ShelfItem,
    onClick: () -> Unit,
    onLongPress: (() -> Unit)?,
) {
    val palette = remember(item.title) { mixPalette(item.title) }
    val (name, tail) = remember(item.title) { splitMixTitle(item.title) }
    val background = LocalMixCoverBackground.current
    val motion = LocalArtworkMotion.current
    val coverStyle = MaterialTheme.typography.titleMedium.copy(
        fontSize = 17.sp,
        lineHeight = 20.sp,
        shadow = Shadow(Color(0x40000000), Offset(0f, 1f), 6f),
    )
    Column(
        modifier = Modifier
            .width(SHELF_CARD_WIDTH)
            .longPressMenuClickable(onClick = onClick, onLongClick = onLongPress)
            .contextClick(onLongPress),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(MixCorner)
                .thumbnailBorder(MixCorner),
        ) {
            if (background != null) {
                background(palette, motion, Modifier.fillMaxSize())
            } else {
                MeshGradientBackground(
                    palette = remember(palette) { MeshPalette(palette.take(4)) },
                    modifier = Modifier.fillMaxSize(),
                    trackKey = item.title,
                    blurRadius = 24.dp,
                )
            }
            Column(Modifier.align(Alignment.TopStart).padding(start = 12.dp, top = 11.dp, end = 12.dp)) {
                Text(
                    text = "Music",
                    color = Color.White.copy(alpha = 0.85f),
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                        shadow = Shadow(Color(0x40000000), Offset(0f, 1f), 4f),
                    ),
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    text = name,
                    color = Color.White,
                    style = coverStyle.copy(fontWeight = FontWeight.Bold),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (tail != null) {
                    Text(
                        text = tail,
                        color = Color.White.copy(alpha = 0.9f),
                        style = coverStyle.copy(fontWeight = FontWeight.Normal),
                        maxLines = 1,
                    )
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        Text(
            text = item.title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = localizeCardSubtitle(item.subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** "Chill Mix" -> ("Chill", "Mix"); "My Mix 1" -> ("My Mix", "1"); lainnya apa adanya. */
internal fun splitMixTitle(title: String): Pair<String, String?> {
    val words = title.trim().split(' ').filter { it.isNotEmpty() }
    if (words.size >= 2) {
        val last = words.last()
        if (last.equals("Mix", ignoreCase = true) || last.all { it.isDigit() }) {
            return words.dropLast(1).joinToString(" ") to last
        }
    }
    return title.trim() to null
}

private val PinkMix = listOf(Color(0xFFFF0055), Color(0xFFF00035), Color(0xFFFF696F), Color(0xFFFF9B91), Color(0xFFFFB0B8))
private val PurpleMix = listOf(Color(0xFF5B1FD1), Color(0xFF8E2DE2), Color(0xFFB65CFF), Color(0xFF3A1C71), Color(0xFFD27BFF))
private val TealMix = listOf(Color(0xFF1E5AA8), Color(0xFF2BB3B1), Color(0xFF4FD1B5), Color(0xFF3A8DDE), Color(0xFF8FE3E0))
private val IndigoMix = listOf(Color(0xFF0A1FD6), Color(0xFF2F6BFF), Color(0xFF5FA8FF), Color(0xFF1A1F8F), Color(0xFF8FC3FF))
private val OrangeMix = listOf(Color(0xFFE8431C), Color(0xFFFF7A1A), Color(0xFFFFC53D), Color(0xFFD62E1F), Color(0xFFFF9A5C))
private val GreenMix = listOf(Color(0xFF12B886), Color(0xFF2EC4B6), Color(0xFF5BE3A5), Color(0xFF0B7A75), Color(0xFF9AF0C4))
private val MixCycle = listOf(TealMix, OrangeMix, IndigoMix, GreenMix, PurpleMix)

/** Warna halaman sebuah mix: latar dari warna dasar cairannya, aksen dari warna terangnya. */
fun mixKeyColors(title: String): com.music.audify.ui.theme.ArtworkKeyColors {
    val base = mixPalette(title)
    return com.music.audify.ui.theme.ArtworkKeyColors(background = base[0], accent = base[2])
}

/** Warna per jenis mix; "My Mix N" bergilir supaya tiap kartu di baris berbeda warna. */
fun mixPalette(title: String): List<Color> {
    val t = title.lowercase()
    return when {
        "supermix" in t || "new release" in t || "new music" in t -> PinkMix
        "discover" in t || "favorite" in t -> PurpleMix
        "chill" in t -> TealMix
        "get up" in t -> OrangeMix
        "friends" in t -> IndigoMix
        "replay" in t -> GreenMix
        else -> {
            val n = Regex("\\d+").find(t)?.value?.toIntOrNull() ?: (t.hashCode() and 0x7fffffff)
            MixCycle[n % MixCycle.size]
        }
    }
}