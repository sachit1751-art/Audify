package com.music.audify.ui.screens

import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.height
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.FirstBaseline
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import com.music.audify.sharedui.resources.*
import kotlin.math.roundToInt
import org.jetbrains.compose.resources.painterResource

/** ic_logo's viewport, 730 x 484. */
private const val WORDMARK_ASPECT = 730f / 484f

/** The mark's ink ends at 464 of the viewport's 484, so its box hangs a little below it. */
private const val WORDMARK_INK_BOTTOM = 0.96f

/** How far the word is raised against the mark, as a share of the word's own height. */
private const val WORDMARK_TEXT_LIFT = 0.03f

/**
 * The Audify mark with its name beside it.
 *
 * The mark's painted bottom edge sits exactly on the word's baseline: the word
 * is measured first and the mark placed from its baseline, rather than leaving
 * the two to a row's alignment rules, which stopped short by the empty strip
 * under the mark.
 */
@Composable
internal fun Wordmark(
    logoHeight: Dp,
    fontSize: TextUnit,
    color: Color,
    modifier: Modifier = Modifier,
    gap: Dp = 6.dp,
) {
    val gapPx = with(LocalDensity.current) { gap.roundToPx() }
    Layout(
        content = {
            Icon(
                painter = painterResource(Res.drawable.ic_logo),
                contentDescription = null,
                tint = color,
                modifier = Modifier.height(logoHeight).aspectRatio(WORDMARK_ASPECT),
            )
            Text(
                text = "Audify",
                color = color,
                fontSize = fontSize,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
            )
        },
        modifier = modifier,
    ) { measurables, constraints ->
        val loose = constraints.copy(minWidth = 0, minHeight = 0)
        val logo = measurables[0].measure(loose)
        val word = measurables[1].measure(loose)
        val baseline = word[FirstBaseline].let { if (it == androidx.compose.ui.layout.AlignmentLine.Unspecified) word.height else it }
        val inkBottom = (logo.height * WORDMARK_INK_BOTTOM).roundToInt()
        // The mark sits a touch lower than baseline-to-baseline, which reads as the word riding a little higher.
        val lift = (word.height * WORDMARK_TEXT_LIFT).roundToInt()
        val logoY = baseline - inkBottom + lift
        val shift = maxOf(0, -logoY)
        val logoTop = logoY + shift
        val wordTop = shift
        val width = logo.width + gapPx + word.width
        val height = maxOf(word.height + wordTop, logoTop + logo.height)
        layout(width, height) {
            logo.placeRelative(0, logoTop)
            word.placeRelative(logo.width + gapPx, wordTop)
        }
    }
}
