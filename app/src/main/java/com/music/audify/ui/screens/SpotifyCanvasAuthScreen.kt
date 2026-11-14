package com.music.audify.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import com.music.audify.ui.icons.SfIcons
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import com.music.audify.ui.components.FrostedTopBar
import com.music.audify.ui.components.topBarContentPadding
import com.music.audify.ui.components.IosSwitch
import com.music.audify.ui.theme.stableStatusBarInsets
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.music.audify.data.settings.AppSettings
import com.music.audify.R

@Composable
fun SpotifyCanvasAuthScreen(
    onNavigateUp: () -> Unit
) {
    val currentToken by AppSettings.spotifySpdcToken.collectAsStateWithLifecycle()
    val autoHidePlayer by AppSettings.spotifyCanvasAutoHide.collectAsStateWithLifecycle()
    val prioritizeSpotify by AppSettings.prioritizeSpotifyCanvas.collectAsStateWithLifecycle()
    var tokenInput by remember(currentToken) { mutableStateOf(currentToken) }

    val scroll = rememberScrollState()
    val titleFadePx = with(LocalDensity.current) { 64.dp.toPx() }

    // Halaman penuh di atas app: latar, bar atas melayang, dan kartu setelan
    // yang sama dengan halaman Settings.
    Box(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scroll)
                .imePadding()
                .navigationBarsPadding()
                .padding(top = topBarContentPadding(), bottom = 24.dp),
        ) {
            Text(
                text = stringResource(R.string.spotify_canvas_setup_description),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = GROUP_INSET + 4.dp, vertical = 8.dp),
            )

            SettingsGroup {
                Spacer(Modifier.height(12.dp))
                SettingsSubRow(
                    title = stringResource(R.string.spotify_canvas_auto_hide),
                    subtitle = stringResource(R.string.spotify_canvas_auto_hide_subtitle),
                    checked = autoHidePlayer,
                    onCheckedChange = AppSettings::setSpotifyCanvasAutoHide,
                )
                Spacer(Modifier.height(4.dp))
                SettingsSubRow(
                    title = stringResource(R.string.prioritize_spotify_canvas),
                    subtitle = stringResource(R.string.prioritize_spotify_canvas_subtitle),
                    checked = prioritizeSpotify,
                    onCheckedChange = AppSettings::setPrioritizeSpotifyCanvas,
                )
            }

            SettingsGroup(
                header = stringResource(R.string.spdc_token),
                footer = stringResource(R.string.spotify_canvas_setup_steps),
            ) {
                TextField(
                    value = tokenInput,
                    onValueChange = { tokenInput = it },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.fillMaxWidth(),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        disabledContainerColor = Color.Transparent,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        disabledIndicatorColor = Color.Transparent,
                    ),
                )
            }

            Spacer(Modifier.height(28.dp))
            Button(
                onClick = {
                    AppSettings.setSpotifySpdcToken(tokenInput.trim())
                    onNavigateUp()
                },
                shape = CircleShape,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = GROUP_INSET)
                    .height(52.dp),
            ) {
                Text(stringResource(R.string.save))
            }
        }

        FrostedTopBar(
            title = stringResource(R.string.spotify_canvas_setup),
            scrolled = { scroll.value > 24 },
            transparentBackdrop = true,
            artworkPageChrome = true,
            onBack = onNavigateUp,
            floatingTitle = stringResource(R.string.spotify_canvas_setup),
            floatingTitleAlpha = { (1f - scroll.value / titleFadePx).coerceIn(0f, 1f) },
            modifier = Modifier.align(Alignment.TopCenter),
        )
    }
}
