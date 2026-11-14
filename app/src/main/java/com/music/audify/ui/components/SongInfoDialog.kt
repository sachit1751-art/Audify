package com.music.audify.ui.components

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.music.audify.R
import com.music.audify.data.CreditKind
import com.music.audify.data.SongInfo
import com.music.audify.data.SongInfoRepository
import com.music.audify.data.compactCount
import com.music.audify.data.innertube.Innertube
import com.music.audify.data.model.Song
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Refresh
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

private val INFO_BODY_MAX_HEIGHT = 360.dp

/** The release date in the app's language ("3 Mei 2021"), not the raw ISO form. */
private fun localizedReleaseDate(raw: String): String = runCatching {
    LocalDate.parse(raw).format(
        DateTimeFormatter.ofLocalizedDate(FormatStyle.LONG)
            .withLocale(Locale.forLanguageTag(Innertube.currentLocaleTag)),
    )
}.getOrDefault(raw)

/**
 * Drawn as the same UIAlertController-style card as [AlertScaffold] — centred
 * title, hairline [AlertRule]s, full-width [AlertAction]s — rather than a
 * Material [AlertDialog], so it reads as one of the app's own alerts.
 *
 * Kept in a window [Dialog] because it opens from the song actions menu, which
 * can sit inside the player's own window; an in-tree overlay would be drawn
 * underneath it. A
 * separate window can't sample the app's haze, so the card is [alertGlass]
 * instead, which samples the app backdrop, and is the plain surface only under
 * "reduce dynamic blur".
 */
@Composable
internal fun SongInfoDialog(
    song: Song,
    backdrop: com.music.audify.ui.components.backdrop.Backdrop? = null,
    onDismiss: () -> Unit,
) {
    // Window Dialog tidak bisa melihat haze/halaman di belakangnya, dan
    // LocalAppBackdrop merekam halaman utama (bukan player yang sedang terbuka).
    // Pakai backdrop menu yang membukanya; di luar menu tetap backdrop app.
    val menuBackdrop = backdrop ?: LocalMenuBackdrop.current ?: LocalAppBackdrop.current
    CompositionLocalProvider(LocalAppBackdrop provides menuBackdrop) {
        SongInfoDialogContent(song, onDismiss)
    }
}

