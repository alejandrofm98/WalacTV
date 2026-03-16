# Series Grouping and Details Screen Design

## Goal
Improve the user experience in the "Series" section by preventing grid clutter. Instead of showing every episode in the main grid, show only a single poster per Series. Upon clicking a series, navigate to a new detail screen to select seasons and episodes (Netflix style layout).

## Current State
- `M3uCatalogStore.kt` creates a `CatalogItem` for every M3U line classified as `ContentKind.SERIES`.
- `ComposeMainFragment.kt`'s `VodGridContent` renders all `SERIES` items in the grid directly.
- The episode name formats usually follow: "Series Title S01 E01" (with possible variations like S1 E1).

## Proposed Design

### 1. Model & Parsing Updates (Series Grouping)
- We will add extension properties/functions on `CatalogItem` to extract `seriesName`, `seasonNumber`, and `episodeNumber` using Regular Expressions targeting the "S01 E01" format.
  - Regex logic: Search for `S\d+\s*E\d+` (case insensitive). Everything before it is the `seriesName`.
- During `ComposeMainFragment.kt`'s filtering logic in `VodGridContent`:
  - When `kind == ContentKind.SERIES`, we group the `searchableItems` by `seriesName`.
  - We will display only the first episode's `CatalogItem` (or a synthesized "Series" `CatalogItem`) as the poster in the main grid, labeled with the `seriesName`.

### 2. Series Detail Flow (`SeriesDetailFragment`)
- We will introduce a new fragment: `SeriesDetailFragment` to match the Leanback navigation flow (like `PlayerFragment` and `SearchFragment`).
- When a user clicks a grouped series card in the `ComposeMainFragment`, it will start a transaction replacing the current fragment with `SeriesDetailFragment`, passing the `seriesName` (and potentially the pre-filtered list of episodes or fetching them from `CatalogMemory`).

### 3. UI Components (`SeriesDetailContent`)
The new screen will be built in Jetpack Compose and contain:
- **Hero Header:** At the top left/right, show the poster image, the title of the series (`seriesName`), and the total number of seasons/episodes.
- **Season Selector Button:** A button below the header showing the currently selected season (e.g., "Temporada 1 ▾").
  - Clicking this opens the reusable `FilterDialog` (created in the previous redesign) to let the user pick a different season.
- **Episode List:** A vertical `LazyColumn` showing the episodes of the currently selected season.
  - Each row shows the episode number and title.
  - It handles D-pad focus correctly (`IptvFocusBg` highlight).
- **Playback:** Clicking an episode row invokes the parent activity's logic to open `PlayerFragment` with that specific `CatalogItem`.

### 4. Integration
- The `MainActivity` or `ComposeMainFragment` interface will need to handle the navigation to the new `SeriesDetailFragment`.
- Given the current architecture uses `requireActivity().supportFragmentManager.beginTransaction()` for `SearchFragment` and `PlayerFragment`, we will do the same for the series details.

## Trade-offs
- **Pros:** Massively cleans up the Series grid. Makes browsing series intuitive and similar to modern VOD platforms. Reuses existing dialog and focus concepts.
- **Cons:** Requires a new screen and somewhat brittle string parsing (M3U lists are notoriously inconsistent). If a series name lacks the "S01 E01" pattern, it will fall back to being displayed as an individual item in the grid, which is acceptable fail-safe behavior.