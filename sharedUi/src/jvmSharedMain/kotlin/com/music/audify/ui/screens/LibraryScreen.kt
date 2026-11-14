package com.music.audify.ui.screens

import com.music.audify.sharedui.resources.*
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.background
import androidx.compose.foundation.Image
import org.jetbrains.compose.resources.stringResource
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import com.music.audify.ui.components.ShelfRow
import com.music.audify.ui.components.shelfStagger
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import com.music.audify.ui.components.ElasticRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.music.audify.data.YtMusicRepository
import com.music.audify.data.model.HomeLayout
import com.music.audify.data.model.HomeShelf
import com.music.audify.data.model.LIBRARY_SLOT_LINKS
import com.music.audify.data.model.LIBRARY_SLOT_ON_DEVICE
import com.music.audify.data.model.LIBRARY_SLOT_PINNED
import com.music.audify.data.model.LIBRARY_SLOT_REPLAY
import com.music.audify.data.model.LibraryPage
import com.music.audify.data.model.homeShelfKey
import com.music.audify.data.model.ShelfItem
import com.music.audify.data.model.UiState
import com.music.audify.data.settings.LibrarySort
import com.music.audify.ui.AppUi
import com.music.audify.ui.icons.AudifyIcons
import com.music.audify.ui.components.LIBRARY_GRID_SPACING
import com.music.audify.ui.components.MessageState
import com.music.audify.ui.components.PAGE_GUTTER
import com.music.audify.ui.components.PullToRefresh
import com.music.audify.ui.components.SHELF_CARD_WIDTH
import com.music.audify.ui.components.libraryGrid
import com.music.audify.ui.components.librarySkeleton

