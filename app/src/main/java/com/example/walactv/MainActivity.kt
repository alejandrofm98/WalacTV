package com.example.walactv

import android.app.AlertDialog
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.drawable.BitmapDrawable
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.leanback.app.BrowseSupportFragment
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
import com.bumptech.glide.Glide
import com.bumptech.glide.request.target.CustomTarget
import com.bumptech.glide.request.transition.Transition
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class MainActivity : FragmentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.main_browse_fragment, ComposeMainFragment())
                .commitNow()
        }
    }
}

class MainFragment : BrowseSupportFragment() {

    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private lateinit var repository: IptvRepository

    private lateinit var rowsAdapter: ArrayObjectAdapter
    private lateinit var channelStateStore: ChannelStateStore
    private var homeSections: List<BrowseSection> = emptyList()
    private var searchableItems: List<CatalogItem> = emptyList()
    private var channelLineup: List<CatalogItem> = emptyList()

    private var currentItem: CatalogItem? = null
    private var currentStreamIndex: Int = 0
    private var currentRowPosition: Int = -1
    private var currentItemPosition: Int = -1

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)

        title = getString(R.string.home_title)
        headersState = HEADERS_HIDDEN
        isHeadersTransitionOnBackEnabled = true
        brandColor = ContextCompat.getColor(requireContext(), R.color.brand_surface)
        searchAffordanceColor = ContextCompat.getColor(requireContext(), R.color.brand_accent)
        channelStateStore = ChannelStateStore(requireContext())
        repository = IptvRepository(requireContext())

        parentFragmentManager.setFragmentResultListener(SettingsFragment.REQUEST_KEY, viewLifecycleOwner) { _, bundle ->
            if (bundle.getBoolean(SettingsFragment.KEY_REFRESHED)) {
                homeSections = emptyList()
                searchableItems = emptyList()
                channelLineup = emptyList()
                loadHome(forceRefreshPlaylist = false)
            }
        }

        parentFragmentManager.setFragmentResultListener(GuideFragment.REQUEST_KEY, viewLifecycleOwner) { _, bundle ->
            val stableId = bundle.getString(GuideFragment.KEY_CHANNEL_ID).orEmpty()
            searchableItems.firstOrNull { it.stableId == stableId }?.let { item ->
                playCatalogItem(item, 0, currentRowPosition, currentItemPosition)
            }
        }

        setupUI()
        setupSearch()
        loadHome()
    }

    private fun setupUI() {
        rowsAdapter = ArrayObjectAdapter(ListRowPresenter())
        adapter = rowsAdapter
        onItemViewClickedListener = ItemViewClickedListener()
        showStatusRow(
            title = getString(R.string.loading_title),
            message = getString(R.string.loading_message),
        )
    }

    private fun setupSearch() {
        setOnSearchClickedListener {
            val searchFragment = SearchFragment()
            searchFragment.setSearchData(searchableItems)

            requireActivity().supportFragmentManager.beginTransaction()
                .replace(R.id.main_browse_fragment, searchFragment)
                .addToBackStack(null)
                .commit()
        }
    }

    private fun loadHome(forceRefreshPlaylist: Boolean = false) {
        if (!forceRefreshPlaylist && homeSections.isNotEmpty() && searchableItems.isNotEmpty()) {
            renderSections(homeSections)
            return
        }

        scope.launch {
            try {
                val catalog = repository.loadHomeCatalog(forceRefreshPlaylist)
                homeSections = catalog.sections
                searchableItems = catalog.searchableItems
                CatalogMemory.searchableItems = searchableItems
                channelLineup = searchableItems.filter { it.kind == ContentKind.CHANNEL }
                if (homeSections.isEmpty()) {
                    showStatusRow(
                        title = getString(R.string.empty_state_title),
                        message = getString(R.string.empty_state_message),
                    )
                } else {
                    renderSections(homeSections)
                }
            } catch (exception: Exception) {
                Log.e(TAG, "No se pudo cargar el catalogo", exception)
                showStatusRow(
                    title = getString(R.string.error_state_title),
                    message = exception.message ?: getString(R.string.error_state_message),
                )
                Toast.makeText(
                    requireContext(),
                    getString(R.string.error_loading_content, exception.message ?: "sin detalle"),
                    Toast.LENGTH_LONG,
                ).show()
            }
        }
    }

    private fun renderSections(sections: List<BrowseSection>) {
        rowsAdapter.clear()

        val sectionsToRender = buildDisplaySections(sections)

        sectionsToRender.forEachIndexed { index, section ->
            if (section.items.isNotEmpty()) {
                val rowAdapter = ArrayObjectAdapter(CardPresenter())
                section.items.forEach(rowAdapter::add)
                rowsAdapter.add(ListRow(HeaderItem(index.toLong(), section.title), rowAdapter))
            }
        }
    }

    private fun buildDisplaySections(sections: List<BrowseSection>): List<BrowseSection> {
        val eventSections = sections.filter { section -> section.items.firstOrNull()?.kind == ContentKind.EVENT }
        val channels = searchableItems.filter { it.kind == ContentKind.CHANNEL }
        val movies = searchableItems.filter { it.kind == ContentKind.MOVIE }
        val series = searchableItems.filter { it.kind == ContentKind.SERIES }
        val liveNowRow = buildLiveNowSection(eventSections)

        val favoriteRow = buildFavoriteSection(channelLineup)
        val recentRow = buildRecentSection(channelLineup)
        val channelSections = buildTypeSections(getString(R.string.channels_header), channels, MAX_MAIN_CHANNEL_ITEMS)
        val movieSections = buildTypeSections(getString(R.string.movies_header), movies, MAX_MAIN_MOVIE_ITEMS)
        val seriesSections = buildTypeSections(getString(R.string.series_header), series, MAX_MAIN_SERIES_ITEMS)

        return buildList {
            add(buildGuideSection())
            liveNowRow?.let(::add)
            eventSections.firstOrNull()?.let(::add)
            favoriteRow?.let(::add)
            recentRow?.let(::add)
            addAll(channelSections)
            addAll(movieSections)
            addAll(seriesSections)
            add(buildSettingsSection())
        }
    }

    private fun buildTypeSections(title: String, items: List<CatalogItem>, mainLimit: Int): List<BrowseSection> {
        if (items.isEmpty()) return emptyList()

        val groupedSections = items.groupBy { it.group.ifBlank { getString(R.string.uncategorized_label) } }
            .entries
            .sortedWith(compareByDescending<Map.Entry<String, List<CatalogItem>>> { it.value.size }.thenBy { it.key })
            .map { entry ->
                BrowseSection(
                    title = "$title · ${entry.key}",
                    items = entry.value.take(MAX_CATEGORY_ROW_ITEMS),
                )
            }

        return buildList {
            add(BrowseSection(title, items.take(mainLimit)))
            addAll(groupedSections)
        }
    }

    private fun buildSettingsSection(): BrowseSection {
        return BrowseSection(
            title = getString(R.string.settings_title),
            items = listOf(
                CatalogItem(
                    stableId = SETTINGS_ITEM_ID,
                    title = getString(R.string.settings_title),
                    subtitle = getString(R.string.settings_panel_subtitle, buildElapsedUpdateText()),
                    description = getString(R.string.settings_description),
                    imageUrl = "",
                    kind = ContentKind.EVENT,
                    group = "settings",
                    badgeText = getString(R.string.settings_badge),
                    streamOptions = emptyList(),
                ),
            ),
        )
    }

    private fun buildGuideSection(): BrowseSection {
        return BrowseSection(
            title = getString(R.string.guide_section_title),
            items = listOf(
                CatalogItem(
                    stableId = GUIDE_ITEM_ID,
                    title = getString(R.string.guide_section_title),
                    subtitle = getString(R.string.guide_section_subtitle),
                    description = getString(R.string.guide_section_description),
                    imageUrl = "",
                    kind = ContentKind.CHANNEL,
                    group = "guide",
                    badgeText = getString(R.string.guide_badge),
                    streamOptions = emptyList(),
                ),
            ),
        )
    }

    private fun buildElapsedUpdateText(): String {
        val lastUpdated = repository.getLastPlaylistUpdateMillis()
        if (lastUpdated == 0L) return getString(R.string.settings_never_short)

        val elapsedMinutes = ((System.currentTimeMillis() - lastUpdated) / 60_000L).coerceAtLeast(1)
        return when {
            elapsedMinutes >= 60 * 24 -> getString(R.string.settings_elapsed_days, elapsedMinutes / (60 * 24))
            elapsedMinutes >= 60 -> getString(R.string.settings_elapsed_hours, elapsedMinutes / 60)
            else -> getString(R.string.settings_elapsed_minutes, elapsedMinutes)
        }
    }

    private fun buildLiveNowSection(eventSections: List<BrowseSection>): BrowseSection? {
        val items = eventSections.flatMap(BrowseSection::items)
            .distinctBy(CatalogItem::stableId)
            .filter(::isLikelyLiveNow)

        return if (items.isEmpty()) null else BrowseSection(getString(R.string.live_now_header), items)
    }

    private fun buildFavoriteSection(channels: List<CatalogItem>): BrowseSection? {
        val favoriteIds = channelStateStore.favoriteIds()
        if (favoriteIds.isEmpty()) return null

        val items = channels.filter { favoriteIds.contains(it.stableId) }
        return if (items.isEmpty()) null else BrowseSection(getString(R.string.favorites_header), items)
    }

    private fun buildRecentSection(channels: List<CatalogItem>): BrowseSection? {
        val itemsById = channels.associateBy(CatalogItem::stableId)
        val items = channelStateStore.recentIds().mapNotNull(itemsById::get)
        return if (items.isEmpty()) null else BrowseSection(getString(R.string.recents_header), items)
    }

    private fun showStatusRow(title: String, message: String) {
        rowsAdapter.clear()
        val rowAdapter = ArrayObjectAdapter(CardPresenter())
        rowAdapter.add(
            CatalogItem(
                stableId = "status:$title",
                title = title,
                subtitle = message,
                description = message,
                imageUrl = "",
                kind = ContentKind.EVENT,
                group = "status",
                badgeText = "",
                streamOptions = emptyList(),
            ),
        )
        rowsAdapter.add(ListRow(HeaderItem(0, title), rowAdapter))
    }

    private inner class ItemViewClickedListener : OnItemViewClickedListener {
        override fun onItemClicked(
            itemViewHolder: Presenter.ViewHolder,
            item: Any,
            rowViewHolder: RowPresenter.ViewHolder,
            row: Row,
        ) {
            val catalogItem = item as? CatalogItem ?: return

            if (catalogItem.stableId == SETTINGS_ITEM_ID) {
                openSettings()
                return
            }

            if (catalogItem.stableId == GUIDE_ITEM_ID) {
                openGuide()
                return
            }

            val rowPosition = findRowPosition(row)
            val itemPosition = findItemPosition(row, catalogItem)

            if (catalogItem.streamOptions.isEmpty()) {
                Toast.makeText(context, R.string.no_streams_available, Toast.LENGTH_SHORT).show()
                return
            }

            if (catalogItem.streamOptions.size == 1) {
                playCatalogItem(catalogItem, 0, rowPosition, itemPosition)
            } else {
                showStreamSelector(catalogItem, rowPosition, itemPosition)
            }
        }
    }

    @androidx.annotation.OptIn(markerClass = [UnstableApi::class])
    private fun playCatalogItem(item: CatalogItem, optionIndex: Int, rowPos: Int, itemPos: Int) {
        val stream = item.streamOptions.getOrNull(optionIndex) ?: return

        currentItem = item
        currentStreamIndex = optionIndex
        currentRowPosition = rowPos
        currentItemPosition = itemPos

        if (item.kind == ContentKind.CHANNEL) {
            channelStateStore.markRecent(item)
            renderSections(homeSections)
        }

        val container = requireActivity().findViewById<FrameLayout>(R.id.player_container)
        val fragmentManager = requireActivity().supportFragmentManager

        fragmentManager.findFragmentById(R.id.player_container)?.let { existing ->
            fragmentManager.beginTransaction()
                .remove(existing)
                .commitNow()
        }

        val playerFragment = PlayerFragment(
            streamUrl = stream.url,
            overlayNumber = buildOverlayNumber(item),
            overlayTitle = item.title,
            overlayMeta = buildOverlayMeta(item, stream),
            contentKind = item.kind,
            onNavigateChannel = ::navigateChannel,
            onNavigateOption = ::navigateOption,
            onDirectChannelNumber = ::navigateToChannelNumber,
            onToggleFavorite = { toggleFavorite(item) },
            onOpenFavorites = ::openFavoriteChannel,
            onOpenRecents = ::openRecentChannel,
        )

        fragmentManager.beginTransaction()
            .replace(R.id.player_container, playerFragment, PLAYER_FRAGMENT_TAG)
            .commitNow()

        container.visibility = View.VISIBLE

        val toastText = if (stream.label.isBlank()) item.title else "${item.title} · ${stream.label}"
        Toast.makeText(context, toastText, Toast.LENGTH_SHORT).show()
    }

    private fun navigateChannel(direction: Int) {
        val current = currentItem ?: return
        if (current.kind == ContentKind.CHANNEL && channelLineup.isNotEmpty()) {
            val currentIndex = channelLineup.indexOfFirst { it.stableId == current.stableId }
            if (currentIndex == -1) return
            val targetIndex = currentIndex + direction
            if (targetIndex !in channelLineup.indices) {
                Toast.makeText(context, if (direction > 0) R.string.no_more_items else R.string.first_item_reached, Toast.LENGTH_SHORT).show()
                return
            }
            playCatalogItem(channelLineup[targetIndex], 0, currentRowPosition, currentItemPosition)
            return
        }

        if (currentRowPosition < 0 || currentItemPosition < 0) return

        val row = rowsAdapter.get(currentRowPosition) as? ListRow ?: return
        val rowAdapter = row.adapter as? ArrayObjectAdapter ?: return
        val newPosition = currentItemPosition + direction

        if (newPosition !in 0 until rowAdapter.size()) {
            val message = if (direction > 0) {
                getString(R.string.no_more_items)
            } else {
                getString(R.string.first_item_reached)
            }
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
            return
        }

        val nextItem = rowAdapter.get(newPosition) as? CatalogItem ?: return
        playCatalogItem(nextItem, 0, currentRowPosition, newPosition)
    }

    private fun navigateOption(direction: Int) {
        val item = currentItem ?: return
        if (item.streamOptions.size <= 1) {
            Toast.makeText(context, R.string.single_option_only, Toast.LENGTH_SHORT).show()
            return
        }

        val newIndex = currentStreamIndex + direction
        if (newIndex !in item.streamOptions.indices) {
            val message = if (direction > 0) {
                getString(R.string.last_option_reached)
            } else {
                getString(R.string.first_option_reached)
            }
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
            return
        }

        playCatalogItem(item, newIndex, currentRowPosition, currentItemPosition)
        Toast.makeText(
            context,
            getString(R.string.option_counter, newIndex + 1, item.streamOptions.size),
            Toast.LENGTH_SHORT,
        ).show()
    }

    private fun showStreamSelector(item: CatalogItem, rowPos: Int, itemPos: Int) {
        val labels = item.streamOptions.map(StreamOption::label).toTypedArray()

        AlertDialog.Builder(requireContext())
            .setTitle(item.title)
            .setItems(labels) { dialog, which ->
                playCatalogItem(item, which, rowPos, itemPos)
                dialog.dismiss()
            }
            .setNegativeButton(R.string.cancel) { dialog, _ -> dialog.dismiss() }
            .show()
    }

    private fun buildOverlayNumber(item: CatalogItem): String {
        return when {
            item.kind == ContentKind.CHANNEL && item.channelNumber != null -> getString(R.string.channel_overlay_number, item.channelNumber)
            item.kind == ContentKind.EVENT -> getString(R.string.live_overlay_label)
            else -> item.kind.name
        }
    }

    private fun buildOverlayMeta(item: CatalogItem, stream: StreamOption): String {
        val favoriteMeta = if (channelStateStore.isFavorite(item)) getString(R.string.favorite_enabled) else null
        return listOf(item.subtitle, stream.label.takeIf { it.isNotBlank() && it != item.subtitle }, favoriteMeta)
            .filterNotNull()
            .filter { it.isNotBlank() }
            .joinToString("  •  ")
            .ifBlank { item.description }
    }

    private fun navigateToChannelNumber(number: Int): Boolean {
        val match = channelLineup.firstOrNull { it.channelNumber == number } ?: return false
        playCatalogItem(match, 0, currentRowPosition, currentItemPosition)
        return true
    }

    private fun openFavoriteChannel(): Boolean {
        val favoriteIds = channelStateStore.favoriteIds()
        val match = channelLineup.firstOrNull { favoriteIds.contains(it.stableId) } ?: return false
        playCatalogItem(match, 0, currentRowPosition, currentItemPosition)
        return true
    }

    private fun openRecentChannel(): Boolean {
        val itemsById = channelLineup.associateBy(CatalogItem::stableId)
        val match = channelStateStore.recentIds().drop(1).mapNotNull(itemsById::get).firstOrNull()
            ?: channelStateStore.recentIds().mapNotNull(itemsById::get).firstOrNull()
            ?: return false
        playCatalogItem(match, 0, currentRowPosition, currentItemPosition)
        return true
    }

    private fun isLikelyLiveNow(item: CatalogItem): Boolean {
        if (item.kind != ContentKind.EVENT) return false
        val parsed = runCatching { EVENT_TIME_FORMAT.parse(item.badgeText) }.getOrNull() ?: return false
        val now = Calendar.getInstance()
        val eventCalendar = Calendar.getInstance().apply {
            time = parsed
            set(Calendar.YEAR, now.get(Calendar.YEAR))
            set(Calendar.MONTH, now.get(Calendar.MONTH))
            set(Calendar.DAY_OF_MONTH, now.get(Calendar.DAY_OF_MONTH))
        }
        val deltaMinutes = (now.timeInMillis - eventCalendar.timeInMillis) / 60_000L
        return deltaMinutes in -20..180
    }

    private fun toggleFavorite(item: CatalogItem): Boolean {
        val favoriteState = channelStateStore.toggleFavorite(item)
        renderSections(homeSections)
        return favoriteState
    }

    private fun openSettings() {
        requireActivity().supportFragmentManager.beginTransaction()
            .replace(R.id.main_browse_fragment, SettingsFragment())
            .addToBackStack(null)
            .commit()
    }

    private fun openGuide() {
        requireActivity().supportFragmentManager.beginTransaction()
            .replace(R.id.main_browse_fragment, GuideFragment())
            .addToBackStack(null)
            .commit()
    }

    private fun findRowPosition(row: Row): Int {
        for (index in 0 until rowsAdapter.size()) {
            if (rowsAdapter.get(index) == row) {
                return index
            }
        }
        return -1
    }

    private fun findItemPosition(row: Row, item: CatalogItem): Int {
        val listRow = row as? ListRow ?: return -1
        val listAdapter = listRow.adapter as? ArrayObjectAdapter ?: return -1

        for (index in 0 until listAdapter.size()) {
            if (listAdapter.get(index) == item) {
                return index
            }
        }
        return -1
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }

    companion object {
        private const val TAG = "MainFragment"
        private const val PLAYER_FRAGMENT_TAG = "player_fragment"
        private const val SETTINGS_ITEM_ID = "settings:panel"
        private const val GUIDE_ITEM_ID = "guide:panel"
        private const val MAX_MAIN_CHANNEL_ITEMS = 30
        private const val MAX_MAIN_MOVIE_ITEMS = 24
        private const val MAX_MAIN_SERIES_ITEMS = 24
        private const val MAX_CATEGORY_ROW_ITEMS = 30
        private val EVENT_TIME_FORMAT = SimpleDateFormat("HH:mm", Locale.getDefault())
    }
}

