package com.music.audify.ui.screens

import com.music.audify.ui.components.trackArtSize
import com.music.audify.ui.components.OrganicFluidBackground
import android.os.Build
import android.content.Context
import android.os.SystemClock
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import com.music.audify.R

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import com.music.audify.ui.components.liquidGlassRim
import com.music.audify.ui.components.continuous
import com.music.audify.ui.components.FadingText
import com.music.audify.ui.components.longPressMenuClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import com.music.audify.data.model.LibraryState
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.withFrameMillis
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.foundation.rememberOverscrollEffect
import com.music.audify.ui.utils.PullDownOverscrollEffect
import com.music.audify.ui.utils.rememberIosOverscrollEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.border
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.music.audify.ui.components.coverFlightSource
import androidx.compose.runtime.CompositionLocalProvider
import com.music.audify.ui.components.LocalSettledMosaic
import com.music.audify.ui.graphics.forPixelAccess
import com.music.audify.ui.graphics.paletteSwatches
import com.music.audify.ui.graphics.toImageBitmap
import androidx.core.graphics.ColorUtils
import com.music.audify.ui.components.coverFlightTarget
import com.music.audify.ui.components.coverFlightClip
import com.music.audify.ui.components.coverFlightClipRendered
import com.music.audify.ui.components.RegisterCoverFlightGrab
import com.music.audify.ui.player.CanvasFrameGrab
import com.music.audify.ui.components.rememberCoverFlightTap
import com.music.audify.ui.components.enterReveal
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.Crossfade
import androidx.compose.ui.draw.drawBehind
import coil3.compose.LocalPlatformContext
import coil3.compose.AsyncImagePainter
import coil3.request.ImageRequest
import coil3.request.transformations
import androidx.compose.runtime.mutableIntStateOf
import kotlinx.coroutines.delay
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Album
import com.music.audify.data.LocalGroups
import com.music.audify.data.OfflinePlaylists
import com.music.audify.data.matchesSearch
import com.music.audify.data.sortedForLibrary
import com.music.audify.data.useTrackTags
import com.music.audify.ui.components.CoverPlaceholder
import com.music.audify.data.canvas.AppleArtistArt
import com.music.audify.data.DebugLog as Log
import com.music.audify.data.canvas.AppleAlbumInfoRepository
import com.music.audify.data.canvas.AppleArtistArtRepository
import com.music.audify.data.canvas.releaseDateText
import com.music.audify.data.canvas.appleCatalogue
import com.music.audify.data.canvas.ArtistSignature
import com.music.audify.data.canvas.ArtistSignatureRepository
import com.music.audify.data.canvas.SignatureInkTransformation
import com.music.audify.data.canvas.keyColors
import dev.chrisbanes.haze.materials.ExperimentalHazeMaterialsApi
import dev.chrisbanes.haze.materials.HazeMaterials
import com.music.audify.data.canvas.CanvasArtwork
import com.music.audify.data.canvas.CanvasRepository
import com.music.audify.data.model.BrowseType
import com.music.audify.data.model.DetailPage
import com.music.audify.data.model.CARD_ART_PX
import com.music.audify.data.model.HEADER_ART_PX
import com.music.audify.data.model.ROW_ART_PX
import com.music.audify.data.model.HomeShelf
import com.music.audify.data.model.ShelfItem
import com.music.audify.data.settings.SongSort
import com.music.audify.data.model.Song
import com.music.audify.data.model.SubscriptionState
import com.music.audify.data.model.UiState
import com.music.audify.data.model.artworkAt
import com.music.audify.data.model.durationMillis
import com.music.audify.data.model.playingRowMatcher
import com.music.audify.data.settings.AppSettings
import com.music.audify.ui.components.DownloadedBadge
import com.music.audify.ui.components.ExplicitSongTitle
import com.music.audify.ui.components.LIBRARY_GRID_SPACING
import com.music.audify.ui.components.MessageState
import com.music.audify.ui.components.PAGE_GUTTER
import com.music.audify.ui.components.NUMBERED_ROW_DIVIDER_INSET
import com.music.audify.ui.components.RowMoreButton
import com.music.audify.ui.components.ROW_DIVIDER_INSET
import com.music.audify.ui.components.SHELF_CARD_WIDTH
import com.music.audify.ui.components.SongRow
import com.music.audify.ui.components.SearchPlayingBars
import com.music.audify.ui.components.libraryGrid
import com.music.audify.ui.components.lightweightLiquidGlass
import com.music.audify.ui.components.thumbnailBorder
import com.music.audify.ui.components.detailSkeleton
import com.music.audify.ui.components.topBarContentPadding
import com.music.audify.ui.components.trackColumnWidth
import com.music.audify.ui.haptics.Haptic
import com.music.audify.ui.haptics.rememberHaptics
import com.music.audify.ui.icons.AudifyIcons
import com.music.audify.ui.player.CanvasArtworkPlayer
import com.music.audify.ui.theme.ArtworkPalette
import com.music.audify.ui.theme.rememberArtworkPalette
import kotlin.math.roundToInt
import kotlin.random.Random
import java.util.Locale
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.translate
import com.music.audify.data.YtMusicRepository

private const val MAX_ARTIST_SONGS = 20
private const val SONGS_PER_COLUMN = 4
private const val ARTIST_ROW_MAX_ITEMS = 5

/** Content that arrives after the page is already up fades in instead of popping in. */
private fun androidx.compose.foundation.lazy.LazyItemScope.arriveFade(): Modifier =
    Modifier.animateItem(
        fadeInSpec = androidx.compose.animation.core.tween(
            durationMillis = 380,
            easing = androidx.compose.animation.core.FastOutSlowInEasing,
        ),
        placementSpec = null,
        fadeOutSpec = null,
    )

/** The artist photo, very slightly taller than it is wide. */
private const val ARTIST_PHOTO_RATIO = 0.95f

/** The framed sleeve on a release page, as a fraction of the page width. */
private const val SLEEVE_FRACTION = 0.76f

/** ...and of its height, so a landscape window still shows the title under it. */
private const val SLEEVE_MAX_HEIGHT = 0.5f

private val SLEEVE_CORNER = 20.dp
private val SLEEVE_SHAPE = RoundedCornerShape(SLEEVE_CORNER)
private val SLEEVE_SHADOW = 18.dp

/** Shuffle and Add to Library either side of the release page's Play pill. */
private val RELEASE_SIDE_BUTTON = 50.dp
private val RELEASE_ACTION_GAP = 14.dp

/**
 * Past this the Play pill stops growing, so neither a tablet nor a wide phone
 * gets a bar. Termasuk gutter kiri-kanan (24dp per sisi): sisa ~288dp untuk
 * Shuffle, Play, dan Add, sehingga Play ~160dp dan tombol samping ~41dp dari
 * tepi layar 360dp, seperti Apple Music.
 */
private val RELEASE_ACTIONS_MAX_WIDTH = 336.dp
private val PILL_SHAPE = RoundedCornerShape(12.dp)

/**
 * Where the search field sits once it is open — directly under the header,
 * which is item zero. The one place that has to know, so that opening the
 * search can carry the page up to it.
 */
private const val SEARCH_ITEM_INDEX = 1

/** The inset the header text and the action pills share. */
private val HEADER_GUTTER = PAGE_GUTTER + 8.dp

/** Extra breathing room for the editorial copy on album and artist pages. */
private val ABOUT_GUTTER = PAGE_GUTTER + 6.dp

/** The artist bio and everything below it: the page edge every page shares. */
private val ARTIST_CONTENT_GUTTER = PAGE_GUTTER

/**
 * How far past the foot of the artist photo the name is allowed to hang.
 *
 * Sat flush to the bottom of the picture it lands wherever the picture happens
 * to be busy, and on a photo with anything going on down there the name reads
 * as part of it rather than as a caption to it. Dropped clear, it sits on the
 * blurred colour instead, which has nothing in it to compete.
 */
private val HEADER_DROP = 44.dp

/** How far the artist name/logo and everything under it sit above the usual [HEADER_DROP]. */
private val ARTIST_HEADER_LIFT = 64.dp

/** The artist action row: a large Play flanked by two smaller glass circles. */
private val ARTIST_PLAY_BUTTON = 70.dp

/** A larger share of a smaller circle than the release headers' 0.44. */
private const val ARTIST_PLAY_ICON_SCALE = 0.56f
private val ARTIST_SIDE_BUTTON = 52.dp
private val ARTIST_ACTION_GAP = 24.dp

/** The title logo's widest share of the page, and tallest share of the photo. */
private const val ARTIST_LOGO_WIDTH = 0.82f

/** The signature closing the About section. */
private val ABOUT_LOGO_HEIGHT = 56.dp

/** A signature is drawn at this share of a title logo's size. */
private const val SIGNATURE_SCALE = 0.7f
private const val ARTIST_LOGO_MAX_HEIGHT = 0.30f

private const val LOGO_RETRIES = 2
private const val LOGO_RETRY_DELAY_MS = 800L

/** Vector signatures decode at this width so small files stay crisp laid out large. */
private const val SVG_DECODE_WIDTH = 1600

private val CREDIT_SEPARATOR = Regex(""",\s|\s&\s|\s(?:x|and|feat\.?|ft\.?|with)\s""", RegexOption.IGNORE_CASE)

/** Whether this reads as several artists credited together rather than one name. */
private fun String.looksLikeCredit() = CREDIT_SEPARATOR.containsMatchIn(this)

/**
 * Album / artist / playlist page. Rendered inside the main content area
 * rather than as a sheet, so the tab bar and mini player stay visible.
 *
 * The page paints itself in the artwork's own colours — one solid tint behind
 * everything, secondary text in a lighter shade of the same hue, and an accent
 * taken off the sleeve for whatever is playing. See [rememberArtworkPalette]
 * for how those are derived and kept legible.
 *
 * A release is one layer: the list, sleeve and all, on a flat colour. An
 * artist page adds [PageBackground] under it — the photograph, dissolving into
 * the tint — since that picture runs edge to edge up under the bar. Nothing in
 * either is re-blurred while the page scrolls.
 */
