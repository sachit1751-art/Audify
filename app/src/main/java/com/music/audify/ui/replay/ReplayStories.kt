@file:OptIn(ExperimentalLayoutApi::class)

package com.music.audify.ui.replay

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.navigationBarsIgnoringVisibility
import androidx.compose.foundation.layout.statusBarsIgnoringVisibility
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import com.music.audify.ui.theme.HideStatusBar
import com.music.audify.ui.theme.HideSystemBars
import kotlinx.coroutines.delay
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PointMode
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.*
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.ColorUtils
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.Player
import coil3.compose.AsyncImage
import com.music.audify.R
import com.music.audify.data.model.CARD_ART_PX
import com.music.audify.data.model.HEADER_ART_PX
import com.music.audify.data.model.ROW_ART_PX
import com.music.audify.data.model.artworkAt
import com.music.audify.data.settings.AppSettings
import com.music.audify.data.stats.ReplaySummary
import com.music.audify.ui.icons.SfIcons
import com.music.audify.ui.player.MeshPalette
import com.music.audify.ui.player.rememberArtworkColors
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * Replay sebagai rangkaian story layar penuh, bergaya editorial sinematik.
 *
 * Satu backdrop gradien hidup berada di level Stage dan berpindah warna
 * mengikuti posisi pager (tanpa rekomposisi), jadi tidak ada kedip antar-story.
 * Tiap story punya komposisi sendiri: intro kolase, menit raksasa, potret artis,
 * lagu editorial, album, kepribadian musik (Canvas) dan recap hitam dengan glow.
 *
 * Navigasi tetap: tap kanan/kiri, tahan untuk jeda, swipe, progress di atas,
 * tombol tutup. Auto-advance berhenti di story terakhir.
 */
@Composable
fun ReplayStories(
    summary: ReplaySummary,
    start: ReplayStoryPage,
    onClose: () -> Unit,
    /** Shares the card that was on screen when the button was pressed. */
    onShare: (ReplayStoryPage) -> Unit,
    /** Holds the clock while something is up in front of the story. */
    paused: Boolean = false,
    /** The app's player, paused while the Replay plays its own backsound. */
    mainPlayer: Player? = null,
) {
    // Kategori kosong tidak punya story. Kebiasaan (HABITS) kini jadi slide
    // sendiri: jam puncak, hari tersibuk, dan jumlah putaran.
    val pages = remember(summary) {
        ReplayStoryPage.ordered.filter { page ->
            when (page) {
                ReplayStoryPage.ALBUMS -> summary.albums.isNotEmpty()
                else -> true
            }
        }
    }
    val pagerState = rememberPagerState(
        initialPage = pages.indexOf(start).coerceAtLeast(0),
        pageCount = { pages.size },
    )
    val scope = rememberCoroutineScope()
    var held by remember { mutableStateOf(false) }
    val progress = remember { Animatable(0f) }
    val current by remember { derivedStateOf { pagerState.settledPage } }
    val dragging by pagerState.interactionSource.collectIsDraggedAsState()
    val moving by remember { derivedStateOf { pagerState.targetPage != pagerState.settledPage } }
    var nudge by remember { mutableStateOf(0) }
    var restart by remember { mutableStateOf(0) }
    val transition = remember { arrayOfNulls<Job>(1) }
    val seen = remember { intArrayOf(-1, -1) }
    val clockStopped = held || paused || dragging || moving
    val reduceAnimation by AppSettings.reduceAnimation.collectAsStateWithLifecycle()
    val motion = !reduceAnimation

    DisposableEffect(Unit) { onDispose { ReplayCapture.clear() } }

    // Story memenuhi seluruh layar: status bar dan navigation bar disembunyikan
    // (swipe dari tepi memunculkannya sebentar), cutout ditangani, dan konfigurasi
    // sebelumnya dipulihkan otomatis saat Replay ditutup.
    HideStatusBar(true)
    HideSystemBars(true)

    ReplayBacksound(
        summary = summary,
        silenced = held || paused,
        mainPlayer = mainPlayer,
    )

    fun goTo(target: Int, animate: Boolean) {
        val next = target.coerceIn(0, pages.lastIndex)
        nudge++
        if (next == pagerState.settledPage) restart++
        transition[0]?.cancel()
        transition[0] = scope.launch {
            if (animate && motion) {
                pagerState.animateScrollToPage(
                    page = next,
                    animationSpec = tween(STEP_MILLIS, easing = FastOutSlowInEasing),
                )
            } else {
                pagerState.scrollToPage(next)
            }
        }
    }

    fun step(forward: Boolean) {
        val from = pagerState.targetPage
        if (forward && from == pages.lastIndex) {
            onClose()
            return
        }
        goTo(from + if (forward) 1 else -1, animate = true)
    }

    LaunchedEffect(current, clockStopped, restart) {
        if (seen[0] != current || seen[1] != restart) {
            seen[0] = current
            seen[1] = restart
            progress.snapTo(0f)
        }
        if (clockStopped) return@LaunchedEffect
        val ticket = nudge
        val remaining = ((1f - progress.value) * PAGE_MILLIS).toInt().coerceAtLeast(0)
        progress.animateTo(1f, tween(remaining, easing = LinearEasing))
        if (ticket != nudge) return@LaunchedEffect
        if (current == pages.lastIndex) onClose() else goTo(current + 1, animate = true)
    }

    // Akar masalah area kosong: Box ini dulu memakai statusBarsPadding +
    // navigationBarsPadding di atas latar hitam, lalu StoryFrame mengunci 9:16
    // (letterbox). Keduanya dihapus; latar kini menembus system bars dan insets
    // diterapkan hanya pada konten dan header di dalam Stage.
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        Stage(
            pages = pages,
            pagerState = pagerState,
            summary = summary,
            current = current,
            progress = progress,
            motion = motion,
            onHold = { held = it },
            onStep = ::step,
            onClose = onClose,
            onShare = onShare,
            onRestart = { goTo(0, animate = true) },
        )
    }
}

// Frame 9:16 dihapus: story kini edge-to-edge dan menyesuaikan tinggi layar.

@Composable
private fun Stage(
    pages: List<ReplayStoryPage>,
    pagerState: PagerState,
    summary: ReplaySummary,
    current: Int,
    progress: Animatable<Float, *>,
    motion: Boolean,
    onHold: (Boolean) -> Unit,
    onStep: (Boolean) -> Unit,
    onClose: () -> Unit,
    onShare: (ReplayStoryPage) -> Unit,
    onRestart: () -> Unit,
) {
    val shown = pagerState.currentPage
    val hold by rememberUpdatedState(onHold)
    val step by rememberUpdatedState(onStep)
    val drift = rememberDrift(motion)
    val layer = rememberGraphicsLayer()
    // Area aman untuk konten dan header; latar tetap menembus seluruh layar.
    val safe = WindowInsets.statusBarsIgnoringVisibility
        .union(WindowInsets.displayCutout)
        .union(WindowInsets.navigationBarsIgnoringVisibility)
    val bgIn = remember { Animatable(0f) }
    val chromeIn = remember { Animatable(0f) }
    LaunchedEffect(motion) {
        if (!motion) {
            bgIn.snapTo(1f)
            chromeIn.snapTo(1f)
            return@LaunchedEffect
        }
        // 0-300 ms latar muncul, 300-600 ms header masuk.
        launch { bgIn.animateTo(1f, tween(300, easing = FastOutSlowInEasing)) }
        delay(300)
        chromeIn.animateTo(1f, tween(300, easing = FastOutSlowInEasing))
    }
    val captureScope = rememberCoroutineScope()
    var capturing by remember { mutableStateOf(false) }
    val shareCard: (ReplayStoryPage) -> Unit = { card ->
        captureScope.launch {
            ReplayCapture.clear()
            // Selama capture, tombol di dalam kartu disembunyikan dan animasi
            // masuk dilompati ke akhir, supaya gambar yang dibagikan sama
            // dengan kartu di layar (termasuk recap).
            capturing = true
            repeat(3) { androidx.compose.runtime.withFrameNanos { } }
            runCatching { layer.toImageBitmap().asAndroidBitmap() }
                .onSuccess { ReplayCapture.put(card, it) }
            capturing = false
            onShare(card)
        }
    }

    // Palet tiap story diekstrak sekali di sini (di-cache per URL), lalu dipakai
    // backdrop bersama dan visual di tiap halaman.
    val palettes = pages.map { rememberArtworkColors(summary.storyArtwork(it)) }
    val hued = remember(palettes, pages) {
        palettes.mapIndexed { index, palette -> palette.rotated(storyHue(pages[index])).colors }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color(0xFF0E0E10))
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        hold(true)
                        tryAwaitRelease()
                        hold(false)
                    },
                    // Sengaja kosong: tanpa ini tiap press-release dianggap tap.
                    onLongPress = {},
                    onTap = { offset -> step(offset.x >= size.width * BACK_ZONE) },
                )
            },
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .drawWithContent {
                    layer.record { this@drawWithContent.drawContent() }
                    drawLayer(layer)
                },
        ) {
            StoryBackdrop(
                colors = hued,
                position = { pagerState.currentPage + pagerState.currentPageOffsetFraction },
                drift = drift,
            )
            FluidBackdrop(
                pages = pages,
                colors = hued[shown.coerceIn(0, hued.lastIndex)],
                position = { pagerState.currentPage + pagerState.currentPageOffsetFraction },
                active = motion && (
                    pages[shown.coerceIn(0, pages.lastIndex)].runsFluid() ||
                        pages[pagerState.targetPage.coerceIn(0, pages.lastIndex)].runsFluid()
                    ),
            )
            val introAt = pages.indexOf(ReplayStoryPage.INTRO)
            if (introAt >= 0) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            val pos = pagerState.currentPage + pagerState.currentPageOffsetFraction
                            alpha = (1f - abs(pos - introAt)).coerceIn(0f, 1f)
                        },
                ) {
                    IntroAtmosphere(
                        modifier = Modifier.fillMaxSize(),
                        active = motion && (shown == introAt || pagerState.targetPage == introAt),
                    )
                }
            }
            // Atmosfer ungu Story 02: crossfade mengikuti posisi pager, jadi latar
            // Intro melebur halus ke ungu tanpa potongan.
            val minutesAt = pages.indexOf(ReplayStoryPage.MINUTES)
            if (minutesAt >= 0) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            val pos = pagerState.currentPage + pagerState.currentPageOffsetFraction
                            alpha = (1f - abs(pos - minutesAt)).coerceIn(0f, 1f)
                        },
                ) {
                    MinutesAtmosphere(
                        modifier = Modifier.fillMaxSize(),
                        active = motion && (shown == minutesAt || pagerState.targetPage == minutesAt),
                    )
                }
            }
            FilmGrain()
            // Fade-in latar: tinta menutup lalu memudar, digambar tanpa layer.
            Box(
                Modifier
                    .fillMaxSize()
                    .drawBehind {
                        val a = 1f - bgIn.value
                        if (a > 0f) drawRect(Color(0xFF0E0E10), alpha = a)
                    },
            )

            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize().windowInsetsPadding(safe),
            ) { index ->
                StoryPage(
                    page = pages[index],
                    summary = summary,
                    colors = hued[index],
                    onNext = { step(true) },
                    onRestart = onRestart,
                    onShare = shareCard,
                    pageOffset = { (pagerState.currentPage - index) + pagerState.currentPageOffsetFraction },
                    drift = drift,
                    motion = motion,
                    capturing = capturing,
                )
            }
            if (capturing) {
                CaptureBrand(summary.localizedLabel(LocalContext.current))
            }
        }

        Box(Modifier.fillMaxWidth().windowInsetsPadding(safe)) {
            StoryChrome(
                label = summary.localizedLabel(LocalContext.current),
                count = pages.size,
                current = current,
                shown = shown,
                progress = { progress.value },
                onClose = onClose,
                onShare = { shareCard(pages[shown.coerceIn(0, pages.lastIndex)]) },
                enter = { chromeIn.value },
            )
        }
    }
}

