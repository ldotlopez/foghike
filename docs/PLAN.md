# Plan

## Fog

- **Animated fog (optional).** Setting to show real-looking moving fog, generated procedurally (e.g. scrolling/evolving noise), only while the screen is on and the map is visible; stop animating when the screen is off or the app is in the background. Default stays the current static texture to save battery.
- **Less visible fog pattern.** At low zoom the 256 px texture visibly repeats as a grid of blobs; use a larger texture or broader noise (lower-frequency octaves).

## Tracks

- **Name a track when it ends.** When the user stops a recording, ask for a name (stored in `track.name`, decision 0010; today recordings show "Exploration HH:MM"). Prefill it with the town where the track started, when known; otherwise leave it empty or use the date.
  - Assume no network: the town must come from an offline source. The IGN maps are raster satellite imagery with no place names, so bundle a small gazetteer instead, e.g. Spain's ~8,100 municipalities with their coordinates (IGN Nomenclátor / INE), and take the nearest one to the first point. Generate it with a script in `tools/` like `spain.mbtiles`.
- **Refine a recorded track (idea).** Clean up a recording after it ends: drop GPS jitter and outliers (jumps, points while standing still), and optionally simplify the line. Recalculate the revealed cells from the refined points (as `deleteTracks` does) and list the trigger in `docs/recalculations.md`.

## Offline maps

- **Auto-download imagery for newly revealed cells (idea).** When there is a network, download the IGN tiles of cells as they are revealed, so explored areas are available offline without opening Settings → Offline maps. Decision 0015 left this open ("possible later on top of `trackTiles`").
  - Reuse what exists: the tiles for cells (`core/OfflineMaps.kt`), the shared tile store and `DownloadWorker`, which already waits for a network (`NetworkType.CONNECTED`); skip tiles already stored.
  - Batch it instead of downloading per GPS point: e.g. enqueue one job when a track ends, or every N new cells, so tracking doesn't spend battery and radio on many small requests.
  - Setting to turn it on or off, plus "Wi-Fi only" (`NetworkType.UNMETERED`) so it doesn't spend mobile data; zoom range from the explored-area download range (13–18 by default, decision 0019; each zoom level roughly doubles a corridor's tiles).
  - Imported routes reveal many cells at once; decide whether they trigger it too or keep the existing download offer.

## UI

- **Material 3 floating toolbar (when material3 1.5 is stable).** The map toolbars are hand-made (`MapToolbar` in `ui/Theme.kt`, decision 0007) because `HorizontalFloatingToolbar` only exists from material3 1.5, which is still beta (1.5.0-beta02 in Oct 2026). Material3 1.5 requires `compileSdk 37`, AGP ≥ 9.1, and a Compose BOM of 2026.08 or later (2026.08+ already needs `compileSdk 37`, decision 0005).
  - Steps: install SDK 37 and set `compileSdk = 37` (targetSdk can stay); update the Compose BOM; replace the body of `MapToolbar` with `HorizontalFloatingToolbar(expanded, floatingActionButton = …)`, keeping its signature so the screens don't change; move the collapsed handle into the toolbar if the component supports it, or keep it outside; re-record the Roborazzi references and compare.
  - Then remove the "Toolbar" exception from `docs/ui/screens/main.md` and `download.md`, and mark decision 0007 superseded.
  - Same check for other Expressive components the specs could use then (e.g. `FloatingToolbarDefaults.VibrantFloatingActionButton` for Explore).
- **Real progress for long tasks.** While recalculating cells and downloading maps, show the percentage and the item count, e.g. "10% · item 4/45", and drive the progress bar with that fraction instead of the indeterminate (oscillating) one.
  - Today the bars are indeterminate: `LinearProgressIndicator(Modifier.fillMaxWidth())` in `ui/SettingsScreen.kt`, `ui/TracksScreen.kt` and `ui/OfflineMaps.kt`. `DownloadWorker` already reports `KEY_DONE`/`KEY_TOTAL` via `setProgress`, so downloads only need the UI to read them; recalculations (e.g. `deleteTracks` rebuilding cells) need to report done/total.
  - Use the determinate `LinearProgressIndicator(progress = { done / total.toFloat() })`; strings in both `strings.xml` files.

## Settings

- **Export / import data and settings (idea).** Let the user save the database (tracks, points, revealed cells) and the settings to a file and restore them, e.g. to move to another phone or keep a backup. An imported file is untrusted: limit its size and validate it as for exchange files.
- **Cycling profile (idea).** A set of settings tuned for riding a bike (e.g. GPS interval, reveal radius, trail), chosen when starting a track (walking or cycling).