class SearchFragment : SearchSupportFragment(), SearchSupportFragment.SearchResultProvider {

    private val rowsAdapter: ArrayObjectAdapter = ArrayObjectAdapter(ListRowPresenter())
    private var items: List<CatalogItem> = emptyList()
    private var channels: List<CatalogItem> = emptyList()
    private lateinit var channelStateStore: ChannelStateStore

    private var currentItem: CatalogItem? = null
    private var currentStreamIndex: Int = 0
    private var currentItemPosition: Int = -1

    fun setSearchData(items: List<CatalogItem>) {
        this.items = items
        this.channels = items.filter { it.kind == ContentKind.CHANNEL }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        channelStateStore = ChannelStateStore(requireContext())
        setSearchResultProvider(this)
        setOnItemViewClickedListener(ItemViewClickedListener())
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

    private fun performSearch(query: String) {
        rowsAdapter.clear()
        if (query.isBlank()) return

        val normalized = query.trim().lowercase()
        val terms = normalized.split(" ").filter { it.isNotBlank() }
        val results = items.filter { item ->
            val haystack = item.searchableText().joinToString(" ").lowercase()
            terms.all { haystack.contains(it) }
        }

        listOf(
            ContentKind.EVENT to getString(R.string.search_events_header),
            ContentKind.CHANNEL to getString(R.string.search_channels_header),
            ContentKind.MOVIE to getString(R.string.search_movies_header),
            ContentKind.SERIES to getString(R.string.search_series_header),
        ).forEachIndexed { index, (kind, title) ->
            val grouped = results.filter { it.kind == kind }
            if (grouped.isNotEmpty()) {
                val rowAdapter = ArrayObjectAdapter(CardPresenter())
                grouped.forEach(rowAdapter::add)
                rowsAdapter.add(ListRow(HeaderItem(index.toLong(), title), rowAdapter))
            }
        }
    }

    private inner class ItemViewClickedListener : OnItemViewClickedListener {
        override fun onItemClicked(
            itemViewHolder: Presenter.ViewHolder,
            item: Any,
            rowViewHolder: RowPresenter.ViewHolder,
            row: Row,
        ) {
            val catalogItem = item as? CatalogItem ?: return
            val rowAdapter = (row as? ListRow)?.adapter as? ArrayObjectAdapter ?: return

            var position = -1
            for (index in 0 until rowAdapter.size()) {
                if (rowAdapter.get(index) == catalogItem) {
                    position = index
                    break
                }
            }

            if (catalogItem.streamOptions.isEmpty()) {
                Toast.makeText(context, R.string.no_streams_available, Toast.LENGTH_SHORT).show()
                return
            }

            if (catalogItem.streamOptions.size == 1) {
                playCatalogItem(catalogItem, 0, position)
            } else {
                showStreamSelector(catalogItem, position)
            }
        }
    }

    @androidx.annotation.OptIn(markerClass = [UnstableApi::class])
    private fun playCatalogItem(item: CatalogItem, optionIndex: Int, itemPosition: Int) {
        val stream = item.streamOptions.getOrNull(optionIndex) ?: return

        currentItem = item
        currentStreamIndex = optionIndex
        currentItemPosition = itemPosition

        val container = requireActivity().findViewById<FrameLayout>(R.id.player_container)
        val fragmentManager = requireActivity().supportFragmentManager

        fragmentManager.findFragmentById(R.id.player_container)?.let { existing ->
            fragmentManager.beginTransaction()
                .remove(existing)
                .commitNow()
        }

        val playerFragment = PlayerFragment(
            streamUrl = stream.url,
            overlayNumber = when {
                item.kind == ContentKind.CHANNEL && item.channelNumber != null -> getString(R.string.channel_overlay_number, item.channelNumber)
                item.kind == ContentKind.EVENT -> getString(R.string.live_overlay_label)
                else -> item.kind.name
            },
            overlayTitle = item.title,
            overlayMeta = listOf(item.subtitle, stream.label, if (channelStateStore.isFavorite(item)) getString(R.string.favorite_enabled) else null)
                .filterNotNull()
                .filter { it.isNotBlank() }
                .joinToString("  •  ")
                .ifBlank { item.description },
            contentKind = item.kind,
            onNavigateChannel = ::navigateChannel,
            onNavigateOption = ::navigateOption,
            onDirectChannelNumber = ::navigateToChannelNumber,
            onToggleFavorite = { channelStateStore.toggleFavorite(item) },
            onOpenFavorites = ::openFavoriteChannel,
            onOpenRecents = ::openRecentChannel,
        )

        fragmentManager.beginTransaction()
            .replace(R.id.player_container, playerFragment, "player_fragment")
            .commitNow()

        container.visibility = View.VISIBLE
        if (item.kind == ContentKind.CHANNEL) {
            channelStateStore.markRecent(item)
        }
        Toast.makeText(context, item.title, Toast.LENGTH_SHORT).show()
    }

    private fun navigateChannel(direction: Int) {
        val current = currentItem ?: return
        if (current.kind == ContentKind.CHANNEL && channels.isNotEmpty()) {
            val currentIndex = channels.indexOfFirst { it.stableId == current.stableId }
            if (currentIndex == -1) return
            val targetIndex = currentIndex + direction
            if (targetIndex !in channels.indices) {
                Toast.makeText(context, if (direction > 0) R.string.no_more_results else R.string.first_result_reached, Toast.LENGTH_SHORT).show()
                return
            }
            playCatalogItem(channels[targetIndex], 0, targetIndex)
            return
        }

        if (currentItemPosition < 0 || rowsAdapter.size() == 0) return

        val row = rowsAdapter.get(0) as? ListRow ?: return
        val listAdapter = row.adapter as? ArrayObjectAdapter ?: return
        val newPosition = currentItemPosition + direction

        if (newPosition !in 0 until listAdapter.size()) {
            val message = if (direction > 0) {
                getString(R.string.no_more_results)
            } else {
                getString(R.string.first_result_reached)
            }
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
            return
        }

        val newItem = listAdapter.get(newPosition) as? CatalogItem ?: return
        playCatalogItem(newItem, 0, newPosition)
    }

    private fun navigateOption(direction: Int) {
        val item = currentItem ?: return
        if (item.streamOptions.size <= 1) {
            Toast.makeText(context, R.string.single_option_only, Toast.LENGTH_SHORT).show()
            return
        }

        val newIndex = currentStreamIndex + direction
        if (newIndex !in item.streamOptions.indices) {
            val message = if (direction > 0) {
                getString(R.string.last_option_reached)
            } else {
                getString(R.string.first_option_reached)
            }
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
            return
        }

        playCatalogItem(item, newIndex, currentItemPosition)
    }

    private fun showStreamSelector(item: CatalogItem, itemPosition: Int) {
        val labels = item.streamOptions.map(StreamOption::label).toTypedArray()

        AlertDialog.Builder(requireContext())
            .setTitle(item.title)
            .setItems(labels) { dialog, which ->
                playCatalogItem(item, which, itemPosition)
                dialog.dismiss()
            }
            .setNegativeButton(R.string.cancel) { dialog, _ -> dialog.dismiss() }
            .show()
    }

    private fun navigateToChannelNumber(number: Int): Boolean {
        val match = channels.firstOrNull { it.channelNumber == number } ?: return false
        val position = channels.indexOfFirst { it.stableId == match.stableId }
        if (position == -1) return false
        playCatalogItem(match, 0, position)
        return true
    }

    private fun openFavoriteChannel(): Boolean {
        val favoriteIds = channelStateStore.favoriteIds()
        val match = channels.firstOrNull { favoriteIds.contains(it.stableId) } ?: return false
        val position = channels.indexOfFirst { it.stableId == match.stableId }
        if (position == -1) return false
        playCatalogItem(match, 0, position)
        return true
    }

    private fun openRecentChannel(): Boolean {
        val itemsById = channels.associateBy(CatalogItem::stableId)
        val match = channelStateStore.recentIds().drop(1).mapNotNull(itemsById::get).firstOrNull()
            ?: channelStateStore.recentIds().mapNotNull(itemsById::get).firstOrNull()
            ?: return false
        val position = channels.indexOfFirst { it.stableId == match.stableId }
        if (position == -1) return false
        playCatalogItem(match, 0, position)
        return true
    }
}

class CardPresenter : Presenter() {

