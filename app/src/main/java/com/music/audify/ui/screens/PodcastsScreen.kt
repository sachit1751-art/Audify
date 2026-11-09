// Copyright (c) 2026 Sachit. All rights reserved.
// Project: AUDIFY
package com.music.audify.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import coil3.compose.AsyncImage
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.music.audify.data.podcast.PodcastEpisode
import com.music.audify.data.podcast.PodcastRepository
import com.music.audify.data.podcast.PodcastShow
import com.music.audify.data.YtMusicRepository
import com.music.audify.data.model.PodcastProviderShow
import com.music.audify.data.model.PodcastDiscoveryCategory
import com.music.audify.data.model.ShelfItem
import com.music.audify.playback.PodcastEpisodeRef
import kotlinx.coroutines.launch

@Composable
fun PodcastsScreen(onPlay: (PodcastEpisodeRef) -> Unit, onPlayProvider: (ShelfItem) -> Unit, modifier: Modifier = Modifier) {
    var feedUrl by remember { mutableStateOf("") }
    var searchQuery by remember { mutableStateOf("") }
    var shows by remember { mutableStateOf(PodcastRepository.subscribedShows()) }
    var selected by remember { mutableStateOf<String?>(null) }
    var newestFirst by remember { mutableStateOf(true) }
    var episodes by remember { mutableStateOf(emptyList<PodcastEpisode>()) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var providerShows by remember { mutableStateOf<List<PodcastProviderShow>>(emptyList()) }
    var discoveryCategories by remember { mutableStateOf<List<PodcastDiscoveryCategory>>(emptyList()) }
    var selectedProvider by remember { mutableStateOf<PodcastProviderShow?>(null) }
    var providerEpisodes by remember { mutableStateOf<List<ShelfItem>>(emptyList()) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(selected) { episodes = selected?.let(PodcastRepository::episodes).orEmpty() }
    LaunchedEffect(Unit) {
        YtMusicRepository.libraryPodcastShows().onSuccess { providerShows = it }
        YtMusicRepository.podcastDiscoveryCategories().onSuccess { discoveryCategories = it }
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text("Podcasts", style = MaterialTheme.typography.headlineLarge)
            Text("Discover shows and pick up where you left off", style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 3.dp, bottom = 10.dp))
            OutlinedTextField(value = searchQuery, onValueChange = { searchQuery = it },
                modifier = Modifier.fillMaxWidth(), label = { Text("Search your podcasts") },
                placeholder = { Text("Shows, episodes, creators") }, singleLine = true)
            message?.let { Text(it, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 6.dp)) }
        }
        if (selected == null && selectedProvider == null) {
            val matchingShows = shows.filter { searchQuery.isBlank() || it.title.contains(searchQuery, true) || it.description.orEmpty().contains(searchQuery, true) }
            val matchingProviderShows = providerShows.filter { searchQuery.isBlank() || it.title.contains(searchQuery, true) || it.subtitle.contains(searchQuery, true) }
            val continueListening = shows.flatMap { PodcastRepository.episodes(it.id) }
                .filter { it.playbackPositionMs > 0L && !it.completed }
                .filter { searchQuery.isBlank() || it.ref.episodeTitle.contains(searchQuery, true) || it.ref.showTitle.contains(searchQuery, true) }
                .sortedByDescending { it.playbackPositionMs }
            item {
                Text("Discover podcasts", style = MaterialTheme.typography.headlineSmall)
                Text("Shows from YouTube Music and your RSS subscriptions", style = MaterialTheme.typography.bodySmall)
            }
            discoveryCategories.forEach { category ->
                val categoryShows = category.shows.filter { searchQuery.isBlank() || it.title.contains(searchQuery, true) || it.subtitle.contains(searchQuery, true) }
                if (categoryShows.isNotEmpty()) {
                    item(key = "discovery:${category.title}") {
                        Column {
                            Text(category.title, style = MaterialTheme.typography.titleLarge)
                            Text("From YouTube Music search", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    item(key = "discovery-row:${category.title}") {
                        LazyRow(contentPadding = PaddingValues(horizontal = 2.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                            items(categoryShows, key = { "${category.title}:${it.browseId}" }) { show ->
                                PodcastArtworkCard(
                                    title = show.title,
                                    subtitle = show.subtitle,
                                    artwork = show.thumbnailUrl,
                                    onClick = {
                                        selectedProvider = show; busy = true; message = null
                                        scope.launch {
                                            YtMusicRepository.podcastEpisodes(show.browseId)
                                                .onSuccess { providerEpisodes = it }
                                                .onFailure { message = it.message ?: "Could not load episodes" }
                                            busy = false
                                        }
                                    },
                                )
                            }
                        }
                    }
                }
            }
            if (continueListening.isNotEmpty()) {
                item { Text("Continue listening", style = MaterialTheme.typography.titleLarge) }
                item {
                    LazyRow(contentPadding = PaddingValues(horizontal = 2.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        items(continueListening, key = { "continue:" + it.ref.stableId }) { episode ->
                            Column(Modifier.width(184.dp)) {
                                Card(onClick = { onPlay(episode.ref) }, shape = RoundedCornerShape(14.dp)) {
                                    Box(Modifier.size(184.dp)) {
                                        if (episode.ref.artworkUri != null) AsyncImage(model = episode.ref.artworkUri, contentDescription = episode.ref.episodeTitle,
                                            modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(14.dp)), contentScale = ContentScale.Crop)
                                        else Box(Modifier.fillMaxSize(), contentAlignment = androidx.compose.ui.Alignment.Center) { Text("♫", style = MaterialTheme.typography.displayLarge) }
                                    }
                                }
                                Text(episode.ref.episodeTitle, style = MaterialTheme.typography.titleSmall, maxLines = 2, modifier = Modifier.padding(top = 8.dp))
                                Text("${episode.ref.showTitle} · Resume at ${episode.playbackPositionMs / 60_000} min", style = MaterialTheme.typography.bodySmall, maxLines = 2)
                            }
                        }
                    }
                }
            }
            item { Text("Your YouTube Music shows", style = MaterialTheme.typography.titleLarge) }
            if (matchingProviderShows.isEmpty()) item {
                Text("Sign in to YouTube Music to load your saved podcast shows.", style = MaterialTheme.typography.bodySmall)
            } else item {
                LazyRow(contentPadding = PaddingValues(horizontal = 2.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    items(matchingProviderShows, key = { "ytm:" + it.browseId }) { show ->
                        PodcastArtworkCard(
                            title = show.title,
                            subtitle = show.subtitle,
                            artwork = show.thumbnailUrl,
                            onClick = {
                                selectedProvider = show; busy = true; message = null
                                scope.launch {
                                    YtMusicRepository.podcastEpisodes(show.browseId)
                                        .onSuccess { providerEpisodes = it }
                                        .onFailure { message = it.message ?: "Could not load episodes" }
                                    busy = false
                                }
                            },
                        )
                    }
                }
            }
            item {
                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                    Text("Your subscriptions", style = MaterialTheme.typography.titleLarge)
                    TextButton(onClick = { shows = PodcastRepository.subscribedShows() }) { Text("Refresh list") }
                }
            }
            if (shows.isEmpty()) item { Text("No RSS / Atom subscriptions yet. Add a feed URL above.", style = MaterialTheme.typography.bodyMedium) }
            else item {
                LazyRow(contentPadding = PaddingValues(horizontal = 2.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    items(matchingShows, key = { "rss:" + it.id }) { show ->
                        PodcastArtworkCard(
                            title = show.title,
                            subtitle = "${PodcastRepository.episodes(show.id).size} episodes",
                            artwork = show.artworkUrl,
                            onClick = { selected = show.id },
                        )
                    }
                }
            }
            item {
                Text("Manage subscriptions", style = MaterialTheme.typography.titleMedium)
                OutlinedTextField(value = feedUrl, onValueChange = { feedUrl = it }, modifier = Modifier.fillMaxWidth(),
                    label = { Text("Add an RSS / Atom feed URL") }, placeholder = { Text("https://example.com/feed.xml") }, singleLine = true)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(enabled = !busy && feedUrl.isNotBlank(), onClick = {
                        busy = true; message = null
                        scope.launch {
                            runCatching { PodcastRepository.subscribe(feedUrl) }
                                .onSuccess { feedUrl = ""; selected = it.id; shows = PodcastRepository.subscribedShows(); message = "Subscribed to ${it.title}" }
                                .onFailure { message = it.message ?: "Could not load this feed" }
                            busy = false
                        }
                    }) { Text(if (busy) "Loading…" else "Add podcast") }
                    TextButton(onClick = { shows = PodcastRepository.subscribedShows() }) { Text("Refresh library") }
                }
            }
            items(shows, key = { "manage:" + it.id }) { show ->
                ShowCard(show = show, onOpen = { selected = show.id }, onRefresh = {
                    busy = true; scope.launch {
                        runCatching { PodcastRepository.refresh(show.id) }
                            .onSuccess { shows = PodcastRepository.subscribedShows(); message = "Updated ${it.title}" }
                            .onFailure { message = it.message ?: "Refresh failed" }
                        busy = false
                    }
                }, onRemove = { PodcastRepository.unsubscribe(show.id); shows = PodcastRepository.subscribedShows() })
            }
        } else if (selectedProvider != null) {
            item {
                Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                    TextButton(onClick = { selectedProvider = null; providerEpisodes = emptyList() }) { Text("Back to podcasts") }
                    TextButton(onClick = {
                        val id = selectedProvider?.browseId ?: return@TextButton
                        busy = true
                        scope.launch {
                            YtMusicRepository.podcastEpisodes(id)
                                .onSuccess { providerEpisodes = it }
                                .onFailure { message = it.message ?: "Could not load episodes" }
                            busy = false
                        }
                    }) { Text(if (busy) "Loading..." else "Reload") }
                }
                Text(selectedProvider?.title.orEmpty(), style = MaterialTheme.typography.headlineSmall)
                Text("YouTube Music episodes", style = MaterialTheme.typography.titleLarge)
                message?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            }
            if (providerEpisodes.isEmpty()) item { Text("No episode rows were returned for this show.") }
            items(providerEpisodes, key = { it.browseId ?: it.videoId ?: it.title + it.subtitle }) { episode ->
                Card(onClick = { if (episode.videoId != null) onPlayProvider(episode) else message = "This YouTube Music row has no playable episode ID." }, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp)) {
                        Text(episode.title, style = MaterialTheme.typography.titleMedium)
                        Text(episode.subtitle, style = MaterialTheme.typography.bodySmall)
                        Text(if (episode.videoId != null) "Tap to play" else "Unavailable", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        } else {
            item {
                val show = shows.firstOrNull { it.id == selected }
                Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                    TextButton(onClick = { selected = null }) { Text("All podcasts") }
                    TextButton(onClick = {
                        val id = selected ?: return@TextButton
                        busy = true; scope.launch {
                            runCatching { PodcastRepository.refresh(id) }
                                .onSuccess { shows = PodcastRepository.subscribedShows(); episodes = PodcastRepository.episodes(id); message = "Episodes updated" }
                                .onFailure { message = it.message ?: "Refresh failed" }
                            busy = false
                        }
                    }) { Text(if (busy) "Refreshing..." else "Refresh") }
                }
                if (!show?.artworkUrl.isNullOrBlank()) {
                    AsyncImage(model = show?.artworkUrl, contentDescription = show?.title,
                        modifier = Modifier.fillMaxWidth().height(220.dp).clip(RoundedCornerShape(20.dp)), contentScale = ContentScale.Crop)
                }
                Text(show?.title ?: "Podcast", style = MaterialTheme.typography.headlineMedium)
                Text(show?.siteUrl ?: "RSS / Atom podcast", style = MaterialTheme.typography.labelMedium)
                show?.let { currentShow ->
                    TextButton(onClick = { PodcastRepository.unsubscribe(currentShow.id); shows = PodcastRepository.subscribedShows(); selected = null }) {
                        Text("Unfollow podcast")
                    }
                }
                show?.description?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
                message?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Text("Episodes", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 8.dp))
                    TextButton(onClick = { newestFirst = !newestFirst }) { Text(if (newestFirst) "Newest first" else "Oldest first") }
                }
            }
            if (episodes.isEmpty()) item { Text("No episodes loaded. Refresh this show to fetch its feed.") }
            items(if (newestFirst) episodes.sortedByDescending { it.published.orEmpty() } else episodes.sortedBy { it.published.orEmpty() }, key = { it.ref.stableId }) { episode ->
                Card(onClick = { onPlay(episode.ref) }, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp)) {
                        Text(episode.ref.episodeTitle, style = MaterialTheme.typography.titleMedium)
                        episode.published?.let { Text(it, style = MaterialTheme.typography.labelSmall) }
                        episode.ref.description?.let { Text(it.take(260), style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp)) }
                        Text(when { episode.completed -> "Played"; episode.playbackPositionMs > 0 -> "Resume - ${episode.playbackPositionMs / 60_000} min"; else -> "Tap to play" },
                            style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 6.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun PodcastArtworkCard(
    title: String,
    subtitle: String,
    artwork: String?,
    onClick: () -> Unit,
) {
    Column(modifier = Modifier.width(184.dp)) {
        Card(onClick = onClick, shape = RoundedCornerShape(14.dp)) {
            Box(modifier = Modifier.size(184.dp)) {
                if (!artwork.isNullOrBlank()) {
                    AsyncImage(
                        model = artwork,
                        contentDescription = title,
                        modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(14.dp)),
                        contentScale = ContentScale.Crop,
                    )
                } else {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = androidx.compose.ui.Alignment.Center) {
                        Text("â™«", style = MaterialTheme.typography.displayLarge)
                    }
                }
            }
        }
        Text(title, style = MaterialTheme.typography.titleSmall, maxLines = 2,
            modifier = Modifier.padding(top = 8.dp))
        Text(subtitle, style = MaterialTheme.typography.bodySmall, maxLines = 2)
    }
}

@Composable
private fun ShowCard(show: PodcastShow, onOpen: () -> Unit, onRefresh: () -> Unit, onRemove: () -> Unit) {
    Card(onClick = onOpen, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp)) {
            Text(show.title, style = MaterialTheme.typography.titleMedium)
            show.description?.let { Text(it.take(180), style = MaterialTheme.typography.bodySmall) }
            Text(show.feedUrl, style = MaterialTheme.typography.labelSmall, maxLines = 1)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onRefresh) { Text("Refresh") }
                TextButton(onClick = onRemove) { Text("Unsubscribe") }
                TextButton(onClick = onOpen) { Text("Episodes") }
            }
        }
    }
}
