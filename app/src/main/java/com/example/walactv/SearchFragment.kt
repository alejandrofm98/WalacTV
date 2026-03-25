package com.example.walactv

import android.graphics.Color
import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.leanback.app.SearchSupportFragment
import androidx.leanback.widget.ArrayObjectAdapter
import androidx.leanback.widget.HeaderItem
import androidx.leanback.widget.ImageCardView
import androidx.leanback.widget.ListRow
import androidx.leanback.widget.ListRowPresenter
import androidx.leanback.widget.ObjectAdapter
import androidx.leanback.widget.OnItemViewClickedListener
import androidx.leanback.widget.Presenter
import androidx.leanback.widget.Row
import androidx.leanback.widget.RowPresenter
import androidx.media3.common.util.UnstableApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class SearchFragment : SearchSupportFragment(), SearchSupportFragment.SearchResultProvider {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val rowsAdapter = ArrayObjectAdapter(ListRowPresenter())

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
        setSearchResultProvider(this)
        setOnItemViewClickedListener(ResultClickListener())
    }

    override fun getResultsAdapter(): ObjectAdapter = rowsAdapter

    override fun onQueryTextChange(newQuery: String): Boolean {
        performSearch(newQuery)
        return true
    }

    override fun onQueryTextSubmit(query: String): Boolean {
        performSearch(query)
        return true
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }

    private fun performSearch(query: String) {
        rowsAdapter.clear()
        currentItem = null
        currentItemPosition = -1

        if (query.isBlank()) {
            activeResults = emptyList()
            channelResults = emptyList()
            return
        }

        val normalizedTerms = query.trim().lowercase().split(" ").filter(String::isNotBlank)
        val results = items.filter { item ->
            val haystack = item.searchableText().joinToString(" ").lowercase()
            normalizedTerms.all(haystack::contains)
        }

        val groupedResults = listOf(
            ContentKind.EVENT to getString(R.string.search_events_header),
            ContentKind.CHANNEL to getString(R.string.search_channels_header),
            ContentKind.MOVIE to getString(R.string.search_movies_header),
            ContentKind.SERIES to getString(R.string.search_series_header),
        ).mapNotNull { (kind, title) ->
            val matches = results.filter { it.kind == kind }
            if (matches.isEmpty()) null else title to matches
        }

        activeResults = groupedResults.flatMap { it.second }
        channelResults = results.filter { it.kind == ContentKind.CHANNEL }

        groupedResults.forEachIndexed { index, (title, matches) ->
            val rowAdapter = ArrayObjectAdapter(SearchResultCardPresenter())
            matches.forEach(rowAdapter::add)
            rowsAdapter.add(ListRow(HeaderItem(index.toLong(), title), rowAdapter))
        }
    }

    private inner class ResultClickListener : OnItemViewClickedListener {
        override fun onItemClicked(
            itemViewHolder: Presenter.ViewHolder,
            item: Any,
            rowViewHolder: RowPresenter.ViewHolder,
            row: Row,
        ) {
            val catalogItem = item as? CatalogItem ?: return
            val position = activeResults.indexOfFirst { it.stableId == catalogItem.stableId }
            playCatalogItem(catalogItem, position = position)
        }
    }

    @androidx.annotation.OptIn(markerClass = [UnstableApi::class])
    private fun playCatalogItem(item: CatalogItem, optionIndex: Int = 0, position: Int = currentItemPosition) {
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

            val playerFragment = PlayerFragment(
                streamUrl = stream.url,
                overlayNumber = when {
                    resolvedItem.kind == ContentKind.CHANNEL && resolvedItem.channelNumber != null -> getString(R.string.channel_overlay_number, resolvedItem.channelNumber)
                    resolvedItem.kind == ContentKind.EVENT -> getString(R.string.live_overlay_label)
                    else -> resolvedItem.kind.name
                },
                overlayTitle = resolvedItem.title,
                overlayMeta = listOf(
                    resolvedItem.subtitle,
                    stream.label,
                    if (channelStateStore.isFavorite(resolvedItem)) getString(R.string.favorite_enabled) else null,
                ).filterNotNull().filter(String::isNotBlank).joinToString("  •  ").ifBlank { resolvedItem.description },
                contentKind = resolvedItem.kind,
                onNavigateChannel = ::navigateChannel,
                onNavigateOption = ::navigateOption,
                onDirectChannelNumber = ::navigateToChannelNumber,
                onToggleFavorite = { toggleFavorite(resolvedItem) },
                onOpenFavorites = ::openFavoriteChannel,
                onOpenRecents = ::openRecentChannel,
                streamOptionLabels = resolvedItem.streamOptions.map { it.label },
                currentOptionIndex = streamIndex,
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
        return channelStateStore.toggleFavorite(item)
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
        private const val PLAYER_FRAGMENT_TAG = "player_fragment"

        fun newInstance(items: List<CatalogItem>): SearchFragment {
            return SearchFragment().apply {
                setSearchData(items)
            }
        }
    }
}

private class SearchResultCardPresenter : Presenter() {

    override fun onCreateViewHolder(parent: ViewGroup): ViewHolder {
        val cardView = ImageCardView(parent.context).apply {
            layoutParams = ViewGroup.LayoutParams(CARD_WIDTH, ViewGroup.LayoutParams.WRAP_CONTENT)
            isFocusable = true
            isFocusableInTouchMode = true
            setMainImageDimensions(CARD_WIDTH, CARD_HEIGHT)
            setBackgroundColor(ContextCompat.getColor(context, android.R.color.darker_gray))
            setInfoAreaBackgroundColor(Color.parseColor("#1E2530"))
        }
        return ViewHolder(cardView)
    }

    override fun onBindViewHolder(viewHolder: ViewHolder, item: Any?) {
        val catalogItem = item as? CatalogItem ?: return
        val cardView = viewHolder.view as ImageCardView
        cardView.titleText = displayCardTitle(catalogItem)
        cardView.contentText = catalogItem.subtitle.ifBlank { catalogItem.description.ifBlank { catalogItem.group } }
        cardView.mainImage = null
    }

    override fun onUnbindViewHolder(viewHolder: ViewHolder) {
        val cardView = viewHolder.view as ImageCardView
        cardView.mainImage = null
    }

    private companion object {
        private const val CARD_WIDTH = 320
        private const val CARD_HEIGHT = 180
    }
}