    override fun onCreateViewHolder(parent: ViewGroup): ViewHolder {
        val cardView = ImageCardView(parent.context).apply {
            layoutParams = ViewGroup.LayoutParams(CARD_WIDTH, ViewGroup.LayoutParams.WRAP_CONTENT)
            isFocusable = true
            isFocusableInTouchMode = true
            setMainImageDimensions(CARD_WIDTH, CARD_HEIGHT)
            setBackgroundColor(ContextCompat.getColor(context, R.color.card_surface))
            setInfoAreaBackgroundColor(ContextCompat.getColor(context, R.color.card_info_surface))
        }

        return ViewHolder(cardView)
    }

    override fun onBindViewHolder(viewHolder: ViewHolder, item: Any?) {
        val cardItem = item as? CatalogItem ?: return
        val cardView = viewHolder.view as ImageCardView
        val context = cardView.context

        cardView.titleText = if (cardItem.channelNumber != null) {
            cardView.context.getString(R.string.channel_card_title, cardItem.channelNumber, cardItem.title)
        } else {
            cardItem.title
        }
        cardView.contentText = cardItem.subtitle.ifBlank { cardItem.description }

        if (cardItem.kind == ContentKind.EVENT) {
            cardView.mainImage = BitmapDrawable(context.resources, createEventCategoryBitmap(cardItem))
            return
        }

        if (cardItem.imageUrl.isBlank()) {
            cardView.mainImage = BitmapDrawable(context.resources, createPlaceholderBitmap(cardItem))
            return
        }

        Glide.with(context)
            .asBitmap()
            .load(cardItem.imageUrl)
            .override(CARD_WIDTH, CARD_HEIGHT)
            .into(object : CustomTarget<Bitmap>(CARD_WIDTH, CARD_HEIGHT) {
                override fun onResourceReady(resource: Bitmap, transition: Transition<in Bitmap>?) {
                    val decorated = decorateBitmap(resource, cardItem)
                    cardView.mainImage = BitmapDrawable(context.resources, decorated)
                }

                override fun onLoadCleared(placeholder: android.graphics.drawable.Drawable?) {
                    cardView.mainImage = BitmapDrawable(context.resources, createPlaceholderBitmap(cardItem))
                }
            })
    }

