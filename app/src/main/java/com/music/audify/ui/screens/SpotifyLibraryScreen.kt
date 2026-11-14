package com.music.audify.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import com.music.audify.ui.icons.AudifyIcons
import org.jetbrains.compose.resources.painterResource
import com.music.audify.sharedui.resources.spotify_logo
import com.music.audify.sharedui.resources.Res
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.music.audify.R
import com.music.audify.data.settings.AppSettings
import com.music.audify.data.spotify.SpotifyLibrary
import com.music.audify.data.spotify.SpotifyPlaylist
import com.music.audify.data.spotify.SpotifyTrack

/** Hijau merek Spotify: satu-satunya aksen warna di halaman ini. */
private val SpotifyGreen = Color(0xFF1DB954)

/**
 * The signed-in Spotify account's playlists. A tap opens one as an ordinary
 * playlist page ([onOpenPlaylist]) — this screen is only the way in.
 */
@Composable
fun SpotifyLibraryScreen(
    onOpenPlaylist: (SpotifyPlaylist) -> Unit,
    onOpenLogin: () -> Unit,
    onOpenTokenSetup: () -> Unit,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
) {
    val cookie by AppSettings.spotifySpdcToken.collectAsStateWithLifecycle()
    var playlists by remember { mutableStateOf<List<SpotifyPlaylist>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(cookie) {
        if (cookie.isBlank()) {
            playlists = emptyList()
            error = null
            return@LaunchedEffect
        }
        loading = true
        error = null
        runCatching { SpotifyLibrary.playlists() }
            .onSuccess { playlists = it }
            .onFailure { error = it.message }
        loading = false
    }

    val glow = SpotifyGreen.copy(
        alpha = if (MaterialTheme.colorScheme.background.luminance() > 0.5f) 0.16f else 0.30f,
    )
    val liked = playlists.firstOrNull { it.id == SpotifyLibrary.LIKED_ID }
    val rest = playlists.filter { it.id != SpotifyLibrary.LIKED_ID }

    Box(modifier.fillMaxSize()) {
        // Cahaya hijau lembut di puncak halaman: aksen merek tanpa membanjiri layar.
        Box(
            Modifier
                .fillMaxWidth()
                .height(420.dp)
                .background(
                    Brush.verticalGradient(
                        listOf(glow, glow.copy(alpha = glow.alpha * 0.35f), Color.Transparent),
                    ),
                ),
        )
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = contentPadding,
        ) {
            item {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 22.dp),
                ) {
                    Icon(
                        painter = painterResource(Res.drawable.spotify_logo),
                        contentDescription = null,
                        tint = SpotifyGreen,
                        modifier = Modifier.size(44.dp),
                    )
                    Spacer(Modifier.width(14.dp))
                    Text(
                        text = stringResource(R.string.spotify),
                        style = MaterialTheme.typography.displayLarge,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                }
            }
            if (cookie.isBlank()) {
                item {
                    Column(Modifier.padding(horizontal = 20.dp)) {
                        Text(
                            text = stringResource(R.string.spotify_connect_subtitle),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(24.dp))
                        Button(
                            onClick = onOpenLogin,
                            shape = CircleShape,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = SpotifyGreen,
                                contentColor = Color.Black,
                            ),
                            modifier = Modifier.fillMaxWidth().height(52.dp),
                        ) {
                            Text(
                                text = stringResource(R.string.spotify_sign_in),
                                style = MaterialTheme.typography.titleMedium,
                            )
                        }
                        Spacer(Modifier.height(10.dp))
                        OutlinedButton(
                            onClick = onOpenTokenSetup,
                            shape = CircleShape,
                            modifier = Modifier.fillMaxWidth().height(52.dp),
                        ) {
                            Text(
                                text = stringResource(R.string.spotify_use_spdc_token),
                                style = MaterialTheme.typography.titleMedium,
                            )
                        }
                    }
                }
            } else {
                if (loading) {
                    item {
                        Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = SpotifyGreen)
                        }
                    }
                }
                error?.let { message ->
                    item {
                        Text(
                            text = message,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                        )
                    }
                }
                if (!loading && error == null && playlists.isEmpty()) {
                    item {
                        Text(
                            text = stringResource(R.string.spotify_empty_playlists),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                        )
                    }
                }
                liked?.let { item ->
                    item(key = "liked") {
                        LikedFeaturedCard(
                            playlist = item,
                            onClick = { onOpenPlaylist(item) },
                        )
                        Spacer(Modifier.height(26.dp))
                    }
                }
                items(rest.chunked(2), key = { row -> row.first().id }) { row ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp)
                            .padding(bottom = 24.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        row.forEach { item ->
                            PlaylistTile(
                                title = item.name,
                                subtitle = item.owner.orEmpty(),
                                imageUrl = item.imageUrl,
                                onClick = { onOpenPlaylist(item) },
                                modifier = Modifier.weight(1f),
                            )
                        }
                        if (row.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

/**
 * Liked Songs sebagai kartu utama: sampul besar berbayang di kiri, judul dan
 * sumbernya di tengah, tombol bulat hijau di ujung kanan sebagai penunjuk buka.
 */
@Composable
private fun LikedFeaturedCard(
    playlist: SpotifyPlaylist,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(24.dp)
    val ink = MaterialTheme.colorScheme.onBackground
    Row(
        modifier = Modifier
            .padding(horizontal = 20.dp)
            .fillMaxWidth()
            .clip(shape)
            .background(ink.copy(alpha = 0.06f))
            .border(1.dp, ink.copy(alpha = 0.08f), shape)
            .clickable(onClick = onClick)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        AsyncImage(
            model = playlist.imageUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(78.dp)
                .shadow(12.dp, RoundedCornerShape(18.dp))
                .clip(RoundedCornerShape(18.dp)),
        )
        Column(Modifier.weight(1f)) {
            Text(
                text = playlist.name,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = playlist.owner ?: stringResource(R.string.spotify),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
        Box(
            modifier = Modifier.size(38.dp).clip(CircleShape).background(SpotifyGreen),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = AudifyIcons.ChevronRight,
                contentDescription = null,
                tint = Color.Black,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

/**
 * Satu playlist di grid dua kolom: sampul persegi besar berbayang lembut, judul
 * di bawahnya rata kiri, lalu pemilik dengan warna samar.
 */
@Composable
private fun PlaylistTile(
    title: String,
    subtitle: String,
    imageUrl: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(18.dp)
    Column(modifier.clickable(onClick = onClick)) {
        AsyncImage(
            model = imageUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .shadow(14.dp, shape)
                .border(1.dp, MaterialTheme.colorScheme.onBackground.copy(alpha = 0.06f), shape),
        )
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 12.dp),
        )
        if (subtitle.isNotBlank()) {
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
