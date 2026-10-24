# Plan

## Fog

- **Animated fog (optional).** Setting to show real-looking moving fog, generated procedurally (e.g. scrolling/evolving noise), only while the screen is on and the map is visible; stop animating when the screen is off or the app is in the background. Default stays the current static texture to save battery.
- **Soft fog edges.** The fog still ends in hard cell-shaped edges; blur (feather) the border so it looks more natural.
- **Less visible fog pattern.** At low zoom the 256 px texture visibly repeats as a grid of blobs; use a larger texture or broader noise (lower-frequency octaves).

## Tracks

- **Deletable tracks (recordings and imports).** Store every recording and every import as a track with an id and a date, list them, and allow deleting one, so transfer and import can be tested and undone. Deleting a track fogs again every cell that no remaining track reveals.
  - Current state: own and imported data share the same tables, told apart by `source`. `track_point` already has a `trackId` (`live-<epoch millis>` per "Start exploring" session in `TrackingService`, `gpx-<coordinate fingerprint>` per GPX in `MainActivity`), but GPX imports keep no import date. `revealed_cell` has no `trackId`: a cell is stored once, with the source of the first track that revealed it, so it can't be deleted with its track.
  - Plan: create the `Track` table already designed in the design doc (`trackId`, `source`, `createdAt`, `fingerprint`, `senderAlias`; add a display name, e.g. the GPX file name). Delete = delete the track row and its points, then rebuild all cells (and the coarse cells' `revealedCount`) from the remaining points: own tracks first, then imported, so own sources win. This is the same reprocessing roadmap step 4 needs for Settings, so write it once.
  - Rejected for now: a cell↔track link table. Deleting would be instant, but the table grows several rows per point (each point touches ~4–5 cells) and insertion gets more complex. Reconsider only if full reprocessing is too slow on a real device (cost grows with the total number of stored points; not measured yet).
  - UI: a simple list of tracks (date, source, name, point count) with a delete button and a confirmation, plus a progress indicator while cells are rebuilt.
  - Room schema change: the database is at version 2 with an auto migration; adding `Track` needs version 3 and a migration that creates a `Track` row for every existing `trackId` in `track_point`.
- **Track and import management screen.** Design (spec in `docs/ui/screens/`) and implement the screen that lists recordings and imports and lets the user delete them; it is the UI of "Deletable tracks" above.

## Settings

- **One recalculation when leaving Settings.** When several changed preferences each need a recalculation, run it once when the user leaves the Settings screen instead of after each change. Under every changed preference that needs it, show a small warning line (smaller font) such as "Will be recalculated when you close this screen".

## Docs

- **README in Spanish.** Translate `README.md` to Spanish.

## UI

- **Material 3 floating toolbar (when material3 1.5 is stable).** The map toolbars are hand-made (`MapToolbar` in `ui/Theme.kt`, decision 0007) because `HorizontalFloatingToolbar` only exists from material3 1.5, which is still beta (1.5.0-beta02 in Oct 2026). Material3 1.5 requires `compileSdk 37`, AGP ≥ 9.1, and a Compose BOM of 2026.08 or later (2026.08+ already needs `compileSdk 37`, decision 0005).
  - Steps: install SDK 37 and set `compileSdk = 37` (targetSdk can stay); update the Compose BOM; replace the body of `MapToolbar` with `HorizontalFloatingToolbar(expanded, floatingActionButton = …)`, keeping its signature so the screens don't change; move the collapsed handle into the toolbar if the component supports it, or keep it outside; re-record the Roborazzi references and compare.
  - Then remove the "Toolbar" exception from `docs/ui/screens/main.md` and `download.md`, and mark decision 0007 superseded.
  - Same check for other Expressive components the specs could use then (e.g. `FloatingToolbarDefaults.VibrantFloatingActionButton` for Explore).