    override fun onUnbindViewHolder(viewHolder: ViewHolder) {
        val cardView = viewHolder.view as ImageCardView
        cardView.mainImageView?.let { Glide.with(cardView.context).clear(it) }
        cardView.badgeImage = null
        cardView.mainImage = null
    }

    private fun decorateBitmap(source: Bitmap, item: CatalogItem): Bitmap {
        if (item.kind == ContentKind.CHANNEL) {
            return createChannelLogoBitmap(source, item)
        }

        val bitmap = Bitmap.createScaledBitmap(source, CARD_WIDTH, CARD_HEIGHT, true)
            .let { scaled ->
                if (scaled.isMutable) scaled
                else scaled.copy(Bitmap.Config.ARGB_8888, true).also { scaled.recycle() }
            }
        val canvas = Canvas(bitmap)

        val bottomShader = LinearGradient(
            0f,
            CARD_HEIGHT * 0.45f,
            0f,
            CARD_HEIGHT.toFloat(),
            Color.TRANSPARENT,
            Color.parseColor("#DD081018"),
            Shader.TileMode.CLAMP,
        )

        val bottomPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { shader = bottomShader }
        canvas.drawRect(0f, 0f, CARD_WIDTH.toFloat(), CARD_HEIGHT.toFloat(), bottomPaint)

        drawBadge(canvas, item.badgeText, item.kind)
        drawCornerTag(canvas, item.kind)

        return bitmap
    }

