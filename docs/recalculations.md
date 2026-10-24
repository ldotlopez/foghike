# Recalculations

Which interactions force something to be recalculated, what is recalculated and how much it costs. If a trigger changes, update this file and the design document. State as of 2026-10-24.

There are three cost levels:

1. **Reprocess stored data**: delete cells and rebuild them from the raw GPS points. Expensive: grows with the total number of stored points.
2. **Store new points**: cells of one batch of points. Cheap and local.
3. **Redraw on screen**: fog, trail or map style. Only what is visible; never touches stored data.

## 1. Reprocess stored data (expensive)

None of these is implemented yet: reprocessing arrives with the Settings screen (roadmap step 4) and is reused by track deletion (`PLAN.md`, "Tracks"). All of them are the same operation: delete `revealed_cell` and feed every point in `track_point` through `recordPoints` again, own tracks first and imported ones after, so own sources win.

| Interaction | Why it forces reprocessing | What is rebuilt |
| --- | --- | --- |
| Change the cell size (`CELL_SIZE_METERS`) | Every cell id and geometry changes | All fine and coarse cells and `revealedCount` |
| Change the reveal radius (`REVEAL_RADIUS_METERS`) | Changes which cells each point clears and which points count as accurate (`isAccurate` uses the same radius) | All cells and `revealedCount` |
| Change `LOD_FACTOR` or `LOD_LEVELS` | Coarse cell sizes change | Coarse cells and `revealedCount` (fine cells stay) |
| Delete a track (own or imported) | Cells don't know which track revealed them; cells only that track cleared must be fogged again | All cells from the remaining points |
| Data stored before database version 2 | The migration left `revealedCount = 1` on old coarse cells, so `DENSITY` draws them almost fully fogged | `revealedCount` (one reprocess is enough) |
| Receive an NFC track crossing own cells (future) | The own source must win; outside reprocessing the insert ignores duplicates | Only the `source` field; reprocessing order fixes it |

**Not reprocessing:** changing `FOG_MODE` or the `fogDensity` formula (applied when drawing), or the threshold of the "Threshold" strategy in the design document, because `revealedCount` stores the number, not a yes/no.

## 2. Store new points (incremental)

Everything goes through `recordPoints` (`Exploration.kt`) in one transaction:

| Interaction | Frequency | What is computed |
| --- | --- | --- |
| Live tracking (`TrackingService`) | Every GPS batch, about 5 s (`INTERVAL_MS`); also with the screen off | Raw points, new fine cells, their coarse cells and the `revealedCount` increment (new fine cells only) |
| Import one or more GPX files | When files are picked; one file = one track | Same; if the fingerprint already exists the file is skipped without computing anything |
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
| Time passes (trail) | Last-hour trail and its fade | Every 10 s (`TRAIL_REFRESH_MS`) while the main screen is visible, and on style reload |
| Move the opacity slider | Nothing: only a layer property changes | — |
| Rotate the phone (compass) | Nothing in the app: MapLibre's location component draws it | — |
| Too many fog rectangles (over `MAX_FOG_RECTS`) | The computation is repeated at the next, coarser detail level | Inside the same computation |

## Download screen

| Interaction | What is recalculated |
| --- | --- |
| Draw or clear a freehand area | The lines drawn on the map |
| Press "Download" | Tile list for the polygons between min and max zoom; above `MAX_TILES_PER_DOWNLOAD` nothing is downloaded |
| A download finishes | Nothing on the main screen until the user returns to it (see `onResume` above) |
