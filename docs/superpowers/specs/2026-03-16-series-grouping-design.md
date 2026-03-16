# Series Grouping and Details Screen Design

## Goal
Improve the user experience in the "Series" section by preventing grid clutter. Instead of showing every episode in the main grid, show only a single poster per Series. Upon clicking a series, navigate to a new detail screen to select seasons and episodes (Netflix style layout).

## Current State
- `M3uCatalogStore.kt` creates a `CatalogItem` for every M3U line classified as `ContentKind.SERIES`.
- `ComposeMainFragment.kt`'s `VodGridContent` renders all `SERIES` items in the grid directly.
- The episode name formats usually follow: "Series Title S01 E01" (with possible variations like S1 E1 or T01 E01 in Spanish).

## Proposed Design

### 1. Model & Parsing Updates (Series Grouping)
- We will add extension properties/functions on `CatalogItem` to extract `seriesName`, `seasonNumber`, and `episodeNumber` using Regular Expressions targeting the "S01 E01" or "T01 E01" format.
  - Regex logic: Search for `(?i)[ST](\d+)\s*E(\d+)`. Everything before it (trimmed) is the `seriesName`.
- During `ComposeMainFragment.kt`'s filtering logic in `VodGridContent`:
  - When `kind == ContentKind.SERIES`, we group the `searchableItems` by `seriesName`.
  - We will synthesize a new `CatalogItem` to represent the "Series Folder". Its `title` will be the `seriesName`, its `imageUrl` will be the first episode's image, and its `streamOptions` will be empty (or a dummy value), but it will carry a specific `stableId` prefix (e.g., `series_group:`) so the click handler knows to open the detail screen rather than play it.

### 2. Series Detail Flow (`SeriesDetailFragment`)
- We will introduce a new fragment: `SeriesDetailFragment`.
- The `ComposeMainFragment` click handler will detect a `series_group:` prefix in the `stableId`. It will then start a transaction replacing the current fragment with `SeriesDetailFragment`, passing the `seriesName` as a String argument.
- The fragment transaction will be added to the back stack (`addToBackStack("SeriesDetailFragment")`) so the TV remote's Back button works correctly.
- `SeriesDetailFragment` will retrieve all episodes for that `seriesName` by querying the in-memory `CatalogMemory.searchableItems`. Passing the `seriesName` string is safe and avoids `TransactionTooLargeException`.

### 3. UI Components (`SeriesDetailContent`)
The new screen will be built in Jetpack Compose and contain:
- **Hero Header:** At the top left, show the poster image, the title of the series (`seriesName`), and the total number of seasons/episodes.
- **Season Selector Button:** A button below the header showing the currently selected season (e.g., "Temporada 1 ▾").
  - The seasons will be extracted by taking distinct `seasonNumber`s from the episodes and sorting them ascending.
  - Clicking this opens the reusable `FilterDialog` (created in the previous redesign) to let the user pick a different season.
- **Episode List:** A vertical `LazyColumn` showing the episodes of the currently selected season, sorted by `episodeNumber`.
  - Each row shows the episode number and title.
  - It handles D-pad focus correctly (`IptvFocusBg` highlight).
- **Playback:** Clicking an episode row invokes the parent activity's logic to open `PlayerFragment` with that specific `CatalogItem` (the episode).

## Trade-offs
- **Pros:** Massively cleans up the Series grid. Makes browsing series intuitive and similar to modern VOD platforms. Safe data passing via `seriesName`. Back stack behavior is preserved.
- **Cons:** Requires a new screen and somewhat brittle string parsing (M3U lists are notoriously inconsistent). If a series name lacks the "[S/T]01 E01" pattern, it will fall back to being displayed as an individual item in the grid, which is acceptable fail-safe behavior.