/** Fase 0..1 yang berulang tiap 24 dtk; dibaca lambda saat draw, bukan di komposisi. */
@Composable
private fun rememberDrift(enabled: Boolean): () -> Float {
    val transition = rememberInfiniteTransition(label = "drift")
    val phase = transition.animateFloat(
        initialValue = 0f,
        targetValue = if (enabled) 1f else 0f,
        animationSpec = infiniteRepeatable(tween(24_000, easing = LinearEasing)),
        label = "driftPhase",
    )
    return { phase.value }
}

/**
 * Backdrop bersama: gradien radial berlapis yang warnanya bercampur mengikuti
 * posisi pager. Tanpa blur — gradien radial murah dan tetap halus.
 */
@Composable
private fun StoryBackdrop(
    colors: List<List<Color>>,
    position: () -> Float,
    drift: () -> Float,
) {
    Box(
        Modifier
            .fillMaxSize()
            .drawBehind {
                if (colors.isEmpty()) return@drawBehind
                val last = colors.lastIndex
                val pos = position().coerceIn(0f, last.toFloat())
                val i = pos.toInt().coerceIn(0, last)
                val j = (i + 1).coerceAtMost(last)
                val f = pos - i
                fun mix(k: Int): Color {
                    val a = colors[i]
                    val b = colors[j]
                    return lerp(a[k % a.size], b[k % b.size], f)
                }
                val w = size.width
                val h = size.height
                drawRect(lerp(mix(0), Color.Black, 0.45f))
                val turn = drift() * 2f * PI.toFloat()
                for (k in 0 until 4) {
                    val (ax, ay) = BlobAnchors[k]
                    val a = turn + k * 1.7f
                    val c = Offset(w * (ax + 0.07f * cos(a)), h * (ay + 0.05f * sin(a)))
                    val r = w * 1.15f
                    drawCircle(
                        brush = Brush.radialGradient(
                            listOf(mix(k).copy(alpha = 0.95f), Color.Transparent),
                            center = c,
                            radius = r,
                        ),
                        radius = r,
                        center = c,
                    )
                }
                drawRect(
                    Brush.verticalGradient(
                        0f to Color.Black.copy(alpha = 0.18f),
                        0.4f to Color.Transparent,
                        1f to Color.Black.copy(alpha = 0.30f),
                    ),
                )
            },
    )
}

/** Story yang latarnya memakai blob shader; story lain tetap memakai [StoryBackdrop]. */
private val FluidPages = ReplayStoryPage.entries.filter { it != ReplayStoryPage.SUMMARY }.toSet()

/**
 * Intro dan Menit punya atmosfer sendiri yang menutup blob shader; saat diam di
 * halaman itu blob tidak perlu dianimasikan (hemat GPU). Transisi ke story lain
 * tetap menjalankannya seperti sebelumnya.
 */
private fun ReplayStoryPage.runsFluid(): Boolean =
    this in FluidPages && this != ReplayStoryPage.INTRO && this != ReplayStoryPage.MINUTES

/**
 * Blob shader (OrganicFluidBackground) di atas [StoryBackdrop], hanya terlihat di
 * [FluidPages] dan memudar mengikuti posisi pager. Bila shader tidak didukung
 * (API < 33 atau gagal kompilasi) tidak ada yang digambar, sehingga backdrop
 * gradien yang sekarang tetap tampil.
 */
@Composable
private fun FluidBackdrop(
    pages: List<ReplayStoryPage>,
    colors: List<Color>,
    position: () -> Float,
    active: Boolean,
) {
    if (!ReplayFluidSupport.shaderSupported) return
    if (pages.none { it in FluidPages }) return
    val tinted = remember(colors) { colors.map { lerp(it, Color.Black, 0.10f) } }
    Box(
        Modifier
            .fillMaxSize()
            .graphicsLayer {
                val last = pages.lastIndex
                val pos = position().coerceIn(0f, last.toFloat())
                val i = pos.toInt().coerceIn(0, last)
                val j = (i + 1).coerceAtMost(last)
                val a = if (pages[i] in FluidPages) 1f else 0f
                val b = if (pages[j] in FluidPages) 1f else 0f
                alpha = a + (b - a) * (pos - i)
            },
    ) {
        ReplayFluidBackground(
            colors = tinted,
            modifier = Modifier.fillMaxSize(),
            animationSpeed = if (active) 0.8f else 0f,
        )
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0f to Color.Black.copy(alpha = 0.25f),
                        0.5f to Color.Transparent,
                        1f to Color.Black.copy(alpha = 0.40f),
                    ),
                ),
        )
    }
}

/** Vignette dan butir film, digambar sekali ke layer sendiri. */
@Composable
private fun FilmGrain() {
    Box(
        Modifier
            .fillMaxSize()
            .graphicsLayer { }
            .drawWithCache {
                val light = ArrayList<Offset>()
                val dark = ArrayList<Offset>()
                GrainDots.forEachIndexed { index, dot ->
                    val o = Offset(size.width * dot.first, size.height * dot.second)
                    if (index % 2 == 0) light.add(o) else dark.add(o)
                }
                val stroke = 1.6.dp.toPx()
                val vignette = Brush.radialGradient(
                    colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.34f)),
                    center = Offset(size.width / 2f, size.height / 2f),
                    radius = size.maxDimension * 0.8f,
                )
                onDrawBehind {
                    drawRect(vignette)
                    drawPoints(light, PointMode.Points, GrainLight, stroke, StrokeCap.Round)
                    drawPoints(dark, PointMode.Points, GrainDark, stroke, StrokeCap.Round)
                }
            },
    )
}

/** Progress minimalis, "Replay'xx", logo, share dan tutup. */
@Composable
private fun StoryChrome(
    label: String,
    count: Int,
    current: Int,
    shown: Int,
    progress: () -> Float,
    onClose: () -> Unit,
    onShare: () -> Unit,
    enter: () -> Float = { 1f },
) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .graphicsLayer {
                val e = ease(enter())
                alpha = e
                translationY = (1f - e) * -14.dp.toPx()
            },
    ) {
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            repeat(count) { index ->
                // Segmen yang baru selesai (atau dibuka kembali) terisi/kosong
                // dengan halus, bukan melompat saat pindah story.
                val settled = animateFloatAsState(
                    targetValue = if (index < shown) 1f else 0f,
                    animationSpec = tween(260, easing = FastOutSlowInEasing),
                    label = "segment",
                )
                Segment(
                    fraction = {
                        when {
                            index < shown -> settled.value
                            index > shown -> settled.value
                            shown == current -> progress()
                            else -> 0f
                        }
                    },
                    modifier = Modifier.weight(1f),
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = if (label.length == 4 && label.all { it.isDigit() }) {
                    "Replay'${label.takeLast(2)}"
                } else {
                    "Replay · $label"
                },
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.W800,
                color = Color.White,
                modifier = Modifier.weight(1f),
            )
            Icon(
                painter = painterResource(R.drawable.ic_logo),
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.92f),
                modifier = Modifier.size(width = 22.dp, height = 14.dp).offset(y = 2.dp),
            )
            Spacer(Modifier.width(3.dp))
            Text(
                text = "Audify",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.W700,
                color = Color.White.copy(alpha = 0.92f),
            )
            Spacer(Modifier.width(14.dp))
            ChromeButton(onShare) {
                Icon(
                    imageVector = SfIcons.Share,
                    contentDescription = stringResource(R.string.share_my_replay),
                    tint = Color.White,
                    modifier = Modifier.size(15.dp),
                )
            }
            Spacer(Modifier.width(8.dp))
            ChromeButton(onClose) {
                Icon(
                    imageVector = Icons.Rounded.Close,
                    contentDescription = stringResource(R.string.close_replay),
                    tint = Color.White,
                    modifier = Modifier.size(17.dp),
                )
            }
        }
    }
}

