# Series Grouping Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Group series episodes into a single card per series on the grid, and navigate to a detailed Season/Episode view upon clicking a series.

**Architecture:** Add `seriesName`, `seasonNumber`, and `episodeNumber` extraction to `CatalogModels.kt` via regex. Modify `ComposeMainFragment.kt`'s `VodGridContent` to group series and show dummy "Folder" items. Create `SeriesDetailFragment.kt` with a Compose UI using the existing `FilterDialog` for season selection, pulling episode data from `CatalogMemory`.

**Tech Stack:** Kotlin, Jetpack Compose for Android TV, JUnit4

---

## Chunk 1: Model Parsing and Grouping Logic

### Task 1: Add Unit Tests for Series Extractor

**Files:**
- Modify: `app/src/test/java/com/example/walactv/CatalogModelsTest.kt`
- Modify: `app/src/main/java/com/example/walactv/CatalogModels.kt`

- [ ] **Step 1: Write the failing test**

Modify `CatalogModelsTest.kt` to add series extraction tests:

```kotlin
    @Test
    fun `extracts series info from English format`() {
        val item = createItem("").copy(title = "Breaking Bad S01 E05 - Ozymandias")
        assertEquals("Breaking Bad", item.seriesName)
        assertEquals(1, item.seasonNumber)
        assertEquals(5, item.episodeNumber)
    }

    @Test
    fun `extracts series info from Spanish format`() {
        val item = createItem("").copy(title = "Los Soprano T06 E12")
        assertEquals("Los Soprano", item.seriesName)
        assertEquals(6, item.seasonNumber)
        assertEquals(12, item.episodeNumber)
    }

    @Test
    fun `returns nulls for non-matching series`() {
        val item = createItem("").copy(title = "Just A Movie Name")
        assertEquals("Just A Movie Name", item.seriesName)
        assertEquals(null, item.seasonNumber)
        assertEquals(null, item.episodeNumber)
    }
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests 'com.example.walactv.CatalogModelsTest'`
Expected: FAIL (unresolved references)

- [ ] **Step 3: Write minimal implementation**

Modify `CatalogModels.kt`:

```kotlin
private val SERIES_REGEX = Regex("(?i)[ST](\\d+)\\s*E(\\d+)")

val CatalogItem.seriesName: String
    get() {
        if (kind != ContentKind.SERIES) return title
        val match = SERIES_REGEX.find(title)
        return if (match != null) {
            title.substring(0, match.range.first).trim().removeSuffix("-").trim()
        } else {
            title
        }
    }

val CatalogItem.seasonNumber: Int?
    get() {
        if (kind != ContentKind.SERIES) return null
        return SERIES_REGEX.find(title)?.groupValues?.get(1)?.toIntOrNull()
    }

val CatalogItem.episodeNumber: Int?
    get() {
        if (kind != ContentKind.SERIES) return null
        return SERIES_REGEX.find(title)?.groupValues?.get(2)?.toIntOrNull()
    }
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests 'com.example.walactv.CatalogModelsTest'`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add app/src/test/java/com/example/walactv/CatalogModelsTest.kt app/src/main/java/com/example/walactv/CatalogModels.kt
git commit -m "feat: add series parsing extensions to CatalogItem"
```

---

## Chunk 2: Grouping Series in Grid

### Task 2: Update `VodGridContent` in `ComposeMainFragment.kt`

**Files:**
- Modify: `app/src/main/java/com/example/walactv/ComposeMainFragment.kt`

- [ ] **Step 1: Group items and synthesize Series folders**

Locate `VodGridContent` in `ComposeMainFragment.kt`. Modify the `displayItems` block:

```kotlin
        val displayItems = remember(selectedIdioma, selectedGrupo, sourceItems) {
            var items = sourceItems
            if (selectedIdioma != ALL_CHANNELS_GROUP) {
                items = items.filter { it.idioma == selectedIdioma }
            }
            if (selectedGrupo != ALL_CHANNELS_GROUP) {
                items = items.filter { it.subgrupo == selectedGrupo }
            }
            
            if (kind == ContentKind.SERIES) {
                val grouped = items.groupBy { it.seriesName }
                grouped.map { (seriesName, episodes) ->
                    val firstEp = episodes.first()
                    CatalogItem(
                        stableId = "series_group:$seriesName",
                        title = seriesName,
                        subtitle = "${episodes.size} episodios",
                        description = firstEp.description,
                        imageUrl = firstEp.imageUrl,
                        kind = ContentKind.SERIES,
                        group = firstEp.group,
                        badgeText = "SERIE",
                        streamOptions = emptyList()
                    )
                }.sortedBy { it.title }
            } else {
                items
            }
        }
