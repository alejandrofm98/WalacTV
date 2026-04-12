package com.example.walactv.ui

import android.util.Log
import android.widget.ImageView
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.foundation.focusable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.util.UnstableApi
import androidx.tv.material3.Icon
import androidx.tv.material3.Text
import com.bumptech.glide.Glide
import com.example.walactv.CatalogItem
import com.example.walactv.ComposeMainFragment
import com.example.walactv.ContentKind
import com.example.walactv.StreamOption
import com.example.walactv.anime.*
import com.example.walactv.ui.theme.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// ── Anime browse ────────────────────────────────────────────────────────────

@Composable
internal fun AnimeBrowseContent(fragment: ComposeMainFragment) {
    var currentTab by remember { mutableStateOf(ComposeMainFragment.AnimeTab.ON_AIR) }
    var onAirItems by remember { mutableStateOf<List<AnimeOnAir>>(emptyList()) }
    var latestItems by remember { mutableStateOf<List<LatestEpisode>>(emptyList()) }
    var searchQuery by remember { mutableStateOf("") }
    var searchResults by remember { mutableStateOf<List<AnimeMedia>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var detailSlug by remember { mutableStateOf<String?>(null) }
    val animeScope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        isLoading = true
        AnimeFlvApiClient.getAnimesOnAir().onSuccess { onAirItems = it }.onFailure { Log.e("Anime", "Error loading animes on air", it) }
        isLoading = false
    }

    if (detailSlug != null) {
        AnimeDetailContent(slug = detailSlug!!, onBack = { detailSlug = null }) { slug, episodeNumber ->
            fragment.showAnimeEpisodeServers(slug, episodeNumber, isLoading, { isLoading = it }, animeScope)
        }
        return
    }

    Column(modifier = Modifier.fillMaxSize().background(IptvBackground)) {
        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ComposeMainFragment.AnimeTab.entries.forEach { tab ->
                val isSelected = currentTab == tab
                val label = when (tab) { ComposeMainFragment.AnimeTab.ON_AIR -> "En emision"; ComposeMainFragment.AnimeTab.LATEST -> "Ultimos"; ComposeMainFragment.AnimeTab.SEARCH -> "Buscar" }
                Box(
                    modifier = Modifier.clip(RoundedCornerShape(8.dp))
                        .background(if (isSelected) IptvAccent else IptvSurface)
                        .border(1.dp, if (isSelected) IptvFocusBorder else IptvSurfaceVariant, RoundedCornerShape(8.dp))
                        .clickable { currentTab = tab }.padding(horizontal = 16.dp, vertical = 8.dp),
                ) {
                    Text(label, color = if (isSelected) IptvTextPrimary else IptvTextMuted, fontSize = 14.sp, fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal)
                }
            }
        }
        when (currentTab) {
            ComposeMainFragment.AnimeTab.ON_AIR -> AnimeGridContent(onAirItems, isLoading) { anime -> detailSlug = anime.slug }
            ComposeMainFragment.AnimeTab.LATEST -> AnimeGridLatestContent(latestItems, isLoading) { ep ->
                fragment.showAnimeEpisodeServers(ep.slug.substringBeforeLast("-"), ep.number, isLoading, { isLoading = it }, animeScope)
            }
            ComposeMainFragment.AnimeTab.SEARCH -> AnimeSearchContent(
                query = searchQuery, onQueryChange = { searchQuery = it },
                onSearch = {
                    animeScope.launch {
                        isLoading = true
                        AnimeFlvApiClient.search(searchQuery).onSuccess { searchResults = it.media }.onFailure { errorMessage = it.message }
                        isLoading = false
                    }
                },
                results = searchResults,
                onAnimeClick = { media -> detailSlug = media.slug },
            )
        }
        errorMessage?.let { Text(text = it, color = Color.Red, fontSize = 14.sp, modifier = Modifier.padding(16.dp)) }
    }
}

// ── Anime episode servers ──────────────────────────────────────────────────

@androidx.annotation.OptIn(markerClass = [UnstableApi::class])
internal fun ComposeMainFragment.showAnimeEpisodeServers(
    slug: String,
    episodeNumber: Int,
    isLoading: Boolean,
    setLoading: (Boolean) -> Unit,
    scope: CoroutineScope,
) {
    scope.launch {
        setLoading(true)
        AnimeFlvApiClient.getEpisodeServers(slug, episodeNumber).onSuccess { episodeDetail ->
            val availableServers = episodeDetail.servers.filter { it.embed?.isNotBlank() == true || it.download?.isNotBlank() == true }
            if (availableServers.isEmpty()) {
                Toast.makeText(requireContext(), "No hay servidores disponibles", Toast.LENGTH_SHORT).show()
                return@onSuccess
            }
            withContext(Dispatchers.Main) {
                android.app.AlertDialog.Builder(requireContext())
                    .setTitle("${episodeDetail.title} - Episodio $episodeNumber")
                    .setItems(availableServers.map { it.name }.toTypedArray()) { _, which ->
                        launchAnimePlayer(availableServers[which], episodeDetail.title, episodeNumber, availableServers)
                    }
                    .setNegativeButton("Cancelar", null).create().show()
            }
        }.onFailure { withContext(Dispatchers.Main) { Toast.makeText(requireContext(), "Error al obtener episodio", Toast.LENGTH_SHORT).show() } }
        setLoading(false)
    }
}

