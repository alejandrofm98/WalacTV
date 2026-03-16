# Movies and Series Screen Redesign Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Improve the "Movies" and "Series" sections to show more items per row (4 to 6), filter by Language and Group, and free up horizontal screen real estate.

**Architecture:** We will add JVM unit tests for metadata parsing, add extension properties to `CatalogItem` to extract `idioma` and `subgrupo`. Then, we'll implement new Compose components `FilterTopBar` and `FilterDialog` in a new file `FilterComponents.kt`. Finally, we will replace the `FilterColumn` in `VodGridContent` with the new top bar and update state management to handle two-level filtering (Idioma -> Grupo).

**Tech Stack:** Kotlin, Android Jetpack Compose for TV, JUnit4 (for JVM tests)

---

## Chunk 1: Model Updates and Parsing Logic

### Task 1: Add Unit Tests for `CatalogItem` Extensions

**Files:**
- Create: `app/src/test/java/com/example/walactv/CatalogModelsTest.kt`
- Modify: `app/src/main/java/com/example/walactv/CatalogModels.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
package com.example.walactv

import org.junit.Assert.assertEquals
import org.junit.Test

class CatalogModelsTest {

    private fun createItem(group: String): CatalogItem {
        return CatalogItem(
            stableId = "1",
            title = "Title",
            subtitle = "Subtitle",
            description = "Desc",
            imageUrl = "",
            kind = ContentKind.MOVIE,
            group = group,
            badgeText = "",
            streamOptions = emptyList()
        )
    }

    @Test
    fun `extracts idioma and subgrupo with pipe separator`() {
        val item = createItem("ES | Accion")
        assertEquals("ES", item.idioma)
        assertEquals("Accion", item.subgrupo)
    }

    @Test
    fun `extracts idioma and subgrupo with dash separator`() {
        val item = createItem("LATAM - Comedia")
        assertEquals("LATAM", item.idioma)
        assertEquals("Comedia", item.subgrupo)
    }

    @Test
    fun `extracts idioma and subgrupo with no separator`() {
        val item = createItem("Documentales")
        assertEquals("Todos", item.idioma)
        assertEquals("Documentales", item.subgrupo)
    }

    @Test
    fun `handles extra whitespace around separator`() {
        val item = createItem("EN| Drama ")
        assertEquals("EN", item.idioma)
        assertEquals("Drama", item.subgrupo)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests 'com.example.walactv.CatalogModelsTest'`
Expected: FAIL (unresolved reference `idioma` and `subgrupo`)

- [ ] **Step 3: Write minimal implementation**

Modify: `app/src/main/java/com/example/walactv/CatalogModels.kt`

```kotlin
// Add to the end of the file

val CatalogItem.idioma: String
    get() {
        val separatorIndex = group.indexOfFirst { it == '|' || it == '-' }
        return if (separatorIndex != -1) {
            group.substring(0, separatorIndex).trim()
        } else {
            "Todos"
        }
    }

val CatalogItem.subgrupo: String
    get() {
        val separatorIndex = group.indexOfFirst { it == '|' || it == '-' }
        return if (separatorIndex != -1) {
            group.substring(separatorIndex + 1).trim()
        } else {
            group.trim()
        }
    }
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests 'com.example.walactv.CatalogModelsTest'`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add app/src/test/java/com/example/walactv/CatalogModelsTest.kt app/src/main/java/com/example/walactv/CatalogModels.kt
git commit -m "feat: add idiom and subgrupo extraction logic to CatalogItem"
```

---

## Chunk 2: Compose UI Components for Filtering

### Task 2: Implement `FilterTopBar` and `FilterDialog`

**Files:**
- Create: `app/src/main/java/com/example/walactv/FilterComponents.kt`

- [ ] **Step 1: Create `FilterComponents.kt` and implement UI**

Create `app/src/main/java/com/example/walactv/FilterComponents.kt`:

```kotlin
package com.example.walactv

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.tv.material3.Text
import com.example.walactv.ui.theme.*

