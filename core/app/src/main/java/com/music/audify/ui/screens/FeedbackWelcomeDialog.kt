package com.music.audify.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowRight
import androidx.compose.material.icons.rounded.BugReport
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material.icons.rounded.WavingHand
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

private const val AUDIFY_GITHUB = "https://github.com/sachit1751-art/Audify"
private const val FEEDBACK_WELCOME_INTERVAL_MS = 8L * 24L * 60L * 60L * 1000L

internal fun isFeedbackWelcomeDue(nowMillis: Long, lastDismissedAtMillis: Long): Boolean =
    lastDismissedAtMillis <= 0L || nowMillis - lastDismissedAtMillis >= FEEDBACK_WELCOME_INTERVAL_MS

@Composable
internal fun FeedbackWelcomeDialog(
    onDismiss: () -> Unit,
    onSuggestFeature: () -> Unit,
    onReportBug: () -> Unit,
) {
    val context = LocalContext.current
    val colors = MaterialTheme.colorScheme

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
        ),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.64f)),
        ) {
            Box(Modifier.fillMaxSize().clickable(onClick = onDismiss))
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 440.dp)
                    .heightIn(max = 780.dp)
                    .padding(horizontal = 4.dp, vertical = 4.dp)
                    .align(Alignment.BottomCenter),
                color = colors.surfaceContainerHigh,
                shape = RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp, bottomStart = 16.dp, bottomEnd = 16.dp),
                tonalElevation = 8.dp,
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp, vertical = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(
                        Modifier
                            .width(40.dp)
                            .height(4.dp)
                            .clip(CircleShape)
                            .background(colors.onSurfaceVariant.copy(alpha = 0.42f)),
                    )
                    Spacer(Modifier.height(16.dp))
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(colors.primary.copy(alpha = 0.12f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Rounded.WavingHand, contentDescription = null, tint = colors.primary, modifier = Modifier.size(28.dp))
                    }
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "Built late at night, shaped by you",
                        style = MaterialTheme.typography.titleLarge.copy(fontSize = 21.sp, lineHeight = 30.sp, fontWeight = FontWeight.Bold),
                        color = colors.onSurface,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "Audify has no tracking and no ads. Just 1 person writing code late at night. Leaving a star or bug report takes 10 seconds and directly keeps me alive.",
                        style = MaterialTheme.typography.bodyMedium.copy(fontSize = 15.sp, lineHeight = 24.sp),
                        color = colors.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "84% of Audify's newest features came directly from listener suggestions",
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 14.sp, lineHeight = 22.sp),
                        color = colors.onSurfaceVariant.copy(alpha = 0.68f),
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(24.dp))
                    FeedbackWelcomeAction(
                        icon = { Icon(Icons.Rounded.Lightbulb, null, tint = colors.primary, modifier = Modifier.size(21.dp)) },
                        iconBackground = colors.primary.copy(alpha = 0.16f),
                        title = "Suggest a feature",
                        subtitle = "Have an idea for audio contr...",
                        onClick = onSuggestFeature,
                    )
                    Spacer(Modifier.height(12.dp))
                    FeedbackWelcomeAction(
                        icon = { Icon(Icons.Rounded.BugReport, null, tint = colors.error, modifier = Modifier.size(21.dp)) },
                        iconBackground = colors.error.copy(alpha = 0.14f),
                        title = "Report a bug",
                        subtitle = "Something not working right...",
                        onClick = onReportBug,
                    )
                    Spacer(Modifier.height(12.dp))
                    FeedbackWelcomeAction(
                        icon = { Icon(Icons.Rounded.Star, null, tint = Color(0xFFFFD166), modifier = Modifier.size(21.dp)) },
                        iconBackground = colors.secondaryContainer.copy(alpha = 0.58f),
                        title = "Star on GitHub",
                        subtitle = "A quick star means the world...",
                        onClick = {
                            runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(AUDIFY_GITHUB))) }
                            onDismiss()
                        },
                    )
                    Spacer(Modifier.height(12.dp))
                    Surface(
                        modifier = Modifier.fillMaxWidth().height(48.dp).clickable(onClick = onDismiss),
                        color = colors.surfaceContainerLow,
                        shape = CircleShape,
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                            Icon(Icons.Rounded.VisibilityOff, null, tint = colors.onSurfaceVariant, modifier = Modifier.size(17.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("I'll keep my thoughts to myself", style = MaterialTheme.typography.labelLarge, color = colors.onSurface)
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "v1.9.3 • Made by Sachit",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                        color = colors.onSurfaceVariant.copy(alpha = 0.6f),
                    )
                    Spacer(Modifier.height(10.dp))
                    Box(Modifier.width(64.dp).height(4.dp).clip(CircleShape).background(colors.onSurface.copy(alpha = 0.22f)))
                }
            }
        }
    }
}

@Composable
private fun FeedbackWelcomeAction(
    icon: @Composable () -> Unit,
    iconBackground: Color,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Surface(
        modifier = Modifier.fillMaxWidth().height(80.dp).clickable(onClick = onClick),
        color = colors.surfaceContainerLow,
        shape = RoundedCornerShape(16.dp),
        tonalElevation = 1.dp,
    ) {
        Row(
            modifier = Modifier.fillMaxSize().padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier.size(44.dp).clip(RoundedCornerShape(13.dp)).background(iconBackground),
                contentAlignment = Alignment.Center,
            ) { icon() }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.Center) {
                Text(title, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold), color = colors.onSurface)
                Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Spacer(Modifier.width(8.dp))
            Icon(Icons.Rounded.ArrowRight, null, tint = colors.onSurfaceVariant, modifier = Modifier.size(20.dp))
        }
    }
}