@Composable
private fun ChromeButton(onClick: () -> Unit, content: @Composable () -> Unit) {
    Box(
        Modifier
            .size(34.dp)
            .clip(CircleShape)
            .background(
                Brush.verticalGradient(
                    listOf(Color.White.copy(alpha = 0.30f), Color.White.copy(alpha = 0.14f)),
                ),
            )
            .border(0.7.dp, Color.White.copy(alpha = 0.28f), CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { content() }
}

@Composable
private fun Segment(fraction: () -> Float, modifier: Modifier = Modifier) {
    Box(
        modifier
            .height(3.dp)
            .clip(RoundedCornerShape(50))
            .background(Color.White.copy(alpha = 0.28f))
            .drawBehind {
                drawRect(
                    color = Color.White,
                    size = Size(this.size.width * fraction().coerceIn(0f, 1f), this.size.height),
                )
            },
    )
}

// ── Motion helpers ──────────────────────────────────────────────────────────

private fun ease(t: Float): Float {
    val c = t.coerceIn(0f, 1f)
    val u = 1f - c
    return 1f - u * u * u * u
}

/** Fade + naik halus saat [reveal] mencapai [start]. Dibaca saat draw. */
private fun Modifier.staged(
    reveal: Animatable<Float, *>,
    start: Float,
    span: Float = 0.45f,
    rise: Dp = 26.dp,
): Modifier = graphicsLayer {
    val e = ease((reveal.value - start) / span)
    alpha = e
    translationY = (1f - e) * rise.toPx()
}

/** Fade + scale reveal untuk artwork. */
private fun Modifier.popped(
    reveal: Animatable<Float, *>,
    start: Float,
    span: Float = 0.5f,
    from: Float = 0.82f,
): Modifier = graphicsLayer {
    val e = ease((reveal.value - start) / span)
    alpha = e
    val s = from + (1f - from) * e
    scaleX = s
    scaleY = s
}

/** Teks naik dari balik garis potong (mask reveal). */
@Composable
private fun MaskReveal(
    reveal: Animatable<Float, *>,
    start: Float,
    modifier: Modifier = Modifier,
    span: Float = 0.5f,
    content: @Composable () -> Unit,
) {
    Box(modifier.clipToBounds()) {
        Box(
            Modifier.graphicsLayer {
                val e = ease((reveal.value - start) / span)
                translationY = (1f - e) * this.size.height * 1.1f
            },
        ) { content() }
    }
}

// ── Satu halaman ────────────────────────────────────────────────────────────

@Composable
private fun StoryPage(
    page: ReplayStoryPage,
    summary: ReplaySummary,
    colors: List<Color>,
    onNext: () -> Unit,
    onRestart: () -> Unit,
    onShare: (ReplayStoryPage) -> Unit,
    pageOffset: () -> Float,
    drift: () -> Float,
    motion: Boolean,
    capturing: Boolean = false,
) {
    val context = LocalContext.current
    val reveal = remember { Animatable(0f) }
    LaunchedEffect(motion) {
        if (motion) {
            reveal.animateTo(1f, tween(2600, delayMillis = 80, easing = LinearEasing))
        } else {
            reveal.snapTo(1f)
        }
    }
    val headline = remember(summary, page) { summary.storyHeadline(context, page) }
    // Dicapture untuk share: lompat ke keadaan akhir supaya gambar tidak setengah jalan.
    LaunchedEffect(capturing) { if (capturing) reveal.snapTo(1f) }

    Box(
        Modifier
            .fillMaxSize()
            .graphicsLayer {
                // Konten memimpin backdrop: keluar lebih cepat, masuk lebih lambat.
                val o = pageOffset().coerceIn(-1f, 1f)
                val a = abs(o)
                translationX = -o * this.size.width * 0.20f
                alpha = (1f - a * 1.1f).coerceIn(0f, 1f)
                scaleX = 1f - a * 0.04f
                scaleY = 1f - a * 0.04f
            },
    ) {
        if (page == ReplayStoryPage.SUMMARY) RecapGlow(colors)
        if (page == ReplayStoryPage.ARTISTS) ArtistPortraitBackdrop(summary, reveal, pageOffset, motion)
        if (page == ReplayStoryPage.INTRO) {
            IntroPage(
                summary = summary,
                headline = headline,
                drift = drift,
                pageOffset = pageOffset,
                onNext = onNext,
                motion = motion,
                capturing = capturing,
            )
        } else Column(
            Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp)
                .padding(top = CHROME_HEIGHT, bottom = 18.dp),
        ) {
            when (page) {
                ReplayStoryPage.INTRO -> Unit
                ReplayStoryPage.MINUTES -> MinutesStory(summary, headline, reveal, drift, pageOffset)
                ReplayStoryPage.ARTISTS -> ArtistsStory(summary, headline, reveal, pageOffset)
                ReplayStoryPage.SONGS -> SongsStory(summary, headline, reveal)
                ReplayStoryPage.ALBUMS -> AlbumsStory(summary, headline, reveal, drift, pageOffset)
                ReplayStoryPage.GENRES -> PersonalityStory(summary, headline, colors, reveal, drift)
                ReplayStoryPage.HABITS -> HabitsStory(summary, headline, reveal)
                ReplayStoryPage.SUMMARY ->
                    RecapStory(summary, headline, reveal, onShare = { onShare(ReplayStoryPage.SUMMARY) }, onRestart = onRestart, hideActions = capturing)
            }
        }
    }
}

// ── Tipografi ───────────────────────────────────────────────────────────────

@Composable
private fun Headline(
    parts: List<HeadlineRun>,
    modifier: Modifier = Modifier,
    textSize: Int = 32,
    family: FontFamily = FontFamily.Default,
    lineHeightScale: Float = 1.1f,
    tracking: Float = -0.5f,
) {
    Text(
        text = buildAnnotatedString {
            parts.forEach { run ->
                withStyle(
                    SpanStyle(
                        fontWeight = if (run.bold) FontWeight.W900 else FontWeight.W700,
                        color = if (run.bold) Color.White else Color.White.copy(alpha = 0.86f),
                    ),
                ) { append(run.text) }
            }
        },
        style = MaterialTheme.typography.displayLarge,
        fontFamily = family,
        fontSize = textSize.sp,
        lineHeight = (textSize * lineHeightScale).sp,
        letterSpacing = tracking.sp,
        modifier = modifier.fillMaxWidth(),
    )
}

@Composable
private fun Kicker(text: String, modifier: Modifier = Modifier, alpha: Float = 0.8f) {
    Text(
        text = text.uppercase(Locale.ROOT),
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.W700,
        letterSpacing = 3.sp,
        color = Color.White.copy(alpha = alpha),
        modifier = modifier,
    )
}

@Composable
private fun PillButton(
    text: String,
    filled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier
            .clip(CircleShape)
            .background(if (filled) Color.White else Color.White.copy(alpha = 0.14f))
            .then(if (filled) Modifier else Modifier.border(0.8.dp, Color.White.copy(alpha = 0.35f), CircleShape))
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 13.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text.uppercase(Locale.ROOT),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.W800,
            letterSpacing = 1.5.sp,
            color = if (filled) Color(0xFF0E0E10) else Color.White,
        )
    }
}

// ── 01 · Intro ──────────────────────────────────────────────────────────────

