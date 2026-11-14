package com.music.audify.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.music.audify.data.YtMusicRepository
import com.music.audify.data.model.CARD_ART_PX
import com.music.audify.data.model.HomeShelf
import com.music.audify.data.model.ShelfItem
import com.music.audify.data.model.ShelfLayout
import com.music.audify.data.model.Song
import com.music.audify.data.model.artworkAt
import com.music.audify.ui.components.PAGE_GUTTER
import com.music.audify.ui.components.ShelfRow
import com.music.audify.ui.components.thumbnailBorder

/** Shelf yang dirender sebagai kartu playlist + 3 lagu, seperti di YouTube Music. */
private val PLAYLIST_PREVIEW_TITLES = listOf(
    "Today's International hits",
    "Today's hits",
    "Hits today",
)

private const val PREVIEW_TRACKS = 3

internal fun HomeShelf.isPlaylistPreviewShelf(): Boolean {
    if (layout != ShelfLayout.CARDS || items.isEmpty()) return false
    if (!items.all { it.browseId != null && it.videoId == null }) return false
    val normalized = title.trim().replace('\u2019', '\'')
    // Hanya shelf terpilih (lihat PLAYLIST_PREVIEW_TITLES) yang jadi kartu besar;
    // shelf playlist lain, mis. "From the community", tetap kartu album biasa.
    return PLAYLIST_PREVIEW_TITLES.any { normalized.equals(it, ignoreCase = true) }
}

private fun Song.asShelfItem(): ShelfItem =
    ShelfItem(title, artist, thumbnailUrl, videoId, null, isVideo = isVideo)

@Composable
internal fun PlaylistPreviewShelf(
    shelf: HomeShelf,
    onItemClick: (ShelfItem) -> Unit,
    onItemLongPress: ((ShelfItem) -> Unit)?,
    onPlaySongs: (List<Song>) -> Unit,
    currentSong: Song?,
    isPlaying: Boolean,
) {
    // Lagu tiap playlist, dimuat sekali per kartu yang tampil.
    val loaded = remember(shelf) { mutableStateMapOf<String, List<Song>>() }
    Column(Modifier.padding(bottom = 26.dp)) {
        PlaylistPreviewHeader(
            title = localizeShelfTitle(shelf.title),
            subtitle = localizeShelfSubtitle(shelf.subtitle),
        )
        BoxWithConstraints {
            // Kartu berikutnya sedikit mengintip di kanan.
            val cardWidth = (maxWidth - PAGE_GUTTER - 40.dp).coerceAtLeast(240.dp)
            ShelfRow(
                contentPadding = PaddingValues(horizontal = PAGE_GUTTER),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(shelf.items) { item ->
                    PlaylistPreviewCard(
                        item = item,
                        width = cardWidth,
                        loaded = loaded,
                        onItemClick = onItemClick,
                        onItemLongPress = onItemLongPress,
                        onPlaySongs = onPlaySongs,
                        currentSong = currentSong,
                        isPlaying = isPlaying,
                    )
                }
            }
        }
    }
}

@Composable
private fun PlaylistPreviewHeader(title: String, subtitle: String) {
    Column(
        modifier = Modifier
            .padding(horizontal = PAGE_GUTTER, vertical = 10.dp)
            .fillMaxWidth(),
    ) {
        if (subtitle.isNotBlank()) {
            Text(
                text = subtitle.uppercase(),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text(
            text = title,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun PlaylistPreviewCard(
    item: ShelfItem,
    width: androidx.compose.ui.unit.Dp,
    loaded: MutableMap<String, List<Song>>,
    onItemClick: (ShelfItem) -> Unit,
    onItemLongPress: ((ShelfItem) -> Unit)?,
    onPlaySongs: (List<Song>) -> Unit,
    currentSong: Song?,
    isPlaying: Boolean,
) {
    val browseId = item.browseId
    LaunchedEffect(browseId) {
        if (browseId != null && loaded[browseId] == null) {
            loaded[browseId] = YtMusicRepository.browseSongs(browseId)
                .getOrNull()?.songs.orEmpty()
        }
    }
    val songs = browseId?.let { loaded[it] }
    val shape = RoundedCornerShape(24.dp)
    Column(
        modifier = Modifier
            .width(width)
            .clip(shape)
            .background(
                Brush.verticalGradient(
                    listOf(
                        MaterialTheme.colorScheme.surfaceVariant,
                        MaterialTheme.colorScheme.surface,
                    ),
                ),
            )
            .clickable { onItemClick(item) }
            .padding(16.dp),
    ) {
        AsyncImage(
            model = item.thumbnailUrl.artworkAt(CARD_ART_PX),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .fillMaxWidth(0.62f)
                .aspectRatio(1f)
                .clip(RoundedCornerShape(14.dp))
                .thumbnailBorder(RoundedCornerShape(14.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
        )
        Spacer(Modifier.height(16.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = localizeCardSubtitle(item.subtitle),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.width(12.dp))
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.onBackground.copy(alpha = 0.14f))
                    .clickable(enabled = !songs.isNullOrEmpty()) { songs?.let(onPlaySongs) },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Rounded.PlayArrow,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.size(30.dp),
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        if (songs == null) {
            // Tempat lagu selagi dimuat, supaya tinggi kartu tidak lompat.
            Spacer(Modifier.height(56.dp * PREVIEW_TRACKS))
        } else {
            songs.take(PREVIEW_TRACKS).forEach { song ->
                val songItem = song.asShelfItem()
                val isCurrent = currentSong?.videoId == song.videoId
                CompactTrackRow(
                    title = song.title,
                    subtitle = song.artist,
                    thumbnailUrl = song.thumbnailUrl,
                    onClick = { onItemClick(songItem) },
                    onLongPress = onItemLongPress?.let { { it(songItem) } },
                    isCurrent = isCurrent,
                    isPlaying = isCurrent && isPlaying,
                    isVideo = song.isVideo,
                )
            }
        }
    }
}