```

- [ ] **Step 2: Add click handler for `series_group:` prefix**

Find `handleCardClick(item: CatalogItem)` in `ComposeMainFragment.kt` (or the `onClick` in `VodGridContent`). Let's add it to `handleCardClick`:

```kotlin
    private fun handleCardClick(item: CatalogItem) {
        if (item.stableId.startsWith("series_group:")) {
            val seriesName = item.stableId.removePrefix("series_group:")
            val fragment = SeriesDetailFragment.newInstance(seriesName)
            requireActivity().supportFragmentManager.beginTransaction()
                .replace(R.id.main_browse_fragment, fragment)
                .addToBackStack("SeriesDetailFragment")
                .commit()
            return
        }
        // ... existing playback logic
```

- [ ] **Step 3: Create stub `SeriesDetailFragment.kt`**

Create `app/src/main/java/com/example/walactv/SeriesDetailFragment.kt` to allow compilation:

```kotlin
package com.example.walactv

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.platform.ComposeView
import androidx.fragment.app.Fragment
import androidx.tv.material3.Text

class SeriesDetailFragment : Fragment() {
    companion object {
        private const val ARG_SERIES_NAME = "series_name"
        fun newInstance(seriesName: String) = SeriesDetailFragment().apply {
            arguments = Bundle().apply { putString(ARG_SERIES_NAME, seriesName) }
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val seriesName = arguments?.getString(ARG_SERIES_NAME) ?: ""
        return ComposeView(requireContext()).apply {
            setContent {
                Text("Detalles de: $seriesName")
            }
        }
    }
}
```

- [ ] **Step 4: Verify Compilation**

Run: `./gradlew :app:assembleDebug`
Expected: SUCCESS

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/example/walactv/ComposeMainFragment.kt app/src/main/java/com/example/walactv/SeriesDetailFragment.kt
git commit -m "feat: group series in grid and route to SeriesDetailFragment"
```

---

## Chunk 3: Implement `SeriesDetailFragment` UI

### Task 3: Build the Series UI with Compose

**Files:**
- Modify: `app/src/main/java/com/example/walactv/SeriesDetailFragment.kt`

- [ ] **Step 1: Write Full Compose UI in `SeriesDetailFragment`**

Update `SeriesDetailFragment.kt` entirely:

```kotlin
package com.example.walactv

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.Fragment
import androidx.tv.material3.Text
import com.bumptech.glide.integration.compose.ExperimentalGlideComposeApi
import com.bumptech.glide.integration.compose.GlideImage
import com.example.walactv.ui.theme.*

class SeriesDetailFragment : Fragment() {
    companion object {
        private const val ARG_SERIES_NAME = "series_name"
        fun newInstance(seriesName: String) = SeriesDetailFragment().apply {
            arguments = Bundle().apply { putString(ARG_SERIES_NAME, seriesName) }
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val seriesName = arguments?.getString(ARG_SERIES_NAME) ?: ""
        return ComposeView(requireContext()).apply {
            setContent {
                WalacTVTheme {
                    SeriesDetailScreen(seriesName) { item ->
                        playEpisode(item)
                    }
                }
            }
        }
    }

    private fun playEpisode(item: CatalogItem) {
        val playerFragment = PlayerFragment.newInstance(item, CatalogMemory.searchableItems)
        requireActivity().supportFragmentManager.beginTransaction()
            .replace(R.id.main_browse_fragment, playerFragment)
            .addToBackStack(null)
            .commit()
    }
}

@OptIn(ExperimentalGlideComposeApi::class)
@Composable
fun SeriesDetailScreen(seriesName: String, onEpisodeClick: (CatalogItem) -> Unit) {
    val allEpisodes = remember(seriesName) {
        CatalogMemory.searchableItems
            .filter { it.kind == ContentKind.SERIES && it.seriesName == seriesName }
            .sortedWith(compareBy(nullsLast()) { it.episodeNumber })
    }
    
    val seasons = remember(allEpisodes) {
        allEpisodes.mapNotNull { it.seasonNumber }.distinct().sorted()
    }
    
    var selectedSeason by remember { mutableStateOf(seasons.firstOrNull() ?: 1) }
    var showSeasonDialog by remember { mutableStateOf(false) }
    val seasonFocusRequester = remember { FocusRequester() }

    val displayEpisodes = remember(selectedSeason, allEpisodes) {
        allEpisodes.filter { (it.seasonNumber ?: 1) == selectedSeason }
    }

    val posterUrl = allEpisodes.firstOrNull()?.imageUrl ?: ""

    Box(modifier = Modifier.fillMaxSize().background(IptvBackground).padding(32.dp)) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Hero Header
            Row(modifier = Modifier.fillMaxWidth().height(140.dp), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                if (posterUrl.isNotBlank()) {
                    GlideImage(
                        model = posterUrl,
                        contentDescription = seriesName,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.width(100.dp).fillMaxHeight().clip(RoundedCornerShape(8.dp))
                    )
                } else {
                    Box(modifier = Modifier.width(100.dp).fillMaxHeight().background(IptvSurfaceVariant, RoundedCornerShape(8.dp)))
                }
                
                Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.weight(1f)) {
                    Text(seriesName, color = IptvTextPrimary, fontSize = 32.sp, fontWeight = FontWeight.Bold)
                    Text("${seasons.size} Temporadas • ${allEpisodes.size} Episodios", color = IptvTextMuted, fontSize = 16.sp)
                    
                    Spacer(Modifier.height(8.dp))
                    
                    if (seasons.isNotEmpty()) {
                        FilterTopBarButton(
                            label = "Temporada $selectedSeason",
                            onClick = { showSeasonDialog = true },
                            focusRequester = seasonFocusRequester
                        )
                    }
                }
            }

            Spacer(Modifier.height(24.dp))

            // Episodes List
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth().weight(1f)
            ) {
                items(displayEpisodes) { ep ->
                    EpisodeRow(ep) { onEpisodeClick(ep) }
                }
            }
        }

        if (showSeasonDialog) {
            FilterDialog(
                title = "Selecciona Temporada",
                options = seasons.map { "Temporada $it" },
                selectedOption = "Temporada $selectedSeason",
                onOptionSelected = {
                    selectedSeason = it.removePrefix("Temporada ").toIntOrNull() ?: 1
                    showSeasonDialog = false
                },
                onDismiss = { showSeasonDialog = false }
            )
        }
        
        LaunchedEffect(showSeasonDialog) {
            if (!showSeasonDialog) {
                runCatching { seasonFocusRequester.requestFocus() }
            }
        }
    }
}

@Composable
fun EpisodeRow(item: CatalogItem, onClick: () -> Unit) {
    var isFocused by remember { mutableStateOf(false) }
    
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (isFocused) IptvFocusBg else IptvCard, RoundedCornerShape(8.dp))
            .border(1.dp, if (isFocused) IptvFocusBorder else IptvSurfaceVariant, RoundedCornerShape(8.dp))
            .clickable { onClick() }
            .focusable()
            .onFocusChanged { isFocused = it.isFocused }
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val epNum = item.episodeNumber?.toString() ?: "-"
        Text(epNum, color = if (isFocused) IptvTextPrimary else IptvTextMuted, fontSize = 18.sp, modifier = Modifier.width(40.dp))
        Spacer(Modifier.width(16.dp))
        Column {
            Text(item.title, color = IptvTextPrimary, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("Episodio $epNum", color = IptvTextMuted, fontSize = 14.sp)
        }
    }
}
```

- [ ] **Step 2: Verify Compilation & Linting**

Run: `./gradlew :app:assembleDebug`
Expected: SUCCESS
Run: `./gradlew :app:lintDebug`
Expected: SUCCESS

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/example/walactv/SeriesDetailFragment.kt
git commit -m "feat(ui): implement series detail screen with season selector"
```
