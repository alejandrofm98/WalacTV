package com.example.walactv

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.activity.compose.BackHandler
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.Fragment
import androidx.media3.common.util.UnstableApi
import androidx.tv.material3.Text
import androidx.tv.material3.MaterialTheme
import com.example.walactv.ui.compose.EventVsCard
import com.example.walactv.ui.compose.MediaCard
import com.example.walactv.ui.compose.buildEpisodeLabel
import com.example.walactv.ui.theme.WalacTVTheme
import com.example.walactv.ui.theme.IptvBackground
import com.example.walactv.ui.theme.IptvAccent
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class SearchFragment : Fragment() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private lateinit var repository: IptvRepository
    private lateinit var channelStateStore: ChannelStateStore

    private var items: List<CatalogItem> = emptyList()
    private var activeResults: List<CatalogItem> = emptyList()
    private var channelResults: List<CatalogItem> = emptyList()
    private var currentItem: CatalogItem? = null
    private var currentStreamIndex: Int = 0
    private var currentItemPosition: Int = -1

    fun setSearchData(items: List<CatalogItem>) {
        this.items = items
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        repository = IptvRepository(requireContext())
        channelStateStore = ChannelStateStore(requireContext())
        if (items.isEmpty()) {
            items = CatalogMemory.searchableItems
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return ComposeView(requireContext()).apply {
            setContent {
                WalacTVTheme {
                    SearchScreen(
                        allItems = items,
                        onUpdateResults = { newActive, newChannel ->
                            activeResults = newActive
                            channelResults = newChannel
                        },
                        onItemClick = { item, activePosition ->
                            playCatalogItem(item, position = activePosition)
                        },
                        onBack = {
                            requireActivity().supportFragmentManager.popBackStack()
                        }
                    )
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }

    @androidx.annotation.OptIn(markerClass = [UnstableApi::class])
    private fun playCatalogItem(item: CatalogItem, optionIndex: Int = 0, position: Int = currentItemPosition) {
        if (item.kind == ContentKind.SERIES && (item.catalogId != null || item.seriesName != null)) {
            requireActivity().supportFragmentManager.beginTransaction()
                .replace(R.id.main_browse_fragment, SeriesDetailFragment.newInstance(item))
                .addToBackStack("SeriesDetailFragment")
                .commit()
            return
        }

        if (item.kind == ContentKind.MOVIE) {
            requireActivity().supportFragmentManager.beginTransaction()
                .replace(R.id.main_browse_fragment, MovieDetailFragment.newInstance(item))
                .addToBackStack("MovieDetailFragment")
                .commit()
            return
        }

        scope.launch {
            val resolvedItem = if (item.kind == ContentKind.EVENT) repository.resolveEventItem(item) else item
            if (resolvedItem.streamOptions.isEmpty()) {
                Toast.makeText(requireContext(), R.string.no_streams_available, Toast.LENGTH_SHORT).show()
                return@launch
            }

            val streamIndex = optionIndex.coerceIn(resolvedItem.streamOptions.indices)
            val stream = resolvedItem.streamOptions[streamIndex]

            currentItem = resolvedItem
            currentStreamIndex = streamIndex
            currentItemPosition = position.takeIf { it >= 0 }
                ?: activeResults.indexOfFirst { it.stableId == resolvedItem.stableId }

            if (resolvedItem.kind == ContentKind.CHANNEL) {
                channelStateStore.markRecent(resolvedItem)
            }

            val fragmentManager = requireActivity().supportFragmentManager
            fragmentManager.findFragmentById(R.id.player_container)?.let { existing ->
                fragmentManager.beginTransaction()
                    .remove(existing)
                    .commitNow()
            }

            val playerFragment = PlayerFragment()
            val unifiedOptions = resolvedItem.streamOptions.toUnifiedOptions()
            playerFragment.initialize(
                streamUrl = stream.url,
                overlayNumber = when {
                    resolvedItem.kind == ContentKind.CHANNEL && resolvedItem.channelNumber != null -> getString(R.string.channel_overlay_number, resolvedItem.channelNumber)
                    resolvedItem.kind == ContentKind.EVENT -> getString(R.string.live_overlay_label)
                    else -> resolvedItem.kind.name
                },
                overlayTitle = resolvedItem.title,
                overlayMeta = if (resolvedItem.kind == ContentKind.SERIES) buildEpisodeLabel(resolvedItem.seasonNumber, resolvedItem.episodeNumber) else resolvedItem.subtitle,
                contentKind = resolvedItem.kind,
                onNavigateChannel = ::navigateChannel,
                onNavigateOption = ::navigateOption,
                onDirectChannelNumber = ::navigateToChannelNumber,
                onToggleFavorite = { toggleFavorite(resolvedItem) },
                onOpenFavorites = ::openFavoriteChannel,
                onOpenRecents = ::openRecentChannel,
                streamOptionLabels = resolvedItem.streamOptions.map { it.label },
                currentOptionIndex = streamIndex,
                overlayLogoUrl = resolvedItem.preferredVodPosterUrl(),
                isFavorite = channelStateStore.isFavorite(resolvedItem),
                contentId = resolvedItem.providerId ?: resolvedItem.stableId,
                onProgressSaved = ComposeMainFragment.progressSavedCallback,
                unifiedStreamOptions = unifiedOptions,
                onSelectUnifiedOption = if (resolvedItem.kind == ContentKind.MOVIE || resolvedItem.kind == ContentKind.SERIES) {
                    { selectedIndex ->
                        val selectedOption = unifiedOptions.getOrNull(selectedIndex) ?: return@initialize
                        val optionIndex = resolvedItem.streamOptions.indexOfFirst { it.url == selectedOption.url }
                        if (optionIndex >= 0) {
                            playCatalogItem(resolvedItem, optionIndex)
                        }
                    }
                } else null,
            )

            fragmentManager.beginTransaction()
                .replace(R.id.player_container, playerFragment, PLAYER_FRAGMENT_TAG)
                .commitNow()

            val container = requireActivity().findViewById<FrameLayout>(R.id.player_container)
            container.visibility = View.VISIBLE
            container.isFocusable = true
            container.isFocusableInTouchMode = true
            container.requestFocus()
            Toast.makeText(requireContext(), resolvedItem.title, Toast.LENGTH_SHORT).show()
        }
    }

    private fun navigateChannel(direction: Int) {
        val current = currentItem ?: return
        if (current.kind == ContentKind.EVENT) {
            navigateOption(direction)
            return
        }
        val source = if (current.kind == ContentKind.CHANNEL && channelResults.isNotEmpty()) {
            channelResults
        } else {
            activeResults
        }
        val currentIndex = source.indexOfFirst { it.stableId == current.stableId }
        if (currentIndex == -1) return
        val targetIndex = currentIndex + direction
        if (targetIndex !in source.indices) {
            val message = if (direction > 0) R.string.no_more_results else R.string.first_result_reached
            Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
            return
        }
        val target = source[targetIndex]
        val activePosition = activeResults.indexOfFirst { it.stableId == target.stableId }
        playCatalogItem(target, position = activePosition)
    }

    private fun navigateOption(direction: Int) {
        val item = currentItem ?: return
        val newIndex = currentStreamIndex + direction
        if (newIndex !in item.streamOptions.indices) return
        playCatalogItem(item, optionIndex = newIndex)
    }

    private fun navigateToChannelNumber(number: Int): Boolean {
        val match = items.firstOrNull { it.kind == ContentKind.CHANNEL && it.channelNumber == number } ?: return false
        playCatalogItem(match, position = activeResults.indexOfFirst { it.stableId == match.stableId })
        return true
    }

    private fun toggleFavorite(item: CatalogItem): Boolean {
        val result = channelStateStore.toggleFavorite(item)
        scope.launch {
            runCatching { repository.updateChannelFavorite(item, result) }
                .onFailure {
                    Log.e(TAG, "No se pudo actualizar favorito ${item.stableId}", it)
                    channelStateStore.setFavorite(item, !result)
                    Toast.makeText(requireContext(), "No se pudo actualizar favoritos", Toast.LENGTH_SHORT).show()
                }
        }
        return result
    }

    private fun openFavoriteChannel(): Boolean {
        val favoriteIds = channelStateStore.favoriteIds()
        val match = items.firstOrNull { it.kind == ContentKind.CHANNEL && favoriteIds.contains(it.stableId) } ?: return false
        playCatalogItem(match, position = activeResults.indexOfFirst { it.stableId == match.stableId })
        return true
    }

    private fun openRecentChannel(): Boolean {
        val channelsById = items.filter { it.kind == ContentKind.CHANNEL }.associateBy(CatalogItem::stableId)
        val match = channelStateStore.recentIds().drop(1).mapNotNull(channelsById::get).firstOrNull()
            ?: channelStateStore.recentIds().mapNotNull(channelsById::get).firstOrNull()
            ?: return false
        playCatalogItem(match, position = activeResults.indexOfFirst { it.stableId == match.stableId })
        return true
    }

    companion object {
        private const val TAG = "SearchFragment"
        private const val PLAYER_FRAGMENT_TAG = "player_fragment"

        fun newInstance(items: List<CatalogItem>): SearchFragment {
            return SearchFragment().apply {
                setSearchData(items)
            }
        }
    }
}

@Composable
fun SearchScreen(
    allItems: List<CatalogItem>,
    onUpdateResults: (List<CatalogItem>, List<CatalogItem>) -> Unit,
    onItemClick: (CatalogItem, Int) -> Unit,
    onBack: () -> Unit
) {
    BackHandler { onBack() }

    val context = LocalContext.current
    val repository = remember { IptvRepository(context) }

    var query by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var searchResults by remember { mutableStateOf<List<Pair<String, List<CatalogItem>>>>(emptyList()) }

    LaunchedEffect(query) {
        if (query.isBlank() || query.length < 2) {
            searchResults = emptyList()
            isLoading = false
            return@LaunchedEffect
        }
        isLoading = true
        delay(400)
        try {
            val (items, _) = repository.search(query)
            val grouped = listOf(
                ContentKind.EVENT to "Eventos",
                ContentKind.CHANNEL to "Canales",
                ContentKind.MOVIE to "Películas",
                ContentKind.SERIES to "Series",
            ).mapNotNull { (kind, title) ->
                val kindMatches = items.filter { it.kind == kind }
                if (kindMatches.isEmpty()) null else title to kindMatches
            }
            searchResults = grouped
            onUpdateResults(items, items.filter { it.kind == ContentKind.CHANNEL })
        } catch (e: Exception) {
            Log.e("SearchFragment", "Search failed", e)
            searchResults = emptyList()
        } finally {
            isLoading = false
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(IptvBackground)
    ) {
        SearchBar(
            query = query,
            onQueryChange = { query = it }
        )

        if (isLoading) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Buscando...",
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = 18.sp
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 32.dp)
            ) {
                items(searchResults) { (title, items) ->
                    SearchRow(title = title, items = items, onItemClick = onItemClick)
                }
            }
        }
    }
}

@Composable
fun SearchBar(
    query: String,
    onQueryChange: (String) -> Unit
) {
    val focusRequester = remember { FocusRequester() }
    var isFocused by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 44.dp, end = 44.dp, top = 44.dp, bottom = 24.dp)
            .background(Color(0xFF1E2530), RoundedCornerShape(12.dp))
            .border(
                width = if (isFocused) 2.dp else 1.dp,
                color = if (isFocused) IptvAccent else Color.White.copy(alpha = 0.2f),
                shape = RoundedCornerShape(12.dp)
            )
            .padding(horizontal = 24.dp, vertical = 16.dp)
    ) {
        BasicTextField(
            value = query,
            onValueChange = onQueryChange,
            textStyle = TextStyle(
                color = Color.White,
                fontSize = 20.sp,
                fontWeight = FontWeight.Medium
            ),
            cursorBrush = SolidColor(IptvAccent),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(focusRequester)
                .onFocusChanged { isFocused = it.isFocused },
            decorationBox = { innerTextField ->
                if (query.isEmpty()) {
                    Text(
                        text = "Buscar canales, películas, series, eventos...",
                        color = Color.White.copy(alpha = 0.5f),
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
                innerTextField()
            }
        )
    }
}

@Composable
fun SearchRow(
    title: String,
    items: List<CatalogItem>,
    onItemClick: (CatalogItem, Int) -> Unit
) {
    Column(
        modifier = Modifier.padding(bottom = 24.dp)
    ) {
        Text(
            text = title,
            color = Color.White,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 44.dp, bottom = 12.dp)
        )
        LazyRow(
            contentPadding = PaddingValues(horizontal = 44.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            items(items) { item ->
                if (item.kind == ContentKind.EVENT) {
                    EventVsCard(
                        item = item,
                        useFixedWidth = true,
                        onFocused = {},
                        onClick = { onItemClick(item, -1) } // The active index isn't properly maintained here, I will fix it
                    )
                } else {
                    MediaCard(
                        item = item,
                        onFocused = {},
                        onClick = { onItemClick(item, -1) }
                    )
                }
            }
        }
    }
}