    private fun createPlaceholderBitmap(item: CatalogItem): Bitmap {
        if (item.kind == ContentKind.CHANNEL) {
            return createChannelPlaceholderBitmap(item)
        }

        val bitmap = Bitmap.createBitmap(CARD_WIDTH, CARD_HEIGHT, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val gradient = LinearGradient(
            0f,
            0f,
            CARD_WIDTH.toFloat(),
            CARD_HEIGHT.toFloat(),
            getStartColor(item.kind),
            getEndColor(item.kind),
            Shader.TileMode.CLAMP,
        )

        val backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { shader = gradient }
        canvas.drawRoundRect(
            RectF(0f, 0f, CARD_WIDTH.toFloat(), CARD_HEIGHT.toFloat()),
            22f,
            22f,
            backgroundPaint,
        )

        drawBadge(canvas, item.badgeText, item.kind)
        drawCornerTag(canvas, item.kind)

        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 28f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }

        val title = item.title.take(26)
        val bounds = Rect()
        textPaint.getTextBounds(title, 0, title.length, bounds)
        canvas.drawText(title, 22f, CARD_HEIGHT - 28f, textPaint)

        return bitmap
    }

    private fun createChannelLogoBitmap(source: Bitmap, item: CatalogItem): Bitmap {
        val bitmap = Bitmap.createBitmap(CARD_WIDTH, CARD_HEIGHT, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val gradient = LinearGradient(
            0f,
            0f,
            CARD_WIDTH.toFloat(),
            CARD_HEIGHT.toFloat(),
            Color.parseColor("#0A1018"),
            Color.parseColor("#141C24"),
            Shader.TileMode.CLAMP,
        )
        val backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { shader = gradient }
        canvas.drawRoundRect(RectF(0f, 0f, CARD_WIDTH.toFloat(), CARD_HEIGHT.toFloat()), 22f, 22f, backgroundPaint)

        val panelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#18242F") }
        canvas.drawRoundRect(RectF(16f, 16f, CARD_WIDTH - 16f, CARD_HEIGHT - 48f), 18f, 18f, panelPaint)

        val target = fitRect(source.width.toFloat(), source.height.toFloat(), RectF(34f, 28f, CARD_WIDTH - 34f, CARD_HEIGHT - 64f))
        canvas.drawBitmap(source, null, target, Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))

        val footerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#CC081018") }
        canvas.drawRoundRect(RectF(0f, CARD_HEIGHT - 52f, CARD_WIDTH.toFloat(), CARD_HEIGHT.toFloat()), 0f, 0f, footerPaint)

        drawBadge(canvas, item.badgeText, item.kind)
        drawChannelNumber(canvas, item.channelNumber)

        return bitmap
    }

