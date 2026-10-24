# Download screen

The user circles one or more areas on the map and downloads IGN imagery for them, at a chosen zoom range, to use offline. Each area becomes one MBTiles file, downloaded in the background by `DownloadWorker` (WorkManager), so leaving the screen doesn't stop it. Follows `docs/ui/guidelines.md` and shares components with `main.md`; exceptions are listed under "Exceptions".

Code: `ui/DownloadActivity.kt` (state, drawing gesture, validation, WorkManager) and `ui/DownloadScreen.kt` (stateless layout).

## Layout

```
┌──────────────────────────────────────┐
│ (status bar)                         │
│ ┌───────────────────────┐       (?)  │  selection chip (top left) · help (top right)
│ │ ▦ 4 210 / 20 000 tiles│            │
│ └───────────────────────┘            │
│ ┌───────────────────────┐            │  progress chip (only while downloading)
│ │ ⇩ 1 area left · 37 %  │            │
│ └───────────────────────┘            │
│        ╭─────────────╮               │
│        │  drawn area │  ← map + IGN online imagery
│        ╰─────────────╯               │
│                                      │
│                                      │
│   ╭────╮ ╭───────────────────────╮   │
│   │ ⇩  │ │  ↶    🗑    ⚙z    ◎   │   │  Download FAB + toolbar: undo · clear · zoom range · recenter
│   ╰────╯ ╰───────────────────────╯   │
│ PNOA cedido por © IGN de España      │
│ (navigation bar)                     │
└──────────────────────────────────────┘
```

- The map shows IGN imagery online (as today), so the user sees what they will download.
- Drawing: one finger draws, two fingers move and zoom the map (unchanged). The toolbar, chips and buttons take touches only on their own surface; drawing starts anywhere else.

## Components

### Selection chip

An `AssistChip` at the top left, shown once at least one area is drawn.

- Shows the tile count of all drawn areas at the current zoom range, against the limit: `grid_view` + "4,210 / 20,000 tiles". It updates when an area is added or removed and when the zoom range changes.
- Over `MAX_TILES_PER_DOWNLOAD` it uses the error colors (`errorContainer`) and the `warning` icon, so the problem shows before tapping Download.
- Counting tiles runs off the main thread; it is the same `tilesFor` count the download check uses today.

### Progress chip

An `AssistChip` under the selection chip, shown while `DownloadWorker` has unfinished work. It replaces today's progress text.

| Worker state | Content |
| --- | --- |
| Running | `download` + "2 areas left · 37 %" and a `LinearProgressIndicator` just under the chip, as wide as the chip |
| Waiting for a connection | `cloud_off` + "2 areas left · no connection" |

When all work finishes, the chip goes away and a Snackbar reports the result (see Snackbar).

### Help button

`IconButton` with `help` at the top right. Its `AlertDialog` explains the drawing gesture (one finger draws, two move), that several areas can be drawn, the zoom range and that downloading needs a connection.

It **opens by itself the first time** the screen is entered, because the drawing gesture can't be guessed. "Seen" is stored in preferences (`helpSeenDownload`).

### Floating toolbar

A `MapToolbar` (hand-made, decision 0007), like the main screen: the Download floating action button on the left, then the pill.

| Slot | Icon | Action |
| --- | --- | --- |
| FAB | `download` | Validate and enqueue every drawn area, then clear them from the map |
| 1 | `undo` | Remove the last drawn area |
| 2 | `delete` | Remove all drawn areas |
| 3 | `tune` | Open the zoom range sheet |
| 4 (pinned) | `my_location` | Recenter (see "Recenter button") |