@Composable
fun DetailScreen(
    page: DetailPage,
    currentSong: Song?,
    isPlaying: Boolean,
    onSongClick: (List<Song>, Int) -> Unit,
    onSongLongPress: (Song) -> Unit,
    onSongSwipe: (Song) -> Unit,
    onShuffle: (List<Song>) -> Unit,
    onSectionItemClick: (ShelfItem) -> Unit,
    onArtistClick: (String, String) -> Unit,
    onAddSuggested: (Song) -> Unit,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
    listState: LazyListState = rememberLazyListState(),
    activeShelf: HomeShelf? = null,
    onActiveShelfChange: (HomeShelf?) -> Unit = {},
    /**
     * Holding one of the album cards on an artist page — the same menu the
     * shelves on every other tab open, so a release can be queued from
     * wherever it is seen rather than only from its own page.
     */
    onSectionItemLongPress: ((ShelfItem) -> Unit)? = null,
    /**
     * Whether the filter box is open under the header. Owned by the caller
     * because the control that opens it sits in the top bar, beside the sort
     * and overflow buttons, not on this page.
     */
    searching: Boolean = false,
    onSearchingChange: (Boolean) -> Unit = {},
    /**
     * Saves this release to the account's library, or takes it out —
     * [DetailPage.library] says which way round. Null hides the control
     * entirely, which is the answer for a guest and for the pages YouTube never
     * offers to save; there is nothing to show a signed-out user here that
     * wouldn't just be refused.
     */
    onToggleLibrary: (() -> Unit)? = null,
    /**
     * Subscribes to this artist's channel, or unsubscribes —
     * [DetailPage.subscription] says which way round. The artist page's answer
     * to [onToggleLibrary], and null in the same circumstances: a guest, or a
     * page whose header never offered the button.
     */
    onToggleSubscription: (() -> Unit)? = null,
    /** Library state of releases shown as cards on this page, by browse id. */
    releaseLibrary: Map<String, LibraryState> = emptyMap(),
    onLoadReleaseLibrary: ((String) -> Unit)? = null,
    /** Null where saving isn't offered — a guest. */
    onToggleReleaseLibrary: ((String) -> Unit)? = null,
    /**
     * How the track list is ordered — the release's own running order by
     * default, or alphabetical. Owned by the caller rather than this page
     * because the control for it lives in the top bar, alongside the account
     * photo, not in this header.
     */
    songSort: SongSort = SongSort.DEFAULT,
    /**
     * Set when this page is a device folder — Downloads, Local Music and the
     * like. The header is the same as any playlist's; under it, instead of one
     * running order, come the folder's Songs / Artists / Albums tabs. See
     * [LocalFolder].
     */
    folder: LocalFolder? = null,
    /**
     * Set while this playlist's songs are being rearranged: drawn in place of
     * the track list, over the same page colour, and handed the page's
     * [ArtworkPalette] so it reads as this page rather than a sheet over it.
     */
    reorder: (@Composable (ArtworkPalette) -> Unit)? = null,
    /** Membuka popup tambah lagu. Hanya diberikan untuk playlist milik akun; null menyembunyikan tombolnya. */
    onAddSongs: (() -> Unit)? = null,
) {
    val rawSongs = (page.songs as? UiState.Success)?.data.orEmpty()
    // A folder keeps its own sort — the one its top bar menu sets — rather
    // than the per-page one a release takes.
    val folderSort = folder?.sort
    val songs = remember(rawSongs, songSort, folderSort) {
        if (folderSort != null) rawSongs.sortedForLibrary(folderSort) else rawSongs.sortedForDetail(songSort)
    }
    // What a numbered row shows regardless of [songSort] — an album's track
    // numbers are the sleeve's own and must not relabel themselves to match
    // wherever a sort put the row. An album grouped off a device's files
    // numbers from its tags where they say, since a release with a track
    // missing must not renumber the rest.
    val originalTrackNumbers = remember(rawSongs) {
        val byTag = rawSongs.useTrackTags()
        rawSongs.withIndex().associate { (i, s) -> s.videoId to ((if (byTag) s.trackNumber else null) ?: (i + 1)) }
    }
    val isArtist = page.type == BrowseType.ARTIST
    // Whether the top release card's album is already saved isn't on the shelf
    // item; it is read off the album once, as soon as the card is known.
    val topReleaseId = if (isArtist) page.sections.topRelease()?.browseId else null
    LaunchedEffect(topReleaseId) { topReleaseId?.let { onLoadReleaseLibrary?.invoke(it) } }
    // Apple Music's hero photo and title logo for this artist, found by name and
    // confirmed by a shared title in both catalogues — so it waits for the
    // page's songs. Null until (and unless) it arrives; the YouTube header
    // stands in meanwhile.
    var appleArt by remember(page.browseId) {
        mutableStateOf(if (isArtist) AppleArtistArtRepository.cached(page.browseId) else null)
    }
    val songsLoading = page.songs is UiState.Loading
    LaunchedEffect(page.browseId, page.title, songsLoading) {
        if (isArtist && !songsLoading) {
            appleArt = AppleArtistArtRepository.artFor(page.browseId, page.title) { page.appleCatalogue() } ?: appleArt
        }
    }
    // Secondary provider: handwritten signature from the user's own
    // artist-signature API. Drawn only when Apple has no title logo.
    val signatureEnabled by AppSettings.signatureApiEnabled.collectAsStateWithLifecycle()
    val signatureBaseUrl by AppSettings.signatureApiUrl.collectAsStateWithLifecycle()
    val signatureUpdates by ArtistSignatureRepository.updates.collectAsStateWithLifecycle()
    var signature by remember(page.browseId) { mutableStateOf<ArtistSignature?>(null) }
    // Opened from a track, the title is the whole credit ("A, B & C") until the
    // page loads and swaps in the one artist's name. Searching a credit finds
    // nobody, so a title that reads as several artists waits for that swap; any
    // other name starts the moment the page opens.
    val nameSettled = !page.title.looksLikeCredit() || page.songs !is UiState.Loading
    LaunchedEffect(page.browseId, page.title, nameSettled, signatureEnabled, signatureBaseUrl, signatureUpdates) {
        if (!isArtist || !nameSettled) return@LaunchedEffect
        val base = ArtistSignatureRepository.effectiveBaseUrl(signatureEnabled, signatureBaseUrl)
        if (base == null) {
            signature = null
            return@LaunchedEffect
        }
        // Bounded retries with backoff: a sleeping self-hosted API needs
        // ~30-60s to wake (Render free tier), and a single miss here would
        // otherwise stand until the page is reopened. Genuine misses aren't
        // cached, so retrying is cheap. Covers roughly a full wake cycle.
        val backoff = listOf(0L, 5_000L, 15_000L, 30_000L)
        for (wait in backoff) {
            if (wait > 0) delay(wait)
            signature = ArtistSignatureRepository.cached(page.title, base)
                ?: ArtistSignatureRepository.signatureFor(page.title, base)
                ?: signature
            if (signature != null) return@LaunchedEffect
        }
    }
    val palette = rememberArtworkPalette(
        // Folder perangkat (Local, Downloads, Cached) tidak punya sampul sendiri:
        // warnanya diambil dari cover lagu pertama, sama dengan hero, supaya
        // halamannya menyatu seperti halaman artis.
        imageUrl = appleArt?.heroUrl ?: page.thumbnailUrl
            ?: if (page.isFolderCover()) songs.firstOrNull { it.thumbnailUrl != null }?.thumbnailUrl else null,
        keyColors = appleArt?.keyColors() ?: page.mix?.let { mixKeyColors(it) },
    )

    // Narrowing the running order in place — the release equivalent of the
    // filter box on the Local Music tab, and the one thing a long track list
    // needs that scrolling can't give it. [searching] says whether it is open;
    // the text itself is the page's, and goes when the box does: a filter left
    // on an album that was closed and reopened would be a page that appears to
    // have lost most of its tracks.
    var query by rememberSaveable(page.browseId) { mutableStateOf("") }
    // Whether the field still owes the keyboard an appearance. Held here rather
    // than in the field, which is a row in a lazy list: scrolled out of sight it
    // is disposed, and a field that asks for focus every time it is composed
    // would throw the keyboard back up each time it scrolled into view.
    var focusSearch by remember(page.browseId) { mutableStateOf(false) }
    val closeSearch = { onSearchingChange(false) }
    // Back closes the search first — this handler is registered after the one
    // that pops the page, so it is the one that answers while it's enabled.
    BackHandler(enabled = searching) { closeSearch() }

    // Each surviving row still knows where it sat in the full running order, so
    // an album's track numbers stay the album's rather than becoming positions
    // in the filtered list.
    val matches = remember(songs, query) { songs.matching(query) }
    // What a tap plays: the filtered set of tracks currently standing in the
    // list. When no filter is active, matches contains the full running order
    // and plays the complete release/playlist from the tapped position.
    val queue = remember(matches) { matches.map { it.value } }
    val suggested = remember(page.suggestedSongs, query) {
        page.suggestedSongs.matching(query).map { it.value }
    }

    // A device folder's tab and Downloads selection. The selection answers
    // Back before the search does: it is registered after it, so it is asked
    // first while it is on.
    val folderState = folder?.state
    val folderGroups = if (folder != null && folderState != null) {
        rememberLocalFolderGroups(page.browseId, folder, folderState.tab, rawSongs, query)
    } else {
        emptyList()
    }
    BackHandler(enabled = folderState?.selecting == true) { folderState?.clear() }

    // What marks a row as already downloaded, tinted from the sleeve like the
    // rest of the page. Null on any page that is itself a reading of this
    // device — the Downloads folder, one downloaded playlist — where every row
    // qualifies and the badge would be decoration rather than information.
    val downloadedTint = palette.accent.takeUnless { page.browseId.startsWith("local:") }

    // Animated cover art on the header, the same feature the player has.
    // Albums only: a playlist's artwork is a collage and an artist page's is a
    // photograph, and neither is something a label publishes a canvas for.
    val canvasEnabled by AppSettings.animatedCanvas.collectAsStateWithLifecycle()
    val prioritizeSpotifyCanvas by AppSettings.prioritizeSpotifyCanvas.collectAsStateWithLifecycle()
    // The credit line the header shows is the artist as far as the catalogue
    // services are concerned. A browse card's subtitle sometimes omits it, in
    // which case the tracks themselves know who it is.
    val credit = page.headerLines(songs.size).first.ifBlank { songs.firstOrNull()?.artist.orEmpty() }
    // Starts from the clip the card was playing when it was tapped, so the page
    // has it from its first frame instead of looking it up after the cover lands.
    var canvas by remember(page.browseId) {
        mutableStateOf<CanvasArtwork?>(coverFlightClip(page.browseId)?.takeIf { canvasEnabled })
    }
    LaunchedEffect(page.browseId, page.title, credit, canvasEnabled, prioritizeSpotifyCanvas) {
        // An artist's clip is set below, by the lookup that finds it.
        if (isArtist) return@LaunchedEffect
        if (!canvasEnabled || page.type != BrowseType.ALBUM) {
            canvas = null
            return@LaunchedEffect
        }
        // As on the player: the credit fills in once the tracks load, so this
        // can run twice. Keep a clip that is already playing if the second
        // pass comes back empty.
        // The same clip as the one handed over is left running, not rebuilt.
        val found = CanvasRepository.canvasForAlbum(page.title, credit)
        if (found != null && found.url != canvas?.url) canvas = found
    }
    // Tanggal rilis lengkap dan baris ℗ dari Apple, hanya untuk album. Tahun dari
    // subtitle tampil lebih dulu; jawaban Apple menggantinya begitu datang.
    var albumInfo by remember(page.browseId) {
        mutableStateOf(AppleAlbumInfoRepository.cached(page.browseId))
    }
    LaunchedEffect(page.browseId, page.title, credit, songsLoading) {
        if (page.type == BrowseType.ALBUM && folder == null &&
            !page.browseId.startsWith("local:") && !songsLoading && credit.isNotBlank()
        ) {
            albumInfo = AppleAlbumInfoRepository.infoFor(
                page.browseId, page.title, credit, songs.size,
            ) ?: albumInfo
        }
    }

    // An artist's clip comes with the same Apple lookup as its photograph.
    val artistVideo = appleArt?.videoUrl
    LaunchedEffect(artistVideo, canvasEnabled) {
        if (!isArtist) return@LaunchedEffect
        canvas = when {
            !canvasEnabled -> null
            // Not found yet: keep the clip the card handed over.
            artistVideo == null -> canvas
            artistVideo == canvas?.url -> canvas
            else -> CanvasArtwork(url = artistVideo)
        }
    }

    // Opening the search carries the page up to it, so the field lands just
    // clear of the frosted bar with the tracks under it rather than at the foot
    // of a screen still filled with artwork. Done as an effect rather than in
    // the tap, so the row it scrolls to is already in the list by the time it
    // runs.
    val searchStop = with(LocalDensity.current) { topBarContentPadding().roundToPx() }
    LaunchedEffect(searching) {
        if (searching) {
            focusSearch = true
            listState.animateScrollToItem(SEARCH_ITEM_INDEX, -searchStop)
        } else {
            query = ""
        }
    }

    AnimatedContent(
        targetState = activeShelf,
        transitionSpec = {
            fadeIn(animationSpec = tween(220)) togetherWith fadeOut(animationSpec = tween(180))
        },
        label = "artist_shelf_transition",
        modifier = modifier.fillMaxSize(),
    ) { targetShelf ->
        if (targetShelf == null) {
            // An artist's photograph dissolves into [ArtworkPalette.wash]; a
            // release's sleeve sits framed on one flat colour with nothing to
            // dissolve, so it takes the same [ArtworkPalette.background] the
            // bars at either end of the screen are already fading into.
            // [palette] already eases into the artwork's colour (see
            // rememberArtworkPalette). A second ease here lagged behind every
            // other surface painted from it, and the two showed as mismatched squares.
            // Album full-bleed seperti foto artis: halamannya memakai wash yang
            // sama dengan tempat kaki cover larut, bukan latar bingkai polos.
            val pageColor = if (isArtist || page.isAlbumHero() || page.isMixHero()) palette.wash else palette.background
            BoxWithConstraints(Modifier.fillMaxSize().background(pageColor)) {
                // Measured rather than read off the window. The player runs
                // full-screen at every window size, so this page's own width in
                // landscape is the *whole* window. Straight off that width, the ratio hands back
                // a picture taller than the window itself — the artwork and track list
                // end up scrolled out of sight beneath what reads as a blank page.
                // Capping against the window's own height is what keeps the ratio's
                // math honest once the width it's fed is no longer guaranteed to be
                // the narrow one it was written for.
                val artHeight = (maxWidth / ARTIST_PHOTO_RATIO).coerceAtMost(maxHeight * 0.6f)
                val sleeveSize = (maxWidth * SLEEVE_FRACTION).coerceAtMost(maxHeight * SLEEVE_MAX_HEIGHT)
                // A folder's grid view: the same column count the Library's
                // "Show all" grids reach at this width.
                val gridColumns = libraryGrid(maxWidth - PAGE_GUTTER * 2).columns
                // Liked Music: tarikan ke bawah dibatasi kecil, jadi seni ekstra di
                // atas hero cukup tipis dan lubang tidak pernah terlihat.
                val listOverscroll = if (isArtist || page.isLikedMusic() || page.covers.isNotEmpty() || page.isFolderCover() || page.isAlbumHero() || page.isMixHero()) {
                    rememberIosOverscrollEffect(LIKED_MAX_PULL)
                } else {
                    rememberOverscrollEffect()
                }

                // The artist photo is drawn behind the list rather than in it, so
                // both need to agree on its height without being able to ask each
                // other — see [artHeight]. A release has no such layer: its sleeve
                // is an ordinary item at the top of the list.
                if (isArtist) {
                    PageBackground(
                        page = page,
                        palette = palette,
                        canvas = canvas,
                        artHeight = artHeight,
                        listState = listState,
                        heroUrl = appleArt?.heroUrl,
                        pull = { (listOverscroll as? PullDownOverscrollEffect)?.pullDownPx ?: 0f },
                        modifier = Modifier.matchParentSize(),
                    )
                }

                if (reorder != null) {
                    reorder(palette)
                } else LazyColumn(
                    state = listState,
                    overscrollEffect = listOverscroll,
                    modifier = Modifier.fillMaxSize(),
                    // The artist photo runs edge-to-edge up under the glass bar;
                    // a release header clears the bar itself, see [ReleaseHeader].
                    contentPadding = PaddingValues(bottom = contentPadding.calculateBottomPadding()),
                ) {
            item(key = "header") {
                if (isArtist) {
                    ArtistHeader(page = page, palette = palette, artHeight = artHeight, appleArt = appleArt, signature = signature)
                } else {
                    ReleaseHeader(
                        page = page,
                        palette = palette,
                        sleeveSize = sleeveSize,
                        heroHeight = artHeight * 0.92f,
                        pull = { (listOverscroll as? PullDownOverscrollEffect)?.pullDownPx ?: 0f },
                        albumGenre = albumInfo?.genre,
                        canvas = canvas,
                        songs = songs,
                        onPlay = { onSongClick(songs, 0) },
                        onShuffle = { onShuffle(songs) },
                        // A folder's credit says where its tracks live, not who made them.
                        onArtistClick = onArtistClick.takeIf { folder == null },
                        onToggleLibrary = onToggleLibrary,
                        onDownloadAll = folder?.onDownload?.let { download -> { download(songs) } },
                        onAddSongs = onAddSongs,
                    )
                }
            }

            if (searching) {
                item(key = "search") {
                    DetailSearchField(
                        query = query,
                        onQueryChange = { query = it },
                        onClose = closeSearch,
                        autoFocus = focusSearch,
                        onFocused = { focusSearch = false },
                        palette = palette,
                        type = page.type,
                    )
                }
            }

            val description = page.description

            if (songs.isNotEmpty() && isArtist) {
                item(key = "actions") {
                    Column(arriveFade()) {
                    ActionRow(
                        onPlay = { onSongClick(songs, 0) },
                        onShuffle = { onShuffle(songs) },
                        subscription = page.subscription?.takeIf { onToggleSubscription != null },
                        onToggleSubscription = onToggleSubscription,
                        bottomSpace = 22.dp,
                        playColor = appleArt?.keyColor?.let { Color(it) },
                    )
                    }
                }
            }

            // The first release on the shelves below, pulled out as a card of its own.
            val topRelease = if (isArtist) page.sections.topRelease() else null
            if (topRelease != null) {
                item(key = "top-release") {
                    Column(arriveFade()) {
                    TopReleaseCard(
                        item = topRelease,
                        palette = palette,
                        onClick = { onSectionItemClick(topRelease) },
                        onLongPress = onSectionItemLongPress?.let { { it(topRelease) } },
                        saved = topRelease.browseId?.let { releaseLibrary[it]?.saved },
                        onToggleSaved = onToggleReleaseLibrary?.let { toggle ->
                            topRelease.browseId?.let { id -> { toggle(id) } }
                        },
                    )
                    }
                }
            }

            // YouTube's own editorial blurb — an album or an artist only, per
            // [DetailPage.description]. A playlist never carries one, and the
            // section is skipped for it even on the rare response that does.
            //
            // On an artist page it closes the page, after the shelves, rather
            // than sitting between the action row and the songs.
            // The artist's counts close it, under the text, so a page whose blurb is
            // empty still gets the section for them.
            val hasStats = isArtist &&
                (page.subscriberCountText != null || page.monthlyListenerCount != null)
            // The header holds Apple's logo here, so the ink goes at the foot of the page instead.
            val aboutSignature = if (isArtist && appleArt?.logoUrl != null) signature else null
            val showAbout = (!description.isNullOrBlank() &&
                (page.type == BrowseType.ALBUM || isArtist)) || hasStats || aboutSignature != null
            fun LazyListScope.aboutItem() {
                if (!showAbout) return
                item(key = "about") {
                    // Clear of the last shelf above it.
                    Box(Modifier.padding(top = if (isArtist) 28.dp else 0.dp)) {
                    AboutSection(
                        title = stringResource(
                            if (isArtist) R.string.about_artist else R.string.about_album,
                        ),
                        text = description?.takeIf { it.isNotBlank() },
                        palette = palette,
                        horizontalPadding = if (isArtist) ARTIST_CONTENT_GUTTER else ABOUT_GUTTER,
                        compact = !isArtist,
                        stats = if (hasStats) {
                            {
                                ArtistStatsRow(
                                    subscriberCountText = page.subscriberCountText,
                                    monthlyListenerCount = page.monthlyListenerCount,
                                    palette = palette,
                                )
                            }
                        } else {
                            null
                        },
                        footer = aboutSignature?.let { sig ->
                            {
                                AboutSignature(
                                    signature = sig,
                                    name = page.title,
                                    palette = palette,
                                    horizontalPadding = ARTIST_CONTENT_GUTTER,
                                )
                            }
                        },
                    )
                    }
                }
            }
            if (!isArtist) aboutItem()

            when (val state = page.songs) {
                is UiState.Loading -> detailSkeleton(isArtist)
                is UiState.Error -> item { MessageState(state.message) }
                is UiState.Success -> if (folder != null && folderState != null) {
                    localFolderItems(
                        afterFlight = page.covers.isNotEmpty(),
                        folder = folder,
                        state = folderState,
                        songs = queue,
                        groups = folderGroups,
                        query = query,
                        columns = gridColumns,
                        palette = palette,
                        currentSong = currentSong,
                        isPlaying = isPlaying,
                        onSongClick = onSongClick,
                        onSongLongPress = onSongLongPress,
                        onSongSwipe = onSongSwipe,
                    )
                } else if (isArtist) {
                    // An artist's full song list would bury the album shelves, so
                    // it pages sideways four at a time and stops at twenty.
                    item(key = "artist-songs") {
                        val top = state.data.take(MAX_ARTIST_SONGS)
                        val isCurrentRow = playingRowMatcher(top, currentSong)
                        Column(arriveFade()) {
                        SectionHeading(
                            title = stringResource(R.string.top_songs),
                            palette = palette,
                            horizontalPadding = ARTIST_CONTENT_GUTTER,
                        )
                        BoxWithConstraints {
                            val columnWidth = trackColumnWidth(maxWidth)
                            val topSongsState = rememberLazyListState()
                            LazyRow(
                                state = topSongsState,
                                contentPadding = PaddingValues(horizontal = ARTIST_CONTENT_GUTTER),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                flingBehavior = rememberSnapFlingBehavior(lazyListState = topSongsState),
                            ) {
                                items(top.chunked(SONGS_PER_COLUMN)) { column ->
                                    Column(Modifier.width(columnWidth)) {
                                        column.forEach { song ->
                                            CompactSongRow(
                                                song = song,
                                                palette = palette,
                                                onClick = { onSongClick(top, top.indexOf(song)) },
                                                onLongPress = { onSongLongPress(song) },
                                                downloadedTint = downloadedTint,
                                                isCurrent = isCurrentRow(song),
                                                isPlaying = isCurrentRow(song) && isPlaying,
                                            )
                                        }
                                    }
                                }
                            }
                        }
                        }
                    }
                } else {
                    // Every row on an album carries the same sleeve, which is
                    // already the largest thing on the page — Apple Music
                    // numbers those rows instead, and so does this.
                    val numbered = page.type == BrowseType.ALBUM
                    val isCurrentRow = playingRowMatcher(matches.map { it.value }, currentSong)
                    if (matches.isEmpty() && state.data.isNotEmpty()) {
                        item(key = "no-matches") {
                            MessageState(stringResource(R.string.nothing_matches, query))
                        }
                    }
                    itemsIndexed(
                        items = matches,
                        key = { _, entry -> "${entry.index}_${entry.value.videoId}" },
                        contentType = { _, _ -> "song-row" },
                    ) { position, entry ->
                        // The first rows arrive one after another; later ones, composed
                        // while scrolling, are already visible and draw as they are.
                        Column(Modifier.enterReveal(340 + position.coerceAtMost(8) * 30)) {
                        val song = entry.value
                        val isCurrent = isCurrentRow(song)
                        SongRow(
                            song = if (numbered) {
                                song
                            } else {
                                song.copy(thumbnailUrl = song.thumbnailUrl ?: page.thumbnailUrl)
                            },
                            onClick = {
                                onSongClick(queue, position)
                            },
                            onLongPress = { onSongLongPress(song) },
                            onSwipeToQueue = { onSongSwipe(song) },
                            rowBackground = Color.Transparent,
                            // The track's place on the release, not its place in
                            // what the filter — or a sort — left standing.
                            trackNumber = originalTrackNumbers[song.videoId].takeIf { numbered },
                            subtitleColor = palette.onBackgroundVariant,
                            // Apple Music: no artist line when it is the album's own
                            // artist, and no per-row duration on an album.
                            hideArtistOf = credit.takeIf { numbered && it.isNotBlank() },
                            showDuration = !numbered,
                            downloadedTint = downloadedTint,
                            isCurrent = isCurrent,
                            isPlaying = isCurrent && isPlaying,
                            activeTint = palette.accent,
                            searchPlayingStyle = true,
                        )
                        if (position < matches.lastIndex) {
                            HorizontalDivider(
                                modifier = Modifier.padding(
                                    start = if (numbered) NUMBERED_ROW_DIVIDER_INSET else ROW_DIVIDER_INSET,
                                ),
                                thickness = 0.5.dp,
                                color = palette.divider,
                            )
                        }
                        }
                    }
                }
            }

            // Info album di bawah daftar lagu: tahun, jumlah lagu, total durasi.
            if (page.type == BrowseType.ALBUM && folder == null) {
                val albumSongs = (page.songs as? UiState.Success)?.data.orEmpty()
                if (albumSongs.isNotEmpty()) {
                    item(key = "album-info") {
                        AlbumInfoFooter(
                            releaseDate = albumInfo?.releaseDateText(),
                            year = RELEASE_YEAR.find(page.subtitle)?.value,
                            copyright = albumInfo?.copyright,
                            songs = albumSongs,
                            palette = palette,
                        )
                    }
                }
            }

            // Tracks YouTube offers to round the playlist out, never folded
            // into the list above — see [DetailPage.suggestedSongs].
            if (suggested.isNotEmpty()) {
                item(key = "suggested-heading") {
                    SectionHeading(stringResource(R.string.suggested), palette)
                }
                val isSuggestedCurrent = playingRowMatcher(suggested, currentSong)
                itemsIndexed(
                    suggested,
                    key = { _, song -> "suggested-${song.videoId}" },
                ) { index, song ->
                    SuggestedSongRow(
                        song = song,
                        palette = palette,
                        onClick = { onSongClick(suggested, index) },
                        onLongPress = { onSongLongPress(song) },
                        onAdd = { onAddSuggested(song) },
                        downloadedTint = downloadedTint,
                        isCurrent = isSuggestedCurrent(song),
                        isPlaying = isSuggestedCurrent(song) && isPlaying,
                    )
                    if (index < suggested.lastIndex) {
                        HorizontalDivider(
                            modifier = Modifier.padding(start = ROW_DIVIDER_INSET),
                            thickness = 0.5.dp,
                            color = palette.divider,
                        )
                    }
                }
            }

            // Albums / Singles & EPs carousels (artist pages).
            items(page.sections, contentType = { "section-shelf" }) { shelf ->
                val canShowAll = shelf.items.size > ARTIST_ROW_MAX_ITEMS
                val displayItems = remember(shelf.items) {
                    if (canShowAll) shelf.items.take(ARTIST_ROW_MAX_ITEMS) else shelf.items
                }
                Column(Modifier.padding(top = 22.dp)) {
                    SectionHeading(
                        title = shelf.title,
                        palette = palette,
                        onShowAll = if (canShowAll) { { onActiveShelfChange(shelf) } } else null,
                        horizontalPadding = ARTIST_CONTENT_GUTTER,
                    )
                    val carouselState = rememberLazyListState()
                    LazyRow(
                        state = carouselState,
                        contentPadding = PaddingValues(horizontal = ARTIST_CONTENT_GUTTER),
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        flingBehavior = rememberSnapFlingBehavior(lazyListState = carouselState),
                    ) {
                        items(displayItems) { item ->
                            SectionCard(
                                item = item,
                                palette = palette,
                                onClick = { onSectionItemClick(item) },
                                onLongPress = onSectionItemLongPress?.let { { it(item) } },
                                modifier = Modifier.width(if (item.isVideo) VIDEO_CARD_WIDTH else SHELF_CARD_WIDTH),
                            )
                        }
                    }
                }
            }
            if (isArtist) aboutItem()
        }

                if (folder != null && folderState != null && folderState.selecting) {
                    LocalFolderSelectionBar(
                        folder = folder,
                        state = folderState,
                        songs = songs,
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(top = topBarContentPadding(), start = PAGE_GUTTER, end = PAGE_GUTTER),
                    )
                }

    }
} else {
    ArtistShelfGridPage(
        shelf = targetShelf,
        palette = palette,
        onItemClick = onSectionItemClick,
        onItemLongPress = onSectionItemLongPress,
        contentPadding = contentPadding,
    )
}
}
}