    private fun createChannelPlaceholderBitmap(item: CatalogItem): Bitmap {
        val bitmap = Bitmap.createBitmap(CARD_WIDTH, CARD_HEIGHT, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val gradient = LinearGradient(
            0f,
            0f,
            CARD_WIDTH.toFloat(),
            CARD_HEIGHT.toFloat(),
            Color.parseColor("#111A22"),
            Color.parseColor("#1C2631"),
            Shader.TileMode.CLAMP,
        )
        val backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { shader = gradient }
        canvas.drawRoundRect(RectF(0f, 0f, CARD_WIDTH.toFloat(), CARD_HEIGHT.toFloat()), 22f, 22f, backgroundPaint)

        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#8FA5B8")
            textSize = 64f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText("TV", CARD_WIDTH / 2f, CARD_HEIGHT / 2f + 18f, textPaint)
        drawChannelNumber(canvas, item.channelNumber)
        drawBadge(canvas, item.badgeText, item.kind)
        return bitmap
    }

    private fun createEventCategoryBitmap(item: CatalogItem): Bitmap {
        val bitmap = Bitmap.createBitmap(CARD_WIDTH, CARD_HEIGHT, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val (startColor, endColor) = getEventCategoryColors(item.group)

        val gradient = LinearGradient(
            0f,
            0f,
            CARD_WIDTH.toFloat(),
            CARD_HEIGHT.toFloat(),
            startColor,
            endColor,
            Shader.TileMode.CLAMP,
        )
        val backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { shader = gradient }
        canvas.drawRoundRect(RectF(0f, 0f, CARD_WIDTH.toFloat(), CARD_HEIGHT.toFloat()), 22f, 22f, backgroundPaint)

        val orbPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#22FFFFFF") }
        canvas.drawCircle(CARD_WIDTH * 0.78f, CARD_HEIGHT * 0.22f, 42f, orbPaint)
        canvas.drawCircle(CARD_WIDTH * 0.18f, CARD_HEIGHT * 0.76f, 64f, orbPaint)

        val stripePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#18FFFFFF")
            strokeWidth = 10f
            style = Paint.Style.STROKE
        }
        canvas.drawLine(22f, CARD_HEIGHT * 0.22f, CARD_WIDTH - 22f, CARD_HEIGHT * 0.58f, stripePaint)
        canvas.drawLine(0f, CARD_HEIGHT * 0.48f, CARD_WIDTH.toFloat(), CARD_HEIGHT * 0.9f, stripePaint)

        val categoryText = getEventCategoryLabel(item.group)
        val categoryPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#E6FFFFFF")
            textSize = 34f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        canvas.drawText(categoryText, 24f, CARD_HEIGHT - 62f, categoryPaint)

        val subtitlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#CCFFFFFF")
            textSize = 22f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        }
        canvas.drawText("Evento destacado", 24f, CARD_HEIGHT - 28f, subtitlePaint)

        drawBadge(canvas, item.badgeText, item.kind)
        drawCornerTag(canvas, item.kind)
        return bitmap
    }

