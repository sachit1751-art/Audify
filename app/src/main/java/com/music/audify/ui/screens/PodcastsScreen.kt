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
import com.music.audify.ui.components.SearchField
import com.music.audify.ui.components.SHELF_CARD_WIDTH
import kotlinx.coroutines.launch

@Composable
fun PodcastsScreen(
    onPlay: (PodcastEpisodeRef) -> Unit,
    onPlayProvider: (ShelfItem) -> Unit,
    modifier: Modifier = Modifier,
    initialProviderShow: PodcastProviderShow? = null,
) {
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
    var providerSearchResults by remember { mutableStateOf<List<PodcastProviderShow>?>(null) }
    var providerSearchBusy by remember { mutableStateOf(false) }
    var selectedProvider by remember { mutableStateOf<PodcastProviderShow?>(null) }
    var providerEpisodes by remember { mutableStateOf<List<ShelfItem>>(emptyList()) }
    var providerDescription by remember { mutableStateOf<String?>(null) }
    var providerLoading by remember { mutableStateOf(false) }
    var providerLoaded by remember { mutableStateOf(false) }
    var providerLoadError by remember { mutableStateOf<String?>(null) }
    var discoveryLoading by remember { mutableStateOf(true) }
    var discoveryError by remember { mutableStateOf<String?>(null) }
    var libraryLoading by remember { mutableStateOf(true) }
    var libraryError by remember { mutableStateOf<String?>(null) }
    var providerSearchError by remember { mutableStateOf<String?>(null) }
    var rssLoading by remember { mutableStateOf(false) }
    var rssLoadError by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(selected) { episodes = selected?.let(PodcastRepository::episodes).orEmpty() }

    fun openProviderShow(show: PodcastProviderShow) {
        selectedProvider = show
        providerEpisodes = emptyList()
        providerDescription = null
        providerLoadError = null
        providerLoaded = false
        providerLoading = true
        scope.launch {
            YtMusicRepository.podcastPage(show)
                .onSuccess { page ->
                    selectedProvider = page.show
                    providerDescription = page.description
                    providerEpisodes = page.episodes
                    providerLoaded = true
                }
                .onFailure { providerLoadError = it.message ?: "Could not load this podcast" }
            providerLoading = false
        }
    }

    LaunchedEffect(initialProviderShow?.browseId) {
        initialProviderShow?.let(::openProviderShow)
    }
    LaunchedEffect(Unit) {
        kotlinx.coroutines.coroutineScope {
            launch {
                YtMusicRepository.libraryPodcastShows()
                    .onSuccess { providerShows = it }
                    .onFailure { libraryError = it.message ?: "Could not load your YouTube Music podcast library" }
                libraryLoading = false
            }
            launch {
                YtMusicRepository.podcastDiscoveryCategories()
                    .onSuccess { discoveryCategories = it }
                    .onFailure { discoveryError = it.message ?: "Could not load podcast discovery" }
                discoveryLoading = false
            }
        }
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
            SearchField(
                query = searchQuery,
                onQueryChange = { searchQuery = it; providerSearchResults = null },
                placeholder = "Search podcasts",
                onSubmit = {
                    if (searchQuery.isBlank() || providerSearchBusy) return@SearchField
                    providerSearchBusy = true
                    providerSearchError = null
                    scope.launch {
                        YtMusicRepository.searchPodcastShows(searchQuery)
                            .onSuccess { providerSearchResults = it }
                            .onFailure { providerSearchError = it.message ?: "YouTube Music podcast search failed" }
                        providerSearchBusy = false
                    }
                },
            )
            if (providerSearchBusy) Text("Searching…", style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            providerSearchError?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
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
                Text("Public YouTube Music podcast shows · no Premium required", style = MaterialTheme.typography.bodySmall)
            }
            providerSearchResults?.let { results ->
                item { Text("YouTube Music results", style = MaterialTheme.typography.titleLarge) }
                if (results.isEmpty()) item {
                    Text("No verified podcast shows were returned. Ordinary playlists are not shown as podcasts.", style = MaterialTheme.typography.bodySmall)
                } else item {
                    LazyRow(contentPadding = PaddingValues(horizontal = 2.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        items(results, key = { "search:${it.browseId}" }) { show ->
                            PodcastArtworkCard(show.title, show.subtitle, show.thumbnailUrl, onClick = {
                                openProviderShow(show)
                            })
                        }
                    }
                }
            }
            if (discoveryLoading) item {
                Text("Loading podcast discovery…", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            discoveryError?.let { error -> item {
                Text(error, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            } }
            discoveryCategories.forEach { category ->
                val categoryShows = category.shows.filter { searchQuery.isBlank() || it.title.contains(searchQuery, true) || it.subtitle.contains(searchQuery, true) }
                if (categoryShows.isNotEmpty()) {
                    item(key = "discovery:${category.title}") {
                        Column {
                            Text(category.title, style = MaterialTheme.typography.titleLarge)
                            Text("Verified YouTube Music podcast shows", style = MaterialTheme.typography.bodySmall)
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
                                        openProviderShow(show)
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
            if (libraryLoading) item {
                Text("Loading your saved shows…", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else if (libraryError != null) item {
                Text(libraryError.orEmpty(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            } else if (matchingProviderShows.isEmpty()) item {
                Text("No saved YouTube Music podcast shows. Public discovery and RSS subscriptions remain available without Premium.", style = MaterialTheme.typography.bodySmall)
            } else item {
                LazyRow(contentPadding = PaddingValues(horizontal = 2.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    items(matchingProviderShows, key = { "ytm:" + it.browseId }) { show ->
                        PodcastArtworkCard(
                            title = show.title,
                            subtitle = show.subtitle,
                            artwork = show.thumbnailUrl,
                            onClick = {
                                openProviderShow(show)
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
                    TextButton(onClick = {
                        selectedProvider = null
                        providerEpisodes = emptyList()
                        providerDescription = null
                        providerLoadError = null
                        providerLoaded = false
                    }) { Text("Back to podcasts") }
                    TextButton(onClick = {
                        selectedProvider?.let(::openProviderShow)
                    }, enabled = !providerLoading) { Text(if (providerLoading) "Loading…" else "Reload") }
                }
                val providerShow = selectedProvider
                if (!providerShow?.thumbnailUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = providerShow?.thumbnailUrl,
                        contentDescription = providerShow?.title,
                        modifier = Modifier.size(196.dp).clip(RoundedCornerShape(12.dp)),
                        contentScale = ContentScale.Crop,
                    )
                }
                Text(providerShow?.title.orEmpty(), style = MaterialTheme.typography.headlineLarge)
                if (!providerShow?.subtitle.isNullOrBlank()) {
                    Text(providerShow?.subtitle.orEmpty(), style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                providerDescription?.takeIf(String::isNotBlank)?.let {
                    Text(it, style = MaterialTheme.typography.bodyMedium)
                }
                val isSaved = providerShow != null && providerShows.any { it.browseId == providerShow.browseId }
                TextButton(enabled = providerShow != null && !busy, onClick = {
                    val show = providerShow ?: return@TextButton
                    busy = true
                    scope.launch {
                        YtMusicRepository.setPodcastShowSaved(show.browseId, saved = !isSaved)
                            .onSuccess {
                                providerShows = if (isSaved) {
                                    providerShows.filterNot { it.browseId == show.browseId }
                                } else {
                                    (providerShows + show).distinctBy(PodcastProviderShow::browseId)
                                }
                                YtMusicRepository.libraryPodcastShows().onSuccess { providerShows = it }
                                message = if (isSaved) "Removed from your YouTube Music library" else "Saved to your YouTube Music library"
                            }
                            .onFailure { message = it.message ?: "Could not update your YouTube Music library" }
                        busy = false
                    }
                }) { Text(if (isSaved) "Remove from YouTube Music library" else "Save to YouTube Music library") }
                Text("Saving requires a signed-in YouTube Music account, not Premium.", style = MaterialTheme.typography.bodySmall)
                providerLoadError?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
                if (providerLoading) Text("Loading show and episodes…", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (providerLoaded && providerEpisodes.isEmpty()) Text("This show currently has no playable episodes.",
                    style = MaterialTheme.typography.bodySmall)
                Text("Episodes", style = MaterialTheme.typography.titleLarge)
            }
            items(providerEpisodes, key = { it.browseId ?: it.videoId ?: it.title + it.subtitle }) { episode ->
                Card(onClick = { if (episode.videoId != null) onPlayProvider(episode) else message = "This YouTube Music row has no playable episode ID." }, modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        if (!episode.thumbnailUrl.isNullOrBlank()) {
                            AsyncImage(model = episode.thumbnailUrl, contentDescription = episode.title,
                                modifier = Modifier.size(64.dp).clip(RoundedCornerShape(8.dp)), contentScale = ContentScale.Crop)
                        }
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text(episode.title, style = MaterialTheme.typography.titleMedium, maxLines = 2)
                            Text(episode.subtitle, style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
                            Text(if (episode.videoId != null) "Play episode" else "Unavailable", style = MaterialTheme.typography.labelSmall)
                        }
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
                        busy = true
                        rssLoading = true
                        rssLoadError = null
                        scope.launch {
                            runCatching { PodcastRepository.refresh(id) }
                                .onSuccess { shows = PodcastRepository.subscribedShows(); episodes = PodcastRepository.episodes(id); message = "Episodes updated" }
                                .onFailure { rssLoadError = it.message ?: "Could not refresh this podcast feed" }
                            rssLoading = false
                            busy = false
                        }
                    }) { Text(if (busy) "Refreshing..." else "Refresh") }
                }
                if (!show?.artworkUrl.isNullOrBlank()) {
                    AsyncImage(model = show?.artworkUrl, contentDescription = show?.title,
                        modifier = Modifier.size(196.dp).clip(RoundedCornerShape(12.dp)), contentScale = ContentScale.Crop)
                }
                Text(show?.title ?: "Podcast", style = MaterialTheme.typography.headlineLarge)
                Text(show?.siteUrl ?: "RSS / Atom podcast", style = MaterialTheme.typography.labelMedium)
                show?.let { currentShow ->
                    TextButton(onClick = { PodcastRepository.unsubscribe(currentShow.id); shows = PodcastRepository.subscribedShows(); selected = null }) {
                        Text("Unfollow podcast")
                    }
                }
                show?.description?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
                message?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                rssLoadError?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Text("Episodes", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 8.dp))
                    TextButton(onClick = { newestFirst = !newestFirst }) { Text(if (newestFirst) "Newest first" else "Oldest first") }
                }
            }
            if (episodes.isEmpty()) item {
                Text(when {
                    rssLoading -> "Refreshing episodes…"
                    rssLoadError != null -> "The feed could not be refreshed. Your saved episode catalogue is unchanged."
                    else -> "This feed currently contains no episodes."
                }, style = MaterialTheme.typography.bodySmall,
                    color = if (rssLoadError != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
            }
            items(if (newestFirst) episodes.sortedByDescending { it.published.orEmpty() } else episodes.sortedBy { it.published.orEmpty() }, key = { it.ref.stableId }) { episode ->
                Card(onClick = { onPlay(episode.ref) }, modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        episode.ref.artworkUri?.let { artwork ->
                            AsyncImage(model = artwork, contentDescription = episode.ref.episodeTitle,
                                modifier = Modifier.size(64.dp).clip(RoundedCornerShape(8.dp)), contentScale = ContentScale.Crop)
                        }
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text(episode.ref.episodeTitle, style = MaterialTheme.typography.titleMedium, maxLines = 2)
                            episode.published?.let { Text(it, style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant) }
                            episode.ref.description?.takeIf(String::isNotBlank)?.let {
                                Text(it.take(180), style = MaterialTheme.typography.bodySmall, maxLines = 2,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Text(when { episode.completed -> "Played"; episode.playbackPositionMs > 0 -> "Resume · ${episode.playbackPositionMs / 60_000} min"; else -> "Play episode" },
                                style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 3.dp))
                        }
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
    Column(modifier = Modifier.width(SHELF_CARD_WIDTH)) {
        Card(onClick = onClick, shape = RoundedCornerShape(12.dp), modifier = Modifier.size(SHELF_CARD_WIDTH)) {
            Box(modifier = Modifier.fillMaxSize()) {
                if (!artwork.isNullOrBlank()) {
                    AsyncImage(
                        model = artwork,
                        contentDescription = title,
                        modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(14.dp)),
                        contentScale = ContentScale.Crop,
                    )
                } else {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = androidx.compose.ui.Alignment.Center) {
                        Text("♫", style = MaterialTheme.typography.displayLarge)
                    }
                }
            }
        }
        Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 1,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 10.dp))
        if (subtitle.isNotBlank()) Text(subtitle, style = MaterialTheme.typography.bodyMedium, maxLines = 1,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
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