@Composable
fun FilterTopBar(
    selectedIdioma: String,
    selectedGrupo: String,
    onIdiomaClicked: () -> Unit,
    onGrupoClicked: () -> Unit,
    idiomaFocusRequester: FocusRequester,
    grupoFocusRequester: FocusRequester,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        FilterTopBarButton(
            label = "Idioma: $selectedIdioma",
            onClick = onIdiomaClicked,
            focusRequester = idiomaFocusRequester
        )
        FilterTopBarButton(
            label = "Grupo: $selectedGrupo",
            onClick = onGrupoClicked,
            focusRequester = grupoFocusRequester
        )
    }
}

@Composable
fun FilterTopBarButton(label: String, onClick: () -> Unit, focusRequester: FocusRequester) {
    var isFocused by remember { mutableStateOf(false) }
    val backgroundColor = if (isFocused) IptvFocusBg else IptvCard
    val borderColor = if (isFocused) IptvFocusBorder else IptvSurfaceVariant
    val contentColor = if (isFocused) IptvTextPrimary else IptvTextMuted

    Box(
        modifier = Modifier
            .background(backgroundColor, RoundedCornerShape(8.dp))
            .border(1.dp, borderColor, RoundedCornerShape(8.dp))
            .clickable { onClick() }
            .focusRequester(focusRequester)
            .focusable()
            .onFocusChanged { isFocused = it.isFocused }
            .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        Text(
            text = "$label ▾",
            color = contentColor,
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
fun FilterDialog(
    title: String,
    options: List<String>,
    selectedOption: String,
    onOptionSelected: (String) -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.7f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) { onDismiss() },
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .width(360.dp)
                    .background(IptvSurface, RoundedCornerShape(12.dp))
                    .border(1.dp, IptvSurfaceVariant, RoundedCornerShape(12.dp))
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(title, color = IptvTextPrimary, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
                
                val listState = rememberLazyListState()
                LaunchedEffect(options) {
                    val index = options.indexOf(selectedOption)
                    if (index > 0) {
                        listState.scrollToItem(index)
                    }
                }

                LazyColumn(
                    state = listState,
                    modifier = Modifier.heightIn(max = 400.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(options) { option ->
                        DialogFilterItem(
                            label = option,
                            selected = option == selectedOption,
                            onClick = { onOptionSelected(option) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun DialogFilterItem(label: String, selected: Boolean, onClick: () -> Unit) {
    var isFocused by remember { mutableStateOf(false) }
    val backgroundColor = when {
        isFocused -> IptvFocusBg
        selected -> IptvCard
        else -> Color.Transparent
    }
    val borderColor = when {
        isFocused -> IptvFocusBorder
        selected -> IptvSurfaceVariant
        else -> Color.Transparent
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(backgroundColor, RoundedCornerShape(8.dp))
            .border(1.dp, borderColor, RoundedCornerShape(8.dp))
            .clickable { onClick() }
            .focusable()
            .onFocusChanged {
                isFocused = it.isFocused
                // Removed the instant selection on focus change that was here
            }
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Text(
            label,
            color = if (isFocused || selected) IptvTextPrimary else IptvTextMuted,
            fontSize = 15.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
```

- [ ] **Step 2: Verify Compilation**

Run: `./gradlew :app:assembleDebug`
Expected: SUCCESS

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/example/walactv/FilterComponents.kt
git commit -m "feat(ui): add extracted FilterComponents for TV layout"
```

---

## Chunk 3: Update `VodGridContent` Logic and Layout

### Task 3: Replace `FilterColumn` with `FilterTopBar` and adjust `LazyVerticalGrid`

**Files:**
- Modify: `app/src/main/java/com/example/walactv/ComposeMainFragment.kt`

- [ ] **Step 1: Update State and Calculation in `VodGridContent`**

Locate `VodGridContent` in `ComposeMainFragment.kt`. Modify the state and memoized lists to use `selectedIdioma` and `selectedGrupo` instead of the old `groups` calculation. Be sure to import `androidx.compose.ui.focus.FocusRequester` if not imported.

Replace the beginning of `VodGridContent` down to the `Column` with this (but be sure to keep the annotations and function signature):
```kotlin
    @Composable
    private fun VodGridContent(kind: ContentKind) {
        var selectedIdioma by remember { mutableStateOf(ALL_CHANNELS_GROUP) }
        var selectedGrupo by remember { mutableStateOf(ALL_CHANNELS_GROUP) }
        
        var showIdiomaDialog by remember { mutableStateOf(false) }
        var showGrupoDialog by remember { mutableStateOf(false) }
        
        val idiomaFocusRequester = remember { androidx.compose.ui.focus.FocusRequester() }
        val grupoFocusRequester = remember { androidx.compose.ui.focus.FocusRequester() }

        val sourceItems = remember(searchableItems, kind) {
            searchableItems.filter { it.kind == kind }
        }

        val idiomas = remember(sourceItems) {
            buildList {
                add(ALL_CHANNELS_GROUP)
                sourceItems.map(CatalogItem::idioma)
                    .filter { it.isNotBlank() && it != "Todos" }
                    .distinct()
                    .sorted()
                    .forEach(::add)
            }
        }

        val gruposForIdioma = remember(selectedIdioma, sourceItems) {
            val filteredByIdioma = if (selectedIdioma == ALL_CHANNELS_GROUP) {
                sourceItems
            } else {
                sourceItems.filter { it.idioma == selectedIdioma }
            }
            buildList {
                add(ALL_CHANNELS_GROUP)
                filteredByIdioma.map(CatalogItem::subgrupo)
                    .filter { it.isNotBlank() && it != "Todos" }
                    .distinct()
                    .sorted()
                    .forEach(::add)
            }
        }

        val displayItems = remember(selectedIdioma, selectedGrupo, sourceItems) {
            var items = sourceItems
            if (selectedIdioma != ALL_CHANNELS_GROUP) {
                items = items.filter { it.idioma == selectedIdioma }
            }
            if (selectedGrupo != ALL_CHANNELS_GROUP) {
                items = items.filter { it.subgrupo == selectedGrupo }
            }
            items
        }
```

- [ ] **Step 2: Update Layout in `VodGridContent`**

Replace the `Row` block in `VodGridContent` that contained `FilterColumn` and `LazyVerticalGrid` with a `Column` that stacks `FilterTopBar` and `LazyVerticalGrid`. Include the focus request wrappers so focus correctly returns.

```kotlin
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                FilterTopBar(
                    selectedIdioma = selectedIdioma,
                    selectedGrupo = selectedGrupo,
                    onIdiomaClicked = { showIdiomaDialog = true },
                    onGrupoClicked = { showGrupoDialog = true },
                    idiomaFocusRequester = idiomaFocusRequester,
                    grupoFocusRequester = grupoFocusRequester
                )

                LazyVerticalGrid(
                    columns = GridCells.Fixed(5),
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(bottom = 24.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    gridItems(displayItems) { item ->
                        MediaCard(item = item, onFocused = { selectedHero = item }) {
                            handleCardClick(item)
                        }
                    }
                }
            }

            if (showIdiomaDialog) {
                FilterDialog(
                    title = "Selecciona Idioma",
                    options = idiomas,
                    selectedOption = selectedIdioma,
                    onOptionSelected = {
                        selectedIdioma = it
                        selectedGrupo = ALL_CHANNELS_GROUP // Reset group on language change
                        selectedHero = null
                        showIdiomaDialog = false
                    },
                    onDismiss = { showIdiomaDialog = false }
                )
            }
            
            LaunchedEffect(showIdiomaDialog) {
                if (!showIdiomaDialog) {
                    runCatching { idiomaFocusRequester.requestFocus() }
                }
            }

            if (showGrupoDialog) {
                FilterDialog(
                    title = "Selecciona Grupo",
                    options = gruposForIdioma,
                    selectedOption = selectedGrupo,
                    onOptionSelected = {
                        selectedGrupo = it
                        selectedHero = null
                        showGrupoDialog = false
                    },
                    onDismiss = { showGrupoDialog = false }
                )
            }

            LaunchedEffect(showGrupoDialog) {
                if (!showGrupoDialog) {
                    runCatching { grupoFocusRequester.requestFocus() }
                }
            }
```

- [ ] **Step 3: Verify Compilation and Linting**

Run: `./gradlew :app:assembleDebug`
Expected: SUCCESS
Run: `./gradlew :app:lintDebug`
Expected: SUCCESS (or existing warnings only)

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/example/walactv/ComposeMainFragment.kt
git commit -m "feat(ui): redesign movies and series grid with top filters and 5 columns"
```