    private fun drawChannelNumber(canvas: Canvas, channelNumber: Int?) {
        if (channelNumber == null) return
        val text = channelNumber.toString()
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 28f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        canvas.drawText(text, 18f, CARD_HEIGHT - 18f, textPaint)
    }

    private fun fitRect(sourceWidth: Float, sourceHeight: Float, targetBounds: RectF): RectF {
        val sourceRatio = sourceWidth / sourceHeight
        val targetRatio = targetBounds.width() / targetBounds.height()
        return if (sourceRatio > targetRatio) {
            val scaledHeight = targetBounds.width() / sourceRatio
            val top = targetBounds.top + (targetBounds.height() - scaledHeight) / 2f
            RectF(targetBounds.left, top, targetBounds.right, top + scaledHeight)
        } else {
            val scaledWidth = targetBounds.height() * sourceRatio
            val left = targetBounds.left + (targetBounds.width() - scaledWidth) / 2f
            RectF(left, targetBounds.top, left + scaledWidth, targetBounds.bottom)
        }
    }

    private fun getEventCategoryColors(category: String): Pair<Int, Int> {
        val normalized = category.lowercase()
        return when {
            normalized.contains("fut") -> Color.parseColor("#0B6E4F") to Color.parseColor("#1A936F")
            normalized.contains("tenis") -> Color.parseColor("#254441") to Color.parseColor("#43AA8B")
            normalized.contains("mma") || normalized.contains("ufc") || normalized.contains("box") -> Color.parseColor("#5F0F40") to Color.parseColor("#9A031E")
            normalized.contains("basket") || normalized.contains("nba") -> Color.parseColor("#7F4F24") to Color.parseColor("#D68C45")
            normalized.contains("motor") || normalized.contains("f1") || normalized.contains("moto") -> Color.parseColor("#1D3557") to Color.parseColor("#457B9D")
            else -> Color.parseColor("#102A43") to Color.parseColor("#D64550")
        }
    }

