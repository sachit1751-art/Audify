package com.music.audify.ui.replay

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.layout.ContentScale
import coil3.compose.AsyncImage
import com.music.audify.data.model.CARD_ART_PX
import com.music.audify.data.model.artworkAt
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.music.audify.sharedui.resources.*
import com.music.audify.ui.player.MeshGradientBackground
import com.music.audify.ui.player.rememberArtworkColors
import java.util.Locale

/**
 * One headline number, set the way Apple Music sets a Replay tile: a square
 * on a soft gradient taken from the artwork, the label small at the top, the
 * artwork as a rounded thumbnail opposite it, and the figure very large at
 * the foot. Tapping it opens Replay at the chart it summarises.
 *
 * The gradient is the player's own mesh, drawn once and held still, so a row
 * of these costs one blurred layer each rather than a redraw per frame.
 */
@Composable
fun ReplayCreditCard(
    label: String,
    value: String,
    detail: String?,
    artworkUrl: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val artUrl = artworkUrl.artworkAt(CARD_ART_PX)
    val palette = rememberArtworkColors(artworkUrl)
    Box(
        modifier = modifier
            .aspectRatio(CARD_RATIO)
            .clip(CardShape)
            .clickable(onClick = onClick),
    ) {
        MeshGradientBackground(
            palette = palette,
            trackKey = artworkUrl ?: label,
            blurRadius = 34.dp,
            animated = false,
        )
        // A light veil so white type holds on any colour, a little deeper at the foot.
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0.0f to Color.Black.copy(alpha = 0.10f),
                        0.55f to Color.Black.copy(alpha = 0.14f),
                        1.0f to Color.Black.copy(alpha = 0.38f),
                    ),
                ),
        )

        Column(Modifier.fillMaxSize().padding(14.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelLarge,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.W600,
                    color = Color.White.copy(alpha = 0.92f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (artUrl != null) {
                    Spacer(Modifier.width(12.dp))
                    AsyncImage(
                        model = artUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(32.dp)
                            .shadow(4.dp, RoundedCornerShape(8.dp), clip = false)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color.White.copy(alpha = 0.12f)),
                    )
                }
            }
            Spacer(Modifier.weight(1f))
            val short = value.length <= 6
            Text(
                text = value,
                style = MaterialTheme.typography.headlineLarge.copy(
                    fontSize = if (short) 34.sp else 20.sp,
                    lineHeight = if (short) 36.sp else 24.sp,
                    fontWeight = FontWeight.W700,
                    letterSpacing = if (short) (-0.8).sp else (-0.2).sp,
                ),
                color = Color.White,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (detail != null) {
                Text(
                    text = detail,
                    style = MaterialTheme.typography.bodyMedium,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.W500,
                    color = Color.White.copy(alpha = 0.80f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
    }
}

/**
 * A rank badge. Number one is the accent; the rest are quiet, because a chart
 * where every position shouts has no first place.
 */
@Composable
fun RankBadge(rank: Int, accent: Color, modifier: Modifier = Modifier) {
    Text(
        text = rank.toString(),
        style = if (rank == 1) {
            MaterialTheme.typography.titleLarge
        } else {
            MaterialTheme.typography.titleMedium
        },
        fontWeight = FontWeight.W800,
        color = if (rank == 1) accent else Color.White.copy(alpha = 0.45f),
        modifier = modifier.width(28.dp),
    )
}

/**
 * A stand-in cover for a row that has no artwork — a genre, which is a word
 * rather than a release. Its initial on a colour derived from the word itself,
 * so the same genre is the same colour every time the page is opened.
 */
@Composable
fun InitialTile(text: String, size: Dp, shape: Shape) {
    val hue = (text.hashCode().toFloat() % 360f + 360f) % 360f
    val color = Color.hsl(hue, 0.55f, 0.45f)
    Box(
        Modifier
            .size(size)
            .clip(shape)
            .background(Brush.linearGradient(listOf(color, color.copy(alpha = 0.55f)))),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text.take(1).uppercase(Locale.ROOT),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.W800,
            color = Color.White,
        )
    }
}

private val CardShape = RoundedCornerShape(16.dp)

/** A compact, slightly wide tile: minimal footprint on the Library page. */
private const val CARD_RATIO = 1.35f
