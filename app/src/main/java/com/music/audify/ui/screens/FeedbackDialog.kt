package com.music.audify.ui.screens

import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.HelpOutline
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.PersonSearch
import androidx.compose.material.icons.rounded.SystemUpdateAlt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.music.audify.BuildConfig
import com.music.audify.data.feedback.FeedbackRepository
import com.music.audify.playback.AudioOutputStatus
import com.music.audify.ui.components.IosSwitch
import kotlinx.coroutines.launch

private val BugCategories = listOf("Playback glitch", "Audio Output / DAC", "UI / Display", "Crash or Freeze", "Other")
private val FeatureCategories = listOf("Playback", "Playlists", "Discovery", "Library", "Downloads", "Lyrics", "Appearance", "Accessibility", "Performance", "Android Auto", "Android TV", "Wear OS", "Other")

@Composable
internal fun FeedbackDialog(
    kind: String,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val outputStatus by AudioOutputStatus.current.collectAsStateWithLifecycle()
    val isBug = kind == "bug"
    val categories = if (isBug) BugCategories else FeatureCategories
    var category by rememberSaveable(kind) { mutableStateOf(categories.first()) }
    var summary by rememberSaveable(kind) { mutableStateOf("") }
    var description by rememberSaveable(kind) { mutableStateOf("") }
    var includeDiagnostics by rememberSaveable(kind) { mutableStateOf(isBug) }
    var showDiagnosticPreview by rememberSaveable(kind) { mutableStateOf(false) }
    var showPrivacyInfo by rememberSaveable(kind) { mutableStateOf(false) }
    var screenshotName by rememberSaveable(kind) { mutableStateOf<String?>(null) }
    var submitting by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    fun diagnosticLines(): Map<String, String> = mapOf(
        "app_version" to "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
        "device" to "${Build.MANUFACTURER} ${Build.MODEL}; ${outputStatus.sink} · ${outputStatus.deviceName}; ${AudioOutputStatus.encodingLabel(outputStatus)}",
        "audio_output" to outputStatus.sink,
        "audio_device" to outputStatus.deviceName,
        "audio_sample_rate" to ((outputStatus.actualSampleRateHz ?: outputStatus.sampleRatesHz.firstOrNull())?.let { "$it Hz" } ?: "Not reported"),
        "audio_encoding" to AudioOutputStatus.encodingLabel(outputStatus),
        "audio_transport" to outputStatus.transportType.name,
    )

    val screenshotPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        screenshotName = uri?.let { picked ->
            context.contentResolver.query(picked, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }
                ?: picked.lastPathSegment
        }
    }
    val exportDiagnostics = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
        if (uri != null) runCatching {
            val details = diagnosticLines()
            val text = buildString {
                appendLine("Audify diagnostic bundle")
                appendLine("App: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
                appendLine("Android: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
                appendLine("Device: ${Build.MANUFACTURER} ${Build.MODEL}")
                details.forEach { (key, value) -> appendLine("${key.replace('_', ' ')}: $value") }
                appendLine()
                appendLine("This bundle contains device and audio-route metadata only.")
                appendLine("Android does not let ordinary apps read unrestricted system logcat; no raw system logs or personal listening history are included.")
            }
            context.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { it.write(text) }
                ?: error("Could not write diagnostic bundle")
        }.onFailure { error = it.message ?: "Could not export diagnostics" }
    }

    Dialog(
        onDismissRequest = { if (!submitting) onDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background,
        ) {
            Column(
                modifier = Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = onDismiss, enabled = !submitting) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                    Text("Send feedback", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                    IconButton(onClick = { showPrivacyInfo = true }) {
                        Icon(Icons.AutoMirrored.Rounded.HelpOutline, contentDescription = "About diagnostics")
                    }
                }
                Column(
                    modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Text(
                        if (isBug) "Describe the issue or unexpected audio behavior. Optional device and audio details help us investigate." else "Describe the feature and the problem it would solve. Include enough detail for us to evaluate it.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 18.sp,
                    )

                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("CATEGORY", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.SemiBold)
                        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            categories.forEach { option ->
                                val selected = category == option
                                Surface(
                                    modifier = Modifier.clip(RoundedCornerShape(10.dp)).clickable { category = option },
                                    color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                                    shape = RoundedCornerShape(10.dp),
                                ) {
                                    Row(Modifier.padding(horizontal = 12.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                                        if (selected) Icon(Icons.Rounded.Check, null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer)
                                        Text(option, style = MaterialTheme.typography.labelMedium, color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                                    }
                                }
                            }
                        }
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        OutlinedTextField(
                            value = summary,
                            onValueChange = { summary = it.take(80).replace(Regex("[\\r\\n]"), " ") },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text(if (isBug) "Issue summary *" else "Feature summary *") },
                            placeholder = { Text(if (isBug) "e.g. Playback drops out over USB DAC" else "e.g. Add smart queue filters") },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                        )
                        Text("${summary.length}/80", modifier = Modifier.align(Alignment.End), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        OutlinedTextField(
                            value = description,
                            onValueChange = { description = it.take(6000) },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text(if (isBug) "Description & steps to reproduce *" else "How should it work? *") },
                            placeholder = { Text(if (isBug) "1. What were you doing?\n2. What happened?\n3. What did you expect?" else "Describe the behavior, use case and why it would be useful.") },
                            minLines = 4,
                            maxLines = 7,
                            shape = RoundedCornerShape(12.dp),
                        )
                        Text(if (isBug) "Include what happened versus what you expected." else "Specific use cases are more useful than a one-line request.", modifier = Modifier.align(Alignment.CenterHorizontally), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }

                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant), shape = RoundedCornerShape(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth().clickable { screenshotPicker.launch("image/*") }.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Icon(Icons.Rounded.Image, null, tint = MaterialTheme.colorScheme.primary)
                            Column(Modifier.weight(1f)) {
                                Text(if (screenshotName == null) "Attach screenshot" else screenshotName!!, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(if (screenshotName == null) "Optional image" else "Selected · add it on the issue page after submission", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Text("${if (screenshotName == null) "Choose" else "Change"}", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
                        }
                    }

                    if (isBug) {
                        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant), shape = RoundedCornerShape(14.dp)) {
                            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Icon(Icons.Rounded.PersonSearch, null, tint = MaterialTheme.colorScheme.primary)
                                    Column(Modifier.weight(1f)) {
                                        Text("System logs and diagnostics", style = MaterialTheme.typography.titleSmall)
                                        Text("Device and audio-route details; raw logcat is restricted by Android", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    IosSwitch(checked = includeDiagnostics, onCheckedChange = { includeDiagnostics = it })
                                }
                                if (includeDiagnostics) {
                                    HorizontalRule()
                                    FeedbackDetail("Device", "${Build.MANUFACTURER} ${Build.MODEL} · Android ${Build.VERSION.RELEASE}")
                                    FeedbackDetail("Audify", "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
                                    FeedbackDetail("Audio output", "${outputStatus.sink} · ${outputStatus.deviceName}")
                                    FeedbackDetail("Audio format", "${(outputStatus.actualSampleRateHz ?: outputStatus.sampleRatesHz.firstOrNull())?.let { "$it Hz" } ?: "Not reported"} · ${AudioOutputStatus.encodingLabel(outputStatus)}")
                                    Row(Modifier.fillMaxWidth().clickable { showDiagnosticPreview = !showDiagnosticPreview }, verticalAlignment = Alignment.CenterVertically) {
                                        Text("View diagnostic preview", modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelMedium)
                                        Icon(if (showDiagnosticPreview) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore, null, tint = MaterialTheme.colorScheme.primary)
                                    }
                                    if (showDiagnosticPreview) {
                                        Text(diagnosticLines().entries.joinToString("\n") { "${it.key}: ${it.value}" }, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            }
                        }
                    }

                    error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                    Text("No account, email address or listening history is collected.", modifier = Modifier.fillMaxWidth(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(4.dp))
                }

                Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            submitting = true
                            error = null
                            scope.launch {
                                runCatching {
                                    FeedbackRepository.submit(
                                        kind = kind,
                                        category = category,
                                        title = summary,
                                        description = description,
                                        includeDiagnostics = includeDiagnostics,
                                        diagnosticDetails = diagnosticLines(),
                                        attachmentNote = screenshotName?.let { "Screenshot selected ($it). User will attach it on the issue page." },
                                    )
                                }.onSuccess { receipt ->
                                    onDismiss()
                                    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(receipt.url))) }
                                }.onFailure { error = it.message ?: "Could not submit feedback. Try again." }
                                submitting = false
                            }
                        },
                        enabled = !submitting && summary.trim().length in 4..80 && description.trim().length in 20..6000,
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        shape = CircleShape,
                    ) {
                        Icon(Icons.AutoMirrored.Rounded.Send, null, Modifier.size(16.dp))
                        Text(if (submitting) "Submitting…" else if (isBug) "Submit bug report" else "Submit suggestion", modifier = Modifier.padding(start = 8.dp))
                    }
                    if (isBug) {
                        OutlinedButton(
                            onClick = { exportDiagnostics.launch("audify-diagnostics.txt") },
                            modifier = Modifier.fillMaxWidth().height(44.dp),
                            shape = CircleShape,
                        ) {
                            Icon(Icons.Rounded.SystemUpdateAlt, null, Modifier.size(16.dp))
                            Text("Export diagnostic bundle (.txt)", modifier = Modifier.padding(start = 8.dp))
                        }
                    }
                }
            }
        }
    }

    if (showPrivacyInfo) {
        AlertDialog(
            onDismissRequest = { showPrivacyInfo = false },
            title = { Text("About feedback data") },
            text = {
                Text(
                    if (isBug) "Bug reports are submitted without an Audify account or email address. If enabled, diagnostics include your device, Android version and active audio-route details. Android does not allow ordinary apps to collect unrestricted system logcat. Screenshots are selected here but attached manually on the GitHub issue page."
                    else "Feature suggestions are submitted without an Audify account or email address. A selected screenshot is not uploaded by the app; attach it manually on the GitHub issue page after submission."
                )
            },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = { showPrivacyInfo = false }) { Text("Got it") }
            },
        )
    }
}

@Composable
private fun FeedbackDetail(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun HorizontalRule() {
    Box(Modifier.fillMaxWidth().height(1.dp).background(MaterialTheme.colorScheme.outlineVariant))
}