@Composable
private fun IntroPage(
    summary: ReplaySummary,
    headline: List<HeadlineRun>,
    drift: () -> Float,
    pageOffset: () -> Float,
    onNext: () -> Unit,
    motion: Boolean,
    capturing: Boolean,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    // Satu jam 0..1 selama 2 dtk; tiap elemen memakai jendela waktunya sendiri
    // (label 900-1200, tahun 1100-1400, headline 1300-1650, teks 1550-1800,
    // CTA 1750-2000 ms) supaya semuanya terasa satu koreografi.
    val reveal = remember { Animatable(0f) }
    val exit = remember { Animatable(0f) }
    LaunchedEffect(motion) {
        if (motion) {
            reveal.animateTo(1f, tween(INTRO_TIMELINE_MS, easing = LinearEasing))
        } else {
            reveal.snapTo(1f)
        }
    }
    LaunchedEffect(capturing) { if (capturing) reveal.snapTo(1f) }

    val label = remember(summary) { summary.localizedLabel(context) }
    val yearSize = when {
        label.length <= 4 -> 92.sp
        label.length <= 9 -> 56.sp
        else -> 38.sp
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val headlineSize = when {
            maxHeight < 560.dp -> 44
            maxHeight < 680.dp -> 52
            else -> 60
        }
        Column(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    // Transisi keluar ke Story 02: konten terangkat dan meredup.
                    val x = exit.value
                    alpha = 1f - 0.45f * x
                    translationY = -x * 22.dp.toPx()
                }
                .padding(horizontal = 24.dp)
                .padding(top = CHROME_HEIGHT + 6.dp, bottom = 20.dp),
        ) {
            Text(
                text = stringResource(R.string.replay_brand).uppercase(Locale.ROOT),
                fontSize = 12.sp,
                fontWeight = FontWeight.W700,
                letterSpacing = 4.sp,
                color = Color.White.copy(alpha = 0.88f),
                modifier = Modifier.staged(reveal, 0.45f, 0.15f, 10.dp),
            )
            Text(
                text = label,
                style = MaterialTheme.typography.displayLarge,
                fontSize = yearSize,
                lineHeight = yearSize * 0.98f,
                fontWeight = FontWeight.W200,
                letterSpacing = (-3).sp,
                color = Color.White,
                maxLines = 1,
                softWrap = false,
                modifier = Modifier.popped(reveal, 0.55f, 0.15f, 0.9f),
            )
            Box(Modifier.weight(1f).fillMaxWidth()) {
                IntroCollage(
                    summary = summary,
                    drift = drift,
                    pageOffset = pageOffset,
                    motion = motion,
                    capturing = capturing,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            MaskReveal(reveal, 0.65f, span = 0.175f) {
                Headline(headline, textSize = headlineSize, lineHeightScale = 1.02f, tracking = -1.6f)
            }
            Spacer(Modifier.height(10.dp))
            Text(
                text = stringResource(R.string.replay_story_intro_sub),
                style = MaterialTheme.typography.bodyLarge,
                fontFamily = FontFamily.Serif,
                fontStyle = FontStyle.Italic,
                color = Color.White.copy(alpha = 0.78f),
                modifier = Modifier.staged(reveal, 0.775f, 0.125f),
            )
            Spacer(Modifier.height(22.dp))
            RelivePill(
                text = stringResource(R.string.replay_cta_relive),
                onClick = {
                    if (exit.value == 0f && !exit.isRunning) {
                        scope.launch {
                            exit.animateTo(1f, tween(170, easing = FastOutSlowInEasing))
                            onNext()
                        }
                    }
                },
                modifier = Modifier.staged(reveal, 0.875f, 0.125f, 18.dp),
            )
        }
    }
}

/** Tombol putih besar: skala menekan dengan pegas, haptic ringan. */
@Composable
private fun RelivePill(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val scale = animateFloatAsState(
        targetValue = if (pressed) 0.955f else 1f,
        animationSpec = spring(dampingRatio = 0.5f, stiffness = Spring.StiffnessMedium),
        label = "pillScale",
    )
    val haptics = LocalHapticFeedback.current
    Box(
        modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = scale.value
                scaleY = scale.value
            }
            .clip(CircleShape)
            .background(Color.White)
            .clickable(interactionSource = source, indication = null) {
                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onClick()
            }
            .padding(horizontal = 32.dp, vertical = 19.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text.uppercase(Locale.ROOT),
            fontSize = 15.sp,
            fontWeight = FontWeight.W800,
            letterSpacing = 1.8.sp,
            color = Color(0xFF0E0E10),
        )
    }
}

private const val INTRO_TIMELINE_MS = 2000
private val IntroTau = (2.0 * PI).toFloat()

/**
 * Satu keping kolase. [cx], [cy] adalah pusat (pecahan lebar/tinggi area),
 * [size] pecahan satuan, [fromX]/[fromY] arah masuk, [freq] kelipatan periode
 * melayang supaya tiap cover bergerak sendiri-sendiri.
 */
private class IntroSlot(
    val cx: Float,
    val cy: Float,
    val size: Float,
    val angle: Float,
    val depth: Float,
    val fromX: Float,
    val fromY: Float,
    val corner: Dp,
    val delayMs: Long,
    val freq: Int,
    val phase: Float,
    val hero: Boolean = false,
)

private val IntroSlots = listOf(
    IntroSlot(0.50f, 0.50f, 0.60f, -2f, 1.0f, 0.0f, 1.0f, 22.dp, 780L, 1, 0.0f, hero = true),
    IntroSlot(0.17f, 0.20f, 0.29f, -9f, 0.6f, -1.0f, -0.7f, 14.dp, 450L, 2, 1.1f),
    IntroSlot(0.85f, 0.15f, 0.26f, 8f, 0.7f, 1.0f, -0.8f, 14.dp, 520L, 3, 2.3f),
    IntroSlot(0.86f, 0.72f, 0.30f, 7f, 1.3f, 1.0f, 0.6f, 14.dp, 590L, 2, 3.4f),
    IntroSlot(0.13f, 0.78f, 0.27f, -6f, 1.2f, -1.0f, 0.8f, 14.dp, 660L, 3, 4.5f),
    IntroSlot(0.60f, 0.92f, 0.19f, 4f, 0.8f, 0.4f, 1.0f, 12.dp, 730L, 1, 5.6f),
)

/** Urutan gambar: yang di belakang dulu, hero di tengah, dua cover depan terakhir. */
private val IntroOrder = intArrayOf(1, 2, 4, 0, 3, 5)

/** Kolase artwork nyata dari data Replay: satu hero di tengah, lima pendamping. */
@Composable
private fun IntroCollage(
    summary: ReplaySummary,
    drift: () -> Float,
    pageOffset: () -> Float,
    motion: Boolean,
    capturing: Boolean,
    modifier: Modifier = Modifier,
) {
    val covers = remember(summary) {
        (summary.songs.mapNotNull { it.song.thumbnailUrl } + summary.albums.mapNotNull { it.artworkUrl })
            .distinct()
            .take(IntroSlots.size)
    }
    if (covers.isEmpty()) return
    BoxWithConstraints(modifier) {
        val w = maxWidth
        val h = maxHeight
        val unit = minOf(w, h * 1.08f)
        IntroOrder.forEach { index ->
            val url = covers.getOrNull(index) ?: return@forEach
            IntroCover(
                url = url,
                slot = IntroSlots[index],
                w = w,
                h = h,
                unit = unit,
                drift = drift,
                pageOffset = pageOffset,
                motion = motion,
                capturing = capturing,
            )
        }
    }
}

@Composable
private fun IntroCover(
    url: String,
    slot: IntroSlot,
    w: Dp,
    h: Dp,
    unit: Dp,
    drift: () -> Float,
    pageOffset: () -> Float,
    motion: Boolean,
    capturing: Boolean,
) {
    val enter = remember { Animatable(0f) }
    LaunchedEffect(motion, capturing) {
        if (!motion || capturing) {
            enter.snapTo(1f)
            return@LaunchedEffect
        }
        delay(slot.delayMs)
        // Hero masuk terakhir dengan pegas lembut; pendamping hampir tanpa pantulan.
        enter.animateTo(
            1f,
            if (slot.hero) spring(0.68f, Spring.StiffnessLow) else spring(1f, 130f),
        )
    }
    val size = unit * slot.size
    val shape = RoundedCornerShape(slot.corner)
    Cover(
        url = url,
        fallbackText = "",
        size = size,
        shape = shape,
        px = CARD_ART_PX,
        elevated = true,
        modifier = Modifier
            .offset(x = w * slot.cx - size / 2, y = h * slot.cy - size / 2)
            .graphicsLayer {
                val e = enter.value
                val back = 1f - e
                val t = drift() * IntroTau * slot.freq + slot.phase
                alpha = (e * 2.2f).coerceIn(0f, 1f)
                val s = 0.80f + 0.20f * e
                scaleX = s
                scaleY = s
                translationX = back * slot.fromX * 140.dp.toPx() + pageOffset() * 60.dp.toPx() * slot.depth
                translationY = back * slot.fromY * 140.dp.toPx() + sin(t) * 3.dp.toPx()
                rotationZ = slot.angle * (0.5f + 0.5f * e) + back * slot.fromX * 10f + sin(t * 0.7f) * 0.5f
            }
            .border(0.6.dp, Color.White.copy(alpha = 0.16f), shape),
    )
}

// ── 02 · Menit mendengarkan ─────────────────────────────────────────────────

@Composable
private fun ColumnScope.MinutesStory(
    summary: ReplaySummary,
    headline: List<HeadlineRun>,
    reveal: Animatable<Float, *>,
    drift: () -> Float,
    pageOffset: () -> Float,
) {
    val context = LocalContext.current
    // Semua angka berasal dari ringkasan Replay; tidak ada nilai tetap.
    val top = buildList {
        add(grouped(summary.hours) to stringResource(R.string.replay_stat_hours))
        add(formatListening(context, summary.averageMsPerDay()) to stringResource(R.string.replay_stat_per_day))
        summary.busiestDay?.let { add(formatDay(context, it) to stringResource(R.string.replay_stat_busiest)) }
    }
    val bottom = listOf(
        grouped(summary.totalPlays.toLong()) to stringResource(R.string.replay_stat_plays),
        grouped(summary.distinctSongs.toLong()) to stringResource(R.string.replay_stat_songs),
        grouped(summary.distinctArtists.toLong()) to stringResource(R.string.replay_stat_artists),
    )

    // Linimasa (fraksi dari 2600 ms): headline 400-750, label 650-950, hitung
    // 900-1900, statistik 1500-1900 dan seterusnya bergantian.
    BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
        val compact = maxHeight < 560.dp
        Column(Modifier.fillMaxSize()) {
            Spacer(Modifier.height(if (compact) 2.dp else 12.dp))
            MinutesHeadline(
                parts = headline,
                textSize = if (compact) 34 else 44,
                modifier = Modifier.staged(reveal, 0.154f, 0.16f, 22.dp),
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = stringResource(R.string.replay_total_minutes).uppercase(Locale.ROOT),
                fontSize = 12.sp,
                fontWeight = FontWeight.W700,
                letterSpacing = 3.5.sp,
                color = WarmWhite.copy(alpha = 0.78f),
                modifier = Modifier.staged(reveal, 0.25f, 0.115f, 10.dp),
            )
            Spacer(Modifier.height(6.dp))
            MinutesNumber(
                target = summary.minutes,
                reveal = reveal,
                modifier = Modifier
                    .fillMaxWidth()
                    .graphicsLayer {
                        translationX = pageOffset() * this.size.width * 0.30f
                        translationY = sin(drift() * 2f * PI.toFloat() * 3f) * 3.dp.toPx()
                    },
            )
            Spacer(Modifier.weight(1f))
            MinutesStats(listOf(top, bottom), reveal, compact)
        }
    }
}

