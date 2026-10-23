package com.sachit.music.ui.component

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.core.content.FileProvider
import com.sachit.music.R
import com.sachit.music.models.MediaMetadata
import java.io.File
import java.io.FileOutputStream
import android.widget.Toast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Plan 030 helpers for rendering a lyric card to a PNG bitmap and sharing it via the system share sheet. */

fun buildLyricsCardBitmap(
    context: Context,
    mediaMetadata: MediaMetadata,
    lyricLine: String,
    targetWidthPx: Int = 1080,
    backgroundColor: Int = 0xFF121212.toInt(),
    textColor: Int = -0x1000000,
    secondaryTextColor: Int = -0x12000000,
): Bitmap? = try {
    val density = context.resources.displayMetrics.density.toFloat()
    val cornerRadiusPx = (24 * density).toInt()
    val cardHeightPx = ((targetWidthPx * 1.4f).toInt()).coerceAtLeast(720)
    val coverArtSizePx = (72 * density).toInt()
    val paddingPx = (48 * density).toInt()
    val footerHeightPx = (64 * density).toInt()
    val headerPaddingBottomPx = (16 * density).toInt()
    val bitmap = Bitmap.createBitmap(targetWidthPx, cardHeightPx, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val path = Path().apply {
        addRoundRect(
            RectF(0f, 0f, targetWidthPx.toFloat(), cardHeightPx.toFloat()),
            cornerRadiusPx.toFloat(),
            cornerRadiusPx.toFloat(),
            Path.Direction.CW,
        )
    }
    canvas.drawPath(path, Paint().apply {
        isAntiAlias = true
        color = backgroundColor
    })
    canvas.drawPath(path, Paint().apply {
        isAntiAlias = true
        color = 0xFF808080.toInt()
        style = Paint.Style.FILL
    })
    canvas.drawCircle(
        (paddingPx + coverArtSizePx / 2f).toFloat(),
        (paddingPx + coverArtSizePx / 2f).toFloat(),
        (coverArtSizePx / 2f).toFloat(),
        Paint().apply {
            isAntiAlias = true
            color = 0xFF404040.toInt()
        },
    )
    val line = lyricLine.ifBlank { context.getString(R.string.lyrics_card_empty) }
    val title = mediaMetadata.title.ifBlank { line }
    val titlePaint = Paint().apply {
        isAntiAlias = true
        color = textColor
        textSize = (22 * density).toFloat()
        textAlign = Paint.Align.LEFT
        typeface = android.graphics.Typeface.DEFAULT_BOLD
    }
    val titleX = (paddingPx + coverArtSizePx + 16).toFloat()
    val titleY = (paddingPx + coverArtSizePx + headerPaddingBottomPx + (titlePaint.textSize / 2)).toFloat()
    canvas.drawText(title, titleX, titleY, titlePaint)
    val artistPaint = Paint().apply {
        isAntiAlias = true
        color = secondaryTextColor
        textSize = (16 * density).toFloat()
        textAlign = Paint.Align.LEFT
    }
    val artistY = (paddingPx + coverArtSizePx + headerPaddingBottomPx + titlePaint.textSize * 0.6f).toFloat()
    canvas.drawText(
        mediaMetadata.artists.joinToString { it.name },
        (paddingPx + coverArtSizePx + 16).toFloat(),
        artistY,
        artistPaint,
    )
    val lyricPaint = Paint().apply {
        isAntiAlias = true
        color = textColor
        textSize = (28 * density).toFloat()
        textAlign = Paint.Align.CENTER
        typeface = android.graphics.Typeface.DEFAULT_BOLD
    }
    val lyricY = (cardHeightPx - footerHeightPx - paddingPx - (lyricPaint.textSize / 2)).toFloat()
    canvas.drawText(line, (targetWidthPx / 2).toFloat(), lyricY, lyricPaint)
    val footerIconSize = (48 * density).toInt()
    val footerIconX = (targetWidthPx - paddingPx - footerIconSize).toFloat()
    val footerIconY = (cardHeightPx - paddingPx - footerIconSize).toFloat()
    canvas.drawCircle(footerIconX, footerIconY, (footerIconSize / 2f).toFloat(), Paint().apply {
        isAntiAlias = true
        color = secondaryTextColor
        style = Paint.Style.FILL
    })
    val footerNamePaint = Paint().apply {
        isAntiAlias = true
        color = secondaryTextColor
        textSize = (14 * density).toFloat()
        textAlign = Paint.Align.LEFT
        typeface = android.graphics.Typeface.DEFAULT_BOLD
    }
    val appNamePx = footerNamePaint.measureText(context.getString(R.string.app_name)).toFloat()
    val footerNameX = (footerIconX - 10f - appNamePx).toFloat()
    val footerNameY = footerIconY + footerNamePaint.textSize / 3f
    canvas.drawText(context.getString(R.string.app_name), footerNameX, footerNameY, footerNamePaint)
    bitmap
} catch (e: Throwable) {
    null
}

fun shareLyricsCardBitmap(
    context: Context,
    bitmap: Bitmap,
    title: String,
) {
    val tmpFile = File.createTempFile("lyrics_share_", ".png")
    tmpFile.deleteOnExit()
    tmpFile.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    if (tmpFile.length() <= 0) {
        tmpFile.delete()
        return
    }
    val uri = FileProvider.getUriForFile(
        context,
        "${context.packageName}.shareprovider",
        tmpFile,
    )
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "image/png"
        putExtra(Intent.EXTRA_STREAM, uri)
        putExtra(Intent.EXTRA_TEXT, title)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    val chooser = Intent.createChooser(intent, context.getString(R.string.share_lyrics_as_image))
    val vibrator = context.getSystemService(Vibrator::class.java) ?: return
    vibrator.vibrate(VibrationEffect.createOneShot(20, VibrationEffect.DEFAULT_AMPLITUDE))
    try {
        context.startActivity(chooser)
    } catch (e: android.content.ActivityNotFoundException) {
        Toast.makeText(context, context.getString(R.string.share_lyrics_as_image), Toast.LENGTH_SHORT).show()
    }
}

suspend fun shareLyricsAsImage(
    context: Context,
    mediaMetadata: MediaMetadata,
    lyricLine: String,
) {
    val bitmap = buildLyricsCardBitmap(context, mediaMetadata, lyricLine)
        ?: run {
            Toast.makeText(context, R.string.lyrics_card_empty, Toast.LENGTH_SHORT).show()
            return
        }

    withContext(Dispatchers.Main) {
        shareLyricsCardBitmap(context, bitmap, mediaMetadata.title)
    }
}
