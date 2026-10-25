# Tracks screen

Every recording and imported route, newest first. From here the user imports GPX files and deletes tracks; deleting one fogs again what only that track cleared. Follows `docs/ui/guidelines.md`. It is a list, not a map screen, so the map-screen rules (floating toolbar, chips, attribution) don't apply; its actions are icon buttons in the top bar.

Code: `ui/TracksActivity.kt` (reads the tracks, file picker, import, delete) and `ui/TracksScreen.kt` (layout; holds only the selection and the open dialog). Opened from the `route` icon of the main screen's toolbar; the system back button returns (or leaves selection mode first).

## Layout

```
┌──────────────────────────────────────┐
│ (status bar)                         │
│ ←  My tracks                ⇪    ☑   │  TopAppBar: back · title · import GPX · select
│ ════════════════════════════════════ │  LinearProgressIndicator while importing or deleting
│ Exploration 5:46 PM             🗑   │  name (bodyLarge, one line, ellipsis)
│ Oct 14, 2026, 5:46 PM · Own          │  date added · origin (bodySmall)
│                                      │
│ Castellón (Oct 11, 2026)        🗑   │  imports: name + the route's own date
│ Oct 13, 2026, 5:46 PM · Imported     │
│ …                                    │
└──────────────────────────────────────┘
```

Selection mode (long press on a row, or the select icon):

```
│ ✕  1 selected               ⬚    🗑   │  close · count · select all · delete selected
│ ☐  Exploration 5:46 PM               │  checkbox instead of the trash icon
│ ☑  Castellón (Oct 11, 2026)          │
```

## Components

### Top bar

`TopAppBar` with `MapIconButton`s (56 dp, tooltip on long press).

| Mode | Navigation | Title | Actions |
| --- | --- | --- | --- |
| Normal | `arrow_back`: back | "My tracks" | `upload_file`: import GPX (system picker, several files); `checklist`: enter selection mode (disabled with nothing deletable) |
| Selecting | `close`: leave selection mode | "N selected" | `select_all`: select every deletable track; `delete`: delete the selected ones (disabled with none) |

While importing or deleting, the actions that start work are disabled and a `LinearProgressIndicator` shows under the bar.

### Track row

`ListItem` inside a `SwipeToDismissBox`, keyed by `trackId`.

- **Name** (headline, one line). Recordings: `track.name`, or "Exploration HH:MM" (start time) while there is none; it will change when "Name a track when it ends" (`docs/PLAN.md`) is built. Imports: the GPX `<name>` (of the track, else of `<metadata>`), else the file name without `.gpx`, at most `MAX_GPX_NAME` characters; then the route's own date in brackets when its points have times: "Castellón (Oct 11, 2026)". Imports from before database version 3 have no name: "Imported route".
- **Details** (supporting text, `bodySmall`, smaller than the name): date added (recording start or import time) · origin ("Own" for `live`, "Imported" for anything else) · "Recording" for the track being recorded.
- **Trailing:** trash icon button: asks to delete that track.
- **Swipe left:** same as the trash icon. The row slides back at once and the confirmation dialog opens; swiping right does nothing.
- **Long press:** enters selection mode with that row selected. In selection mode a tap toggles the row, and a leading `Checkbox` replaces the trash icon.
- **The track being recorded** (`TrackingService.activeTrackId`) has no trash icon, can't be swiped or selected, and "Select all" skips it.

### Confirmation dialog

`AlertDialog`, for one track or several: "Delete this track?" / "Delete N tracks?", "The fog comes back wherever only these tracks cleared it. This can't be undone.", buttons "Cancel" and "Delete" (error color). Confirming leaves selection mode and starts the delete.

### Delete

`deleteTracks` (`core/Exploration.kt`): deletes the track rows and their points, then rebuilds every cell from the points left, own recordings first (`docs/recalculations.md`). One transaction, run as `NonCancellable` so leaving the screen doesn't stop it. Then a Snackbar: "Track deleted." / "N tracks deleted.".

### Import

The same importer the main screen had: one track per file, skipped if its fingerprint already exists, and a Snackbar with the imported / already imported / unreadable counts. New: a file with no track points counts as unreadable (it would leave an empty track in the list), and the track gets its name (see above).

When at least one route was imported, the Snackbar is long and has the action "Download map" (decision 0015): imported routes are usually where no map was downloaded yet. It opens `TileOfferSheet` (`ui/OfflineMaps.kt`) titled "Download maps for the imported routes": the zoom range (13–18 the first time, then the last used, `trackDownloadMinZoom` / `trackDownloadMaxZoom`), the tiles under the routes' revealed cells that aren't stored yet against the 20,000 limit, the estimated size and a Download button (disabled at 0 tiles or over the limit). Download enqueues `DownloadWorker.enqueueTracks` and shows "Download started. Follow it in the notification."

### Empty list

Centered text: "No tracks yet. Start exploring, or import GPX files with the button at the top."

## States

Each state has a Roborazzi reference image (`ui/TracksScreenScreenshotTest.kt`, time zone fixed to UTC) and, except 4 and 5, a `@Preview` in `TracksScreen.kt`. States 4 and 5 are reached by long-pressing a row and tapping a trash icon.

| # | State | Differences from the default |
| --- | --- | --- |
| 1 | Default | Four tracks: two recordings, a named import with its route date, a long name cut with an ellipsis |
| 2 | Empty | The empty-list text |
| 3 | Recording | The newest recording shows "Recording" and no trash icon |
| 4 | Selecting | Long press on "Castellón": selection bar and checkboxes |
| 5 | Confirm | Delete dialog for one track |
| 6 | Busy | Progress bar under the top bar, actions disabled |
| 7 | Dark | State 1 in the dark scheme |

## Strings

New keys (English / Spanish). `action_import_gpx`, `cancel`, `action_back` and the `gpx_*` import messages are reused.

| Key | English | Spanish |
| --- | --- | --- |
| `action_tracks` | My tracks | Mis rutas |
| `action_select` | Select | Seleccionar |
| `action_select_all` | Select all | Seleccionar todas |
| `action_cancel_selection` | Cancel selection | Cancelar selección |
| `action_delete_track` | Delete track | Borrar ruta |
| `action_delete_selected` | Delete selected | Borrar seleccionadas |
| `delete` | Delete | Borrar |
| `delete_tracks_title` (plural) | Delete this track? / Delete %d tracks? | ¿Borrar esta ruta? / ¿Borrar %d rutas? |
| `delete_tracks_text` | The fog comes back wherever only these tracks cleared it. This can't be undone. | La niebla vuelve allí donde solo la despejaron estas rutas. No se puede deshacer. |
| `tracks_selected` (plural) | %d selected | %d seleccionada(s) |
| `tracks_deleted` (plural) | Track deleted. / %d tracks deleted. | Ruta borrada. / %d rutas borradas. |
| `track_live_name` | Exploration %s | Exploración %s |
| `track_unnamed` | Imported route | Ruta importada |
| `source_own` | Own | Propia |
| `source_imported` | Imported | Importada |
| `track_recording` | Recording | Grabando |
| `tracks_empty` | No tracks yet. Start exploring, or import GPX files with the button at the top. | Aún no hay rutas. Empieza a explorar o importa ficheros GPX con el botón de arriba. |

## Exceptions

- No help `(?)` button: every action has a visible icon; swipe and long press are shortcuts, not the only way.
- Not a map screen: no floating toolbar or attribution (no IGN imagery is shown).

## Open questions

None.
