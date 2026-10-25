# Recalculations

Which interactions force something to be recalculated, what is recalculated and how much it costs. If a trigger changes, update this file and the design document. State as of 2026-10-24.

There are three cost levels:

1. **Reprocess stored data**: delete cells and rebuild them from the raw GPS points. Expensive: grows with the total number of stored points.
2. **Store new points**: cells of one batch of points. Cheap and local.
3. **Redraw on screen**: fog, trail or map style. Only what is visible; never touches stored data.

## 1. Reprocess stored data (expensive)

Track deletion (decision 0010) and the Settings changes (decision 0011) are implemented; both call `rebuildCells` in `core/Exploration.kt`. All of them are the same operation: delete `revealed_cell` and reveal the cells of every track left in `track_point` again, own tracks first and imported ones after, so own sources win. Rebuilt cells get the current time as `firstRevealedAt`.

| Interaction | Why it forces reprocessing | What is rebuilt |
| --- | --- | --- |
| Change the cell size (Settings → Advanced) — implemented | Every cell id and geometry changes | All fine and coarse cells and `revealedCount`, once when leaving Settings even if the radius changed too. The new values are saved first with a `cellsStale` flag, so a rebuild cut short by the process dying runs again at the next start (`FogHikeApp`) |
| Change the reveal radius (Settings → Advanced) — implemented | Changes which cells each point clears and which points count as accurate (`isAccurate` uses the same radius) | Same as the cell size, in the same single rebuild |
| Change `LOD_FACTOR` or `LOD_LEVELS` | Coarse cell sizes change | Coarse cells and `revealedCount` (fine cells stay) |
| Delete one or more tracks (tracks screen; own or imported) — implemented | Cells don't know which track revealed them; cells only those tracks cleared must be fogged again | All cells from the remaining points, in one transaction; the main screen's fog refreshes when it comes back (cell table changed) |
| Data stored before database version 2 | The migration left `revealedCount = 1` on old coarse cells, so `DENSITY` draws them almost fully fogged | `revealedCount` (one reprocess is enough) |
| Receive an NFC track crossing own cells (future) | The own source must win; outside reprocessing the insert ignores duplicates | Only the `source` field; reprocessing order fixes it |

**Not reprocessing:** changing the fog mode (Settings) or the `fogDensity` formula (applied when drawing), or the threshold of the "Threshold" strategy in the design document, because `revealedCount` stores the number, not a yes/no.

## 2. Store new points (incremental)

Everything goes through `recordPoints` (`core/Exploration.kt`) in one transaction:

| Interaction | Frequency | What is computed |
| --- | --- | --- |
| Live tracking (`TrackingService`) | Every GPS batch: the GPS interval from Settings (5 s by default, 15 s with battery saver; read when exploring starts); also with the screen off. Cell size and radius are read per batch | Raw points, new fine cells, their coarse cells and the `revealedCount` increment (new fine cells only) |
| Import one or more GPX files (tracks screen) | When files are picked; one file = one track | Same; if the fingerprint already exists the file is skipped without computing anything |
| Google Maps or NFC import (future) | On import | Same, through the same path |

Inaccurate points (`accuracy` above the reveal radius) are stored but neither clear cells nor show in the trail.

## 3. Redraw on screen (cheap)

None of this runs with the screen off or while the camera moves.

| Interaction | What is recalculated | Condition |
| --- | --- | --- |
| Release the map after panning or zooming (`onCameraIdle`) | Fog for the visible region plus one screen of margin per side | Only if the view leaves the computed area or the detail level changes |
| The cell table changes (new point, import) | Fog, forced | Every time Room reports a change in `revealed_cell`, while the main screen is visible |
| Back to the main screen (`onResume`) with a changed list of downloaded areas | Whole map style: map layers, fog, trail and position; the camera frames the latest area | Only if the list changed (e.g. back from a download) |
| First start of the screen | Same as above, centering the camera on the user | Always |
| Time passes (trail) | Trail of the chosen duration and its fade | Every 10 s (30 s with Old device or Battery saver, `AppSettings.trailRefreshMs`) while the main screen is visible, and on style reload |
| Back to the main screen (`onResume`) after changing the fog opacity in Settings | Nothing: only a layer property changes | Always (cheap) |
| Back to the main screen (`onStart`) from Settings | Settings read again; fog forced (the cell count collector restarts, with the new cell size, mode, detail and piece limit); trail color, width and visibility set on the layer | Always (cheap; the cell rebuild already ran in Settings) |
| My location button in Follow or Compass mode | The camera moves with every GPS fix; each camera idle runs the fog row above | Same condition as above: walking only recomputes when the view leaves the computed area |
| Map gesture in Follow or Compass mode | Following pauses; 5 s after the camera last stops (`RESUME_FOLLOWING_MS`) it re-centers, and that camera idle runs the fog row above | Once per pause; a new gesture restarts the wait |
| Rotate the phone (compass) | Nothing in the app: MapLibre's location component draws it | — |
| Too many fog rectangles (over `AppSettings.maxFogRects`: 5,000, or 1,500 with Old device) | The computation is repeated at the next, coarser detail level | Inside the same computation |

## Download screen

| Interaction | What is recalculated |
| --- | --- |
| Draw, undo or clear a freehand area | The lines drawn on the map (while drawing also the start dot and the dashed closing segment) and the tile count of all drawn areas, off the main thread; a newer request cancels the older one |
| Move the zoom range slider | The tile count, as above |
| Press "Download" | Nothing new: it uses the last tile count (counts again only if one is still running); above `MAX_TILES_PER_DOWNLOAD` nothing is downloaded |
| Open the screen, or a download finishes with the screen open | The outlines of the downloaded areas (bounding boxes from the database) |
| A download finishes | Nothing on the main screen until the user returns to it (see `onResume` above) |

## Offline maps from cells (decision 0015)

| Interaction | What is recalculated |
| --- | --- |
| Open the download sheet (Settings → "Download maps of explored areas", or "Download map" after an import), or move its zoom slider | Tiles under the cells (every revealed base cell, or the cells the imported tracks reveal, from their points), minus the tiles already in the store, and their estimated size; off the main thread, a newer range cancels the older count |
| Press Download in the sheet | Nothing at once: `DownloadWorker` computes the tiles again when it runs (the cells may have grown), skips the stored ones and records the download when complete |
| Open the clean-up dialog, or toggle "Keep areas downloaded by hand" | Every stored tile against the tiles under the revealed cells at the stored zoom levels (and the kept areas' boxes and zoom ranges): the tiles to delete and their size, off the main thread |
| Confirm the clean-up | The same plan again, then the deletes in one transaction and `VACUUM` (rewrites the whole file); without kept areas, their `downloaded_area` rows are replaced by one with their zoom range. The main screen reloads its style when the area list changed |
| App start with areas from before decision 0015 | Each old `.mbtiles` is copied into the store and deleted, once; the area rows change file name, so the main screen reloads its style |