@Composable
private fun SongInfoDialogContent(song: Song, onDismiss: () -> Unit) {
    var retry by remember(song.videoId) { mutableIntStateOf(0) }
    val info by produceState<SongInfo?>(null, song, retry) {
        value = null
        value = SongInfoRepository.load(song)
    }
    val onSurface = MaterialTheme.colorScheme.onSurface

    val motion = rememberMacOSPopupState(onClosed = onDismiss)
    val dialogWindow = (androidx.compose.ui.platform.LocalView.current.parent
        as? androidx.compose.ui.window.DialogWindowProvider)?.window

    Dialog(
        onDismissRequest = motion::requestClose,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        // The scrim below fades with the card; the platform's flat dim would snap in.
        androidx.compose.runtime.SideEffect { dialogWindow?.setDimAmount(0f) }
        // Window layar penuh, bukan seukuran kartu: kalau window menyusut
        // mengikuti kartu, posisi kartu di window-nya 0,0 dan kaca mengambil
        // gambar latar dari pojok kiri atas, bukan dari tempat kartu berada.
        Box(
            Modifier
                .fillMaxSize()
                .macOSPopupScrim(motion, SCRIM_COLOR)
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() },
                    onClick = motion::requestClose,
                ),
            contentAlignment = Alignment.Center,
        ) {
        Box(
            Modifier.clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
                onClick = {},
            ),
        ) {
        Column(
            Modifier
                .width(ALERT_WIDTH)
                .macOSPopupMotion(motion)
                .jellyPopIn(animateIn = false)
                .clip(RoundedCornerShape(ALERT_CORNER))
                .alertGlass(RoundedCornerShape(ALERT_CORNER))
                .touchGlow()
                // Credits and values arrive after the card is up; it eases to its new
                // height instead of jumping.
                .animateContentSize(
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioNoBouncy,
                        stiffness = Spring.StiffnessMediumLow,
                    ),
                ),
        ) {
            Column(Modifier.fillMaxWidth().macOSPopupContent(motion)) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 19.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = stringResource(R.string.song_info),
                    style = MaterialTheme.typography.bodyLarge.copy(fontSize = 17.sp, fontWeight = FontWeight.W600),
                    color = onSurface,
                    textAlign = TextAlign.Center,
                )
                Text(
                    text = song.title,
                    modifier = Modifier.padding(top = 4.dp),
                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp, lineHeight = 17.sp),
                    color = onSurface,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (song.artist.isNotBlank()) {
                    Text(
                        text = song.artist,
                        style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp, lineHeight = 17.sp),
                        color = onSurface.copy(alpha = 0.6f),
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            AlertRule()
            Column(
                Modifier
                    .heightIn(max = INFO_BODY_MAX_HEIGHT)
                    .verticalScroll(rememberScrollState()),
            ) {
                val unavailable = stringResource(R.string.song_info_unavailable)
                val rows = listOf(
                    R.string.song_info_album to (info?.album ?: song.albumName),
                    R.string.song_info_release_date to (info?.releaseDate?.let(::localizedReleaseDate) ?: info?.releaseYear),
                    R.string.song_info_views to info?.views?.let(::compactCount),
                    R.string.song_info_likes to info?.likes,
                    R.string.song_info_comments to info?.comments,
                )
                rows.forEachIndexed { index, (label, value) ->
                    if (index > 0) AlertRule(Modifier.padding(start = 16.dp))
                    SongInfoRow(
                        label = stringResource(label),
                        value = value ?: unavailable.takeIf { info != null },
                    )
                }
                val credits = info?.credits.orEmpty()
                if (credits.isNotEmpty()) {
                    AlertRule(Modifier.padding(start = 16.dp))
                    Text(
                        text = stringResource(R.string.song_info_credits),
                        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 4.dp),
                        style = MaterialTheme.typography.bodyLarge.copy(fontSize = 20.sp, fontWeight = FontWeight.W700),
                        color = onSurface,
                    )
                    credits.forEachIndexed { index, credit ->
                        if (index > 0) AlertRule(Modifier.padding(horizontal = 16.dp))
                        SongCreditBlock(
                            label = stringResource(creditLabel(credit.kind)),
                            text = credit.names.joinToString(if (credit.kind == CreditKind.SAMPLE) "; " else ", ") + ".",
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                }
                if (info?.failed == true) {
                    Text(
                        text = stringResource(R.string.song_info_error),
                        modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 10.dp),
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp, lineHeight = 15.sp),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }

            AlertRule()
            if (info?.failed == true) {
                AlertAction(label = stringResource(R.string.song_info_retry), emphasised = false, onClick = { retry++ })
                AlertRule()
            }
            AlertAction(label = stringResource(R.string.done), emphasised = true, onClick = motion::requestClose)
            }
        }
        }
        }
    }
}

private fun creditLabel(kind: CreditKind): Int = when (kind) {
    CreditKind.WRITERS -> R.string.song_info_writers
    CreditKind.PRODUCERS -> R.string.song_info_producers
    CreditKind.ADDITIONAL_VOCALS -> R.string.song_info_additional_vocals
    CreditKind.SAMPLE -> R.string.song_info_sample
}

/** One group of credits: its role in small grey type, the names under it. */
@Composable
private fun SongCreditBlock(label: String, text: String) {
    val onSurface = MaterialTheme.colorScheme.onSurface
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp),
            color = onSurface.copy(alpha = 0.6f),
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp, lineHeight = 20.sp),
            color = onSurface,
        )
    }
}

/** Label on the left, value on the right; a small spinner stands in for the value while it loads. */
@Composable
private fun SongInfoRow(label: String, value: String?) {
    val onSurface = MaterialTheme.colorScheme.onSurface
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = ACTION_HEIGHT)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp),
            color = onSurface.copy(alpha = 0.6f),
        )
        Spacer(Modifier.width(12.dp))
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) {
            if (value == null) {
                CircularProgressIndicator(
                    modifier = Modifier.size(14.dp),
                    color = onSurface.copy(alpha = 0.5f),
                    strokeWidth = 1.75.dp,
                )
            } else {
                Text(
                    text = value,
                    style = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp),
                    color = onSurface,
                    textAlign = TextAlign.End,
                )
            }
        }
    }
}