/**
 * An album or playlist, laid out as Apple Music does: the sleeve framed in the
 * middle of the page, the title, credit and meta under it, then Shuffle, a
 * labelled Play pill and Add to Library across one row.
 *
 * One flat page colour behind all of it — the sleeve is a picture *on* the
 * page, not the page's top edge, so there is nothing to blend into and no
 * layer drawn behind the list. Search, sort and the overflow menu are the top
 * bar's, not this header's.
 */
@Composable
private fun ReleaseHeader(
    page: DetailPage,
    palette: ArtworkPalette,
    sleeveSize: Dp,
    canvas: CanvasArtwork?,
    songs: List<Song>,
    onPlay: () -> Unit,
    onShuffle: () -> Unit,
    onArtistClick: ((String, String) -> Unit)?,
    onToggleLibrary: (() -> Unit)?,
    /** The Cached songs folder's: saves every track on it to Downloads. Null hides it. */
    onDownloadAll: (() -> Unit)? = null,
    /** Tombol "Add songs" di bawah baris aksi; juga tampil saat playlist masih kosong. */
    onAddSongs: (() -> Unit)? = null,
    /** Tinggi hero full-bleed, hanya dipakai halaman Liked Music. */
    heroHeight: Dp = sleeveSize * 1.2f,
    /** Genre yang Apple catat untuk album ini; tampil menggantikan label "Album". */
    albumGenre: String? = null,
    /** How far the list is pulled past its top right now, in px; the hero zooms by it. */
    pull: () -> Float = { 0f },
) {
    val (credit, listMeta) = page.headerLines(songs.size, songs.playtime())
    // Apple Music: "Jazz • 2023". Jumlah lagu dan durasi pindah ke kaki halaman.
    val meta = if (page.type == BrowseType.ALBUM && !page.browseId.startsWith("local:")) {
        page.albumMeta(albumGenre)
    } else {
        listMeta
    }
    // Liked Music dan Most Played tidak memakai sleeve berbingkai: seninya full-bleed.
    val albumHero = page.isAlbumHero()
    val mixHero = page.isMixHero()
    val fullBleed = page.isLikedMusic() || page.covers.isNotEmpty() || page.isFolderCover() || albumHero || mixHero
    // Every row on a release carries the same credit — see [pageCredit] — so
    // the first one speaks for the whole page, the same source the rows'
    // own long-press "Open artist" already reads from.
    val artist = songs.firstOrNull()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            // Clear of the floating bar, which the sleeve no longer runs under.
            // Hero full-bleed justru naik sampai di bawah bar.
            .padding(top = if (fullBleed) 0.dp else topBarContentPadding(), bottom = 18.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (page.covers.isNotEmpty()) {
            MostPlayedHero(
                page = page,
                palette = palette,
                height = heroHeight * 1.3f,
                modifier = Modifier.zoomOnPull(pull),
            )
        } else if (page.isFolderCover()) {
            FolderCoverHero(
                page = page,
                palette = palette,
                songs = songs,
                height = heroHeight,
                modifier = Modifier.zoomOnPull(pull),
            )
        } else if (albumHero) {
            AlbumHero(
                page = page,
                // Kaki hero, jahitan, dan wash target terbang semuanya membaca
                // palette.background di dalam AlbumHero; diganti wash di sini
                // supaya ketiganya sama dengan warna halaman di atas.
                palette = palette.copy(background = palette.wash),
                canvas = canvas,
                height = heroHeight * ALBUM_HERO_HEIGHT_SCALE,
                modifier = Modifier.zoomOnPull(pull),
            )
        } else if (mixHero) {
            MixHero(
                page = page,
                // Sama seperti AlbumHero: kaki hero dan wash target terbang memakai warna halaman.
                palette = palette.copy(background = palette.wash),
                height = heroHeight * ALBUM_HERO_HEIGHT_SCALE,
                modifier = Modifier.zoomOnPull(pull),
            )
        } else if (fullBleed) {
            LikedMusicHero(
                page = page,
                palette = palette,
                songs = songs,
                height = heroHeight,
                modifier = Modifier.zoomOnPull(pull),
            )
        } else Box(
            Modifier
                .coverFlightTarget(page.browseId, SLEEVE_CORNER)
                .size(sleeveSize)
                .shadow(SLEEVE_SHADOW, SLEEVE_SHAPE)
                .background(palette.elevated)
                .thumbnailBorder(SLEEVE_SHAPE),
        ) {
            // A cover that isn't there, or wouldn't load — a device folder, a
            // playlist with nothing on it yet, files with no picture — gets a
            // placeholder sleeve rather than an empty square. Not shown while a
            // real one is still on its way, so a loading cover doesn't flash
            // a glyph first.
            // Halaman folder Downloads, Local Music dan Cached songs tidak punya
            // sampul sendiri, jadi memakai sampul lagu pertama di halamannya.
            val coverUrl = page.thumbnailUrl ?: if (
                page.browseId == "local:downloads" ||
                page.browseId == "local:all" ||
                page.browseId == CACHE_FOLDER_BROWSE_ID
            ) {
                songs.firstOrNull { it.thumbnailUrl != null }?.thumbnailUrl
            } else {
                null
            }
            var artFailed by remember(coverUrl) { mutableStateOf(false) }
            if (coverUrl == null || artFailed) {
                CoverPlaceholder(
                    icon = page.placeholderIcon(),
                    base = palette.elevated,
                    tint = palette.onBackgroundVariant,
                    modifier = Modifier.matchParentSize(),
                )
            }
            // The card's own picture, already in the cache, underneath: the cover
            // is on screen from the first frame of the flight, instead of an
            // empty sleeve flying over until the larger one has downloaded.
            AsyncImage(
                model = coverUrl.artworkAt(CARD_ART_PX),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.matchParentSize(),
            )
            AsyncImage(
                model = coverUrl.artworkAt(HEADER_ART_PX),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                onState = { artFailed = it is AsyncImagePainter.State.Error },
                modifier = Modifier.matchParentSize(),
            )
            // Halaman Most Played: sleeve-nya kolase statis yang sama dengan kartu,
            // jadi cover yang terbang mendarat di atas dirinya sendiri.
            if (page.covers.isNotEmpty()) {
                MostPlayedArt(
                    covers = page.covers,
                    motion = true,
                    modifier = Modifier.matchParentSize().clip(SLEEVE_SHAPE),
                )
            }
            // Halaman Made for You Mix: sleeve-nya blob cairan yang sama dengan kartu.
            page.mix?.let { mixTitle ->
                MixFluidArt(
                    title = mixTitle,
                    modifier = Modifier.matchParentSize().clip(SLEEVE_SHAPE),
                )
                // Teks/logo sampul ikut muncul pelan setelah cover mendarat.
                MixCoverOverlay(
                    title = mixTitle,
                    subtitle = "",
                    modifier = Modifier
                        .matchParentSize()
                        .clip(SLEEVE_SHAPE)
                        .flightReveal(delayMs = 0, rise = 0.dp, afterFlight = true),
                )
            }
            // Animated cover art, inside the same frame. Always running:
            // unlike the player's sleeve there is no transport here to follow,
            // and the page is only up while it's being read.
            canvas?.let { clip ->
                val frameGrab = remember(clip) { CanvasFrameGrab() }
                RegisterCoverFlightGrab(page.browseId, frameGrab)
                // The clip eases in once it is actually drawing, instead of
                // popping over the still the moment it is composed.
                var clipShown by remember(clip) { mutableStateOf(false) }
                val clipAlpha by animateFloatAsState(
                    targetValue = if (clipShown) 1f else 0f,
                    animationSpec = tween(400),
                    label = "artistClipFade",
                )
                CanvasArtworkPlayer(
                    canvas = clip,
                    isPlaying = true,
                    modifier = Modifier
                        .matchParentSize()
                        .graphicsLayer { alpha = clipAlpha },
                    onRenderedChanged = {
                        clipShown = it
                        if (it) coverFlightClipRendered(page.browseId)
                    },
                    frameGrab = frameGrab,
                )
            }
        }

        Spacer(Modifier.height(20.dp))
        Text(
            text = page.title,
            style = MaterialTheme.typography.headlineMedium,
            color = palette.onBackground,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.flightReveal(150, afterFlight = page.covers.isNotEmpty()).padding(horizontal = HEADER_GUTTER),
        )
        if (credit.isNotBlank()) {
            Spacer(Modifier.height(2.dp))
            Text(
                text = credit,
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.Normal,
                    fontSize = 19.sp,
                ),
                color = palette.onBackgroundVariant,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .flightReveal(200, afterFlight = page.covers.isNotEmpty())
                    .padding(horizontal = HEADER_GUTTER)
                    .let { m ->
                        val id = artist?.artistId
                        if (id == null || onArtistClick == null) {
                            m
                        } else {
                            m.clip(RoundedCornerShape(6.dp))
                                .clickable { onArtistClick(id, artist.artist) }
                        }
                    },
            )
        }
        if (meta.isNotBlank()) {
            Spacer(Modifier.height(4.dp))
            Text(
                text = meta,
                style = MaterialTheme.typography.labelMedium,
                color = palette.onBackgroundVariant,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.flightReveal(240, afterFlight = page.covers.isNotEmpty()).padding(horizontal = HEADER_GUTTER),
            )
        }

        if (songs.isNotEmpty()) {
            // Only where YouTube said the release can be saved and the caller
            // is willing to take the write — see [onToggleLibrary].
            val library = page.library?.takeIf { onToggleLibrary != null }
            Spacer(Modifier.height(18.dp))
            Row(
                modifier = Modifier
                    .flightReveal(290, afterFlight = page.covers.isNotEmpty())
                    .widthIn(max = RELEASE_ACTIONS_MAX_WIDTH)
                    .fillMaxWidth()
                    .padding(horizontal = HEADER_GUTTER),
                horizontalArrangement = Arrangement.spacedBy(RELEASE_ACTION_GAP),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (page.browseId.startsWith("local:") || library == null) {
                    // Already on this device — a folder, a download, a local
                    // playlist — so there is no library to add it to, and a
                    // greyed-out plus would only promise one. Apple Music's
                    // own pair for that case: Play and Shuffle, side by side.
                    ActionPill(
                        icon = AudifyIcons.Play,
                        label = stringResource(R.string.play),
                        onClick = onPlay,
                        filled = true,
                        size = RELEASE_SIDE_BUTTON,
                        modifier = Modifier.weight(1f),
                    )
                    ActionPill(
                        icon = AudifyIcons.Shuffle,
                        label = stringResource(R.string.shuffle),
                        onClick = onShuffle,
                        filled = false,
                        size = RELEASE_SIDE_BUTTON,
                        modifier = Modifier.weight(1f),
                    )
                    // The song cache's copies, saved to Downloads in one go —
                    // from disk, so it works offline. See [LocalFolder.onDownload].
                    onDownloadAll?.let {
                        CircleIconButton(
                            icon = Icons.Rounded.Download,
                            contentDescription = stringResource(R.string.download_all),
                            onClick = it,
                            size = RELEASE_SIDE_BUTTON,
                        )
                    }
                    return@Row
                }
                CircleIconButton(
                    icon = AudifyIcons.Shuffle,
                    contentDescription = stringResource(R.string.shuffle),
                    onClick = onShuffle,
                    haptic = Haptic.Resume,
                    size = RELEASE_SIDE_BUTTON,
                )
                ActionPill(
                    icon = AudifyIcons.Play,
                    label = stringResource(R.string.play),
                    onClick = onPlay,
                    filled = true,
                    size = RELEASE_SIDE_BUTTON,
                    modifier = Modifier.weight(1f),
                )
                if (library != null) {
                    CircleIconButton(
                        // A tick, not a filled-in plus: the pair reads as
                        // "not yet / done", which is what the state is.
                        icon = if (library.saved) AudifyIcons.Check else AudifyIcons.Plus,
                        contentDescription = if (library.saved) {
                            stringResource(R.string.remove_from_library)
                        } else {
                            stringResource(R.string.add_to_library)
                        },
                        onClick = { onToggleLibrary?.invoke() },
                        haptic = if (library.saved) Haptic.ToggleOff else Haptic.ToggleOn,
                        size = RELEASE_SIDE_BUTTON,
                    )
                }
            }
        }

        // Di luar blok "songs.isNotEmpty()" di atas: playlist yang baru dibuat
        // masih kosong, dan baris Shuffle/Play belum ada, tapi tombol ini harus.
        if (onAddSongs != null) {
            Spacer(Modifier.height(if (songs.isEmpty()) 18.dp else 10.dp))
            ActionPill(
                icon = AudifyIcons.Plus,
                label = stringResource(R.string.playlist_add_songs),
                onClick = onAddSongs,
                filled = songs.isEmpty(),
                size = RELEASE_SIDE_BUTTON,
                modifier = Modifier
                    .flightReveal(290, afterFlight = page.covers.isNotEmpty())
                    .widthIn(max = RELEASE_ACTIONS_MAX_WIDTH)
                    .fillMaxWidth()
                    .padding(horizontal = HEADER_GUTTER),
            )
        }
    }
}

/**
 * The filter box, shown under the header while the top bar's search button is on.
 *
 * Live rather than submit-on-enter, and for the same reason the Local Music
 * one is (see `LocalSearchField`): the list it narrows is already in memory, so
 * there is nothing for a submit action to wait for.
 *
 * Glass rather than a filled field — it is one of the header's controls that
 * happens to be typed into, and it sits close enough to the circles that a
 * Material text field beside them would read as a different app's furniture.
 */