/**
 * The signed-in library: the saved collections, as shelves of cards.
 *
 * Deliberately only the collections. This page used to end with two runs of
 * track rows — "Liked Music" and "Songs" — which are two overlapping answers
 * to the same question and read as one list that couldn't make up its mind: a
 * track that stopped being liked didn't leave the page, it moved down it, into
 * a section most people had taken for more of the same. Liked Music is a
 * playlist, and it is reached the way every other playlist here is, by opening
 * its card.
 *
 * The liked list is still fetched — it is what the rest of the app reads a
 * track's rating off (see MainViewModel's `likeStatuses`); it just isn't a
 * second place to browse it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    signedIn: Boolean,
    state: UiState<LibraryPage>,
    listState: LazyListState,
    onShelfItemClick: (ShelfItem) -> Unit,
    onShelfItemLongPress: (ShelfItem) -> Unit,
    onNewPlaylist: () -> Unit,
    onImportSpotifyPlaylist: (() -> Unit)? = null,
    /**
     * A shelf's "Show all" — every shelf's row here stops at five cards (see
     * [LibraryGridShelf]), so this is the only way to reach whatever didn't
     * fit.
     */
    onShowAll: (HomeShelf) -> Unit,
    /**
     * The way in to Replay at the head of the page — the row of Replay cards,
     * their placeholders while the history is read, or nothing at all.
     */
    replay: @Composable () -> Unit,
    onSignIn: () -> Unit,
    onRetry: () -> Unit,
    refreshing: Boolean,
    onRefresh: () -> Unit,
    pullState: ElasticRefreshState,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues,
    /**
     * The device's folders — downloads, local files, the remote libraries this
     * build supports — drawn as a list under the Replay cards. Built by the
     * app, since which of those exist is the platform's business.
     */
    links: List<LibraryLink>,
    /**
     * The "On device" shelf under that list: the releases kept on this device
     * whole. Left off the page entirely while there are none.
     */
    deviceItems: List<ShelfItem>,
    /** The big "Library" heading; the desktop's pages carry none. */
    showTitle: Boolean = true,
    /** The listener's own order and hidden rows; empty keeps the built-in order. */
    layout: HomeLayout = HomeLayout(),
    /** Artists as one row that scrolls sideways instead of the two-across grid. */
    artistsRow: Boolean = false,
    /** Called with true for the sideways row, false for the grid, from the Artists header. */
    onArtistsRowChange: (Boolean) -> Unit = {},
    /**
     * Cards for pinned playlists that sit in no shelf of this page — a Spotify
     * account's, which carries its own title and cover with the pin.
     */
    pinnedExtraItems: List<ShelfItem> = emptyList(),
    /** Opens Audify's rule-based playlists page. */
    onSmartPlaylists: (() -> Unit)? = null,
) {
    val pinnedPlaylists by AppUi.host.pinnedPlaylists.collectAsStateWithLifecycle()
    val onDevice = stringResource(Res.string.on_device)
    // Playlist yang di-pin ditampilkan sebagai grid 3 kolom di bawah judul,
    // seperti Apple Music, dengan urutan sesuai urutan pin.
    val playlistItems = if (!signedIn) {
        emptyList()
    } else {
        (state as? UiState.Success<LibraryPage>)
            ?.data?.shelves
            ?.firstOrNull { it.title == PLAYLISTS }
            ?.items
            .orEmpty()
    }
    // A pin can name an account playlist or one kept on this device (a Spotify
    // import), and the second kind lives on the On Device shelf, not the
    // account's Playlists shelf, so both are searched.
    val pinnedItems = pinnedPlaylists.mapNotNull { id ->
        playlistItems.firstOrNull { it.browseId == id }
            ?: deviceItems.firstOrNull { it.browseId == id }
            ?: pinnedExtraItems.firstOrNull { it.browseId == id }
    }
    PullToRefresh(
        refreshing = refreshing,
        onRefresh = onRefresh,
        state = pullState,
        topInset = contentPadding.calculateTopPadding(),
        modifier = modifier,
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            // The heading sits where Home's does: the whole page is lifted by
            // the same amount, not just the title.
            contentPadding = if (showTitle) {
                PaddingValues(
                    top = (contentPadding.calculateTopPadding() - HOME_TITLE_LIFT).coerceAtLeast(0.dp),
                    bottom = contentPadding.calculateBottomPadding(),
                )
            } else {
                contentPadding
            },
        ) {
            if (showTitle) {
                item {
                    Text(
                        text = stringResource(Res.string.library),
                        style = MaterialTheme.typography.displayLarge,
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.padding(horizontal = PAGE_GUTTER, vertical = 8.dp),
                    )
                }
            }
            // Tiap bagian halaman adalah satu slot, supaya urutan dan sembunyinya
            // bisa diatur lewat Customize (AppSettings.libraryLayout).
            val sections = ArrayList<Pair<String, LazyListScope.() -> Unit>>()
            fun section(key: String, emit: LazyListScope.() -> Unit) {
                sections += key to emit
            }
            // Status muat/gagal selalu di akhir dan tidak ikut diatur.
            val status = ArrayList<LazyListScope.() -> Unit>()
            section(LIBRARY_SLOT_REPLAY) {
                item(key = "replay-heading") {
                    Column(Modifier.padding(horizontal = PAGE_GUTTER).padding(top = 12.dp, bottom = 2.dp)) {
                        Text(
                            text = "Your Replay",
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onBackground,
                        )
                        Text(
                            text = "A look at what you have been listening to.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                item(key = "replay") { replay() }
                if (onSmartPlaylists != null) {
                    item(key = "smart-playlists") {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(onClick = onSmartPlaylists)
                                .padding(horizontal = PAGE_GUTTER, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                imageVector = AudifyIcons.StarFilled,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(28.dp),
                            )
                            Column(Modifier.padding(start = 16.dp).weight(1f)) {
                                Text(
                                    text = stringResource(Res.string.smart_playlists),
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                                Text(
                                    text = stringResource(Res.string.smart_playlists_subtitle),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        HorizontalDivider(
                            modifier = Modifier.padding(start = PAGE_GUTTER),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f),
                        )
                    }
                }
            }
            section(LIBRARY_SLOT_LINKS) {
                if (links.isNotEmpty()) {
                    item(key = "links") { LibraryLinkList(links = links, onClick = onShelfItemClick) }
                }
            }
            section(LIBRARY_SLOT_PINNED) {
                if (pinnedItems.isNotEmpty()) {
                    item(key = "pinned") {
                        LibraryPinnedGrid(
                            items = pinnedItems,
                            onClick = onShelfItemClick,
                            onLongPress = onShelfItemLongPress,
                        )
                    }
                }
            }
            section(LIBRARY_SLOT_ON_DEVICE) {
                item(key = "on-device-heading") {
                    Column(Modifier.padding(horizontal = PAGE_GUTTER).padding(top = 20.dp, bottom = 12.dp)) {
                        Text(
                            text = onDevice,
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onBackground,
                        )
                        Text(
                            text = "Saved albums and playlists on this device will appear here.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                // What is pinned already sits in the grid above, so the shelf
                // keeps only the rest; "Show all" still opens everything.
                val unpinnedDevice = deviceItems.filterNot { it.browseId != null && it.browseId in pinnedPlaylists }
                if (unpinnedDevice.isNotEmpty()) {
                    item(key = "shelf:$onDevice") {
                        val onDeviceShelf = HomeShelf(title = onDevice, items = unpinnedDevice)
                        LibraryGridShelf(
                            shelf = onDeviceShelf,
                            onItemClick = onShelfItemClick,
                            onItemLongPress = onShelfItemLongPress,
                            onShowAll = { onShowAll(HomeShelf(title = onDevice, items = deviceItems)) },
                        )
                    }
                }
            }
            if (!signedIn) {
                section(homeShelfKey(PLAYLISTS)) {
                    item(key = "shelf:$PLAYLISTS") {
                        val emptyPlaylists = HomeShelf(PLAYLISTS, emptyList())
                        PlaylistShelf(
                            shelf = emptyPlaylists,
                            onItemClick = onShelfItemClick,
                            onItemLongPress = onShelfItemLongPress,
                            onNewPlaylist = onNewPlaylist,
                            onImportSpotifyPlaylist = onImportSpotifyPlaylist,
                            onShowAll = { onShowAll(emptyPlaylists) },
                        )
                    }
                    item {
                        MessageState(
                            message = stringResource(Res.string.library_sign_in_description),
                            actionLabel = stringResource(Res.string.sign_in),
                            onAction = onSignIn,
                        )
                    }
                }
            } else {
                when (state) {
                    is UiState.Loading -> status.add { librarySkeleton() }
                    is UiState.Error -> status.add {
                        item {
                            MessageState(state.message, actionLabel = stringResource(Res.string.retry), onAction = onRetry)
                        }
                    }
                    is UiState.Success -> {
                        // A fresh account has no Playlists shelf at all, and that
                        // is exactly the account most in need of the button that
                        // makes one — so the row is drawn either way, empty but
                        // for the tile that creates the first playlist.
                        val shelves = state.data.shelves
                        if (shelves.none { it.title == PLAYLISTS }) {
                            section(homeShelfKey(PLAYLISTS)) {
                                item(key = "shelf:$PLAYLISTS") {
                                    val emptyPlaylists = HomeShelf(PLAYLISTS, emptyList())
                                    PlaylistShelf(
                                        shelf = emptyPlaylists,
                                        onItemClick = onShelfItemClick,
                                        onItemLongPress = onShelfItemLongPress,
                                        onNewPlaylist = onNewPlaylist,
                                        onImportSpotifyPlaylist = onImportSpotifyPlaylist,
                                        onShowAll = { onShowAll(emptyPlaylists) },
                                    )
                                }
                            }
                        }
                        shelves.forEach { shelf ->
                            section(homeShelfKey(shelf.title)) {
                                item(key = "shelf:${shelf.title}") {
                                    if (shelf.title == PLAYLISTS) {
                                        val pinnedFirst = shelf
                                            .copy(items = shelf.items.filterNot { it.browseId == YtMusicRepository.LIKED_MUSIC })
                                            .pinnedFirst(pinnedPlaylists)
                                        // Yang di-pin sudah tampil di grid atas, jadi shelf
                                        // hanya menampilkan sisanya. "Show all" tetap membuka semuanya.
                                        val unpinned = pinnedFirst.copy(
                                            items = pinnedFirst.items.filterNot { it.browseId != null && it.browseId in pinnedPlaylists },
                                        )
                                        PlaylistShelf(
                                            shelf = unpinned,
                                            onItemClick = onShelfItemClick,
                                            onItemLongPress = onShelfItemLongPress,
                                            onNewPlaylist = onNewPlaylist,
                                            onImportSpotifyPlaylist = onImportSpotifyPlaylist,
                                            onShowAll = { onShowAll(pinnedFirst) },
                                            pinnedPlaylists = pinnedPlaylists,
                                        )
                                    } else {
                                        LibraryGridShelf(
                                            shelf = shelf,
                                            onItemClick = onShelfItemClick,
                                            onItemLongPress = onShelfItemLongPress,
                                            onShowAll = { onShowAll(shelf) },
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
            layout.arrange(sections) { it.first }.forEach { it.second(this) }
            status.forEach { it(this) }
        }
    }
}

/** One of the Library's folder rows: the page [item] opens, behind [icon]. */
/** [logo], when set, is drawn in place of [icon] — a service's own mark. */
data class LibraryLink(val item: ShelfItem, val icon: ImageVector, val logo: DrawableResource? = null)

/**
 * The folders as a plain list — icon, name, chevron, hairlines between — the
 * way a music app's library has always opened, rather than as cards that all
 * look alike because none of them has artwork.
 */
@Composable
private fun LibraryLinkList(links: List<LibraryLink>, onClick: (ShelfItem) -> Unit) {
    Column(Modifier.padding(top = 4.dp, bottom = 22.dp)) {
        links.forEachIndexed { index, link ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 54.dp)
                    .clickable { onClick(link.item) }
                    .padding(horizontal = PAGE_GUTTER),
            ) {
                if (link.logo != null) {
                    // A mark, not a glyph: white on dark, black on light, with
                    // its cut-outs left clear.
                    Icon(
                        painter = painterResource(link.logo),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.size(LINK_ICON_SIZE),
                    )
                } else {
                    Icon(
                        imageVector = link.icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(LINK_ICON_SIZE),
                    )
                }
                Spacer(Modifier.width(LINK_ICON_GAP))
                Text(
                    text = link.item.title,
                    style = MaterialTheme.typography.bodyLarge.copy(fontSize = 17.sp),
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Icon(
                    imageVector = AudifyIcons.ChevronRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = .6f),
                    modifier = Modifier.size(20.dp),
                )
            }
            if (index < links.lastIndex) {
                HorizontalDivider(
                    modifier = Modifier.padding(start = PAGE_GUTTER + LINK_ICON_SIZE + LINK_ICON_GAP),
                    thickness = 0.5.dp,
                    color = MaterialTheme.colorScheme.outline,
                )
            }
        }
    }
}

private val LINK_ICON_SIZE = 28.dp
private val LINK_ICON_GAP = 16.dp

/**
 * The one shelf on this page that can be written to: it leads with the tile
 * that creates a playlist, and holding a card gets rename and delete on top of
 * the queue actions every other shelf's menu offers.
 */
@Composable
private fun PlaylistShelf(
    shelf: HomeShelf,
    onItemClick: (ShelfItem) -> Unit,
    onItemLongPress: (ShelfItem) -> Unit,
    onNewPlaylist: () -> Unit,
    onImportSpotifyPlaylist: (() -> Unit)? = null,
    onShowAll: () -> Unit,
    pinnedPlaylists: List<String> = emptyList(),
) {
    LibraryRowShelf(
        shelf = shelf,
        onItemClick = onItemClick,
        onItemLongPress = onItemLongPress,
        onShowAll = onShowAll,
        pinnedPlaylists = pinnedPlaylists,
        leadingCards = buildList<@Composable (Modifier) -> Unit> {
            add { m ->
                NewShelfCard(
                    icon = AudifyIcons.Plus,
                    label = stringResource(Res.string.new_playlist),
                    subtitle = stringResource(Res.string.saved_to_youtube_music),
                    onClick = onNewPlaylist,
                    modifier = m,
                )
            }
            if (onImportSpotifyPlaylist != null) {
                add { m ->
                    NewShelfCard(
                        icon = AudifyIcons.Download,
                        label = stringResource(Res.string.import_spotify),
                        subtitle = stringResource(Res.string.import_spotify_subtitle),
                        onClick = onImportSpotifyPlaylist,
                        modifier = m,
                        logo = Res.drawable.spotify_logo,
                    )
                }
            }
        },
    )
}

/** A Library shelf's preview row never swipes past this many cards. */
private const val LIBRARY_ROW_MAX_ITEMS = 6

/**
 * A Library shelf in the Apple Music style: a bold heading with "Show all"
 * beside it, then one sideways-scrolling row of [SHELF_CARD_WIDTH] cards,
 * stopped at [LIBRARY_ROW_MAX_ITEMS] — the rest is behind "Show all"
 * ([LibraryGridPage]).
 *
 * [leadingCards], if given, take the first slots in the row and count against
 * that cap — see [PlaylistShelf]. Each is handed the width of a card.
 */
@Composable
internal fun LibraryGridShelf(
    shelf: HomeShelf,
    onItemClick: (ShelfItem) -> Unit,
    onItemLongPress: (ShelfItem) -> Unit,
    onShowAll: () -> Unit,
    leadingCards: List<@Composable (Modifier) -> Unit> = emptyList(),
    pinnedPlaylists: List<String> = emptyList(),
    /** A small control drawn in the header, just before "Show all". */
    headerAction: (@Composable () -> Unit)? = null,
) {
    val leadingCount = leadingCards.size
    val visibleItems = shelf.items.take((LIBRARY_ROW_MAX_ITEMS - leadingCount).coerceAtLeast(0))
    Column(Modifier.padding(bottom = 26.dp)) {
        LibrarySectionHeader(
            title = shelf.title,
            subtitle = shelf.subtitle,
            onShowAll = onShowAll.takeIf { shelf.items.size + leadingCount > LIBRARY_ROW_MAX_ITEMS },
            trailing = headerAction,
        )
        ShelfRow(
            state = rememberShelfListState("library:${shelf.title}"),
            contentPadding = PaddingValues(horizontal = PAGE_GUTTER),
            horizontalArrangement = Arrangement.spacedBy(LIBRARY_GRID_SPACING),
        ) {
            itemsIndexed(leadingCards) { _, card ->
                card(Modifier.width(SHELF_CARD_WIDTH))
            }
            itemsIndexed(visibleItems) { index, item ->
                ShelfCard(
                    item = item,
                    onClick = { onItemClick(item) },
                    onLongPress = { onItemLongPress(item) },
                    modifier = Modifier.shelfStagger(index).width(SHELF_CARD_WIDTH),
                    isPinned = item.browseId != null && item.browseId in pinnedPlaylists,
                )
            }
        }
    }
}

/** Tombol di header Artists: ikon grid saat sedang satu baris, ikon daftar saat sedang grid. */
@Composable
private fun ArtistsLayoutToggle(row: Boolean, onChange: (Boolean) -> Unit) {
    Icon(
        imageVector = if (row) AudifyIcons.GridView else AudifyIcons.ListView,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.primary,
        modifier = Modifier
            .padding(start = 8.dp)
            .size(32.dp)
            .clickable { onChange(!row) }
            .padding(5.dp),
    )
}

/** Judul shelf Artists di Library, seperti yang dikirim YtMusicRepository. */
private const val ARTISTS_SHELF = "Artists"

/**
 * Shelf Artists: grid dua kolom (bukan satu baris geser), paling banyak
 * [LIBRARY_ROW_MAX_ITEMS] kartu; sisanya di balik "Show all".
 */
@Composable
private fun LibraryTwoColumnShelf(
    shelf: HomeShelf,
    onItemClick: (ShelfItem) -> Unit,
    onItemLongPress: (ShelfItem) -> Unit,
    onShowAll: () -> Unit,
    headerAction: (@Composable () -> Unit)? = null,
) {
    Column(Modifier.padding(bottom = 26.dp)) {
        LibrarySectionHeader(
            title = shelf.title,
            subtitle = shelf.subtitle,
            onShowAll = onShowAll.takeIf { shelf.items.size > LIBRARY_ROW_MAX_ITEMS },
            trailing = headerAction,
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = PAGE_GUTTER),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            shelf.items.take(LIBRARY_ROW_MAX_ITEMS).chunked(2).forEach { rowItems ->
                Row(horizontalArrangement = Arrangement.spacedBy(LIBRARY_GRID_SPACING)) {
                    rowItems.forEach { item ->
                        ShelfCard(
                            item = item,
                            onClick = { onItemClick(item) },
                            onLongPress = { onItemLongPress(item) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                    repeat(2 - rowItems.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

/** The Playlists row never swipes past this many cards, tiles included. */
private const val LIBRARY_ROW_SHELF_MAX = 5

/**
 * The Playlists shelf: one sideways-scrolling row of [SHELF_CARD_WIDTH] cards
 * under the Library heading, stopped at [LIBRARY_ROW_SHELF_MAX] with the rest
 * behind "Show all". Every other shelf stays the two-across [LibraryGridShelf].
 *
 * [leadingCards] (New playlist, Import Spotify) take the first slots and count
 * against that cap. Each is handed the width of a card.
 */
@Composable
private fun LibraryRowShelf(
    shelf: HomeShelf,
    onItemClick: (ShelfItem) -> Unit,
    onItemLongPress: (ShelfItem) -> Unit,
    onShowAll: () -> Unit,
    leadingCards: List<@Composable (Modifier) -> Unit> = emptyList(),
    pinnedPlaylists: List<String> = emptyList(),
) {
    val leadingCount = leadingCards.size
    val visibleItems = shelf.items.take((LIBRARY_ROW_SHELF_MAX - leadingCount).coerceAtLeast(0))
    Column(Modifier.padding(bottom = 26.dp)) {
        LibrarySectionHeader(
            title = shelf.title,
            subtitle = shelf.subtitle,
            onShowAll = onShowAll.takeIf { shelf.items.size + leadingCount > LIBRARY_ROW_SHELF_MAX },
        )
        ShelfRow(
            state = rememberShelfListState("library:${shelf.title}"),
            contentPadding = PaddingValues(horizontal = PAGE_GUTTER),
            horizontalArrangement = Arrangement.spacedBy(LIBRARY_GRID_SPACING),
        ) {
            itemsIndexed(leadingCards) { _, card ->
                card(Modifier.width(SHELF_CARD_WIDTH))
            }
            itemsIndexed(visibleItems) { index, item ->
                ShelfCard(
                    item = item,
                    onClick = { onItemClick(item) },
                    onLongPress = { onItemLongPress(item) },
                    modifier = Modifier.shelfStagger(index).width(SHELF_CARD_WIDTH),
                    isPinned = item.browseId != null && item.browseId in pinnedPlaylists,
                )
            }
        }
    }
}

/**
 * The Library's section heading: bold title, with "Show all" in the accent
 * colour at the far end. Library only — Home and Explore keep [SectionHeader].
 */
@Composable
private fun LibrarySectionHeader(
    title: String,
    subtitle: String = "",
    onShowAll: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    val displayTitle = localizeShelfTitle(title)
    val displaySubtitle = localizeShelfSubtitle(subtitle)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = PAGE_GUTTER)
            .padding(top = 8.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = displayTitle,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (displaySubtitle.isNotBlank()) {
                Text(
                    text = displaySubtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        trailing?.invoke()
        if (onShowAll != null) {
            Text(
                text = stringResource(Res.string.show_all),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .clickable(onClick = onShowAll)
                    .padding(start = 12.dp, top = 4.dp, bottom = 4.dp),
            )
        }
    }
}

/**
 * The pinned playlists as a three-across grid right under the title, the way
 * Apple Music's Library opens. Long-press still opens the card's menu, which
 * is where a playlist is unpinned.
 */
@Composable
private fun LibraryPinnedGrid(
    items: List<ShelfItem>,
    onClick: (ShelfItem) -> Unit,
    onLongPress: (ShelfItem) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = PAGE_GUTTER)
            .padding(top = 4.dp, bottom = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        items.chunked(3).forEach { rowItems ->
            Row(horizontalArrangement = Arrangement.spacedBy(LIBRARY_GRID_SPACING)) {
                rowItems.forEach { item ->
                    ShelfCard(
                        item = item,
                        onClick = { onClick(item) },
                        onLongPress = { onLongPress(item) },
                        modifier = Modifier.weight(1f),
                    )
                }
                repeat(3 - rowItems.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

/**
 * Everything a Library shelf's "Show all" opens onto — the same cards, at the
 * same [libraryGrid] width, run down the screen instead of stopping at one row.
 */
@Composable
fun LibraryGridPage(
    shelf: HomeShelf,
    gridState: LazyGridState,
    onItemClick: (ShelfItem) -> Unit,
    onItemLongPress: (ShelfItem) -> Unit,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
    onNewPlaylist: (() -> Unit)? = null,
) {
    // Re-read live rather than trusting [shelf] to already be sorted: this page
    // is opened from a snapshot (see `AppRoute.LibraryShowAll`), and a
    // pin toggled from this page's own long-press menu must move the card
    // immediately rather than waiting for the row underneath to be revisited.
    val pinnedPlaylists by AppUi.host.pinnedPlaylists.collectAsStateWithLifecycle()
    val librarySort by AppUi.host.librarySort.collectAsStateWithLifecycle()
    // Pinning wins over the default order, but an explicit sort is a stronger,
    // more deliberate signal than a pin and is left to reorder the whole grid,
    // pinned cards included.
    val sortedShelf = shelf.pinnedFirst(pinnedPlaylists).sortedForLibrary(librarySort)
    BoxWithConstraints(modifier.fillMaxSize()) {
        val grid = libraryGrid(maxWidth - PAGE_GUTTER * 2)
        LazyVerticalGrid(
            columns = GridCells.Fixed(grid.columns),
            state = gridState,
            contentPadding = contentPadding,
            horizontalArrangement = Arrangement.spacedBy(LIBRARY_GRID_SPACING),
            verticalArrangement = Arrangement.spacedBy(20.dp),
            modifier = Modifier.padding(horizontal = PAGE_GUTTER),
        ) {
            if (onNewPlaylist != null) {
                item(key = "leading") {
                    NewShelfCard(
                        icon = AudifyIcons.Plus,
                        label = stringResource(Res.string.new_playlist),
                        subtitle = stringResource(Res.string.saved_to_youtube_music),
                        onClick = onNewPlaylist,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            items(sortedShelf.items, key = { it.browseId ?: it.title }) { item ->
                ShelfCard(
                    item = item,
                    onClick = { onItemClick(item) },
                    onLongPress = { onItemLongPress(item) },
                    modifier = Modifier.fillMaxWidth(),
                    isPinned = item.browseId != null && item.browseId in pinnedPlaylists,
                )
            }
        }
    }
}

/**
 * Moves whichever of this shelf's cards are in [pinned] to the front, in the
 * order they were pinned, leaving everything else in its existing order behind
 * them.
 *
 * A no-op on any shelf that isn't Playlists: [pinned] only ever holds playlist
 * browse ids, so an album or artist shelf never has a card that matches.
 */
private fun HomeShelf.pinnedFirst(pinned: List<String>): HomeShelf {
    if (pinned.isEmpty()) return this
    val byId = items.filter { it.browseId != null }.associateBy { it.browseId }
    val pinnedItems = pinned.mapNotNull { byId[it] }
    if (pinnedItems.isEmpty()) return this
    val pinnedSet = pinnedItems.toSet()
    return copy(items = pinnedItems + items.filter { it !in pinnedSet })
}

/**
 * A card's title is all a Library shelf carries, so [LibrarySort.DEFAULT] is
 * the only option that isn't alphabetical — everything else sorts on it.
 */
private fun HomeShelf.sortedForLibrary(sort: LibrarySort): HomeShelf = when (sort) {
    LibrarySort.DEFAULT -> this
    LibrarySort.TITLE_ASC -> copy(items = items.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.title }))
    LibrarySort.TITLE_DESC -> copy(
        items = items.sortedWith(compareByDescending(String.CASE_INSENSITIVE_ORDER) { it.title }),
    )
}

/** The library feed whose cards are the account's own — see [PlaylistShelf]. */
private const val PLAYLISTS = YtMusicRepository.PLAYLISTS_SHELF
