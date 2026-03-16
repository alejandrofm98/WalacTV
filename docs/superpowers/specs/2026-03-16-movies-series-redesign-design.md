# Movies and Series Screen Redesign

## Goal
Improve the "Movies" and "Series" sections in the WalacTV Android TV application to show more items per row (4 to 6), filter by Language and Group, and free up horizontal screen real estate.

## Current State
- The `VodGridContent` composable in `ComposeMainFragment.kt` renders a `FilterColumn` on the left side taking 248dp of width.
- The right side shows a `LazyVerticalGrid` using `GridCells.Adaptive(190.dp)`, which shows fewer items per row due to the filter column taking up space.
- The `group` field in the M3U metadata currently contains the entire group title (e.g., `ES | Action`).

## Proposed Design

### 1. Model Updates
- The `CatalogItem` model remains largely unchanged, but we will introduce utility extension functions to extract the "Language" (`idioma`) and "Sub-group" (`grupo`) from the full `group` string.
- The prefix before the first `|` or `-` (if any) will be considered the "Idioma". The remainder of the string will be the "Grupo". If no separator exists, the language is "Todos" and the group is the full string.

### 2. UI Layout (`VodGridContent`)
- **Remove Left Sidebar:** The `FilterColumn` on the left will be removed.
- **Top Bar Filters:** A horizontal row at the top (below the screen header) will house two dropdown-like buttons: "Idioma" and "Grupo".
- **Grid Real Estate:** The `LazyVerticalGrid` will now span the full width of the parent container minus standard padding.
- **Grid Configuration:** The `GridCells.Adaptive` value will be reduced to ~`150.dp` - `160.dp` or changed to `GridCells.Fixed(5)` to comfortably fit 4 to 6 posters per row.

### 3. TV-Optimized Dropdown (Filters)
- Standard Compose dropdowns can behave unpredictably on Android TV with D-Pad navigation.
- We will implement a custom Compose `Dialog` (or full screen overlay) displaying a focused `LazyColumn` to let the user pick from the list of Languages/Groups.
- Clicking the filter buttons opens this dialog. The user selects an option with DPAD CENTER, the dialog closes, and the grid updates.

### 4. Logic & State Management
- `selectedIdioma` state and `selectedGrupo` state will be added to `VodGridContent`.
- Available Languages will be dynamically calculated from the `searchableItems`.
- Available Groups will be dynamically calculated based on the currently selected Language.
- "Todos los canales/grupos" will remain an option to clear the filters.

### 5. Implementation Steps
1. Modify `ComposeMainFragment.kt` to extract language and subgroup.
2. Replace `FilterColumn` with a top bar of filter buttons.
3. Add a Compose `Dialog` to show selectable options for the filters.
4. Update the `LazyVerticalGrid` parameters.
5. Ensure D-Pad focus flows naturally from the filter row down into the grid.

## Trade-offs
- **Pros:** Full width grid allows scanning more posters at a glance. Splitting language and group makes navigation in large playlists much easier.
- **Cons:** It takes an extra click to open a filter compared to simply pressing LEFT and navigating a sidebar, but the gain in visual space is worth the minor friction.