@Composable
private fun DetailSearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    onClose: () -> Unit,
    autoFocus: Boolean,
    onFocused: () -> Unit,
    palette: ArtworkPalette,
    type: BrowseType,
) {
    // Opened by a tap on a button, which is as clear a statement of intent as
    // the keyboard is going to get — so it comes up with the field rather than
    // making the tap land twice. Once only: see [autoFocus]'s owner.
    val focus = remember { FocusRequester() }
    LaunchedEffect(autoFocus) {
        if (autoFocus) {
            focus.requestFocus()
            onFocused()
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = HEADER_GUTTER)
            .padding(bottom = 10.dp)
            .height(46.dp)
            .clip(CircleShape)
            // The recipe the Play, Shuffle and Add songs pills above it wear:
            // a light fill, the glass rim the floating bars have, and the
            // bright top edge. Rim only, since over the page's flat tint a
            // blurred fill would look the same.
            .background(Color.White.copy(alpha = LIGHT_FILL_ALPHA), CircleShape)
            .liquidGlassRim(CircleShape)
            .border(1.dp, Color.White.copy(alpha = TOP_RELEASE_EDGE_ALPHA), CircleShape)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = AudifyIcons.Search,
            contentDescription = null,
            tint = palette.onBackgroundVariant,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(10.dp))
        Box(Modifier.weight(1f)) {
            if (query.isEmpty()) {
                Text(
                    text = stringResource(R.string.search_this_list),
                    style = MaterialTheme.typography.bodyLarge,
                    color = palette.onBackgroundVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = palette.onBackground),
                cursorBrush = SolidColor(palette.accent),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focus),
            )
        }
        Box(
            modifier = Modifier
                .size(30.dp)
                .clip(CircleShape)
                // Clearing and closing are the same gesture: an empty filter
                // showing an unfiltered list is a row of furniture with nothing
                // left to do.
                .clickable { if (query.isEmpty()) onClose() else onQueryChange("") },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Rounded.Close,
                contentDescription = stringResource(
                    if (query.isEmpty()) R.string.close_search else R.string.clear_search,
                ),
                tint = palette.onBackgroundVariant,
                modifier = Modifier.size(17.dp),
            )
        }
    }
}

internal fun List<Song>.sortedForDetail(sort: SongSort): List<Song> = when (sort) {
    SongSort.DEFAULT -> this
    SongSort.TITLE_ASC -> sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.title })
    SongSort.TITLE_DESC -> sortedWith(compareByDescending(String.CASE_INSENSITIVE_ORDER) { it.title })
    // A catalogue row carries no added date, so its place in the running
    // order stands in for one: a playlist is appended to as songs are added,
    // and reversed that puts the most recent addition on top. Rows that do
    // carry a MediaStore timestamp — device tracks, downloads — are dated
    // properly, with the position order left to break the ties.
    SongSort.DATE_ADDED_ASC -> withIndex()
        .sortedWith(
            compareBy<IndexedValue<Song>> { (_, song) ->
                song.localDateAddedSeconds ?: Long.MAX_VALUE
            }.thenBy { (position, _) -> position },
        )
        .map { it.value }
    SongSort.DATE_ADDED_DESC -> withIndex()
        .sortedWith(
            compareByDescending<IndexedValue<Song>> { (_, song) ->
                song.localDateAddedSeconds ?: Long.MIN_VALUE
            }.thenByDescending { (position, _) -> position },
        )
        .map { it.value }
}

/**
 * The rows a typed query leaves standing, each still carrying its place in the
 * full list — see the track numbers on an album, which are the release's own
 * and not positions in whatever the filter left.
 */
internal fun List<Song>.matching(query: String): List<IndexedValue<Song>> {
    val all = withIndex().toList()
    if (query.isBlank()) return all
    return all.filter { (_, song) -> song.matchesSearch(query) }
}

/**
 * An artist: their name across the foot of the photo [PageBackground] is
 * drawing behind this.
 *
 * The photograph itself is not here — it is pinned behind the list while this
 * scrolls over it. What this item holds in its place is a spacer of exactly
 * the picture's height, which is what keeps the two in step: the list
 * reserves the room, the background fills it.
 */
@Composable
private fun ArtistHeader(
    page: DetailPage,
    palette: ArtworkPalette,
    artHeight: Dp,
    appleArt: AppleArtistArt?,
    signature: ArtistSignature?,
) {
    Box(Modifier.fillMaxWidth()) {
        Spacer(Modifier.fillMaxWidth().height(artHeight + HEADER_DROP - ARTIST_HEADER_LIFT))
        val logoUrl = appleArt?.logoUrl
        // Secondary provider: drawn only when Apple has no title logo.
        val signatureUrl = signature?.imageUrl.takeIf { logoUrl == null }
        val imageUrl = logoUrl ?: signatureUrl
        // Signature vectors are dark ink on transparency — over a dark artist
        // photo they vanish, so they are tinted white like the web UI's
        // inverted plate. Scanned photos (jpeg/png) keep their own pixels:
        // tinting an opaque light scan would flatten it into a white blob.
        val tint = if (logoUrl == null && signature?.format == "svg") {
            ColorFilter.tint(Color.White)
        } else {
            null
        }
        // Whether the logo has actually been drawn. Until it has — a first fetch
        // of an Apple logo is a few hundred KB the CDN may still be resizing — the
        // name stands in for it, so the header is never left with nothing in it.
        var logoShown by remember(imageUrl) { mutableStateOf(false) }
        if (imageUrl != null) {
            val context = LocalPlatformContext.current
            var attempt by remember(imageUrl) { mutableIntStateOf(0) }
            var failed by remember(imageUrl) { mutableStateOf(false) }
            val isSignatureSvg = logoUrl == null && signature?.format == "svg"
            // Scanned paper (jpeg/png) goes through the ink transformation
            // (white paper to transparency, strokes to white); vectors are
            // tinted at draw time instead.
            val isSignatureScan = logoUrl == null && signature != null && !isSignatureSvg
            val aspect = (if (logoUrl != null) appleArt?.logoAspect else signature?.aspect)
                ?.coerceIn(0.4f, 6f) ?: 1f
            // Remembered, and at the size the URL already names: nothing about the
            // load waits on layout to decide how big to decode, and a recomposition
            // can't hand Coil a new request that cancels the one in flight.
            // Vectors decode small (Charlie Puth's is 213px) and would upscale
            // ~3x into a blur, so SVGs render large; rasters keep ORIGINAL
            // since decoding past their pixels only spends memory.
            val request = remember(imageUrl, attempt) {
                ImageRequest.Builder(context).data(imageUrl).apply {
                    if (isSignatureSvg) {
                        size(SVG_DECODE_WIDTH, (SVG_DECODE_WIDTH / aspect).roundToInt())
                    } else {
                        size(coil3.size.Size.ORIGINAL)
                    }
                    if (isSignatureScan) transformations(SignatureInkTransformation)
                }.build()
            }
            // A dropped fetch gets two more tries before the name is left in place.
            LaunchedEffect(failed) {
                if (failed && attempt < LOGO_RETRIES) {
                    delay(LOGO_RETRY_DELAY_MS)
                    failed = false
                    attempt++
                }
            }
            // Apple's own title logo stands in for the name, laid over its photo.
            // Capped in height as well as width: a logo set on two or three
            // lines is nearly square, and by width alone it would swallow the
            // photograph it is meant to sit on. A signature is a panoramic ink
            // strip and reads best smaller than a title logo.
            val isSignature = logoUrl == null && signature != null
            val sizeFactor = if (isSignature) SIGNATURE_SCALE else 1f
            val maxLogoHeight = artHeight * ARTIST_LOGO_MAX_HEIGHT * sizeFactor
            Column(
                modifier = Modifier.align(Alignment.BottomCenter).enterReveal(500),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
            AsyncImage(
                model = request,
                contentDescription = page.title,
                contentScale = ContentScale.Fit,
                colorFilter = tint,
                onState = { state ->
                    when (state) {
                        is AsyncImagePainter.State.Success -> logoShown = true
                        is AsyncImagePainter.State.Error -> {
                            val cause = (state as? AsyncImagePainter.State.Error)
                                ?.result?.throwable?.let { "${it.javaClass.simpleName}: ${it.message}" }
                            Log.d("ArtistHeader", "image load failed: $imageUrl attempt $attempt cause=$cause")
                            failed = true
                        }
                        else -> Unit
                    }
                },
                modifier = Modifier
                    .padding(top = 14.dp, bottom = if (isSignature) 6.dp else 20.dp)
                    // Sized from the incoming width in layout rather than through a
                    // BoxWithConstraints, which would subcompose the image.
                    .layout { measurable, constraints ->
                        val widest = constraints.maxWidth * ARTIST_LOGO_WIDTH * sizeFactor
                        val height = minOf(widest / aspect, maxLogoHeight.toPx()).roundToInt()
                        val width = (height * aspect).roundToInt()
                        val placeable = measurable.measure(Constraints.fixed(width, height))
                        layout(width, height) { placeable.place(0, 0) }
                    },
            )
            if (isSignature && logoShown) {
                SignedByCaption(
                    name = page.title,
                    palette = palette,
                    modifier = Modifier.padding(horizontal = HEADER_GUTTER).padding(bottom = 20.dp),
                )
            }
            }
        }
        if (!logoShown) Text(
            text = page.title,
            style = MaterialTheme.typography.displayLarge,
            color = palette.onBackground,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .enterReveal(500)
                // Bottom is half the top inset — the stats pills (or, absent
                // those, the action row) sit closer under the name than the
                // name sits under the artwork.
                .padding(start = HEADER_GUTTER, end = HEADER_GUTTER, top = 14.dp, bottom = 7.dp),
        )
    }
}

/**
 * Everything on an artist page that is colour rather than words: the photo,
 * with its foot dissolved into the solid [ArtworkPalette.wash] the page is
 * painted in. A release page has no such layer — its sleeve is framed.
 *
 * A layer of its own so the list can be scrolled over it rather than carry it.
 * It follows the scroll instead of being scrolled: the list owns the gesture
 * and reserves the room, and the picture is offset to follow whatever the list
 * did with item zero. Read in a placement block, so a scroll moves it without
 * recomposing anything.
 *
 * The join used to be hidden by a live blur laid across it, re-run on every
 * frame of every scroll. A blurred copy of the sleeve does the same job here
 * drawn once: nothing about it changes as the page moves, so it is rasterised
 * on the first frame and only composited after that.
 */
@Composable
private fun PageBackground(
    page: DetailPage,
    palette: ArtworkPalette,
    canvas: CanvasArtwork?,
    artHeight: Dp,
    listState: LazyListState,
    heroUrl: String? = null,
    /** How far the list is pulled past its top right now, in px; the photo zooms by it. */
    pull: () -> Float = { 0f },
    modifier: Modifier = Modifier,
) {
    val reduceDynamicBlur by AppSettings.reduceDynamicBlur.collectAsStateWithLifecycle()
    // Not under a canvas: a still blurred over the foot of a moving clip would
    // freeze the bottom of the video into the wrong picture.
    val softenFoot = canvas == null && !reduceDynamicBlur &&
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val art = heroUrl ?: page.thumbnailUrl.artworkAt(HEADER_ART_PX)



    // The photo waits for the page colour instead of showing over a black page
    // that then turns purple a moment later. A read that never finishes gives
    // up after 1.2 s so the photo is never held back for good.
    var gaveUp by remember(art) { mutableStateOf(false) }
    LaunchedEffect(art) {
        delay(1_200)
        gaveUp = true
    }
    val reveal by animateFloatAsState(
        targetValue = if (palette.ready || gaveUp) 1f else 0f,
        animationSpec = tween(260),
        label = "artistHeroReveal",
    )

    Box(modifier.clipToBounds()) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(artHeight)
                .graphicsLayer { alpha = reveal }
                .zoomBackgroundOnPull(pull)
                .offset { IntOffset(0, listState.headerTop(artHeight.toPx()).roundToInt()) }
                // After the offset, so the flight lands on where the photo really is.
                .coverFlightTarget(page.browseId, 0.dp, palette.wash),
        ) {
            SwapArt(
                url = art,
                modifier = Modifier
                    .matchParentSize()
                    .background(palette.elevated),
            )

            // Above the still art but below the scrim, so the scrim that
            // settles the header into the page still sits over it. Always
            // running: unlike the player's sleeve there is no transport here
            // to follow, and the page is only up while it's being read.
            canvas?.let { clip ->
                val frameGrab = remember(clip) { CanvasFrameGrab() }
                RegisterCoverFlightGrab(page.browseId, frameGrab)
                // The clip eases in once it is actually drawing, instead of
                // popping over the still the moment it is composed.
                var clipShown by remember(clip) { mutableStateOf(false) }
                val clipAlpha by animateFloatAsState(
                    targetValue = if (clipShown) 1f else 0f,
                    animationSpec = tween(400),
                    label = "artistClipFade",
                )
                CanvasArtworkPlayer(
                    canvas = clip,
                    isPlaying = true,
                    modifier = Modifier
                        .matchParentSize()
                        .graphicsLayer { alpha = clipAlpha },
                    onRenderedChanged = {
                        clipShown = it
                        if (it) coverFlightClipRendered(page.browseId)
                    },
                    frameGrab = frameGrab,
                )
            }

            // The same sleeve, blurred and faded in over the lower half, so the
            // picture loses its detail before it loses its colour — a merge
            // rather than a fade to a flat tint. The same request as the sharp
            // copy, so it is a memory-cache hit and lands on the same frame.
            if (softenFoot) {
                SwapArt(
                    url = art,
                    modifier = Modifier
                        .matchParentSize()
                        // Offscreen so the mask cuts the blurred result rather
                        // than each draw beneath it.
                        .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                        .drawWithContent {
                            drawContent()
                            drawRect(SOFT_FOOT_MASK, blendMode = BlendMode.DstIn)
                        }
                        // Rectangle keeps the edges clamped to the picture's own
                        // colour rather than fading to transparent at the foot.
                        .blur(SOFT_FOOT_BLUR, BlurredEdgeTreatment.Rectangle),
                )
            }

            // Ends on the page colour at full strength, so there is no edge
            // left where the artwork stops. Eased rather than run straight: a
            // gradient that changes slope at a stop shows a line at that stop,
            // however close the colours either side of it are.
            Box(
                Modifier
                    .matchParentSize()
                    .background(
                        Brush.verticalGradient(
                            0.45f to Color.Transparent,
                            0.65f to palette.wash.copy(alpha = 0.30f),
                            0.82f to palette.wash.copy(alpha = 0.72f),
                            0.94f to palette.wash.copy(alpha = 0.95f),
                            1.00f to palette.wash,
                        ),
                    ),
            )
        }
    }
}

/**
 * Where the top of the artwork currently is.
 *
 * The list is the one being scrolled; the background only has to agree with it.
 * While the header is item zero and on screen, how far it has been scrolled off
 * the top is exactly the offset the picture behind it needs. Once it isn't,
 * there is nothing to agree with, and the picture parks two artwork-heights up
 * — far enough that no part of it comes back down.
 */
private fun LazyListState.headerTop(artHeightPx: Float): Float =
    if (firstVisibleItemIndex == 0) -firstVisibleItemScrollOffset.toFloat() else -artHeightPx * 2f

/** Where the blurred copy starts to show and where it has fully taken over. */
private val SOFT_FOOT_MASK = Brush.verticalGradient(
    0.55f to Color.Transparent,
    0.85f to Color.Black,
)

/**
 * Wide enough that no shapes survive where the blurred copy is at full
 * strength — a blur that leaves them reads as a blurred photograph, and a
 * blurred photograph next to a flat colour is still two surfaces.
 */
private val SOFT_FOOT_BLUR = 48.dp

/**
 * A photo that changes its picture without a cut. The new one is composed on
 * top, invisible, through the same AsyncImage the old one uses; it fades in
 * only once its pixels have actually arrived, then becomes the base. Loading
 * it any other way misses the image cache and fades in an empty frame.
 */
@Composable
private fun SwapArt(url: String?, modifier: Modifier = Modifier) {
    var base by remember { mutableStateOf(url) }
    var loaded by remember(url) { mutableStateOf(false) }
    val fade = remember { androidx.compose.animation.core.Animatable(0f) }
    LaunchedEffect(url, loaded) {
        if (url != base && loaded) {
            fade.animateTo(1f, tween(320))
            base = url
            fade.snapTo(0f)
        }
    }
    Box(modifier) {
        AsyncImage(
            model = base,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        if (url != base) {
            AsyncImage(
                model = url,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                onState = { if (it is AsyncImagePainter.State.Success) loaded = true },
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = fade.value },
            )
        }
    }
}

/**
 * Shuffle • Play • Star — the artist page's action row, as Apple Music lays
 * it out: a large Play circle with a small glass circle either side.
 *
 * The star is the page's existing subscribe toggle. Where the page offers none
 * (a guest) it stays, greyed out and untappable, so Play keeps the middle.
 */