@androidx.annotation.OptIn(markerClass = [UnstableApi::class])
internal fun ComposeMainFragment.launchAnimePlayer(server: AnimeServer, animeTitle: String, episodeNumber: Int, allServers: List<AnimeServer>) {
    val embedUrl = server.embed ?: server.download ?: run { Toast.makeText(requireContext(), "Sin URL disponible", Toast.LENGTH_SHORT).show(); return }
    scope.launch {
        withContext(Dispatchers.Main) { Toast.makeText(requireContext(), "Extrayendo video...", Toast.LENGTH_SHORT).show() }
        val token = runCatching { repository.getAccessToken() }.getOrNull()
        if (token != null) VideoExtractorApiClient.authToken = token
        val result = VideoExtractorApiClient.extract(embedUrl)
        withContext(Dispatchers.Main) {
            result.onSuccess { extractResult ->
                playResolvedCatalogItem(CatalogItem(
                    stableId = "anime:$animeTitle:ep$episodeNumber",
                    title = animeTitle,
                    subtitle = "Episodio $episodeNumber [${extractResult.provider}]",
                    description = "Anime - Episodio $episodeNumber - ${extractResult.type}",
                    imageUrl = "", kind = ContentKind.SERIES, group = "Anime", badgeText = "Anime",
                    seriesName = animeTitle, episodeNumber = episodeNumber,
                    streamOptions = listOf(StreamOption(extractResult.provider, extractResult.url, headers = extractResult.headers)),
                ), 0)
            }.onFailure { Toast.makeText(requireContext(), "Error: ${it.message ?: "No se pudo extraer el video"}", Toast.LENGTH_LONG).show() }
        }
    }
}

// ── Anime grid screens ─────────────────────────────────────────────────────

@Composable
private fun AnimeGridContent(items: List<AnimeOnAir>, isLoading: Boolean, onClick: (AnimeOnAir) -> Unit) {
    if (isLoading) { Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("Cargando...", color = IptvTextMuted, fontSize = 16.sp) }; return }
    LazyVerticalGrid(columns = GridCells.Adaptive(minSize = 160.dp), contentPadding = PaddingValues(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        items(items) { anime -> AnimeCard(title = anime.title, type = anime.type) { onClick(anime) } }
    }
}

@Composable
private fun AnimeGridLatestContent(items: List<LatestEpisode>, isLoading: Boolean, onClick: (LatestEpisode) -> Unit) {
    if (isLoading) { Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("Cargando...", color = IptvTextMuted, fontSize = 16.sp) }; return }
    LazyVerticalGrid(columns = GridCells.Adaptive(minSize = 160.dp), contentPadding = PaddingValues(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        items(items) { ep -> AnimeCard(title = "${ep.title} - Ep. ${ep.number}", type = "Episodio", coverUrl = ep.cover) { onClick(ep) } }
    }
}

@Composable
private fun AnimeSearchContent(query: String, onQueryChange: (String) -> Unit, onSearch: () -> Unit, results: List<AnimeMedia>, onAnimeClick: (AnimeMedia) -> Unit) {
    Column(modifier = Modifier.fillMaxSize()) {
        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(
                modifier = Modifier.weight(1f).height(48.dp).clip(RoundedCornerShape(8.dp)).background(IptvSurface).border(1.dp, IptvSurfaceVariant, RoundedCornerShape(8.dp)).padding(horizontal = 16.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                BasicTextField(value = query, onValueChange = onQueryChange, textStyle = TextStyle(color = IptvTextPrimary, fontSize = 14.sp), modifier = Modifier.fillMaxWidth().onKeyEvent { e ->
                    if (e.nativeKeyEvent.action == android.view.KeyEvent.ACTION_DOWN && e.nativeKeyEvent.keyCode == android.view.KeyEvent.KEYCODE_DPAD_CENTER) { onSearch(); true } else false
                }, singleLine = true)
                if (query.isEmpty()) Text("Buscar anime...", color = IptvTextMuted, fontSize = 14.sp)
            }
            Box(modifier = Modifier.size(48.dp).clip(RoundedCornerShape(8.dp)).background(IptvAccent).clickable { onSearch() }, contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.Search, contentDescription = "Buscar", tint = Color.White)
            }
        }
        LazyVerticalGrid(columns = GridCells.Adaptive(minSize = 160.dp), contentPadding = PaddingValues(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items(results) { media -> AnimeCard(title = media.title, type = media.type, coverUrl = media.cover) { onAnimeClick(media) } }
        }
    }
}

