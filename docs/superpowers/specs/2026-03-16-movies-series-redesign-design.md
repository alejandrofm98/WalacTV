# Movies and Series Screen Redesign

## Goal
Improve the "Movies" and "Series" sections in the WalacTV Android TV application to show more items per row (4 to 6), filter by Language and Group, and free up horizontal screen real estate.

## Current State
- The `VodGridContent` composable in `ComposeMainFragment.kt` renders a `FilterColumn` on the left side taking 248dp of width.
- The right side shows a `LazyVerticalGrid` using `GridCells.Adaptive(190.dp)`, which shows fewer items per row due to the filter column taking up space.
- The `group` field in the M3U metadata currently contains the entire group title (e.g., `ES | Action`).

## Proposed Design

### 1. Model Updates
- We will introduce utility extension functions on `CatalogItem` to extract the "Language" (`idioma`) and "Sub-group" (`grupo`) from the full `group` string.
- The string will be split by the first `|` or `-`. 
- Both parts must be `trim()`med to avoid whitespace issues (e.g. "ES" and "Action"). 
- If no separator exists, the `idioma` is "Todos" and the `grupo` is the full trimmed string.

### 2. UI Components & Interfaces
We will introduce distinct composables with clear boundaries:

- **FilterTopBar**: `(selectedIdioma: String, selectedGrupo: String, onIdiomaClicked: () -> Unit, onGrupoClicked: () -> Unit)`
  Renders the top row buttons.
- **FilterDialog**: `(title: String, options: List<String>, selectedOption: String, onOptionSelected: (String) -> Unit, onDismiss: () -> Unit)`
  A TV-optimized full-screen or centered `Dialog` containing a focused `LazyColumn` for selection.
- **Grid Real Estate**: The `LazyVerticalGrid` will span the full width of the parent container minus standard padding, and use `GridCells.Fixed(5)` to guarantee exactly 5 items per row regardless of screen density.

### 3. TV-Optimized Dropdown (Filters)
- Standard Compose dropdowns can behave unpredictably on Android TV with D-Pad navigation.
- The `FilterDialog` will handle the selection. Clicking a filter button opens this dialog. The user selects an option with DPAD CENTER, the dialog closes (`onDismiss`), and the grid updates.
- Focus restoration: When `FilterDialog` closes, Compose should automatically return focus to the button that opened it.

### 4. Logic & State Management
- `selectedIdioma` and `selectedGrupo` state will be added to `VodGridContent`.
- Available Languages (`idiomas`) will be dynamically calculated from the `searchableItems`.
- Available Groups (`grupos`) will be dynamically calculated based on the currently selected Language.
- **State Reset Logic**: If `selectedIdioma` changes, `selectedGrupo` MUST be reset to "Todos" to prevent invalid filter combinations and empty states.
- "Todos" will remain an option to clear the filters for both Language and Group.

### 5. Implementation Steps
1. Modify `ComposeMainFragment.kt` to extract `idioma` and `grupo`.
2. Replace `FilterColumn` with `FilterTopBar`.
3. Implement `FilterDialog` to show selectable options for the filters.
4. Update the `LazyVerticalGrid` to use `GridCells.Fixed(5)`.
5. Implement the state reset logic for `selectedGrupo` when `selectedIdioma` changes.