@Composable
private fun ActionRow(
    onPlay: () -> Unit,
    onShuffle: () -> Unit,
    bottomSpace: Dp = 22.dp,
    /** The artist header's subscribe state, or null where it isn't offered. */
    subscription: SubscriptionState? = null,
    onToggleSubscription: (() -> Unit)? = null,
    /** Apple's fill for the Play circle; null leaves it white. */
    playColor: Color? = null,
) {
    Row(
        modifier = Modifier
            .enterReveal(540)
            .fillMaxWidth()
            .padding(horizontal = HEADER_GUTTER),
        horizontalArrangement = Arrangement.spacedBy(ARTIST_ACTION_GAP, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CircleIconButton(
            icon = AudifyIcons.Shuffle,
            contentDescription = stringResource(R.string.shuffle),
            onClick = onShuffle,
            haptic = Haptic.Resume,
            size = ARTIST_SIDE_BUTTON,
        )

        PlayCircle(
            onClick = onPlay,
            size = ARTIST_PLAY_BUTTON,
            iconScale = ARTIST_PLAY_ICON_SCALE,
            containerColor = playColor ?: Color.White,
        )

        if (subscription != null) {
            CircleIconButton(
                icon = if (subscription.subscribed) Icons.Rounded.Star else Icons.Rounded.StarBorder,
                contentDescription = stringResource(
                    if (subscription.subscribed) R.string.unsubscribe else R.string.subscribe,
                ),
                onClick = { onToggleSubscription?.invoke() },
                haptic = if (subscription.subscribed) Haptic.ToggleOff else Haptic.ToggleOn,
                size = ARTIST_SIDE_BUTTON,
                iconScale = 0.56f,
            )
        } else {
            CircleIconButton(
                icon = Icons.Rounded.StarBorder,
                contentDescription = stringResource(R.string.subscribe),
                onClick = {},
                size = ARTIST_SIDE_BUTTON,
                enabled = false,
                iconScale = 0.56f,
            )
        }
    }
    Spacer(Modifier.height(bottomSpace))
}

/**
 * A labelled pill in the release page's action row. [filled] is Play: white
 * with black content, so the primary action survives every palette, filling
 * whatever width the row leaves it. Otherwise it wears the light veil and glass
 * rim of the circles beside it — Shuffle, on a page with no Add to sit opposite.
 */
@Composable
private fun ActionPill(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    filled: Boolean,
    modifier: Modifier = Modifier,
    size: Dp = 50.dp,
) {
    // Resume rather than a flat tap: both start a queue, and the rising pair
    // says so.
    val haptics = rememberHaptics()
    val content = if (filled) Color.Black else MaterialTheme.colorScheme.onSurface
    Row(
        modifier = modifier
            .height(size)
            .clip(CircleShape)
            .then(
                if (filled) {
                    Modifier.background(Color.White)
                } else {
                    Modifier
                        .background(Color.White.copy(alpha = LIGHT_FILL_ALPHA), CircleShape)
                        .liquidGlassRim(CircleShape)
                        .border(1.dp, Color.White.copy(alpha = TOP_RELEASE_EDGE_ALPHA), CircleShape)
                },
            )
            .clickable {
                haptics.play(Haptic.Resume)
                onClick()
            }
            .padding(horizontal = 24.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = content,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.titleMedium,
            color = content,
            maxLines = 1,
        )
    }
}

/**
 * The artist page's Play: a filled circle with the triangle punched out of it,
 * so the page shows through, as Apple's does.
 */
@Composable
private fun PlayCircle(
    onClick: () -> Unit,
    size: Dp,
    iconScale: Float,
    containerColor: Color,
) {
    val haptics = rememberHaptics()
    val description = stringResource(R.string.play)
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .clickable {
                haptics.play(Haptic.Resume)
                onClick()
            }
            // Offscreen so the clear below cuts the circle drawn under it,
            // not whatever the page has behind.
            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
            .drawWithContent {
                drawCircle(containerColor)
                val box = this.size.minDimension * iconScale
                val unit = box / 24f
                val origin = Offset((this.size.width - box) / 2f, (this.size.height - box) / 2f)
                val triangle = androidx.compose.ui.graphics.Path().apply {
                    moveTo(origin.x + 6.8f * unit, origin.y + 4.8f * unit)
                    lineTo(origin.x + 19.2f * unit, origin.y + 12f * unit)
                    lineTo(origin.x + 6.8f * unit, origin.y + 19.2f * unit)
                    close()
                }
                drawPath(triangle, Color.Black, blendMode = BlendMode.Clear)
                // The same round-joined pen the icon is drawn with.
                drawPath(
                    triangle,
                    Color.Black,
                    style = androidx.compose.ui.graphics.drawscope.Stroke(
                        width = 2f * unit,
                        cap = androidx.compose.ui.graphics.StrokeCap.Round,
                        join = androidx.compose.ui.graphics.StrokeJoin.Round,
                    ),
                    blendMode = BlendMode.Clear,
                )
            }
            .semantics { contentDescription = description },
    )
}

/**
 * Small circular icon-only button — Shuffle and its partner either side of
 * Play, on both the release and the artist page. A light veil with a hairline
 * edge, Apple's look for these, rather than the dark glass.
 */
@Composable
private fun CircleIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    haptic: Haptic = Haptic.Tap,
    size: Dp = 50.dp,
    /**
     * False greys the button out and stops it taking taps, for an action the
     * page offers but this user can't take — a guest's Add or Subscribe.
     * Dimmed rather than dropped, so the row keeps its shape.
     */
    enabled: Boolean = true,
    /** Glyph size as a fraction of the button; the star needs more than shuffle. */
    iconScale: Float = 0.44f,
) {
    val haptics = rememberHaptics()
    Box(
        modifier = Modifier
            .size(size)
            .alpha(if (enabled) 1f else DISABLED_ACTION_ALPHA)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = LIGHT_FILL_ALPHA), CircleShape)
            // The glass rim the floating bars wear, with Liquid Glass on. Rim
            // only: over the page's flat tint a blurred fill would look the same.
            .liquidGlassRim(CircleShape)
            .border(1.dp, Color.White.copy(alpha = TOP_RELEASE_EDGE_ALPHA), CircleShape)
            .clickable(enabled = enabled) {
                haptics.play(haptic)
                onClick()
            },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.size(size * iconScale),
        )
    }
}

/** "1.2M subscribers" and "3.4M monthly listeners", off the artist header. */
@Composable
private fun ArtistStatsRow(
    subscriberCountText: String?,
    monthlyListenerCount: String?,
    palette: ArtworkPalette,
) {
    // Stacked and left-aligned with the text under it, since this now sits in
    // the About section rather than across the header.
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = ARTIST_CONTENT_GUTTER, end = ARTIST_CONTENT_GUTTER, bottom = 12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        horizontalAlignment = Alignment.Start,
    ) {
        // YouTube's own count text already reads "1.2M subscribers" in full,
        // so only the number is kept and the label re-said in the app's own
        // words — the one way to fit both stats on one line on a narrow
        // screen without either wrapping into two.
        subscriberCountText?.let {
            StatChip(
                icon = Icons.Rounded.Person,
                text = stringResource(R.string.subscribers, it.substringBefore(' ')),
                palette = palette,
            )
        }
        monthlyListenerCount?.let {
            StatChip(
                icon = Icons.Rounded.GraphicEq,
                text = stringResource(R.string.monthly_listeners, it.substringBefore(' ')),
                palette = palette,
            )
        }
    }
}

@Composable
private fun StatChip(icon: ImageVector, text: String, palette: ArtworkPalette) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .lightweightLiquidGlass(
                shape = CircleShape,
                fallbackColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.6f),
            )
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = palette.onBackgroundVariant,
            modifier = Modifier.size(15.dp),
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = palette.onBackgroundVariant,
        )
    }
}

/**
 * YouTube's own editorial note for a release or an artist, collapsed to a
 * few lines with a tap to read the rest — the same "About" block Apple
 * Music and YouTube Music itself show under the header.
 *
 * Whether there's anything to expand is only knowable once the text has
 * been laid out at the collapsed line count, so the "More" toggle is held
 * back until that measurement says the clipped text actually lost
 * something — otherwise a two-line bio would show a toggle with nothing
 * behind it to reveal.
 */
/** "Signed by **Name**" — the artist's name in bold, whatever order the language puts it in. */
@Composable
private fun SignedByCaption(
    name: String,
    palette: ArtworkPalette,
    modifier: Modifier = Modifier,
    textAlign: TextAlign = TextAlign.Center,
) {
    val signed = stringResource(R.string.signed_by, name)
    val nameAt = signed.indexOf(name)
    Text(
        text = buildAnnotatedString {
            append(signed)
            if (nameAt >= 0) {
                addStyle(SpanStyle(fontWeight = FontWeight.Bold), nameAt, nameAt + name.length)
            }
        },
        style = MaterialTheme.typography.bodyMedium,
        color = palette.onBackground.copy(alpha = 0.8f),
        textAlign = textAlign,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier,
    )
}

/**
 * The handwritten signature with its signed-by line, closing the About section of an artist whose
 * header already carries Apple's title logo.
 */
@Composable
private fun AboutSignature(
    signature: ArtistSignature,
    name: String,
    palette: ArtworkPalette,
    horizontalPadding: Dp,
) {
    val context = LocalPlatformContext.current
    val isSvg = signature.format == "svg"
    val aspect = signature.aspect.coerceIn(0.4f, 6f)
    val request = remember(signature.imageUrl) {
        ImageRequest.Builder(context).data(signature.imageUrl).apply {
            if (isSvg) {
                size(SVG_DECODE_WIDTH, (SVG_DECODE_WIDTH / aspect).roundToInt())
            } else {
                size(coil3.size.Size.ORIGINAL)
                transformations(SignatureInkTransformation)
            }
        }.build()
    }
    var loaded by remember(signature.imageUrl) { mutableStateOf(false) }
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = horizontalPadding),
        horizontalAlignment = Alignment.Start,
    ) {
        AsyncImage(
            model = request,
            contentDescription = name,
            contentScale = ContentScale.Fit,
            colorFilter = if (isSvg) ColorFilter.tint(Color.White) else null,
            onState = { if (it is AsyncImagePainter.State.Success) loaded = true },
            modifier = Modifier
                .padding(top = 20.dp, bottom = 6.dp)
                .height(ABOUT_LOGO_HEIGHT)
                .aspectRatio(aspect),
        )
        if (loaded) SignedByCaption(name = name, palette = palette, textAlign = TextAlign.Start)
    }
}

/**
 * Kaki halaman album: "March 3, 1988 · 5 songs · 18 min", lalu baris ℗ bila
 * Apple punya. Sebelum Apple menjawab, [year] dari subtitle yang dipakai.
 */
@Composable
private fun AlbumInfoFooter(
    releaseDate: String?,
    year: String?,
    copyright: String?,
    songs: List<Song>,
    palette: ArtworkPalette,
) {
    // Baris pertama tanggal rilis (atau tahun), baris kedua jumlah lagu dan durasi.
    val dateLine = releaseDate ?: year
    val countLine = listOfNotNull(
        pluralStringResource(R.plurals.song_count_plural, songs.size, songs.size),
        songs.playtime(),
    ).joinToString(" · ")
    Column(
        modifier = Modifier.padding(
            start = ABOUT_GUTTER,
            end = ABOUT_GUTTER,
            top = 16.dp,
            bottom = 12.dp,
        ),
    ) {
        if (dateLine != null) {
            Text(
                text = dateLine,
                style = MaterialTheme.typography.bodyMedium,
                color = palette.onBackgroundVariant,
            )
        }
        Text(
            text = countLine,
            style = MaterialTheme.typography.bodyMedium,
            color = palette.onBackgroundVariant,
        )
        if (copyright != null) {
            Text(
                text = copyright,
                style = MaterialTheme.typography.bodySmall,
                color = palette.onBackgroundVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

@Composable
private fun AboutSection(
    title: String,
    text: String?,
    palette: ArtworkPalette,
    horizontalPadding: Dp = ABOUT_GUTTER,
    /** Ala Apple Music: tanpa judul, dengan MORE di ujung baris kedua. */
    compact: Boolean = false,
    /** Drawn under the text. */
    stats: (@Composable () -> Unit)? = null,
    /** Drawn last, under the stats. */
    footer: (@Composable () -> Unit)? = null,
) {
    var expanded by remember(text) { mutableStateOf(false) }
    var clipped by remember(text) { mutableStateOf(false) }
    Column {
        if (!compact) Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = palette.onBackground,
            modifier = Modifier.padding(
                start = horizontalPadding,
                end = horizontalPadding,
                top = 2.dp,
                bottom = 6.dp,
            ),
        )
        if (text != null && compact) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .animateContentSize()
                    .padding(horizontal = horizontalPadding, vertical = 4.dp)
                    .let { m -> if (clipped || expanded) m.clickable { expanded = !expanded } else m },
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = text,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = palette.onBackgroundVariant,
                    maxLines = if (expanded) Int.MAX_VALUE else 2,
                    overflow = TextOverflow.Ellipsis,
                    onTextLayout = { result -> if (!expanded) clipped = result.hasVisualOverflow },
                    modifier = Modifier.weight(1f),
                )
                if (clipped && !expanded) {
                    Text(
                        text = stringResource(R.string.more).uppercase(),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = palette.onBackground,
                    )
                }
            }
        }
        if (text != null && !compact) Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium,
            color = palette.onBackgroundVariant,
            maxLines = if (expanded) Int.MAX_VALUE else 2,
            overflow = TextOverflow.Ellipsis,
            onTextLayout = { result -> if (!expanded) clipped = result.hasVisualOverflow },
            modifier = Modifier
                .fillMaxWidth()
                .animateContentSize()
                .padding(horizontal = horizontalPadding)
                .let { m -> if (clipped || expanded) m.clickable { expanded = !expanded } else m },
        )
        if (!compact && (clipped || expanded)) {
            Text(
                text = stringResource(if (expanded) R.string.less else R.string.more),
                style = MaterialTheme.typography.labelLarge,
                color = palette.accent,
                modifier = Modifier
                    .padding(horizontal = horizontalPadding, vertical = 4.dp)
                    .clickable { expanded = !expanded },
            )
        }
        if (stats != null) {
            Spacer(Modifier.height(if (text != null) 12.dp else 4.dp))
            stats()
        }
        footer?.invoke()
    }
}

@Composable
private fun SectionHeading(
    title: String,
    palette: ArtworkPalette,
    onShowAll: (() -> Unit)? = null,
    horizontalPadding: Dp = PAGE_GUTTER,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                start = horizontalPadding,
                end = horizontalPadding,
                top = 10.dp,
                bottom = 8.dp,
            ),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineMedium,
            color = palette.onBackground,
            modifier = Modifier.weight(1f, fill = false),
        )
        if (onShowAll != null) {
            Text(
                text = stringResource(R.string.show_all),
                style = MaterialTheme.typography.titleSmall,
                color = palette.accent,
                modifier = Modifier
                    .clickable(onClick = onShowAll)
                    .padding(start = 12.dp, top = 4.dp, bottom = 4.dp),
            )
        }
    }
}

/** Compact row used inside the artist song grid; no swipe, to keep the
 *  horizontal pager's gestures unambiguous. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CompactSongRow(
    song: Song,
    palette: ArtworkPalette,
    onClick: () -> Unit,
    onLongPress: () -> Unit,
    downloadedTint: Color? = null,
    isCurrent: Boolean = false,
    isPlaying: Boolean = false,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .longPressMenuClickable(onClick = onClick, onLongClick = onLongPress)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.trackArtSize(48.dp, song.isVideo)) {
            AsyncImage(
                model = song.artworkAt(ROW_ART_PX),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .matchParentSize()
                    .clip(RoundedCornerShape(7.dp))
                    .thumbnailBorder(RoundedCornerShape(7.dp))
                    .background(palette.elevated),
            )
            if (isCurrent && isPlaying) SearchPlayingBars(Modifier.align(Alignment.Center))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            ExplicitSongTitle(
                song = song,
                style = MaterialTheme.typography.titleMedium,
                color = if (isCurrent) palette.accent else palette.onBackground,
            )
            Text(
                text = song.artist,
                style = MaterialTheme.typography.bodyMedium,
                color = palette.onBackgroundVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (downloadedTint != null) {
            DownloadedBadge(song.videoId, downloadedTint)
        }
        RowMoreButton(onClick = onLongPress, tint = palette.onBackgroundVariant)
    }
}

/**
 * A row under "Suggested" — a track YouTube offers to round the playlist
 * out but that was never added. [onAdd] is the point of the row, so it gets
 * the trailing spot a track already on the playlist spends on "more"; the
 * long-press sheet is still one gesture away for anything else.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SuggestedSongRow(
    song: Song,
    palette: ArtworkPalette,
    onClick: () -> Unit,
    onLongPress: () -> Unit,
    onAdd: () -> Unit,
    downloadedTint: Color? = null,
    isCurrent: Boolean = false,
    isPlaying: Boolean = false,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .longPressMenuClickable(
                onClick = onClick,
                onLongClick = onLongPress,
                liftPadding = PaddingValues(horizontal = PAGE_GUTTER),
            )
            .padding(horizontal = PAGE_GUTTER, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.trackArtSize(52.dp, song.isVideo)) {
            AsyncImage(
                model = song.artworkAt(ROW_ART_PX),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .matchParentSize()
                    .clip(RoundedCornerShape(8.dp))
                    .thumbnailBorder(RoundedCornerShape(8.dp))
                    .background(palette.elevated),
            )
            if (isCurrent && isPlaying) SearchPlayingBars(Modifier.align(Alignment.Center))
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            ExplicitSongTitle(
                song = song,
                style = MaterialTheme.typography.titleMedium,
                color = if (isCurrent) palette.accent else palette.onBackground,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = song.artist,
                style = MaterialTheme.typography.bodyMedium,
                color = palette.onBackgroundVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (downloadedTint != null) {
            DownloadedBadge(song.videoId, downloadedTint)
        }
        Spacer(Modifier.width(8.dp))
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(palette.accent.copy(alpha = 0.16f))
                .clickable(onClick = onAdd),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Rounded.Add,
                contentDescription = stringResource(R.string.add_to_playlist),
                tint = palette.accent,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

/**
 * The newest release on an artist's shelves — an album, or a single or EP that
 * came out after it. Playlists, videos and related artists share these shelves
 * and are skipped: only an `MPRE…` browse id is a release.
 *
 * Newest by the year in the card's subtitle, which is all a shelf item says
 * about when. Ties and releases with no year keep YouTube's own order, which
 * lists albums first.
 */