- 56 dp touch targets, `contentDescription` and long-press tooltip on every button, as in `main.md`.
- Undo and clear are disabled (`enabled = false`) while there are no areas.
- The FAB is always enabled (Material's FAB has no disabled state). Tapping it with a problem shows a Snackbar instead of downloading.
- It does not collapse: see "Exceptions".

### Zoom range sheet

A `ModalBottomSheet` opened from `tune`, with one `RangeSlider` (steps of 1, from 0 to `IGN_MAX_ZOOM`) and the two values shown as "Zoom 13 – 16". It replaces the two number fields: a range slider can't hold an invalid value, so the "invalid zoom" error disappears and no keyboard opens over the map. The selection chip updates live while dragging.

Default 13–16, kept while the screen is open (the app is portrait only), not stored across launches.

### Drawing feedback

While a finger draws, the stroke is a line as today, plus:

- A dot at the **start point**, so the user knows where they began (the `TODO` item).
- A dashed segment from the finger back to the start point, showing how the area will be closed when the finger lifts.

Both are extra features in the existing `areas` GeoJSON source, drawn with the area color. See open question 2.

### Recenter button

The last button of the toolbar pill (`my_location`), in the same place as the main screen's my location button. One tap centers the map on the user at `AREA_PICK_ZOOM`. It has no follow or compass modes: following would move the map under a stroke being drawn. Without location permission or a fix, the same behavior as on the main screen (permission request, "Waiting for your location…" Snackbar).

### Downloaded areas

Areas already downloaded are drawn on the map so they aren't downloaded twice: the bounding box stored in `DownloadedArea` (min/max lat and lon), as a thin line with a very light fill, in a fixed brand color different from the drawn-area color. A bounding box covers more than the shape that was drawn; it marks "something is downloaded around here", not the exact edge. It uses a second GeoJSON source and two layers, with no database change.

### Snackbar

One `SnackbarHost` above the toolbar. Replaces the message text.

| Message | Action |
| --- | --- |
| "Draw at least one area first." (Download tapped with no areas) | — |
| "Too many tiles (25,310, max 20,000): use smaller areas or fewer zoom levels." | "Zoom levels" (opens the sheet) |
| "Download finished." | "View" (back to the main screen, which frames the new area) |
| "Download cancelled or failed." | — (retries are roadmap step 7) |

## States

Each state has a `@Preview` in `DownloadScreen.kt` and a Roborazzi reference image (`ui/DownloadScreenScreenshotTest.kt`), except state 2: the stroke, its start dot and the downloaded areas are MapLibre layers, checked on a device (`docs/HUMAN_TEST.md`). The map slot is a plain colored box.

| # | State | Differences |
| --- | --- | --- |
| 1 | Empty | No areas: no selection chip, undo and clear disabled |
| 2 | Drawing | Stroke with start dot and closing segment |
| 3 | Areas drawn | Two areas, selection chip with the tile count, one downloaded area's box |
| 4 | Too many tiles | Selection chip in error colors |
| 5 | Zoom sheet | Bottom sheet open with the range slider |
| 6 | Downloading | Progress chip with percentage and progress bar |
| 7 | Waiting | Progress chip with `cloud_off` |
| 8 | Finished | Snackbar "Download finished." with "View" |
| 9 | Help | Help dialog open (first visit) |
| 10 | Dark | State 3 in the dark scheme |

### State 4 — Too many tiles

```
│ ┌───────────────────────────┐  (?)  │
│ │ ⚠ 25 310 / 20 000 tiles   │       │  errorContainer colors
│ └───────────────────────────┘       │
```

### State 5 — Zoom sheet

```
│                                      │
│ ┌──────────────────────────────────┐ │
│ │             ───                  │ │  drag handle
│ │  Zoom 13 – 16                    │ │
│ │  0 ─────────●━━━━━━●───────── 19 │ │  RangeSlider
│ │  ┌─────────────────────────────┐ │ │
│ │  │ ▦ 4 210 / 20 000 tiles      │ │ │  selection chip, live
│ │  └─────────────────────────────┘ │ │
│ └──────────────────────────────────┘ │
```

### State 2 — Drawing

```
│            ●~~~~~~~~~~~~~~╮           │  ● start point
│            ┆              │           │  ┆ closing segment (dashed)
│            ┆      ╭───────╯           │
│            ┆ ┄ ┄ ☝                    │  finger
```

## Strings

New or changed keys. Removed: `download_help` (moves into the help dialog as `help_download`), `min_zoom`, `max_zoom`, `invalid_zoom`, `clear`, `download_progress`, `download_waiting` (replaced below).

| Key | English | Spanish |
| --- | --- | --- |
| `action_download` | Download drawn areas | Descargar las áreas dibujadas |
| `action_undo_area` | Remove last area | Quitar la última área |
| `action_clear_areas` | Remove all areas | Quitar todas las áreas |
| `action_zoom_range` | Zoom levels | Niveles de zoom |
| `action_recenter` | Center on my location | Centrar en mi ubicación |
| `chip_tiles` | %1$s / %2$s tiles | %1$s / %2$s tiles |
| `chip_progress` (plurals) | %1$d areas left · %2$d %% | Quedan %1$d áreas · %2$d %% |
| `chip_waiting` (plurals) | %1$d areas left · no connection | Quedan %1$d áreas · sin conexión |
| `zoom_range` | Zoom %1$d – %2$d | Zoom %1$d – %2$d |
| `action_view` | View | Ver |
| `help_download_title` | Download maps | Descargar mapas |
| `help_download` | Gesture, undo and clear, tile chip and limit, zoom levels, downloaded areas in blue, background download | (same in Spanish) |

Kept: `no_areas`, `too_many_tiles`, `download_done`, `download_stopped`, `download_channel`, `download_notification`, `cancel`. Also removed: `download` (replaced by `action_download`).

## Exceptions

- **Hand-made toolbar** (`MapToolbar`, decision 0007), as on the main screen.
- **Toolbar doesn't collapse.** Nobody explores from this screen, and the toolbar holds the controls needed while drawing. Collapsing would only hide them.

## Open questions

None.
