package com.music.audify.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.music.audify.data.YtMusicRepository
import com.music.audify.data.model.HEADER_ART_PX
import com.music.audify.data.model.HomeShelf
import com.music.audify.data.model.ShelfItem
import com.music.audify.data.model.ShelfLayout
import com.music.audify.data.model.artworkAt
import com.music.audify.ui.components.PAGE_GUTTER
import com.music.audify.ui.components.ShelfRow
import com.music.audify.ui.components.longPressMenuClickable
import com.music.audify.ui.components.thumbnailBorder
import java.util.Locale
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

/**
 * Apple Music's "New" tab card, beside Top Picks rather than instead of it: the
 * kind and name sit above a wide card, and one sentence about the release sits
 * on the picture's dark foot.
 *
 * The sentence is YouTube Music's own blurb for the release when it has one,
 * fetched as the card comes into view and kept for the process; until then, or
 * when there is none, the card's own subtitle line stands in.
 */
private const val EDITORIAL_MAX = 8
private const val EDITORIAL_MIN = 3
private const val EDITORIAL_ASPECT = 1.5f
private val EDITORIAL_SHAPE = RoundedCornerShape(16.dp)

/** Blurbs already asked for, by browse id; "" for a release that has none. */
private val editorialBlurbs = HashMap<String, String>()

/**
 * Blurb dimuat bertahap: tiap kartu mengambil satu halaman album penuh (dua
 * request + parse JSON besar), dan delapan sekaligus saat tab dibuka membuat
 * transisi dan scroll tersendat. Maksimal dua berjalan bersamaan, dan baru
 * dimulai setelah tab selesai masuk.
 */
private val blurbGate = Semaphore(2)
private const val BLURB_START_DELAY_MS = 400L
private val BLURB_WHITESPACE = Regex("\\s+")

private fun ShelfItem.isRelease() =
    !browseId.isNullOrEmpty() && !thumbnailUrl.isNullOrEmpty() && !isVideo && videoId == null

/**
 * The shelf that gets this treatment: the first release shelf after the lead two
 * (Recents and the mood row's neighbour keep their places), or null if the feed
 * has none.
 */
internal fun editorialShelfIndex(shelves: List<HomeShelf>): Int? =
    shelves.withIndex().firstOrNull { (index, shelf) ->
        index >= 2 &&
            shelf.layout == ShelfLayout.CARDS &&
            shelf.items.count { it.isRelease() } >= EDITORIAL_MIN
    }?.index

internal fun LazyListScope.editorialShelf(
    shelf: HomeShelf,
    onItemClick: (ShelfItem, String) -> Unit,
    onItemLongPress: ((ShelfItem) -> Unit)?,
) {
    item(key = "editorial") {
        EditorialShelf(
            shelf = shelf,
            onItemClick = { onItemClick(it, shelf.title) },
            onItemLongPress = onItemLongPress,
        )
    }
}

@Composable
private fun EditorialShelf(
    shelf: HomeShelf,
    onItemClick: (ShelfItem) -> Unit,
    onItemLongPress: ((ShelfItem) -> Unit)?,
) {
    val releases = remember(shelf) {
        shelf.items.filter { it.isRelease() }.distinctBy { it.browseId }.take(EDITORIAL_MAX)
    }
    Column(Modifier.padding(bottom = 26.dp)) {
        SectionHeader(title = shelf.title, subtitle = shelf.subtitle)
        BoxWithConstraints {
            // The next card peeks in at the edge, as on Apple's New tab.
            val cardWidth = maxWidth - PAGE_GUTTER - 36.dp
            ShelfRow(
                contentPadding = PaddingValues(horizontal = PAGE_GUTTER),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                items(releases, key = { it.browseId ?: it.title }) { item ->
                    EditorialCard(
                        item = item,
                        onClick = { onItemClick(item) },
                        onLongPress = onItemLongPress?.let { { it(item) } },
                        modifier = Modifier.width(cardWidth),
                    )
                }
            }
        }
    }
}

@Composable
private fun EditorialCard(
    item: ShelfItem,
    onClick: () -> Unit,
    onLongPress: (() -> Unit)?,
    modifier: Modifier,
) {
    val parts = remember(item.subtitle) {
        item.subtitle.split(" • ").map { it.trim() }.filter { it.isNotEmpty() }
    }
    // "Single • Maroon 5 • 2026": the kind rides above the name, the artist under it.
    val kind = if (parts.size > 1) parts.first() else ""
    val artist = if (parts.size > 1) parts[1] else parts.firstOrNull().orEmpty()

    val id = item.browseId.orEmpty()
    var blurb by remember(id) { mutableStateOf(editorialBlurbs[id]?.takeIf { it.isNotEmpty() }) }
    LaunchedEffect(id) {
        if (id.isEmpty() || id in editorialBlurbs) return@LaunchedEffect
        delay(BLURB_START_DELAY_MS)
        val text = blurbGate.withPermit {
            YtMusicRepository.browseSongs(id).getOrNull()?.description.cleanBlurb()
        }
        editorialBlurbs[id] = text.orEmpty()
        blurb = text
    }

    Column(modifier.longPressMenuClickable(onClick = onClick, onLongClick = onLongPress)) {
        if (kind.isNotEmpty()) {
            Text(
                text = kind.uppercase(Locale.getDefault()),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = 0.4.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text(
            text = item.title,
            style = MaterialTheme.typography.titleMedium.copy(
                fontSize = 15.sp,
                lineHeight = 20.sp,
                fontWeight = FontWeight.SemiBold,
            ),
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (artist.isNotEmpty()) {
            Text(
                text = artist,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontSize = 15.sp,
                    lineHeight = 20.sp,
                    fontWeight = FontWeight.Normal,
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.height(8.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(EDITORIAL_ASPECT)
                .clip(EDITORIAL_SHAPE)
                .thumbnailBorder(EDITORIAL_SHAPE)
                .background(MaterialTheme.colorScheme.surfaceVariant),
        ) {
            AsyncImage(
                model = item.thumbnailUrl.artworkAt(HEADER_ART_PX),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                alignment = Alignment.Center,
                modifier = Modifier.fillMaxSize(),
            )
            Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            0.45f to Color.Transparent,
                            1f to Color.Black.copy(alpha = 0.78f),
                        ),
                    ),
            )
            Text(
                text = blurb ?: item.subtitle,
                color = Color.White,
                fontSize = 14.sp,
                lineHeight = 18.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 16.dp, end = 16.dp, bottom = 14.dp),
            )
        }
    }
}

/** A blurb as one tidy paragraph: Wikipedia's credit line and stray line breaks gone; null when too thin to read as a sentence. */
private fun String?.cleanBlurb(): String? =
    this?.substringBefore("From Wikipedia")
        ?.replace(BLURB_WHITESPACE, " ")
        ?.trim()
        ?.takeIf { it.length >= 20 }