private fun List<HomeShelf>.topRelease(): ShelfItem? =
    asSequence()
        .flatMap { it.items.asSequence() }
        .filter { it.browseId?.startsWith("MPRE") == true }
        .withIndex()
        .maxWithOrNull(
            compareBy<IndexedValue<ShelfItem>> { it.value.releaseYear() ?: 0 }
                .thenByDescending { it.index },
        )?.value

private val RELEASE_YEAR = Regex("""\b(19|20)\d{2}\b""")

private fun ShelfItem.releaseYear(): Int? =
    RELEASE_YEAR.find(subtitle)?.value?.toIntOrNull()

/** "Recent Single" and "Recent EP" where the card says so, otherwise "Recent Album". */
@androidx.annotation.StringRes
private fun ShelfItem.recentLabel(): Int {
    val kind = subtitle.lowercase()
    return when {
        kind.contains("single") -> R.string.recent_single
        Regex("""\bep\b""").containsMatchIn(kind) -> R.string.recent_ep
        else -> R.string.recent_album
    }
}

/**
 * The artist page's top-release card: sleeve, what it is and when, and its
 * title, in a squircle panel the width of the page.
 *
 * Proportioned off Apple Music's: a 24pt continuous corner, a 96pt sleeve 12pt
 * in from the edge, the text 12pt after it, and a 32pt add button 19pt in
 * from the end. The panel and its button are a light veil with a light
 * hairline, as Apple's containers read slightly white against the page.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TopReleaseCard(
    item: ShelfItem,
    palette: ArtworkPalette,
    onClick: () -> Unit,
    onLongPress: (() -> Unit)?,
    /** Whether the album is in the library; null while that is still being read. */
    saved: Boolean?,
    /** Null hides the button. */
    onToggleSaved: (() -> Unit)?,
) {
    val shape = remember { RoundedCornerShape(TOP_RELEASE_CORNER).continuous() }
    // Concentric with the panel: its corner less the inset the sleeve sits at.
    val coverShape = remember { RoundedCornerShape(TOP_RELEASE_CORNER - TOP_RELEASE_INSET).continuous() }
    val haptics = rememberHaptics()
    val hasButton = onToggleSaved != null && saved != null
    val coverToken = remember { Any() }
    val openWithCover = rememberCoverFlightTap(item.browseId, coverToken, onClick)
    Row(
        modifier = Modifier
            .enterReveal(600)
            .fillMaxWidth()
            .padding(horizontal = ARTIST_CONTENT_GUTTER)
            .padding(bottom = 22.dp)
            // A light veil rather than the dark glass: Apple's containers read
            // slightly white against the page, and a dark panel reads as a hole.
            .clip(shape)
            .background(Color.White.copy(alpha = LIGHT_FILL_ALPHA), shape)
            .border(1.dp, Color.White.copy(alpha = TOP_RELEASE_EDGE_ALPHA), shape)
            .clip(shape)
            .longPressMenuClickable(onClick = openWithCover, onLongClick = onLongPress)
            .padding(
                start = TOP_RELEASE_INSET,
                top = TOP_RELEASE_INSET,
                bottom = TOP_RELEASE_INSET,
                end = if (hasButton) 19.dp else TOP_RELEASE_INSET,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AsyncImage(
            model = item.thumbnailUrl.artworkAt(CARD_ART_PX),
            contentDescription = null,
            modifier = Modifier
                .coverFlightSource(
                    coverToken,
                    item.thumbnailUrl.artworkAt(CARD_ART_PX),
                    TOP_RELEASE_CORNER - TOP_RELEASE_INSET,
                )
                .size(96.dp)
                .clip(coverShape)
                .border(1.dp, Color.White.copy(alpha = TOP_RELEASE_COVER_EDGE_ALPHA), coverShape)
                .background(palette.elevated),
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            // The lines either side of the title in the player's secondary
            // treatment, the artist line's: the page's own colour, partly
            // transparent.
            val secondary = palette.onBackground.copy(alpha = TOP_RELEASE_SECONDARY_ALPHA)
            Text(
                text = item.subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = secondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            // Faded out at the end of its last line, as the player's title is,
            // and set a little tighter than the scale for when it takes two.
            FadingText(
                text = item.title,
                style = MaterialTheme.typography.titleLarge.copy(
                    lineHeight = TOP_RELEASE_TITLE_LINE_HEIGHT,
                    lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None),
                ),
                color = palette.onBackground,
                maxLines = 2,
            )
            Text(
                text = stringResource(item.recentLabel()),
                style = MaterialTheme.typography.bodyMedium,
                color = secondary,
                maxLines = 1,
            )
        }
        if (onToggleSaved != null && saved != null) {
            Spacer(Modifier.width(10.dp))
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = LIGHT_FILL_ALPHA), CircleShape)
                    .border(1.dp, Color.White.copy(alpha = TOP_RELEASE_EDGE_ALPHA), CircleShape)
                    .clickable {
                        haptics.play(if (saved) Haptic.ToggleOff else Haptic.ToggleOn)
                        onToggleSaved()
                    },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = if (saved) Icons.Rounded.Check else Icons.Rounded.Add,
                    contentDescription = stringResource(
                        if (saved) R.string.remove_from_library else R.string.add_to_library,
                    ),
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(17.dp),
                )
            }
        }
    }
}

/** The top-release panel's continuous corner, and the inset its sleeve sits at. */
private val TOP_RELEASE_CORNER = 24.dp
private val TOP_RELEASE_INSET = 12.dp

/** The panel's secondary lines, at the player's artist line's strength. */
private const val TOP_RELEASE_SECONDARY_ALPHA = 0.55f

/** The panel's title on two lines: a little under titleLarge's natural 24sp. */
private val TOP_RELEASE_TITLE_LINE_HEIGHT = 22.sp

/** The white veil under Apple-style containers and their buttons. */
private const val LIGHT_FILL_ALPHA = 0.07f
/** A side button this user can't use — a guest's Add or Subscribe. */
private const val DISABLED_ACTION_ALPHA = 0.38f