private val WarmWhite = Color(0xFFFFF4E8)

/** Ease-out kubik: hitungan melambat alami menuju angka akhir. */
private fun easeCubic(t: Float): Float {
    val u = 1f - t.coerceIn(0f, 1f)
    return 1f - u * u * u
}

/** Judul gaya majalah: serif, kata kunci miring tebal, putih hangat. */
@Composable
private fun MinutesHeadline(parts: List<HeadlineRun>, textSize: Int, modifier: Modifier = Modifier) {
    Text(
        text = buildAnnotatedString {
            parts.forEach { run ->
                withStyle(
                    SpanStyle(
                        fontWeight = if (run.bold) FontWeight.W700 else FontWeight.W400,
                        fontStyle = if (run.bold) FontStyle.Italic else FontStyle.Normal,
                        color = if (run.bold) WarmWhite else WarmWhite.copy(alpha = 0.86f),
                    ),
                ) { append(run.text) }
            }
        },
        style = MaterialTheme.typography.displayLarge,
        fontFamily = FontFamily.Serif,
        fontSize = textSize.sp,
        lineHeight = (textSize * 1.08f).sp,
        letterSpacing = (-0.8f).sp,
        modifier = modifier.fillMaxWidth(),
    )
}

/** Angka monumental di atas latar, tanpa kartu; ukuran ditentukan dari lebar angka akhir. */
@Composable
private fun MinutesNumber(target: Long, reveal: Animatable<Float, *>, modifier: Modifier = Modifier) {
    val finalText = remember(target) { grouped(target) }
    BoxWithConstraints(modifier) {
        val digits = finalText.count { it.isDigit() }
        val em = digits * 0.60f + (finalText.length - digits) * 0.30f
        val fontSize = (maxWidth.value / em).coerceIn(48f, 176f).sp
        MinutesCount(
            target = target,
            reveal = reveal,
            fontSize = fontSize,
            modifier = Modifier.graphicsLayer {
                alpha = ease((reveal.value - 0.346f) / 0.06f)
            },
        )
    }
}

/**
 * Hanya composable ini yang rekomposisi saat menghitung, dan hanya ketika
 * bilangan bulatnya berubah. Pada akhir hitungan nilainya persis [target].
 */
@Composable
private fun MinutesCount(
    target: Long,
    reveal: Animatable<Float, *>,
    fontSize: TextUnit,
    modifier: Modifier = Modifier,
) {
    val shown by remember(target) {
        derivedStateOf {
            (target * easeCubic((reveal.value - 0.346f) / 0.385f).toDouble()).toLong().coerceIn(0L, target)
        }
    }
    Text(
        text = grouped(shown),
        style = MaterialTheme.typography.displayLarge.copy(fontFeatureSettings = "tnum"),
        fontSize = fontSize,
        lineHeight = fontSize * 0.95f,
        fontWeight = FontWeight.W800,
        letterSpacing = (fontSize.value * -0.035f).sp,
        color = WarmWhite,
        maxLines = 1,
        softWrap = false,
        modifier = modifier,
    )
}