    private fun getEventCategoryLabel(category: String): String {
        val normalized = category.lowercase()
        return when {
            normalized.contains("fut") -> "FUTBOL"
            normalized.contains("tenis") -> "TENIS"
            normalized.contains("mma") || normalized.contains("ufc") -> "MMA"
            normalized.contains("box") -> "BOXEO"
            normalized.contains("basket") || normalized.contains("nba") -> "BASKET"
            normalized.contains("motor") || normalized.contains("f1") -> "MOTOR"
            else -> category.uppercase().ifBlank { "EVENTO" }.take(12)
        }
    }

    private fun drawBadge(canvas: Canvas, badgeText: String, kind: ContentKind) {
        if (badgeText.isBlank()) return

        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = if (kind == ContentKind.EVENT) Color.parseColor("#E84855") else Color.parseColor("#1C9D8D")
        }
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 24f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }

        val bounds = Rect()
        textPaint.getTextBounds(badgeText, 0, badgeText.length, bounds)

        val left = 18f
        val top = 18f
        val right = left + bounds.width() + 34f
        val bottom = top + bounds.height() + 22f
        canvas.drawRoundRect(RectF(left, top, right, bottom), 18f, 18f, paint)
        canvas.drawText(badgeText, left + 17f, bottom - 12f, textPaint)
    }

    private fun drawCornerTag(canvas: Canvas, kind: ContentKind) {
        val label = when (kind) {
            ContentKind.EVENT -> "EVENTO"
            ContentKind.CHANNEL -> "CANAL"
            ContentKind.MOVIE -> "CINE"
            ContentKind.SERIES -> "SERIE"
        }

        val background = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#B2131C24")
        }
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 22f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }

        val bounds = Rect()
        textPaint.getTextBounds(label, 0, label.length, bounds)
        val right = CARD_WIDTH - 18f
        val left = right - bounds.width() - 30f
        val top = CARD_HEIGHT - bounds.height() - 28f
        val bottom = CARD_HEIGHT - 16f

        canvas.drawRoundRect(RectF(left, top, right, bottom), 18f, 18f, background)
        canvas.drawText(label, left + 15f, bottom - 12f, textPaint)
    }

    private fun getStartColor(kind: ContentKind): Int {
        return when (kind) {
            ContentKind.EVENT -> Color.parseColor("#102A43")
            ContentKind.CHANNEL -> Color.parseColor("#0F4C5C")
            ContentKind.MOVIE -> Color.parseColor("#5A1E2C")
            ContentKind.SERIES -> Color.parseColor("#3A4E48")
        }
    }

    private fun getEndColor(kind: ContentKind): Int {
        return when (kind) {
            ContentKind.EVENT -> Color.parseColor("#D64550")
            ContentKind.CHANNEL -> Color.parseColor("#1C9D8D")
            ContentKind.MOVIE -> Color.parseColor("#F18F01")
            ContentKind.SERIES -> Color.parseColor("#7C9885")
        }
    }

    companion object {
        private const val CARD_WIDTH = 340
        private const val CARD_HEIGHT = 190
    }
}