/** Brighter than the default 0.15 hairlines, as on Apple Music's own card. */
private const val TOP_RELEASE_EDGE_ALPHA = 0.10f
private const val TOP_RELEASE_COVER_EDGE_ALPHA = 0.215f

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SectionCard(
    item: ShelfItem,
    palette: ArtworkPalette,
    onClick: () -> Unit,
    modifier: Modifier = Modifier.width(SHELF_CARD_WIDTH),
    onLongPress: (() -> Unit)? = null,
) {
    // An artist's Videos shelf: widescreen, as on the web player.
    if (item.isVideo) {
        VideoShelfCard(
            item = item,
            onClick = onClick,
            onLongPress = onLongPress,
            modifier = modifier,
            titleColor = palette.onBackground,
            subtitleColor = palette.onBackgroundVariant,
            placeholderColor = palette.elevated,
        )
        return
    }
    // Sama seperti kartu di Home: ketuk memulai penerbangan cover ke halaman
    // album/single, lalu membuka halamannya.
    val coverToken = remember { Any() }
    val openWithCover = rememberCoverFlightTap(item.browseId, coverToken, onClick)
    Column(
        modifier = modifier
            .longPressMenuClickable(onClick = openWithCover, onLongClick = onLongPress),
    ) {
        AsyncImage(
            model = item.thumbnailUrl.artworkAt(CARD_ART_PX),
            contentDescription = null,
            modifier = Modifier
                .coverFlightSource(coverToken, item.thumbnailUrl.artworkAt(CARD_ART_PX), 10.dp)
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(10.dp))
                .thumbnailBorder(RoundedCornerShape(10.dp))
                .background(palette.elevated),
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = item.title,
            style = MaterialTheme.typography.titleMedium,
            color = palette.onBackground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = item.subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = palette.onBackgroundVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun ArtistShelfGridPage(
    shelf: HomeShelf,
    palette: ArtworkPalette,
    onItemClick: (ShelfItem) -> Unit,
    onItemLongPress: ((ShelfItem) -> Unit)?,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
) {
    val gridState = rememberLazyGridState()
    BoxWithConstraints(modifier.fillMaxSize().background(palette.background)) {
        val grid = libraryGrid(maxWidth - PAGE_GUTTER * 2)
        LazyVerticalGrid(
            columns = GridCells.Fixed(grid.columns),
            state = gridState,
            contentPadding = PaddingValues(
                top = topBarContentPadding(),
                bottom = contentPadding.calculateBottomPadding() + 16.dp,
            ),
            horizontalArrangement = Arrangement.spacedBy(LIBRARY_GRID_SPACING),
            verticalArrangement = Arrangement.spacedBy(20.dp),
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = PAGE_GUTTER),
        ) {
            items(shelf.items, key = { it.browseId ?: it.videoId ?: it.title }) { item ->
                SectionCard(
                    item = item,
                    palette = palette,
                    onClick = { onItemClick(item) },
                    onLongPress = onItemLongPress?.let { { it(item) } },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

/**
 * Splits the one subtitle a browse row hands over — "Album • Travis Scott •
 * 2023", or sometimes just "Travis Scott" — into the credit line and the
 * metadata line the header shows separately.
 *
 * Everything is optional, because every caller supplies a different amount of
 * it: the player knows an album's artist but not its year, search knows both,
 * and a home card frequently knows neither.
 */
@Composable
private fun DetailPage.headerLines(trackCount: Int, playtime: String? = null): Pair<String, String> {
    val parts = subtitle.split("•", "·").map { it.trim() }.filter { it.isNotEmpty() }
    val year = parts.lastOrNull { it.length == 4 && it.all(Char::isDigit) }
    val kind = parts.firstOrNull { it.lowercase(Locale.ROOT) in KIND_WORDS }
    val credit = parts.filter { it != year && it != kind }.joinToString(", ")
    // Title case, "Playlist · 2022 · 10 Tracks · 38 min" — except the
    // playtime, whose "min" and "1h 25m" are units, not words.
    // An artist grouped out of a device folder is a plain track list, not an
    // artist page, but it is still billed as the artist it is.
    // A device directory plays like a playlist, but it is billed as a folder.
    val localKind = when {
        LocalGroups.isArtist(browseId) -> stringResource(R.string.artist)
        LocalGroups.isDirectory(browseId) -> stringResource(R.string.folder)
        else -> null
    }
    val meta = listOfNotNull(
        (kind ?: localKind ?: type.localizedLabel())?.capitalizeWords(),
        year,
        trackCount.takeIf { it > 0 }?.let {
            pluralStringResource(R.plurals.track_count_plural, it, it).capitalizeWords()
        },
        playtime,
    ).joinToString(" · ")
    return credit to meta
}

/** Baris info header album ala Apple Music: genre (atau jenis rilisan) • tahun. */
@Composable
private fun DetailPage.albumMeta(genre: String?): String {
    val parts = subtitle.split("•", "·").map { it.trim() }.filter { it.isNotEmpty() }
    val year = parts.lastOrNull { it.length == 4 && it.all(Char::isDigit) }
    val kind = parts.firstOrNull { it.lowercase(Locale.ROOT) in KIND_WORDS }
    return listOfNotNull(
        genre ?: (kind ?: type.localizedLabel())?.capitalizeWords(),
        year,
    ).joinToString(" • ")
}

/**
 * Header Most Played: mosaic yang sama dengan kartu Home, full-bleed selebar
 * layar sampai di bawah bar (tanpa bingkai dan sudut membulat). Kakinya larut
 * ke warna halaman dan judul naik menimpanya, seperti [LikedMusicHero].
 */
@Composable
private fun MostPlayedHero(
    page: DetailPage,
    palette: ArtworkPalette,
    height: Dp,
    modifier: Modifier = Modifier,
) {
    // Mosaic direkam sekali; salinan blur di kaki memakai rekaman yang sama.
    val shared = rememberGraphicsLayer()
    val reduceDynamicBlur by AppSettings.reduceDynamicBlur.collectAsStateWithLifecycle()
    val blurFoot = !reduceDynamicBlur && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val footLayer = rememberGraphicsLayer()
    val foot = remember { FootSnapshot() }
    var onScreen by remember { mutableStateOf(true) }
    Box(
        modifier
            .onGloballyPositioned { c ->
                val v = c.isAttached && !c.boundsInWindow().isEmpty
                if (v != onScreen) onScreen = v
            }
            // Paling luar: tinggi yang dilaporkan lebih pendek, sehingga
            // judul di bawahnya tergambar di atas kaki hero.
            .layout { measurable, constraints ->
                val placeable = measurable.measure(constraints)
                val overlap = LIKED_HERO_OVERLAP.roundToPx().coerceAtMost(placeable.height)
                layout(placeable.width, placeable.height - overlap) { placeable.place(0, 0) }
            }
            .fillMaxWidth()
            .height(height)
            // Dipotong di sisi dan bawah, tapi tidak di atas: tarikan ke bawah
            // (dibatasi LIKED_MAX_PULL) menggeser hero turun dan bagian atasnya
            // tetap terisi seni, jadi tidak ada ruang kosong.
            .drawWithContent {
                val extra = LIKED_OVERSCROLL.toPx()
                clipRect(left = 0f, top = -extra, right = size.width, bottom = size.height) {
                    this@drawWithContent.drawContent()
                }
                // Tarikan menggeser hero ke posisi piksel pecahan; tepi bawah yang
                // dipotong tepat di batas itu menyisakan satu baris setengah
                // transparan (garis glitch). Strip warna halaman menutup jahitannya.
                val seam = 2.dp.toPx()
                drawRect(
                    color = palette.background,
                    topLeft = Offset(0f, size.height - seam),
                    size = androidx.compose.ui.geometry.Size(size.width, seam * 2f),
                )
            }
            .coverFlightTarget(page.browseId, 0.dp, palette.background),
    ) {
        // Perpanjangan di atas tepi hero untuk tarikan ke bawah: warna awal
        // gradien mosaic, jadi tidak pernah ada ruang kosong.
        Box(
            Modifier
                .matchParentSize()
                .drawBehind {
                    val extra = LIKED_OVERSCROLL.toPx()
                    drawRect(
                        color = RECAP_SURFACE,
                        topLeft = Offset(0f, -extra),
                        size = androidx.compose.ui.geometry.Size(size.width, extra + 1f),
                    )
                },
        )
        // Mosaic digambar dalam kotak persegi seperti di kartu (sisi terpanjang
        // hero), di tengah, lalu dipotong di tepi hero. Susunan tile-nya sama
        // persis dengan cover yang terbang, hanya lebih besar.
        Box(
            Modifier.matchParentSize().layout { measurable, constraints ->
                val w = constraints.maxWidth
                val h = constraints.maxHeight
                val side = maxOf(w, h)
                val placeable = measurable.measure(Constraints.fixed(side, side))
                layout(w, h) { placeable.place((w - side) / 2, (h - side) / 2) }
            },
        ) {
            MostPlayedArt(
                covers = page.covers,
                motion = !HeroMotion.frozen && onScreen,
                modifier = Modifier.fillMaxSize().drawWithContent {
                    shared.record(
                        size = IntSize(
                            size.width.roundToInt().coerceAtLeast(1),
                            size.height.roundToInt().coerceAtLeast(1),
                        ),
                    ) { this@drawWithContent.drawContent() }
                    drawLayer(shared)
                    if (blurFoot) {
                        val stamp = SystemClock.uptimeMillis()
                        if (stamp - foot.at >= FOOT_REFRESH_MS) {
                            foot.at = stamp
                            footLayer.record(
                                size = IntSize(
                                    size.width.roundToInt().coerceAtLeast(1),
                                    size.height.roundToInt().coerceAtLeast(1),
                                ),
                            ) { drawLayer(shared) }
                        }
                    }
                },
            )
        }
        // Salinan mosaic yang di-blur, dimunculkan di kaki lewat mask, seperti
        // blur cover #1 di kartu: kaki hero masuk dulu ke warna cover sebenarnya,
        // baru larut ke warna halaman.
        if (blurFoot) {
            Box(
                Modifier
                    .matchParentSize()
                    // Offscreen agar mask memotong hasil blur, bukan tiap gambar di bawahnya.
                    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                    .drawWithContent {
                        drawContent()
                        drawRect(
                            brush = Brush.verticalGradient(
                                0.00f to Color.Transparent,
                                0.50f to Color.Transparent,
                                0.66f to Color.Black.copy(alpha = 0.40f),
                                0.82f to Color.Black.copy(alpha = 0.85f),
                                1.00f to Color.Black,
                            ),
                            blendMode = BlendMode.DstIn,
                        )
                    }
                    .blur(LIKED_FOOT_BLUR, BlurredEdgeTreatment.Rectangle),
            ) {
                Box(
                    Modifier.matchParentSize().layout { measurable, constraints ->
                        val w = constraints.maxWidth
                        val h = constraints.maxHeight
                        val side = maxOf(w, h)
                        val placeable = measurable.measure(Constraints.fixed(side, side))
                        layout(w, h) { placeable.place((w - side) / 2, (h - side) / 2) }
                    },
                ) {
                    Box(Modifier.fillMaxSize().drawBehind { drawLayer(footLayer) })
                }
            }
        }
        // Berakhir di warna halaman penuh, jadi tidak ada tepi tempat seni berhenti.
        val clear = palette.background.copy(alpha = 0f)
        Box(
            Modifier
                .matchParentSize()
                .background(
                    Brush.verticalGradient(
                        // Banyak titik di sepanjang kurva halus (ease), dekat dengan
                        // wash cover terbang (CoverFlight) supaya pendaratan tetap mulus.
                        0.00f to clear,
                        0.45f to clear,
                        0.55f to palette.background.copy(alpha = 0.08f),
                        0.65f to palette.background.copy(alpha = 0.30f),
                        0.73f to palette.background.copy(alpha = 0.52f),
                        0.82f to palette.background.copy(alpha = 0.72f),
                        0.88f to palette.background.copy(alpha = 0.84f),
                        0.94f to palette.background.copy(alpha = 0.95f),
                        1.00f to palette.background,
                    ),
                ),
        )
        // Sedikit gelap di atas supaya ikon bar kaca tetap terbaca.
        Box(
            Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.28f)
                .background(
                    Brush.verticalGradient(
                        0f to Color.Black.copy(alpha = 0.22f),
                        1f to Color.Transparent,
                    ),
                ),
        )
    }
}

/** Downloads, Local Music dan Cached songs: folder perangkat yang memakai cover lagu pertama. */
private fun DetailPage.isFolderCover(): Boolean =
    browseId == "local:downloads" || browseId == "local:all" || browseId == CACHE_FOLDER_BROWSE_ID

/**
 * Header halaman folder perangkat: cover lagu pertama full-bleed sampai di bawah
 * bar, kakinya larut ke warna halaman. Judul menimpa kaki hero, seperti [LikedMusicHero].
 */
@Composable
private fun FolderCoverHero(
    page: DetailPage,
    palette: ArtworkPalette,
    songs: List<Song>,
    height: Dp,
    modifier: Modifier = Modifier,
) {
    val coverUrl = page.thumbnailUrl ?: songs.firstOrNull { it.thumbnailUrl != null }?.thumbnailUrl
    var artFailed by remember(coverUrl) { mutableStateOf(false) }

    Box(
        modifier
            .layout { measurable, constraints ->
                val placeable = measurable.measure(constraints)
                val overlap = LIKED_HERO_OVERLAP.roundToPx().coerceAtMost(placeable.height)
                layout(placeable.width, placeable.height - overlap) { placeable.place(0, 0) }
            }
            .fillMaxWidth()
            .height(height)
            .drawWithContent {
                val extra = LIKED_OVERSCROLL.toPx()
                clipRect(left = 0f, top = -extra, right = size.width, bottom = size.height) {
                    this@drawWithContent.drawContent()
                }
                val seam = 2.dp.toPx()
                drawRect(
                    color = palette.background,
                    topLeft = Offset(0f, size.height - seam),
                    size = androidx.compose.ui.geometry.Size(size.width, seam * 2f),
                )
            }
            .coverFlightTarget(page.browseId, 0.dp, palette.background),
    ) {
        // Seni lebih tinggi daripada hero: kelebihannya menjulur ke atas.
        Box(
            Modifier.layout { measurable, constraints ->
                val extra = LIKED_OVERSCROLL.roundToPx()
                val placeable = measurable.measure(
                    Constraints.fixed(constraints.maxWidth, constraints.maxHeight + extra),
                )
                layout(constraints.maxWidth, constraints.maxHeight) { placeable.place(0, -extra) }
            },
        ) {
            Box(Modifier.matchParentSize().background(palette.elevated))
            if (coverUrl == null || artFailed) {
                CoverPlaceholder(
                    icon = page.placeholderIcon(),
                    base = palette.elevated,
                    tint = palette.onBackgroundVariant,
                    modifier = Modifier.matchParentSize(),
                )
            }
            AsyncImage(
                model = coverUrl.artworkAt(CARD_ART_PX),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.matchParentSize(),
            )
            AsyncImage(
                model = coverUrl.artworkAt(HEADER_ART_PX),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                onState = { artFailed = it is AsyncImagePainter.State.Error },
                modifier = Modifier.matchParentSize(),
            )
        }

        // Berakhir di warna halaman penuh, jadi tidak ada tepi tempat seni berhenti.
        val page0 = palette.background.copy(alpha = 0f)
        Box(
            Modifier
                .matchParentSize()
                .background(
                    Brush.verticalGradient(
                        0.00f to page0,
                        0.50f to page0,
                        0.62f to palette.background.copy(alpha = 0.25f),
                        0.74f to palette.background.copy(alpha = 0.60f),
                        0.86f to palette.background.copy(alpha = 0.88f),
                        0.94f to palette.background.copy(alpha = 0.97f),
                        1.00f to palette.background,
                    ),
                ),
        )

        // Sedikit gelap di atas supaya ikon bar kaca tetap terbaca.
        Box(
            Modifier
                .fillMaxWidth()
                .height(height * 0.28f)
                .background(
                    Brush.verticalGradient(
                        0f to Color.Black.copy(alpha = 0.22f),
                        1f to Color.Transparent,
                    ),
                ),
        )
    }
}

/** Album: cover full-bleed sampai di bawah bar, seperti halaman folder perangkat. */
private fun DetailPage.isAlbumHero(): Boolean =
    type == BrowseType.ALBUM && mix == null && covers.isEmpty() &&
        !isFolderCover() && !isLikedMusic()

/** Hero album sedikit lebih tinggi dari hero folder, supaya cover persegi tidak banyak terpotong. */
private const val ALBUM_HERO_HEIGHT_SCALE = 1.13f

/**
 * Header album: cover full-bleed sampai di bawah bar, kakinya larut ke warna
 * halaman. Kerangkanya sama dengan [FolderCoverHero] (kotak target terbang yang
 * sama, sudut 0, wash warna halaman), jadi cover terbang dari kartu mendarat di
 * kotak ini dan pulang dari sini lagi. Klip cover animasi, kalau ada, berjalan
 * di kotak yang persis sama dengan target terbang, bukan di seni yang menjulur
 * ke atas, supaya frame yang ditahan selama terbang tidak bergeser.
 */
@Composable
private fun AlbumHero(
    page: DetailPage,
    palette: ArtworkPalette,
    canvas: CanvasArtwork?,
    height: Dp,
    modifier: Modifier = Modifier,
) {
    val coverUrl = page.thumbnailUrl
    var artFailed by remember(coverUrl) { mutableStateOf(false) }

    Box(
        modifier
            .layout { measurable, constraints ->
                val placeable = measurable.measure(constraints)
                val overlap = LIKED_HERO_OVERLAP.roundToPx().coerceAtMost(placeable.height)
                layout(placeable.width, placeable.height - overlap) { placeable.place(0, 0) }
            }
            .fillMaxWidth()
            .height(height)
            .drawWithContent {
                val extra = LIKED_OVERSCROLL.toPx()
                clipRect(left = 0f, top = -extra, right = size.width, bottom = size.height) {
                    this@drawWithContent.drawContent()
                }
                val seam = 2.dp.toPx()
                drawRect(
                    color = palette.background,
                    topLeft = Offset(0f, size.height - seam),
                    size = androidx.compose.ui.geometry.Size(size.width, seam * 2f),
                )
            }
            .coverFlightTarget(page.browseId, 0.dp, palette.background),
    ) {
        // Seni lebih tinggi daripada hero: kelebihannya menjulur ke atas.
        Box(
            Modifier.layout { measurable, constraints ->
                val extra = LIKED_OVERSCROLL.roundToPx()
                val placeable = measurable.measure(
                    Constraints.fixed(constraints.maxWidth, constraints.maxHeight + extra),
                )
                layout(constraints.maxWidth, constraints.maxHeight) { placeable.place(0, -extra) }
            },
        ) {
            Box(Modifier.matchParentSize().background(palette.elevated))
            if (coverUrl == null || artFailed) {
                CoverPlaceholder(
                    icon = page.placeholderIcon(),
                    base = palette.elevated,
                    tint = palette.onBackgroundVariant,
                    modifier = Modifier.matchParentSize(),
                )
            }
            AsyncImage(
                model = coverUrl.artworkAt(CARD_ART_PX),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.matchParentSize(),
            )
            AsyncImage(
                model = coverUrl.artworkAt(HEADER_ART_PX),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                onState = { artFailed = it is AsyncImagePainter.State.Error },
                modifier = Modifier.matchParentSize(),
            )
        }

        // Cover animasi di atas gambar diam dan di bawah gradien kaki, di kotak
        // yang sama dengan target terbang. Selalu berjalan: halaman ini hanya
        // hidup selagi dibaca.
        canvas?.let { clip ->
            val frameGrab = remember(clip) { CanvasFrameGrab() }
            RegisterCoverFlightGrab(page.browseId, frameGrab)
            var clipShown by remember(clip) { mutableStateOf(false) }
            val clipAlpha by animateFloatAsState(
                targetValue = if (clipShown) 1f else 0f,
                animationSpec = tween(400),
                label = "albumHeroClipFade",
            )
            CanvasArtworkPlayer(
                canvas = clip,
                isPlaying = true,
                modifier = Modifier
                    .matchParentSize()
                    .graphicsLayer { alpha = clipAlpha },
                onRenderedChanged = {
                    clipShown = it
                    if (it) coverFlightClipRendered(page.browseId)
                },
                frameGrab = frameGrab,
            )
        }

        // Berakhir di warna halaman penuh, jadi tidak ada tepi tempat seni berhenti.
        // Mulai agak lebih bawah daripada hero folder: sampul album lebih banyak
        // yang perlu terbaca.
        val page0 = palette.background.copy(alpha = 0f)
        Box(
            Modifier
                .matchParentSize()
                .background(
                    Brush.verticalGradient(
                        0.00f to page0,
                        0.58f to page0,
                        0.68f to palette.background.copy(alpha = 0.25f),
                        0.78f to palette.background.copy(alpha = 0.60f),
                        0.88f to palette.background.copy(alpha = 0.88f),
                        0.95f to palette.background.copy(alpha = 0.97f),
                        1.00f to palette.background,
                    ),
                ),
        )

        // Sedikit gelap di atas supaya ikon bar kaca tetap terbaca.
        Box(
            Modifier
                .fillMaxWidth()
                .height(height * 0.28f)
                .background(
                    Brush.verticalGradient(
                        0f to Color.Black.copy(alpha = 0.22f),
                        1f to Color.Transparent,
                    ),
                ),
        )
    }
}

/** Made for You Mix: cairan warna mix full-bleed sampai di bawah bar, seperti [AlbumHero]. */
private fun DetailPage.isMixHero(): Boolean = mix != null && covers.isEmpty()

/**
 * Header Made for You Mix: kerangka dan target terbang yang sama dengan
 * [AlbumHero] (sudut 0, wash warna halaman), jadi cover kartu mendarat di kotak
 * ini dan pulang dari sini lagi. Teks sampul muncul setelah cover mendarat dan
 * ditata di area yang terlihat, di bawah bar dan di atas judul halaman.
 */
@Composable
private fun MixHero(
    page: DetailPage,
    palette: ArtworkPalette,
    height: Dp,
    modifier: Modifier = Modifier,
) {
    val mixTitle = page.mix ?: return
    Box(
        modifier
            .layout { measurable, constraints ->
                val placeable = measurable.measure(constraints)
                val overlap = LIKED_HERO_OVERLAP.roundToPx().coerceAtMost(placeable.height)
                layout(placeable.width, placeable.height - overlap) { placeable.place(0, 0) }
            }
            .fillMaxWidth()
            .height(height)
            .drawWithContent {
                val extra = LIKED_OVERSCROLL.toPx()
                clipRect(left = 0f, top = -extra, right = size.width, bottom = size.height) {
                    this@drawWithContent.drawContent()
                }
                val seam = 2.dp.toPx()
                drawRect(
                    color = palette.background,
                    topLeft = Offset(0f, size.height - seam),
                    size = androidx.compose.ui.geometry.Size(size.width, seam * 2f),
                )
            }
            .coverFlightTarget(page.browseId, 0.dp, palette.background),
    ) {
        // Cairan lebih tinggi daripada hero: kelebihannya menjulur ke atas.
        Box(
            Modifier.layout { measurable, constraints ->
                val extra = LIKED_OVERSCROLL.roundToPx()
                val placeable = measurable.measure(
                    Constraints.fixed(constraints.maxWidth, constraints.maxHeight + extra),
                )
                layout(constraints.maxWidth, constraints.maxHeight) { placeable.place(0, -extra) }
            },
        ) {
            MixFluidArt(title = mixTitle, modifier = Modifier.matchParentSize())
        }

        val page0 = palette.background.copy(alpha = 0f)
        Box(
            Modifier
                .matchParentSize()
                .background(
                    Brush.verticalGradient(
                        0.00f to page0,
                        0.58f to page0,
                        0.68f to palette.background.copy(alpha = 0.25f),
                        0.78f to palette.background.copy(alpha = 0.60f),
                        0.88f to palette.background.copy(alpha = 0.88f),
                        0.95f to palette.background.copy(alpha = 0.97f),
                        1.00f to palette.background,
                    ),
                ),
        )

        // Sedikit gelap di atas supaya ikon bar kaca tetap terbaca.
        Box(
            Modifier
                .fillMaxWidth()
                .height(height * 0.28f)
                .background(
                    Brush.verticalGradient(
                        0f to Color.Black.copy(alpha = 0.22f),
                        1f to Color.Transparent,
                    ),
                ),
        )
    }
}

/** Liked Music di YouTube Music (VLLM) atau Liked milik perangkat. */
private fun DetailPage.isLikedMusic(): Boolean =
    browseId == YtMusicRepository.LIKED_MUSIC || OfflinePlaylists.isLikedPage(browseId)

/** Seberapa jauh blok judul naik menimpa kaki hero. */
private val LIKED_HERO_OVERLAP = 88.dp
private const val LIKED_MAX_COVERS = 9

/** Cover pilihan disimpan selama ini, lalu diundi ulang dari lagu yang ada. */
private const val LIKED_COVER_TTL_MS = 24L * 60 * 60 * 1000

/** Naikkan angka ini untuk membuang pilihan cover lama yang tersimpan di perangkat. */
private const val LIKED_COVER_CACHE_VERSION = 3

/** Salinan ter-blur di kaki hero direkam ulang tiap sekian ms, bukan tiap frame. */
private const val FOOT_REFRESH_MS = 300L

private class FootSnapshot {
    var at = 0L
}
private val LIKED_FOOT_BLUR = 36.dp

private val LIKED_COLORS = listOf(Color(0xFF7B5CF0), Color(0xFFB45FDB), Color(0xFFFF5FA8))
/**
 * Most Played: warna latar bawaan (royal-amethyst), dipakai selama warna dari
 * cover belum terbaca. Setelah itu latar mengikuti [rememberMostPlayedTint].
 */
private val MOST_PLAYED_COLORS = listOf(Color(0xFF1B2FB0), Color(0xFF6A33CC), Color(0xFF1A6FD8))

/**
 * Seberapa jauh seni hero menjulur ke atas, melewati tepi atasnya. Nol: tarikan
 * ke bawah kini memperbesar hero (lihat [zoomOnPull]) sehingga tidak ada lubang
 * yang perlu ditutup. Seni yang lebih tinggi dari kotak hero justru di-crop
 * lebih besar daripada cover terbang yang mendarat di kotak itu, dan terlihat
 * sebagai loncatan zoom saat masuk halaman.
 */
private val LIKED_OVERSCROLL = 0.dp

/**
 * Batas tarikan ke bawah halaman dengan hero full-bleed. Tarikan itu tidak
 * menyeret seni hero turun, melainkan memperbesarnya: lihat [zoomOnPull].
 */
private val LIKED_MAX_PULL = 140.dp

/**
 * Seperti [zoomOnPull], tetapi untuk seni yang digambar di belakang daftar
 * (foto artis): lapisan ini tidak ikut bergeser saat daftar ditarik, jadi tidak
 * perlu digeser balik. Cukup diskalakan dari tepi atasnya sejauh tarikan, sehingga
 * kakinya tetap bertemu dengan konten daftar yang turun.
 */
private fun Modifier.zoomBackgroundOnPull(pull: () -> Float): Modifier = graphicsLayer {
    val p = pull()
    if (p > 0f && size.height > 0f) {
        val scale = 1f + p / size.height
        transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.5f, 0f)
        scaleX = scale
        scaleY = scale
    }
}

/**
 * Menarik daftar melewati puncaknya memperbesar seni hero seperti Apple Music,
 * bukan menyeretnya turun. Daftar tetap bergeser sejauh tarikan, jadi hero
 * digeser balik ke puncak layar dan diskalakan dari tepi atasnya sehingga
 * kakinya tetap menempel pada konten di bawahnya.
 */
private fun Modifier.zoomOnPull(pull: () -> Float): Modifier = graphicsLayer {
    val p = pull()
    if (p > 0f && size.height > 0f) {
        val scale = 1f + p / size.height
        transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.5f, 0f)
        scaleX = scale
        scaleY = scale
        translationY = -p
    }
}

/**
 * Header Liked Music: full-bleed sampai di bawah bar, isinya mosaik abstrak
 * dari beberapa cover lagu di playlist, di atas latar cairan yang mengalir
 * pelan (berhenti saat player terbuka). Bagian bawahnya larut ke warna halaman.
 * Judul dan meta di bawahnya naik menimpa kaki hero, lihat [LIKED_HERO_OVERLAP].
 */
@Composable
private fun LikedMusicHero(
    page: DetailPage,
    palette: ArtworkPalette,
    songs: List<Song>,
    height: Dp,
    modifier: Modifier = Modifier,
) {
    // Beberapa cover saja, diundi sekali lalu dipakai ulang selama 24 jam.
    val covers = rememberLikedCovers(page.browseId, songs)

    Box(
        modifier
            // Paling luar: tinggi yang dilaporkan lebih pendek, sehingga
            // judul di bawahnya tergambar di atas kaki hero.
            .layout { measurable, constraints ->
                val placeable = measurable.measure(constraints)
                val overlap = LIKED_HERO_OVERLAP.roundToPx().coerceAtMost(placeable.height)
                layout(placeable.width, placeable.height - overlap) { placeable.place(0, 0) }
            }
            .fillMaxWidth()
            .height(height)
            // Dipotong di sisi dan bawah, tapi tidak di atas: tarikan ke bawah
            // menggeser hero turun, dan bagian atasnya harus tetap terisi seni.
            .drawWithContent {
                val extra = LIKED_OVERSCROLL.toPx()
                clipRect(left = 0f, top = -extra, right = size.width, bottom = size.height) {
                    this@drawWithContent.drawContent()
                }
                // Jahitan piksel pecahan di tepi bawah saat ditarik: lihat MostPlayedHero.
                val seam = 2.dp.toPx()
                drawRect(
                    color = palette.background,
                    topLeft = Offset(0f, size.height - seam),
                    size = androidx.compose.ui.geometry.Size(size.width, seam * 2f),
                )
            }
            .coverFlightTarget(page.browseId, 0.dp, palette.background),
    ) {
        // Seni sengaja lebih tinggi daripada hero: kelebihannya menjulur ke atas.
        Box(
            Modifier.layout { measurable, constraints ->
                val extra = LIKED_OVERSCROLL.roundToPx()
                val placeable = measurable.measure(
                    Constraints.fixed(constraints.maxWidth, constraints.maxHeight + extra),
                )
                layout(constraints.maxWidth, constraints.maxHeight) { placeable.place(0, -extra) }
            },
        ) {
            LikedArt(
                covers = covers,
                topInset = LIKED_OVERSCROLL,
                modifier = Modifier.matchParentSize(),
            )
        }

        // Berakhir di warna halaman penuh, jadi tidak ada tepi tempat seni berhenti.
        val page0 = palette.background.copy(alpha = 0f)
        Box(
            Modifier
                .matchParentSize()
                .background(
                    Brush.verticalGradient(
                        0.00f to page0,
                        0.50f to page0,
                        0.62f to palette.background.copy(alpha = 0.25f),
                        0.74f to palette.background.copy(alpha = 0.60f),
                        0.86f to palette.background.copy(alpha = 0.88f),
                        0.94f to palette.background.copy(alpha = 0.97f),
                        1.00f to palette.background,
                    ),
                ),
        )

        // Sedikit gelap di atas supaya ikon bar kaca tetap terbaca.
        Box(
            Modifier
                .fillMaxWidth()
                .height(height * 0.28f)
                .background(
                    Brush.verticalGradient(
                        0f to Color.Black.copy(alpha = 0.22f),
                        1f to Color.Transparent,
                    ),
                ),
        )
    }
}

/**
 * Isi hero: latar cairan [LikedFluidBackground] yang warnanya mengikuti cover, dan
 * [AnimatedAlbumCollage] (cover utama + cover dekoratif mengambang dalam satu Canvas),
 * dengan satu fade-in saat halaman dibuka.
 */
@Composable
private fun LikedArt(
    covers: List<String>,
    // Bagian seni yang menjulur di atas tepi hero; icon digeser turun setengahnya
    // supaya tetap di tengah area yang terlihat.
    topInset: Dp = 0.dp,
    modifier: Modifier = Modifier,
) {
    // Mosaic yang sudah tampil di kartu lalu terbang tidak mengulang fade-in.
    val settled = LocalSettledMosaic.current
    var shown by remember { mutableStateOf(settled) }
    LaunchedEffect(Unit) { shown = true }
    val enter by animateFloatAsState(
        targetValue = if (shown) 1f else 0f,
        animationSpec = tween(durationMillis = 500, easing = FastOutSlowInEasing),
        label = "likedArtEnter",
    )

    BoxWithConstraints(modifier) {
        val width = maxWidth

        // Latar cairan mengikuti warna dominan cover (cache + crossfade warna).
        LikedFluidBackground(covers = covers, modifier = Modifier.matchParentSize())

        if (covers.isEmpty()) {
            // Belum ada cover (daftar belum datang, atau lagu tanpa gambar).
            Icon(
                imageVector = Icons.Rounded.Favorite,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.85f),
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(width * 0.34f)
                    .graphicsLayer { translationY = topInset.toPx() / 2f },
            )
            return@BoxWithConstraints
        }

        // Satu Canvas, satu clock: cover utama + cover dekoratif mengambang.
        AnimatedAlbumCollage(
            covers = covers,
            topInset = topInset,
            enter = { enter },
            modifier = Modifier.matchParentSize(),
        )
    }
}

/**
 * Latar Liked: shader fluid yang sama, tapi warnanya dari warna dominan cover
 * (di-cache di [MostPlayedTints]) dan beralih halus lewat animateColorAsState.
 * Recomposition akibat transisi warna terisolasi di composable ini.
 */
@Composable
private fun LikedFluidBackground(covers: List<String>, modifier: Modifier = Modifier) {
    val tint = rememberMostPlayedTint(covers, LIKED_COLORS)
    val fade = tween<Color>(900)
    val top by androidx.compose.animation.animateColorAsState(tint[0], fade, label = "likedTop")
    val mid by androidx.compose.animation.animateColorAsState(tint[1], fade, label = "likedMid")
    val bottom by androidx.compose.animation.animateColorAsState(tint[2], fade, label = "likedBottom")
    // Gradasi statis dari warna cover: tanpa shader, tanpa clock, tanpa redraw per frame.
    Box(
        modifier = modifier.drawBehind {
            drawRect(Brush.verticalGradient(listOf(top, mid, bottom)))
        },
    )
}

/**
 * Diisi MainActivity: true selagi player ditarik atau terbuka di atas halaman.
 * Hero Most Played berhenti beranimasi selama itu, supaya mosaic, mesh, dan blur
 * kaki tidak digambar ulang tiap frame berebut GPU dengan canvas player.
 */
object HeroMotion {
    var frozen by mutableStateOf(false)
}

/**
 * Cover Most Played: komposisi berlapis, satu cover utama besar dengan dua cover
 * pendukung yang lebih kecil dan sedikit miring di belakangnya, di atas permukaan
 * gelap #171719 dengan satu cahaya merah tipis. Cover asli tidak diberi filter.
 * Tanpa jam global dan tanpa animasi terus-menerus; pergantian cover memakai crossfade.
 * Hanya tiga gambar dan satu latar yang digambar sekali.
 *
 * Satu composable ini dipakai kartu Home, cover terbang, sleeve, dan hero
 * full-bleed, jadi semuanya ikut ringan dan animasi terbang (coverFlightSource /
 * coverFlightTarget) tidak tersentuh. Isi diposisikan relatif terhadap sisi
 * persegi terkecil dan dipusatkan, jadi aman saat hero memotong sisi kiri-kanan.
 * [motion] dipertahankan demi kompatibilitas pemanggil.
 */
@Composable
fun MostPlayedArt(covers: List<String>, motion: Boolean, modifier: Modifier = Modifier) {
    BoxWithConstraints(
        modifier.drawBehind {
            drawRect(RECAP_SURFACE)
            // Satu cahaya merah tipis dari kiri atas; gradien radial statis.
            val radius = size.width * 0.75f
            val center = Offset(size.width * 0.22f, size.height * 0.18f)
            drawCircle(
                brush = Brush.radialGradient(
                    listOf(RECAP_ACCENT.copy(alpha = 0.20f), RECAP_ACCENT.copy(alpha = 0f)),
                    center = center,
                    radius = radius,
                ),
                radius = radius,
                center = center,
            )
        },
    ) {
        if (covers.isEmpty()) return@BoxWithConstraints
        val side = minOf(maxWidth, maxHeight)
        val originX = (maxWidth - side) / 2
        val originY = (maxHeight - side) / 2

        // Dari belakang ke depan; cover utama (0) digambar terakhir.
        for (slot in MOST_PLAYED_LAYERS) {
            val url = covers.getOrNull(slot.cover) ?: continue
            val tile = side * slot.size
            val shape = RoundedCornerShape(maxOf(12.dp, minOf(16.dp, tile * 0.10f)))
            Box(
                Modifier
                    .offset(originX + side * slot.x, originY + side * slot.y)
                    .size(tile)
                    .graphicsLayer { rotationZ = slot.rotation }
                    .shadow(side * (if (slot.cover == 0) 0.045f else 0.02f), shape)
                    .clip(shape)
                    .border(0.75.dp, Color.White.copy(alpha = 0.14f), shape),
            ) {
                androidx.compose.animation.Crossfade(
                    targetState = url,
                    animationSpec = tween(600),
                    label = "mostPlayedCover",
                ) { shown ->
                    AsyncImage(
                        model = shown.artworkAt(CARD_ART_PX),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                if (slot.dim > 0f) {
                    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = slot.dim)))
                }
            }
        }
    }
}

private fun Color.lighter(f: Float): Color =
    Color(red + (1f - red) * f, green + (1f - green) * f, blue + (1f - blue) * f)

/** Warna latar per kombinasi tiga cover teratas, supaya kartu, cover terbang, sleeve, dan hero langsung sama. */
private val MostPlayedTints = java.util.concurrent.ConcurrentHashMap<String, List<Color>>()

/**
 * Tiga warna latar (atas, tengah, bawah) dari warna dominan tiap cover teratas,
 * digelapkan agar teks putih tetap terbaca. Memakai warna bawaan sampai terbaca.
 */
@Composable
private fun rememberMostPlayedTint(
    covers: List<String>,
    fallback: List<Color> = MOST_PLAYED_COLORS,
): List<Color> {
    val context = LocalPlatformContext.current
    val key = covers.take(3).joinToString("|")
    var tint by remember(key) { mutableStateOf(MostPlayedTints[key] ?: fallback) }
    LaunchedEffect(key) {
        if (key.isEmpty() || MostPlayedTints.containsKey(key)) return@LaunchedEffect
        val picked = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
            covers.take(3).mapNotNull { url ->
                val request = ImageRequest.Builder(context)
                    .data(url.artworkAt(CARD_ART_PX))
                    .size(96)
                    .forPixelAccess()
                    .build()
                val result = coil3.SingletonImageLoader.get(context).execute(request)
                val bitmap = (result as? coil3.request.SuccessResult)?.image?.toImageBitmap()
                    ?: return@mapNotNull null
                tintRgbOf(bitmap)
            }
        }
        if (picked.isEmpty()) return@LaunchedEffect
        val colors = spreadTint(picked).map { Color(it) }
        MostPlayedTints[key] = colors
        tint = colors
    }
    return tint
}

