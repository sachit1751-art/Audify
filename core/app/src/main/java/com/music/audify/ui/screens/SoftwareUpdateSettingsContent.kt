package com.music.audify.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.DownloadDone
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.verticalScroll
import com.halilibo.richtext.markdown.Markdown
import com.halilibo.richtext.ui.RichTextStyle
import com.halilibo.richtext.ui.material3.RichText
import com.music.audify.data.AppUpdateChecker
import com.music.audify.data.AppUpdateChecker.DownloadState
import com.music.audify.data.AppUpdateChecker.LatestReleaseInfo
import com.music.audify.R
import com.music.audify.ui.components.IosSwitch
import java.io.File

/** Theme-aware updater page with distinct available, downloading and verified states. */
@Composable
internal fun SoftwareUpdateSettingsContent(
    currentVersion: String,
    release: LatestReleaseInfo?,
    checking: Boolean,
    downloadState: DownloadState,
    automaticChecks: Boolean,
    prereleaseUpdates: Boolean,
    onCheckOrDownload: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onCancel: () -> Unit,
    onInstall: (File) -> Unit,
    onAutomaticChecksChanged: (Boolean) -> Unit,
    onPrereleaseChanged: (Boolean) -> Unit,
    onOpenReleasePage: () -> Unit,
) {
    var notesExpanded by rememberSaveable(release?.version) { mutableStateOf(true) }
    val colors = MaterialTheme.colorScheme
    val cardShape = RoundedCornerShape(20.dp)
    val isDownloading = downloadState is DownloadState.Downloading
    val isPaused = false
    val ready = downloadState as? DownloadState.Ready
    val failed = downloadState as? DownloadState.Failed
    val available = release?.updateAvailable == true
    val progress = when (val state = downloadState) {
        is DownloadState.Downloading -> state.fraction
        else -> null
    }
    val statusColor = when {
        ready != null || available -> colors.primary
        failed != null -> colors.error
        else -> colors.tertiary
    }
    val statusTitle = when {
        checking -> "Checking for updates…"
        isDownloading -> "Downloading update…"
        ready != null -> "Ready to install"
        failed != null -> "Download failed"
        available -> "An update is available"
        release != null -> "Your software is up to date"
        else -> "Check for updates"
    }
    val shownVersion = release?.version ?: currentVersion

    Column(
        Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Column(
            Modifier.fillMaxWidth().clip(cardShape)
                .background(colors.surfaceContainerLow)
                .padding(horizontal = 20.dp, vertical = 22.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                Modifier.size(88.dp).clip(CircleShape).background(statusColor.copy(alpha = 0.13f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = when {
                        ready != null -> Icons.Rounded.CheckCircle
                        isDownloading -> Icons.Rounded.Download
                        available -> Icons.Rounded.Download
                        else -> Icons.Rounded.DownloadDone
                    },
                    contentDescription = null,
                    tint = statusColor,
                    modifier = Modifier.size(38.dp),
                )
            }
            Text(
                text = statusTitle,
                modifier = Modifier.padding(top = 16.dp),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )
            Text(
                text = stringResource(
                    if (release?.isPrerelease == true) R.string.latest_beta_release else R.string.latest_stable_release,
                    shownVersion,
                ),
                modifier = Modifier.padding(top = 6.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = colors.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            if (ready != null) {
                Text(
                    "Update verified · ${formatFileSize(ready.file.length())} ready to install",
                    modifier = Modifier.padding(top = 8.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            } else if (release?.apkUrl == null && available) {
                Text(
                    "This release has no compatible APK. Open the release page for details.",
                    modifier = Modifier.padding(top = 8.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }

            if (progress != null) {
                Column(Modifier.fillMaxWidth().padding(top = 22.dp)) {
                    LinearProgressIndicator(
                        progress = { progress.coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth().height(6.dp).clip(CircleShape),
                        color = colors.primary,
                        trackColor = colors.surfaceContainerHighest,
                    )
                    Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("${(progress * 100).toInt()}% downloaded", style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant)
                        Text(if (isPaused) "Paused" else "Downloading", style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant)
                    }
                }
            }
            failed?.let {
                Text(it.message, Modifier.padding(top = 10.dp), style = MaterialTheme.typography.bodySmall, color = colors.error, textAlign = TextAlign.Center)
            }

            when {
                ready != null -> Button(
                    onClick = { onInstall(ready.file) },
                    modifier = Modifier.fillMaxWidth().padding(top = 20.dp).height(50.dp),
                    shape = CircleShape,
                ) {
                    Icon(Icons.Rounded.Refresh, contentDescription = null)
                    Spacer(Modifier.size(8.dp))
                    Text("Install update", fontWeight = FontWeight.SemiBold)
                }
                isDownloading || isPaused -> Row(
                    Modifier.fillMaxWidth().padding(top = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Button(
                        onClick = if (isPaused) onResume else onPause,
                        modifier = Modifier.weight(1f).height(48.dp),
                        shape = CircleShape,
                    ) {
                        Icon(if (isPaused) Icons.Rounded.PlayArrow else Icons.Rounded.Pause, contentDescription = null)
                        Spacer(Modifier.size(6.dp))
                        Text(if (isPaused) "Resume" else "Pause")
                    }
                    OutlinedButton(onClick = onCancel, modifier = Modifier.height(48.dp), shape = CircleShape) { Text("Cancel") }
                }
                else -> Button(
                    onClick = onCheckOrDownload,
                    enabled = !checking,
                    modifier = Modifier.fillMaxWidth().padding(top = 20.dp).height(50.dp),
                    shape = CircleShape,
                ) {
                    Icon(if (available || failed != null) Icons.Rounded.Download else Icons.Rounded.Refresh, contentDescription = null)
                    Spacer(Modifier.size(8.dp))
                    Text(
                        when {
                            checking -> "Checking…"
                            failed != null -> "Try again"
                            available -> if (release.apkUrl == null) "Open release page" else "Download update"
                            else -> "Check for update"
                        },
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
            if (available && release.apkUrl == null) {
                Text(
                    "Open release page",
                    Modifier.padding(top = 8.dp).clip(CircleShape).clickable(onClick = onOpenReleasePage).padding(8.dp),
                    color = colors.primary,
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        }

        if (release != null) {
            Column(Modifier.fillMaxWidth().clip(cardShape).background(colors.surfaceContainerLow)) {
                Row(
                    Modifier.fillMaxWidth().clickable { notesExpanded = !notesExpanded }.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Rounded.DownloadDone, contentDescription = null, tint = colors.primary, modifier = Modifier.size(22.dp))
                    Column(Modifier.weight(1f).padding(start = 12.dp)) {
                        Text("What’s new in v${release.version}", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                        Text("Release notes", style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                    }
                    Icon(Icons.Rounded.ExpandMore, contentDescription = if (notesExpanded) "Collapse release notes" else "Expand release notes", tint = colors.onSurfaceVariant)
                }
                if (notesExpanded) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                            .heightIn(max = 300.dp)
                            .clipToBounds()
                            .verticalScroll(rememberScrollState()),
                    ) {
                        androidx.compose.runtime.CompositionLocalProvider(androidx.compose.material3.LocalContentColor provides colors.onSurface) {
                            RichText(style = RichTextStyle.Default) {
                                Markdown(release.notes?.takeIf(String::isNotBlank) ?: "No release notes were published for this version.")
                            }
                        }
                    }
                    Row(
                        Modifier.fillMaxWidth().clickable(onClick = onOpenReleasePage).padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("View full changelog on GitHub", Modifier.weight(1f), color = colors.primary, style = MaterialTheme.typography.labelLarge)
                        Icon(Icons.AutoMirrored.Rounded.OpenInNew, contentDescription = null, tint = colors.primary, modifier = Modifier.size(18.dp))
                    }
                }
            }
        }

        Text("UPDATE PREFERENCES", Modifier.padding(start = 4.dp, top = 2.dp), style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
        Column(Modifier.fillMaxWidth().clip(cardShape).background(colors.surfaceContainerLow).padding(horizontal = 16.dp)) {
            PreferenceToggle(
                title = stringResource(R.string.automatic_update_checks_title),
                subtitle = stringResource(R.string.automatic_update_checks_subtitle),
                checked = automaticChecks,
                onCheckedChange = onAutomaticChecksChanged,
            )
            PreferenceToggle(
                title = stringResource(R.string.prerelease_updates_title),
                subtitle = stringResource(
                    if (prereleaseUpdates) R.string.prerelease_updates_enabled_subtitle
                    else R.string.prerelease_updates_disabled_subtitle,
                ),
                checked = prereleaseUpdates,
                onCheckedChange = onPrereleaseChanged,
            )
        }
        Text(
            "Updates are downloaded and verified automatically when enabled. Installation always requires your confirmation.",
            Modifier.padding(horizontal = 4.dp),
            style = MaterialTheme.typography.bodySmall,
            color = colors.onSurfaceVariant,
        )
    }
}

@Composable
private fun PreferenceToggle(title: String, subtitle: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable { onCheckedChange(!checked) }.padding(vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f).padding(end = 12.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Medium)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        IosSwitch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

private fun formatFileSize(bytes: Long): String = when {
    bytes >= 1024L * 1024L -> "${"%.1f".format(bytes / (1024f * 1024f))} MB"
    bytes >= 1024L -> "${bytes / 1024L} KB"
    else -> "$bytes B"
}