/** Tiga kolom x dua baris, garis tipis di atas tiap sel, masuk bergantian. */
@Composable
private fun MinutesStats(
    rows: List<List<Pair<String, String>>>,
    reveal: Animatable<Float, *>,
    compact: Boolean,
) {
    Column(verticalArrangement = Arrangement.spacedBy(if (compact) 14.dp else 22.dp)) {
        rows.forEachIndexed { r, cells ->
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                cells.forEachIndexed { c, (value, label) ->
                    MinutesStat(
                        value = value,
                        label = label,
                        compact = compact,
                        modifier = Modifier
                            .weight(1f)
                            .staged(reveal, 0.577f + (r * 3 + c) * 0.02f, 0.12f, 14.dp),
                    )
                }
                repeat(3 - cells.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun MinutesStat(value: String, label: String, compact: Boolean, modifier: Modifier = Modifier) {
    val base = if (compact) 24f else 30f
    // Nilai panjang (mis. "5 November") mengecil sampai muat, bukan terpotong.
    var size by remember(value) { mutableStateOf(base) }
    Column(modifier) {
        Box(Modifier.fillMaxWidth().height(0.7.dp).background(Color.White.copy(alpha = 0.26f)))
        Spacer(Modifier.height(10.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.headlineMedium.copy(fontFeatureSettings = "tnum"),
            fontSize = size.sp,
            lineHeight = (size * 1.1f).sp,
            fontWeight = FontWeight.W800,
            letterSpacing = (-0.5).sp,
            color = WarmWhite,
            maxLines = 1,
            softWrap = false,
            onTextLayout = { if (it.hasVisualOverflow && size > 14f) size *= 0.92f },
        )
        Spacer(Modifier.height(3.dp))
        Text(
            text = label.uppercase(Locale.ROOT),
            fontSize = 10.sp,
            lineHeight = 13.sp,
            fontWeight = FontWeight.W600,
            letterSpacing = 1.6.sp,
            color = Color.White.copy(alpha = 0.62f),
            maxLines = 2,
        )
    }
}

// ── 03 · Artis teratas ──────────────────────────────────────────────────────

/** Potret artis memenuhi layar, dibalut warna latar, zoom sinematik pelan. */
@Composable
private fun ArtistPortraitBackdrop(
    summary: ReplaySummary,
    reveal: Animatable<Float, *>,
    pageOffset: () -> Float,
    motion: Boolean,
) {
    val url = summary.artists.firstOrNull()?.artworkUrl ?: return
    val scale = remember { Animatable(1.14f) }
    LaunchedEffect(motion) {
        if (motion) {
            scale.animateTo(1f, tween(PAGE_MILLIS.toInt() + 4_000, easing = LinearEasing))
        } else {
            scale.snapTo(1.04f)
        }
    }
    Box(Modifier.fillMaxSize()) {
        AsyncImage(
            model = url.artworkAt(HEADER_ART_PX),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            alignment = Alignment.TopCenter,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    val s = scale.value
                    scaleX = s
                    scaleY = s
                    translationX = pageOffset() * this.size.width * 0.12f
                    alpha = 0.30f * ease(reveal.value / 0.5f)
                },
        )
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0f to Color.Black.copy(alpha = 0.25f),
                        0.5f to Color.Transparent,
                        1f to Color.Black.copy(alpha = 0.45f),
                    ),
                ),
        )
    }
}

@Composable
private fun ColumnScope.ArtistsStory(
    summary: ReplaySummary,
    headline: List<HeadlineRun>,
    reveal: Animatable<Float, *>,
    pageOffset: () -> Float,
) {
    val lead = summary.artistRows(1).firstOrNull() ?: return
    val name = lead.title
    val nameSize = when {
        name.length <= 8 -> 84
        name.length <= 14 -> 64
        else -> 46
    }
    val share = if (summary.totalMs > 0) (lead.ms * 100 / summary.totalMs).toInt() else 0
    Kicker(stringResource(R.string.replay_kicker_top_artist), Modifier.staged(reveal, 0f))
    Spacer(Modifier.height(6.dp))
    MaskReveal(reveal, 0.05f) { Headline(headline, textSize = 21) }
    Spacer(Modifier.height(12.dp))
    MaskReveal(reveal, 0.15f, span = 0.55f) {
        Text(
            text = name,
            style = MaterialTheme.typography.displayLarge,
            fontSize = nameSize.sp,
            lineHeight = (nameSize * 0.98f).sp,
            fontWeight = FontWeight.W900,
            letterSpacing = (-2).sp,
            color = Color.White,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth(),
        )
    }
    Spacer(Modifier.height(2.dp))
    Text(
        text = "${formatMinutes(lead.ms)} ${stringResource(R.string.minutes)}",
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.W600,
        color = Color.White.copy(alpha = 0.95f),
        modifier = Modifier.staged(reveal, 0.35f),
    )
    if (share > 0) {
        Text(
            text = stringResource(R.string.replay_share_of_listening, share),
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White.copy(alpha = 0.65f),
            modifier = Modifier.staged(reveal, 0.40f),
        )
    }
    val runnerUps = summary.artistRows(5).drop(1)
    if (runnerUps.isNotEmpty()) {
        Spacer(Modifier.height(12.dp))
        RunnerUps(runnerUps, reveal, 0.46f)
    }
    BoxWithConstraints(
        Modifier.weight(1f).fillMaxWidth().padding(top = 14.dp),
        contentAlignment = Alignment.BottomCenter,
    ) {
        val d = minOf(maxWidth * 0.96f, maxHeight)
        Cover(
            url = lead.artworkUrl,
            fallbackText = name,
            size = d,
            shape = CircleShape,
            px = HEADER_ART_PX,
            modifier = Modifier
                .popped(reveal, 0.25f, 0.6f, 0.86f)
                .graphicsLayer { translationX = pageOffset() * this.size.width * 0.25f }
                .border(1.dp, Color.White.copy(alpha = 0.25f), CircleShape),
        )
    }
}

// ── 04 · Lagu teratas ───────────────────────────────────────────────────────

@Composable
private fun ColumnScope.SongsStory(
    summary: ReplaySummary,
    headline: List<HeadlineRun>,
    reveal: Animatable<Float, *>,
) {
    val context = LocalContext.current
    val rows = summary.songRows(STORY_ROWS)
    val lead = rows.firstOrNull() ?: return
    MaskReveal(reveal, 0f) { Headline(headline, textSize = 32) }
    Spacer(Modifier.height(14.dp))
    BoxWithConstraints(Modifier.weight(3f).fillMaxWidth()) {
        val d = minOf(maxHeight, maxWidth * 0.55f)
        val numberSize = minOf(d.value * 0.36f, 64f)
        Column(
            Modifier.align(Alignment.CenterStart).width(maxWidth - d - 12.dp).staged(reveal, 0.15f),
        ) {
            Text(
                text = "01",
                fontSize = numberSize.sp,
                lineHeight = numberSize.sp,
                fontWeight = FontWeight.W900,
                letterSpacing = (-2).sp,
                color = Color.White.copy(alpha = 0.95f),
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = lead.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.W800,
                color = Color.White,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
            lead.subtitle?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.75f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                text = listOf(
                    context.replayCount(lead.plays, R.plurals.replay_play_count),
                    formatListening(context, lead.ms),
                ).joinToString(" · "),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.W700,
                color = Color.White.copy(alpha = 0.9f),
            )
        }
        Cover(
            url = lead.artworkUrl,
            fallbackText = lead.title,
            size = d,
            shape = RoundedCornerShape(14.dp),
            px = HEADER_ART_PX,
            modifier = Modifier.align(Alignment.CenterEnd).popped(reveal, 0.10f, 0.55f, 0.8f),
        )
    }
    Spacer(Modifier.height(12.dp))
    rows.drop(1).forEachIndexed { index, row ->
        SongRowItem(
            row = row,
            indent = if (index % 2 == 0) 0.dp else 28.dp,
            reveal = reveal,
            start = 0.40f + index * 0.09f,
            modifier = Modifier.weight(1f).fillMaxWidth(),
        )
    }
}

@Composable
private fun SongRowItem(
    row: ReplayRow,
    indent: Dp,
    reveal: Animatable<Float, *>,
    start: Float,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    BoxWithConstraints(modifier.staged(reveal, start, 0.4f, 30.dp), contentAlignment = Alignment.CenterStart) {
        val art = maxHeight * 0.84f
        Row(Modifier.padding(start = indent), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "%02d".format(row.rank),
                fontFamily = FontFamily.Serif,
                fontStyle = FontStyle.Italic,
                fontSize = 26.sp,
                fontWeight = FontWeight.W700,
                color = Color.White.copy(alpha = 0.85f),
                modifier = Modifier.width(44.dp),
            )
            Cover(
                url = row.artworkUrl,
                fallbackText = row.title,
                size = art,
                shape = RoundedCornerShape(8.dp),
                px = ROW_ART_PX,
                modifier = Modifier.popped(reveal, start + 0.05f, 0.4f, 0.7f),
            )
            Spacer(Modifier.width(12.dp))
            Column {
                Text(
                    text = row.title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.W700,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = listOfNotNull(
                        row.subtitle,
                        context.replayCount(row.plays, R.plurals.replay_play_count),
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.White.copy(alpha = 0.62f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

// ── 05 · Album teratas ──────────────────────────────────────────────────────

@Composable
private fun ColumnScope.AlbumsStory(
    summary: ReplaySummary,
    headline: List<HeadlineRun>,
    reveal: Animatable<Float, *>,
    drift: () -> Float,
    pageOffset: () -> Float,
) {
    val context = LocalContext.current
    val rows = summary.albumRows(STORY_ROWS)
    val lead = rows.firstOrNull() ?: return
    MaskReveal(reveal, 0f) { Headline(headline, textSize = 32) }
    BoxWithConstraints(Modifier.weight(1.9f).fillMaxWidth().padding(top = 12.dp)) {
        val d = minOf(maxHeight, maxWidth * 0.70f)
        Cover(
            url = lead.artworkUrl,
            fallbackText = lead.title,
            size = d,
            shape = RoundedCornerShape(16.dp),
            px = HEADER_ART_PX,
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .popped(reveal, 0.08f, 0.6f, 0.84f)
                .graphicsLayer {
                    translationX = pageOffset() * this.size.width * 0.20f
                    translationY = sin(drift() * 2f * PI.toFloat() * 3f) * 5.dp.toPx()
                },
        )
        Text(
            text = "01",
            fontSize = (d.value * 0.42f).sp,
            fontWeight = FontWeight.W900,
            letterSpacing = (-3).sp,
            color = Color.White,
            modifier = Modifier.align(Alignment.BottomStart).staged(reveal, 0.22f, 0.5f, 40.dp),
        )
    }
    Spacer(Modifier.height(12.dp))
    Text(
        text = lead.title,
        style = MaterialTheme.typography.headlineMedium,
        fontFamily = FontFamily.Serif,
        fontSize = 26.sp,
        lineHeight = 30.sp,
        fontWeight = FontWeight.W800,
        color = Color.White,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.staged(reveal, 0.35f),
    )
    Text(
        text = listOfNotNull(
            lead.subtitle,
            context.replayCount(lead.plays, R.plurals.replay_play_count),
            formatListening(context, lead.ms),
        ).joinToString(" · "),
        style = MaterialTheme.typography.bodyMedium,
        color = Color.White.copy(alpha = 0.72f),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.staged(reveal, 0.40f),
    )
    Spacer(Modifier.height(14.dp))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        rows.drop(1).take(3).forEachIndexed { index, row ->
            Column(
                Modifier
                    .weight(1f)
                    .offset(y = if (index % 2 == 0) 0.dp else 12.dp)
                    .staged(reveal, 0.50f + index * 0.09f, 0.4f, 30.dp),
            ) {
                Text(
                    text = "%02d".format(row.rank),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.W700,
                    letterSpacing = 1.5.sp,
                    color = Color.White.copy(alpha = 0.7f),
                )
                Spacer(Modifier.height(4.dp))
                Cover(
                    url = row.artworkUrl,
                    fallbackText = row.title,
                    size = 72.dp,
                    shape = RoundedCornerShape(10.dp),
                    px = ROW_ART_PX,
                )
                Spacer(Modifier.height(5.dp))
                Text(
                    text = row.title,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.W700,
                    color = Color.White,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = context.replayCount(row.plays, R.plurals.replay_play_count),
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.55f),
                )
            }
        }
    }
}

// ── 06 · Kepribadian musik ──────────────────────────────────────────────────

@Composable
private fun ColumnScope.PersonalityStory(
    summary: ReplaySummary,
    headline: List<HeadlineRun>,
    colors: List<Color>,
    reveal: Animatable<Float, *>,
    drift: () -> Float,
) {
    val context = LocalContext.current
    val persona = remember(summary) { summary.personality() }
    val behaviors = remember(summary, persona) { summary.behaviorLines(context, persona) }
    val genres = remember(summary) { summary.genres.take(3).joinToString("  ·  ") { it.title } }

    Kicker(stringResource(R.string.replay_kicker_personality), Modifier.staged(reveal, 0f))
    SoundOrb(
        hours = summary.hourOfDay,
        colors = colors,
        reveal = reveal,
        drift = drift,
        modifier = Modifier.weight(1.4f).fillMaxWidth(),
    )
    MaskReveal(reveal, 0.30f) {
        Text(
            text = persona.title(context),
            style = MaterialTheme.typography.displayMedium,
            fontSize = 40.sp,
            lineHeight = 42.sp,
            fontWeight = FontWeight.W900,
            letterSpacing = (-1).sp,
            color = Color.White,
        )
    }
    Spacer(Modifier.height(6.dp))
    Text(
        text = persona.description(context),
        style = MaterialTheme.typography.bodyMedium,
        color = Color.White.copy(alpha = 0.80f),
        modifier = Modifier.staged(reveal, 0.42f),
    )
    if (genres.isNotEmpty()) {
        Spacer(Modifier.height(10.dp))
        GenreBars(summary, reveal, 0.52f)
    }
    Spacer(Modifier.height(10.dp))
    behaviors.take(1).forEachIndexed { index, line ->
        Row(
            Modifier.padding(vertical = 2.dp).staged(reveal, 0.60f + index * 0.08f, 0.3f, 18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(5.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.8f)))
            Spacer(Modifier.width(10.dp))
            Text(
                text = line,
                style = MaterialTheme.typography.labelLarge,
                color = Color.White.copy(alpha = 0.78f),
            )
        }
    }
}

/**
 * Visualisasi suara melingkar dari jam-jam mendengarkan pengguna: panjang batang
 * mengikuti profil 24 jam, bergelombang pelan, dengan partikel mengorbit.
 */
@Composable
private fun SoundOrb(
    hours: List<Long>,
    colors: List<Color>,
    reveal: Animatable<Float, *>,
    drift: () -> Float,
    modifier: Modifier = Modifier,
) {
    val peak = remember(hours) { (hours.maxOrNull() ?: 0L).coerceAtLeast(1L).toFloat() }
    val accent = colors.getOrElse(1) { Color.White }
    val second = colors.getOrElse(2) { Color.White }
    Canvas(modifier) {
        val grow = ease((reveal.value - 0.05f) / 0.6f)
        val c = center
        val base = minOf(size.width, size.height) / 2f
        val inner = base * 0.34f
        val reach = base * 0.58f * grow
        val turn = drift() * 2f * PI.toFloat()
        val phase = turn * 4f
        drawCircle(
            brush = Brush.radialGradient(listOf(accent.copy(alpha = 0.55f), Color.Transparent), center = c, radius = base),
            radius = base,
            center = c,
        )
        val bars = 90
        for (i in 0 until bars) {
            val t = i / bars.toFloat()
            val a = t * 2f * PI.toFloat() - PI.toFloat() / 2f
            val pos = t * 24f
            val h0 = pos.toInt() % 24
            val h1 = (h0 + 1) % 24
            val f = pos - pos.toInt()
            val p = if (hours.size >= 24) (hours[h0] * (1f - f) + hours[h1] * f) / peak else 0.5f
            val wave = 0.65f + 0.35f * sin(a * 5f + phase)
            val len = (0.12f + 0.88f * p) * reach * wave
            drawLine(
                color = lerp(accent, Color.White, p),
                start = Offset(c.x + cos(a) * inner, c.y + sin(a) * inner),
                end = Offset(c.x + cos(a) * (inner + len), c.y + sin(a) * (inner + len)),
                strokeWidth = 2.6.dp.toPx(),
                cap = StrokeCap.Round,
                alpha = (0.5f + 0.5f * p).coerceIn(0f, 1f),
            )
        }
        drawCircle(Color.White.copy(alpha = 0.08f), inner * 0.9f, c)
        drawCircle(Color.White.copy(alpha = 0.5f), inner * 0.9f, c, style = Stroke(1.dp.toPx()))
        for (k in 0 until 12) {
            val a = turn * (1 + k % 3) + k * 0.52f
            val r = inner + reach * 0.9f + (k % 4) * 6.dp.toPx()
            drawCircle(
                color = second.copy(alpha = 0.75f * grow),
                radius = (1.5f + k % 3).dp.toPx(),
                center = Offset(c.x + cos(a) * r, c.y + sin(a) * r),
            )
        }
    }
}

// ── 07 · Recap akhir ────────────────────────────────────────────────────────

/** Latar hitam dengan glow warna di tepi, seperti ringkasan Replay Apple Music. */
@Composable
private fun RecapGlow(colors: List<Color>) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .drawBehind {
                val w = size.width
                val h = size.height
                fun glow(color: Color, cx: Float, cy: Float, r: Float, alpha: Float) {
                    val c = Offset(cx, cy)
                    drawCircle(
                        brush = Brush.radialGradient(listOf(color.copy(alpha = alpha), Color.Transparent), center = c, radius = r),
                        radius = r,
                        center = c,
                    )
                }
                fun at(i: Int) = colors[i % colors.size]
                glow(at(0), w * 0.5f, 0f, w * 0.95f, 0.95f)
                glow(at(1), 0f, h * 0.35f, w * 0.7f, 0.80f)
                glow(at(2), w, h * 0.55f, w * 0.8f, 0.85f)
                glow(at(3), w, h, w * 0.7f, 0.60f)
            },
    )
}

@Composable
private fun ColumnScope.RecapStory(
    summary: ReplaySummary,
    headline: List<HeadlineRun>,
    reveal: Animatable<Float, *>,
    onShare: () -> Unit,
    onRestart: () -> Unit,
    hideActions: Boolean = false,
) {
    val context = LocalContext.current
    val artists = remember(summary) { summary.artistRows(3) }
    val songs = remember(summary) { summary.songRows(3) }
    val albums = remember(summary) { summary.albumRows(3) }

    Kicker(
        stringResource(R.string.replay_brand),
        Modifier.fillMaxWidth().staged(reveal, 0f),
    )
    Text(
        text = summary.localizedLabel(context),
        style = MaterialTheme.typography.displayMedium,
        fontWeight = FontWeight.W800,
        color = Color.White,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().staged(reveal, 0.04f),
    )
    Text(
        text = "${formatMinutes(summary.totalMs)} ${stringResource(R.string.minutes).lowercase(LocalLocale.current.platformLocale)}" +
            " · ${context.replayCount(summary.totalPlays, R.plurals.replay_play_count)}",
        style = MaterialTheme.typography.titleMedium,
        color = Color.White.copy(alpha = 0.6f),
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().staged(reveal, 0.10f),
    )
    summary.genres.firstOrNull()?.let {
        Text(
            text = stringResource(R.string.top_genre) + " · " + it.title,
            style = MaterialTheme.typography.labelMedium,
            color = Color.White.copy(alpha = 0.5f),
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().staged(reveal, 0.12f),
        )
    }
    Spacer(Modifier.height(10.dp))
    Row(Modifier.weight(1f).fillMaxWidth()) {
        BoxWithConstraints(Modifier.weight(1.2f).fillMaxHeight()) {
            // Layar pendek: subtitle dibuang dan baris dirapatkan supaya
            // ketiga daftar muat tanpa terpotong.
            val roomy = maxHeight >= 400.dp
            // Jumlah baris per daftar mengikuti tinggi yang tersedia, supaya
            // daftar tidak meluber dan tertimpa headline di bawahnya.
            val rowHeight = if (roomy) 42.dp else 24.dp
            val perList = ((maxHeight / 3f - 30.dp) / rowHeight).toInt().coerceIn(1, 3)
            Column(
                Modifier.fillMaxSize().clipToBounds().staged(reveal, 0.18f, 0.5f),
                verticalArrangement = Arrangement.SpaceEvenly,
            ) {
                RecapList(stringResource(R.string.top_artists), artists.take(perList), withSubtitle = false, roomy = roomy)
                RecapList(stringResource(R.string.top_songs), songs.take(perList), withSubtitle = roomy, roomy = roomy)
                RecapList(stringResource(R.string.top_albums), albums.take(perList), withSubtitle = roomy, roomy = roomy)
            }
        }
        BoxWithConstraints(Modifier.weight(0.8f).fillMaxHeight()) {
            val d = minOf(maxWidth - 8.dp, maxHeight / 3.3f)
            val x = (maxWidth - d) / 2f
            val gap = d * 1.12f
            artists.firstOrNull()?.let {
                Cover(it.artworkUrl, it.title, d, CircleShape, CARD_ART_PX,
                    Modifier.offset(x = x).popped(reveal, 0.22f).border(1.dp, Color.White.copy(alpha = 0.3f), CircleShape))
            }
            songs.firstOrNull()?.let {
                Cover(it.artworkUrl, it.title, d, RoundedCornerShape(14.dp), CARD_ART_PX,
                    Modifier.offset(x = x, y = gap).popped(reveal, 0.28f))
            }
            albums.firstOrNull()?.let {
                Cover(it.artworkUrl, it.title, d, RoundedCornerShape(14.dp), CARD_ART_PX,
                    Modifier.offset(x = x, y = gap * 2f).popped(reveal, 0.34f))
            }
        }
    }
    Spacer(Modifier.height(10.dp))
    MaskReveal(reveal, 0.55f, span = 0.4f) {
        Headline(headline, textSize = 24, family = FontFamily.Serif)
    }
    Text(
        text = stringResource(R.string.replay_story_final_sub),
        style = MaterialTheme.typography.bodyMedium,
        color = Color.White.copy(alpha = 0.7f),
        modifier = Modifier.staged(reveal, 0.65f),
    )
    Spacer(Modifier.height(12.dp))
    // Disembunyikan (tetap memakan ruang) saat kartu ditangkap untuk dibagikan.
    val hide = if (hideActions) Modifier.graphicsLayer { alpha = 0f } else Modifier
    PillButton(
        text = stringResource(R.string.share_your_replay),
        filled = true,
        onClick = onShare,
        modifier = Modifier.fillMaxWidth().then(hide).staged(reveal, 0.78f, 0.2f),
    )
    Spacer(Modifier.height(8.dp))
    PillButton(
        text = stringResource(R.string.replay_cta_again),
        filled = false,
        onClick = onRestart,
        modifier = Modifier.fillMaxWidth().then(hide).staged(reveal, 0.84f, 0.16f),
    )
}

@Composable
private fun RecapList(title: String, rows: List<ReplayRow>, withSubtitle: Boolean, roomy: Boolean = true) {
    if (rows.isEmpty()) return
    Column {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.W800,
            color = Color.White,
        )
        rows.forEach { row ->
            Row(Modifier.padding(top = if (roomy) 5.dp else 2.dp)) {
                Text(
                    text = row.rank.toString(),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.W700,
                    color = Color.White.copy(alpha = 0.45f),
                    modifier = Modifier.width(22.dp),
                )
                Column {
                    Text(
                        text = row.title,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.W600,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    val sub = row.subtitle
                    if (withSubtitle && sub != null) {
                        Text(
                            text = sub,
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White.copy(alpha = 0.5f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

// ── Potongan umum ───────────────────────────────────────────────────────────

@Composable
private fun Cover(
    url: String?,
    fallbackText: String,
    size: Dp,
    shape: Shape,
    px: Int,
    modifier: Modifier = Modifier,
    elevated: Boolean = false,
) {
    val base = modifier
        .size(size)
        .then(
            if (elevated || size >= 120.dp) {
                Modifier.shadow(20.dp, shape, clip = false, ambientColor = Color.Black, spotColor = Color.Black)
            } else {
                Modifier
            },
        )
        .clip(shape)
    when {
        url != null -> AsyncImage(
            model = url.artworkAt(px),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = base,
        )
        fallbackText.isNotBlank() -> Box(base) { InitialTile(fallbackText, size, shape) }
        else -> Box(base.background(Color.White.copy(alpha = 0.10f)))
    }
}

// ── Kebiasaan mendengarkan ──────────────────────────────────────────────────

/** Jam puncak, hari tersibuk, jumlah putaran, dan profil 24 jam, semuanya dari data nyata. */
@Composable
private fun ColumnScope.HabitsStory(
    summary: ReplaySummary,
    headline: List<HeadlineRun>,
    reveal: Animatable<Float, *>,
) {
    val context = LocalContext.current
    val persona = remember(summary) { summary.personality() }
    val peak = summary.peakHour
    val stats = buildList {
        peak?.let { add(formatHour(context, it) to stringResource(R.string.replay_stat_peak)) }
        add(formatListening(context, summary.averageMsPerDay()) to stringResource(R.string.replay_stat_per_day))
        summary.busiestDay?.let { add(formatDay(context, it) to stringResource(R.string.replay_stat_busiest)) }
        add(grouped(summary.totalPlays.toLong()) to stringResource(R.string.replay_stat_plays))
        add(grouped(summary.distinctSongs.toLong()) to stringResource(R.string.replay_stat_songs))
        add(grouped(summary.distinctArtists.toLong()) to stringResource(R.string.replay_stat_artists))
        if (summary.distinctAlbums > 0) {
            add(grouped(summary.distinctAlbums.toLong()) to stringResource(R.string.replay_stat_albums))
        }
        add("${(persona.nightShare * 100f).toInt()}%" to stringResource(R.string.replay_stat_night))
        add(String.format(Locale.US, "%.1f", persona.repeatRatio) to stringResource(R.string.replay_stat_repeat))
    }.take(9)

    Kicker(stringResource(R.string.replay_kicker_habits), Modifier.staged(reveal, 0f))
    Spacer(Modifier.height(6.dp))
    MaskReveal(reveal, 0.05f) { Headline(headline, textSize = 28) }
    Spacer(Modifier.height(14.dp))
    Text(
        text = stringResource(R.string.listening_shape).uppercase(Locale.ROOT),
        style = MaterialTheme.typography.labelSmall,
        letterSpacing = 2.sp,
        color = Color.White.copy(alpha = 0.6f),
        modifier = Modifier.staged(reveal, 0.15f),
    )
    Spacer(Modifier.height(8.dp))
    HourChart(
        hours = summary.hourOfDay,
        peak = peak,
        reveal = reveal,
        modifier = Modifier.weight(1f).fillMaxWidth(),
    )
    Row(
        Modifier.fillMaxWidth().padding(top = 4.dp).staged(reveal, 0.30f, 0.3f, 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        listOf("00", "06", "12", "18", "24").forEach {
            Text(
                text = it,
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.55f),
            )
        }
    }
    Spacer(Modifier.height(16.dp))
    StatGrid(stats, reveal, 0.45f)
}

/** 24 batang, satu per jam; jam puncak menyala penuh, lainnya meredup menurut porsinya. */
@Composable
private fun HourChart(
    hours: List<Long>,
    peak: Int?,
    reveal: Animatable<Float, *>,
    modifier: Modifier = Modifier,
) {
    val top = remember(hours) { (hours.maxOrNull() ?: 0L).coerceAtLeast(1L).toFloat() }
    Canvas(modifier) {
        val grow = ease((reveal.value - 0.15f) / 0.6f)
        val n = 24
        val gap = 3.dp.toPx()
        val barWidth = (size.width - gap * (n - 1)) / n
        val minHeight = 3.dp.toPx()
        for (i in 0 until n) {
            val v = if (hours.size >= n) hours[i] / top else 0f
            val h = (minHeight + (size.height - minHeight) * v) * grow
            drawRoundRect(
                color = Color.White.copy(alpha = if (i == peak) 1f else 0.40f + 0.30f * v),
                topLeft = Offset(i * (barWidth + gap), size.height - h),
                size = Size(barWidth, h),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(barWidth / 2f),
            )
        }
    }
}

/** Angka-angka kecil dalam grid tiga kolom, masuk bergantian per baris. */
@Composable
private fun StatGrid(
    stats: List<Pair<String, String>>,
    reveal: Animatable<Float, *>,
    start: Float,
    modifier: Modifier = Modifier,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        stats.chunked(3).forEachIndexed { row, chunk ->
            Row(
                Modifier.fillMaxWidth().staged(reveal, start + row * 0.07f, 0.35f),
                horizontalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                chunk.forEach { (value, label) -> StatCell(value, label, Modifier.weight(1f)) }
                repeat(3 - chunk.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun StatCell(value: String, label: String, modifier: Modifier = Modifier) {
    Column(modifier) {
        Box(Modifier.fillMaxWidth().height(0.8.dp).background(Color.White.copy(alpha = 0.35f)))
        Spacer(Modifier.height(8.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.W800,
            color = Color.White,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = label.uppercase(Locale.ROOT),
            style = MaterialTheme.typography.labelSmall,
            letterSpacing = 2.sp,
            color = Color.White.copy(alpha = 0.6f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Tiga genre teratas dengan porsi dari seluruh waktu genre yang tercatat. */
@Composable
private fun GenreBars(summary: ReplaySummary, reveal: Animatable<Float, *>, start: Float) {
    val rows = remember(summary) { summary.genres.take(3) }
    val total = remember(summary) { summary.genres.sumOf { it.ms }.coerceAtLeast(1L) }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        rows.forEachIndexed { index, genre ->
            val share = (genre.ms.toFloat() / total).coerceIn(0f, 1f)
            Row(
                Modifier.fillMaxWidth().staged(reveal, start + index * 0.07f, 0.3f, 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = genre.title,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.W700,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.width(96.dp),
                )
                Box(
                    Modifier
                        .weight(1f)
                        .height(6.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.20f)),
                ) {
                    Box(
                        Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(share.coerceAtLeast(0.03f))
                            .clip(CircleShape)
                            .background(Color.White),
                    )
                }
                Text(
                    text = "${(share * 100f).toInt()}%",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.W700,
                    color = Color.White.copy(alpha = 0.85f),
                    textAlign = TextAlign.End,
                    modifier = Modifier.width(40.dp),
                )
            }
        }
    }
}

/** Artis peringkat 2 sampai 5, bundar kecil di bawah nama artis teratas. */
@Composable
private fun RunnerUps(rows: List<ReplayRow>, reveal: Animatable<Float, *>, start: Float) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        rows.take(4).forEachIndexed { index, row ->
            Column(
                Modifier.weight(1f).staged(reveal, start + index * 0.06f, 0.3f, 18.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Cover(
                    url = row.artworkUrl,
                    fallbackText = row.title,
                    size = 44.dp,
                    shape = CircleShape,
                    px = ROW_ART_PX,
                    modifier = Modifier.border(0.8.dp, Color.White.copy(alpha = 0.25f), CircleShape),
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "%02d".format(row.rank),
                    style = MaterialTheme.typography.labelSmall,
                    letterSpacing = 1.5.sp,
                    color = Color.White.copy(alpha = 0.6f),
                )
                Text(
                    text = row.title,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.W700,
                    color = Color.White,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** Judul "Replay'xx" dan logo, hanya digambar saat kartu ditangkap untuk dibagikan. */
@Composable
private fun CaptureBrand(label: String) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(top = 24.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = if (label.length == 4 && label.all { it.isDigit() }) {
                "Replay'${label.takeLast(2)}"
            } else {
                "Replay · $label"
            },
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.W800,
            color = Color.White,
            modifier = Modifier.weight(1f),
        )
        Icon(
            painter = painterResource(R.drawable.ic_logo),
            contentDescription = null,
            tint = Color.White.copy(alpha = 0.92f),
            modifier = Modifier.size(width = 22.dp, height = 14.dp).offset(y = 2.dp),
        )
        Spacer(Modifier.width(3.dp))
        Text(
            text = "Audify",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.W700,
            color = Color.White.copy(alpha = 0.92f),
        )
    }
}

/** [this] with every colour turned [degrees] around the wheel, tone untouched. */
private fun MeshPalette.rotated(degrees: Float): MeshPalette {
    if (degrees == 0f) return this
    return MeshPalette(
        colors.map { color ->
            val hsl = FloatArray(3)
            ColorUtils.colorToHSL(color.toArgb(), hsl)
            hsl[0] = (hsl[0] + degrees) % 360f
            Color(ColorUtils.HSLToColor(hsl))
        },
    )
}

/** Ruang yang dipakai chrome (progress + header) di atas konten kartu. */
private val CHROME_HEIGHT = 68.dp

/** How far down a chart a card goes. */
private const val STORY_ROWS = 5

/** The shape of a story card, everywhere. */
private const val STORY_ASPECT = 9f / 16f

/** How long a card holds before moving on, unless a finger is on the screen. */
private const val PAGE_MILLIS = 14_000f

/** How long a tap or auto-advance takes to slide from one card to the next. */
private const val STEP_MILLIS = 420

private val GrainLight = Color.White.copy(alpha = 0.06f)
private val GrainDark = Color.Black.copy(alpha = 0.09f)

/** Posisi dasar empat gumpalan warna backdrop, sebagai pecahan layar. */
private val BlobAnchors = listOf(0.15f to 0.12f, 0.88f to 0.30f, 0.80f to 0.88f, 0.10f to 0.75f)

/** Fixed film grain: x, y as fractions of the card, then the speck's radius in dp. */
private val GrainDots: List<Triple<Float, Float, Float>> = run {
    val random = Random(26)
    List(420) { Triple(random.nextFloat(), random.nextFloat(), 0.4f + random.nextFloat() * 0.9f) }
}

/** The share of the width that means "back" — the left edge, as everywhere else. */
private const val BACK_ZONE = 0.32f