// ── Anime card ─────────────────────────────────────────────────────────────

@Composable
private fun AnimeCard(title: String, type: String, coverUrl: String? = null, onClick: () -> Unit) {
    var isFocused by remember { mutableStateOf(false) }
    Column(
        modifier = Modifier.clip(RoundedCornerShape(12.dp))
            .background(if (isFocused) IptvFocusBg else IptvCard)
            .border(width = if (isFocused) 2.dp else 0.dp, color = if (isFocused) IptvFocusBorder else Color.Transparent, shape = RoundedCornerShape(12.dp))
            .clickable(onClick = onClick).onFocusChanged { isFocused = it.hasFocus }.padding(12.dp).focusable(),
    ) {
        Box(
            modifier = Modifier.fillMaxWidth().aspectRatio(3f / 4f).clip(RoundedCornerShape(8.dp)).background(IptvSurfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            if (!coverUrl.isNullOrBlank()) {
                AndroidView(factory = { ctx -> ImageView(ctx) }, update = { iv -> Glide.with(iv).load(coverUrl).centerCrop().into(iv) }, modifier = Modifier.fillMaxSize())
            } else {
                Text(title.take(2).uppercase(), color = IptvTextMuted, fontSize = 24.sp, fontWeight = FontWeight.Bold)
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(title, color = IptvTextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis)
        if (type.isNotBlank()) Text(type.uppercase(), color = IptvTextMuted, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

// ── Anime detail screen ────────────────────────────────────────────────────

@Composable
internal fun AnimeDetailContent(slug: String, onBack: () -> Unit, onPlayEpisode: (slug: String, episodeNumber: Int) -> Unit) {
    var animeDetail by remember { mutableStateOf<AnimeDetail?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(slug) {
        isLoading = true
        AnimeFlvApiClient.getAnimeDetail(slug).onSuccess { animeDetail = it }.onFailure { errorMessage = it.message }
        isLoading = false
    }

    Column(modifier = Modifier.fillMaxSize().background(IptvBackground)) {
        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Box(modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(IptvSurface).clickable { onBack() }.padding(horizontal = 16.dp, vertical = 8.dp)) {
                Text("< Volver", color = IptvTextPrimary, fontSize = 14.sp)
            }
        }

        if (isLoading) { Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("Cargando...", color = IptvTextMuted, fontSize = 16.sp) }; return }

        val detail = animeDetail ?: run {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(text = errorMessage ?: "No se encontro el anime", color = Color.Red, fontSize = 16.sp)
            }
            return
        }

        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            Box(modifier = Modifier.width(140.dp).aspectRatio(3f / 4f).clip(RoundedCornerShape(12.dp)).background(IptvSurfaceVariant), contentAlignment = Alignment.Center) {
                if (detail.cover.isNotBlank()) AndroidView(factory = { ctx -> ImageView(ctx) }, update = { iv -> Glide.with(iv).load(detail.cover).centerCrop().into(iv) }, modifier = Modifier.fillMaxSize())
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(detail.title, color = IptvTextPrimary, fontSize = 22.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(detail.type.uppercase(), color = IptvAccent, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    Text("•", color = IptvTextMuted, fontSize = 12.sp); Text(detail.status, color = IptvTextMuted, fontSize = 12.sp)
                    Text("•", color = IptvTextMuted, fontSize = 12.sp); Text("Rating: ${detail.rating}", color = IptvTextMuted, fontSize = 12.sp)
                }
                Spacer(Modifier.height(6.dp))
                Text(detail.genres.joinToString(", "), color = IptvTextSecondary, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (detail.synopsis.isNotBlank()) {
                    Spacer(Modifier.height(8.dp))
                    Text(detail.synopsis, color = IptvTextMuted, fontSize = 12.sp, maxLines = 3, overflow = TextOverflow.Ellipsis)
                }
            }
        }

        if (detail.episodes.isNotEmpty()) {
            Text("Episodios (${detail.episodes.size})", color = IptvTextPrimary, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp))
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 100.dp),
                contentPadding = PaddingValues(horizontal = 24.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(detail.episodes.sortedByDescending { it.number }) { episode ->
                    var epFocused by remember { mutableStateOf(false) }
                    Box(
                        modifier = Modifier.fillMaxWidth().height(48.dp).clip(RoundedCornerShape(8.dp))
                            .background(if (epFocused) IptvAccent else IptvSurface)
                            .border(width = if (epFocused) 2.dp else 0.dp, color = if (epFocused) IptvFocusBorder else Color.Transparent, shape = RoundedCornerShape(8.dp))
                            .clickable { onPlayEpisode(slug, episode.number) }.onFocusChanged { epFocused = it.hasFocus }.focusable(),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("Ep. ${episode.number}", color = if (epFocused) Color.White else IptvTextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                    }
                }
            }
        }
    }
}
