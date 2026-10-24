# 0015. Offline maps from revealed cells, in one shared tile store

- Status: Accepted
- Date: 2026-10-24

## Context
An imported route is usually somewhere no map was downloaded, and drawing its area by hand is awkward. The fog hides every tile outside the revealed cells, so those are the only tiles worth keeping, and they are cheap: a revealed corridor is about 60 m wide, so at high zooms its tile count grows with the route's length, not its area (a 15 km route is roughly 400 tiles at zoom 13–17). Until now every drawn area was its own `.mbtiles` file and its own map source, overlapping areas stored the same tiles twice, and removing tiles meant editing many files.

## Decision
- **One tile store**: `maps/tiles.mbtiles` (`tiles/TileStore.kt`), a standard MBTiles file where each tile is stored once. Every download writes into it and skips the tiles it already has, so a restarted download resumes. `downloaded_area` keeps one row per finished download (bounding box, zoom range); the schema is unchanged, every row points at the shared file.
- **Kinds of download**: hand-drawn areas (name prefix `area-`) and downloads from cells (`cells-`), so the download screen only outlines the hand-drawn ones and a clean-up can keep them. `DownloadWorker` takes a polygon, "every revealed cell" or a list of track ids, and computes the tiles itself when it runs.
- **Tiles from cells**: `tilesCovering` (`geo/Tiles.kt`) maps each base cell's rectangle to the 1 to 2×2 tiles it touches. `exploredTiles` and `trackTiles` (`core/OfflineMaps.kt`) apply it to every revealed cell or to the cells a track reveals (`cellsRevealedBy`, the same rule recording uses).
- **Map style**: one raster source per distinct max zoom among the downloads, all reading the store, lowest first. Above its max zoom a source shows its last level enlarged, and where a higher source has no tile it is transparent, so each area still shows above its own max zoom.
- **Where the user finds it**:
  - Tracks screen: after an import, the Snackbar offers "Download map" for the routes just imported.
  - Settings → Offline maps: "Download maps of explored areas", and "Remove maps outside explored areas" with "Keep areas downloaded by hand" (on by default) and the size it frees. A clean-up waits for running downloads, deletes in one transaction and runs `VACUUM`. Without the kept areas, their rows are replaced by one `cells-` row with their zoom range, so the explored tiles they leave keep showing.
  - Both offers use one sheet (`TileOfferSheet`, `ui/OfflineMaps.kt`): zoom range, new tiles against `MAX_TILES_PER_DOWNLOAD`, and an estimated size from the store's average tile (25 KB while empty). Each kind of download remembers its own zoom range, 13–17 the first time.
- **Migration**: on start, `FogHikeApp.mergeLegacyMaps` copies every old area file into the store and deletes it once copied.

## Alternatives
- Keep one file per download: no deduplication, a source per file, and pruning across many files.
- A `kind` column in `downloaded_area`: cleaner, but needs a Room migration while a release build is already installed. Left for the next real migration (`shortcut:` in `data/Entities.kt`).
- Splitting a download larger than the limit into several jobs: refused instead, as on the download screen; downloads skip stored tiles, so a smaller zoom range can be extended later.
- Downloading new cells automatically while exploring: not asked for; possible later on top of `trackTiles`.

## Consequences
- The style has a few sources instead of one per area.
- `exploredTiles` reads every base cell; very large histories (Timeline imports) should use the coarse cells at low zooms.
- Pruning deletes downloaded imagery; it is confirmed and cannot be undone.