/** Warna paling "berwarna" dan dominan di cover, digelapkan dan dijenuhkan. */
private fun tintRgbOf(bitmap: androidx.compose.ui.graphics.ImageBitmap): Int? {
    val swatches = paletteSwatches(bitmap, 16, false).ifEmpty { paletteSwatches(bitmap, 16, true) }
    val hsl = FloatArray(3)
    val best = swatches.maxByOrNull { s ->
        ColorUtils.colorToHSL(s.rgb, hsl)
        s.population * (0.25f + hsl[1])
    } ?: return null
    ColorUtils.colorToHSL(best.rgb, hsl)
    hsl[1] = hsl[1].coerceIn(0.45f, 0.90f)
    hsl[2] = hsl[2].coerceIn(0.24f, 0.40f)
    return ColorUtils.HSLToColor(hsl)
}

/** Selalu tiga warna; yang terlalu mirip dengan sebelumnya digeser hue-nya. */
private fun spreadTint(picked: List<Int>): List<Int> {
    val out = mutableListOf<Int>()
    val hsl = FloatArray(3)
    for (i in 0 until 3) {
        var rgb = picked[i % picked.size]
        if (out.any { rgbClose(it, rgb) }) {
            ColorUtils.colorToHSL(rgb, hsl)
            hsl[0] = (hsl[0] + 35f * i) % 360f
            rgb = ColorUtils.HSLToColor(hsl)
        }
        out += rgb
    }
    return out
}

private fun rgbClose(a: Int, b: Int): Boolean {
    val x = FloatArray(3)
    val y = FloatArray(3)
    ColorUtils.colorToHSL(a, x)
    ColorUtils.colorToHSL(b, y)
    val gap = kotlin.math.abs(x[0] - y[0]).let { kotlin.math.min(it, 360f - it) }
    return gap < 20f && kotlin.math.abs(x[2] - y[2]) < 0.12f
}

/** Permukaan kartu Most Played dan aksennya (Z Music). */
private val RECAP_SURFACE = Color(0xFF171719)
private val RECAP_ACCENT = Color(0xFFFA243C)

/** Satu cover dalam komposisi berlapis; nilai pecahan dari sisi persegi seni. */
private class MostPlayedSlot(
    val cover: Int,
    val x: Float,
    val y: Float,
    val size: Float,
    val rotation: Float,
    val dim: Float,
)

// Urutan gambar: belakang ke depan. Cover utama (0) di depan, tegak, dan terbesar;
// dua cover pendukung sedikit miring dan saling menumpuk di belakangnya.
// Seluruh komposisi berakhir di y 0.73, di atas blok teks kartu.
private val MOST_PLAYED_LAYERS = listOf(
    MostPlayedSlot(cover = 2, x = 0.600f, y = 0.460f, size = 0.270f, rotation = -5f, dim = 0.18f),
    MostPlayedSlot(cover = 1, x = 0.550f, y = 0.130f, size = 0.310f, rotation = 6f, dim = 0.06f),
    MostPlayedSlot(cover = 0, x = 0.080f, y = 0.180f, size = 0.540f, rotation = 0f, dim = 0f),
)

/**
 * Cover untuk hero Liked Music: paling banyak [LIKED_MAX_COVERS], diundi dari
 * lagu di playlist lalu disimpan [LIKED_COVER_TTL_MS]. Selama masih segar, membuka
 * halaman tidak mengundi ulang dan tidak memuat gambar lain. Kalau daftar lagu
 * belum datang, pilihan lama (jika ada) dipakai dan jam undian tidak dimulai.
 */
@Composable
private fun rememberLikedCovers(pageId: String, songs: List<Song>): List<String> {
    val context = LocalContext.current.applicationContext
    val hasSongs = songs.isNotEmpty()
    return remember(pageId, hasSongs) {
        val prefs = context.getSharedPreferences("liked_hero", Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()
        val saved = prefs.getString("covers_v${LIKED_COVER_CACHE_VERSION}_$pageId", null)
            ?.split('\n')
            ?.filter { it.isNotBlank() }
            .orEmpty()
        val age = now - prefs.getLong("at_v${LIKED_COVER_CACHE_VERSION}_$pageId", 0L)
        val pool = songs.mapNotNull { it.thumbnailUrl }.distinct()
        // Pilihan lama dipakai ulang hanya kalau masih segar dan jumlahnya sudah cukup.
        val fresh = saved.isNotEmpty() && age in 0L until LIKED_COVER_TTL_MS &&
            saved.size >= minOf(LIKED_MAX_COVERS, pool.size)
        if (fresh) return@remember saved
        if (pool.isEmpty()) return@remember saved
        val picked = pool.shuffled().take(LIKED_MAX_COVERS)
        prefs.edit()
            .putString("covers_v${LIKED_COVER_CACHE_VERSION}_$pageId", picked.joinToString("\n"))
            .putLong("at_v${LIKED_COVER_CACHE_VERSION}_$pageId", now)
            .apply()
        picked
    }
}

/** The glyph on a release page's placeholder sleeve: what kind of thing has no cover. */
private fun DetailPage.placeholderIcon(): ImageVector = deviceFolderIcon(browseId) ?: when {
    LocalGroups.isArtist(browseId) -> Icons.Rounded.Person
    OfflinePlaylists.isLikedPage(browseId) -> Icons.Rounded.Favorite
    LocalGroups.isDirectory(browseId) -> Icons.Rounded.Folder
    type == BrowseType.ALBUM -> Icons.Rounded.Album
    type == BrowseType.PLAYLIST -> Icons.AutoMirrored.Rounded.QueueMusic
    else -> AudifyIcons.MusicNote
}

/** "10 tracks" → "10 Tracks", "playlist" → "Playlist". */
private fun String.capitalizeWords(): String =
    split(' ').joinToString(" ") { word ->
        word.replaceFirstChar { it.titlecase(Locale.getDefault()) }
    }

/** Subtitle words that name what a page *is* rather than who made it. */
private val KIND_WORDS = setOf(
    "album", "single", "ep", "playlist", "artist", "podcast", "episode", "song", "video",
)

@Composable
private fun BrowseType.localizedLabel(): String? = when (this) {
        BrowseType.ALBUM -> stringResource(R.string.album)
        BrowseType.PLAYLIST -> stringResource(R.string.playlist)
        BrowseType.ARTIST -> stringResource(R.string.artist)
        BrowseType.OTHER -> null
    }

/**
 * How long the page plays for — "41 min", "1h 25m" — summed over the rows
 * on it, or null when none of them carry a duration. A playlist still filling
 * in counts up with it, so the figure is never ahead of the list it sits over.
 */
@Composable
private fun List<Song>.playtime(): String? {
    val minutes = sumOf { it.durationMillis() } / 60_000
    return when {
        minutes <= 0 -> null
        minutes < 60 -> stringResource(R.string.minutes_short, minutes.toInt())
        else -> stringResource(R.string.hours_minutes_short, (minutes / 60).toInt(), (minutes % 60).toInt())
    }
}
