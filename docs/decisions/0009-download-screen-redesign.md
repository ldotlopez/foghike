# 0009. Download screen redesign

- Status: Accepted
- Date: 2026-10-24

## Context
The download screen showed a permanent help text, two number fields for the zoom range (keyboard over the map, an "invalid zoom" error), progress and errors as a text line, and only "Clear" for the drawn areas. Testers couldn't tell where a stroke began or which areas were already downloaded. Spec: `docs/ui/screens/download.md`.

## Decision
- Layout in `ui/DownloadScreen.kt`, state in `ui/DownloadActivity.kt`:
  - Tiles chip: count of the drawn areas against `MAX_TILES_PER_DOWNLOAD`, in error colors over the limit.
  - Progress chip, with a bar for the running area or a "no connection" notice.
  - `(?)` help that opens by itself the first time (`helpSeenDownload` in `SharedPreferences` `ui`).
  - Recenter button.
  - `MapToolbar` with undo, clear and zoom range, plus the Download FAB.
  - Snackbar for results and validation.
- Zoom range: a `RangeSlider` in a `ModalBottomSheet`. It can't hold an invalid range, so the zoom validation and its error are gone.
- The tile count runs on `Dispatchers.Default` whenever the areas or the zoom range change. A newer request cancels the older one. Download reuses the count.
- Drawing feedback: the `areas` GeoJSON source also gets the stroke's start point (a `CircleLayer` filtered to points) and a closing segment. That segment has a `closing` property and is drawn dashed by its own `LineLayer`; the solid line layer filters it out.
- Downloaded areas: their bounding boxes (`DownloadedArea` min/max lat/lon) in a second source, outlined in `DOWNLOADED_AREA_COLOR`. No schema change.
- Download results: a Snackbar only for work that finishes while the screen is open. WorkManager keeps finished work, which would otherwise be reported on every visit. "View" returns to the main screen, which frames the new area.
- The old shared `MapPanel` and `ActionButton` are deleted; `MapAttribution` is shared by both map screens.

## Alternatives
Decided with the user while writing the spec:
- Presets instead of a zoom range.
- Only undo, or only clear.
- Follow/compass modes on this screen.
- Storing the drawn polygon to outline downloaded areas exactly: needs a schema change and a migration once testers have the APK.

## Consequences
- The drawn stroke and the downloaded outlines are MapLibre layers, so they have no JVM screenshot. They are checked on a device (`docs/HUMAN_TEST.md`).
- A bounding box marks more than what was downloaded; enough to avoid duplicates, not an exact coverage map.
