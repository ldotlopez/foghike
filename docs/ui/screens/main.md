# Main screen

The map with the fog, the user's position and the recent trail. From here the user starts and stops exploring and opens every other screen. Follows `docs/ui/guidelines.md`; exceptions are listed under "Exceptions".

Code: `ui/MainActivity.kt` (state, permissions, file picker, map) and `ui/MainScreen.kt` (stateless layout).

## Layout

```
┌──────────────────────────────────────┐
│ (status bar)                         │
│ ┌──────────────────┐            (?)  │  status chip (top left) · help (top right)
│ │ ▦ 1 234  ·  🗺 3 │                 │
│ └──────────────────┘                 │
│                                      │
│              map + fog               │
│                 ➤                    │  user position (MapLibre location component)
│                                      │
│                               ╭──╮   │
│                               │◎ │   │  my location (follow mode)
│      ╭───────────────────╮ ╭──╰──╯   │
│      │  🗺    ⇪    ⚙     │ │ ●  │    │  floating toolbar + Explore FAB
│      ╰───────────────────╯ ╰────╯    │
│ PNOA cedido por © IGN de España      │  IGN attribution (bottom left, one line)
│ (navigation bar)                     │
└──────────────────────────────────────┘
```

- The map fills the whole screen, edge to edge, behind the system bars.
- Floating elements pad themselves from the system bars and block map gestures only on their own surface.
- The attribution is plain `labelSmall` text, no panel. It sits above the navigation bar, below the toolbar.

## Components

### Status chip

A Material 3 `AssistChip` at the top left, below the status bar.

| Content | Icon | Meaning |
| --- | --- | --- |
| Revealed cells count | `grid_view` | `cellCount`, grouped by thousands (locale-aware) |
| Offline maps count | `map` | Number of downloaded areas |

- Tapping the chip does nothing while there are maps. Its `contentDescription` reads both values in full ("1,234 revealed cells, 3 offline maps").
- **No maps:** the chip shows `warning` + "No offline maps" instead of the map count. Tapping it opens the download screen.

### Help button

`IconButton` with `help` at the top right. It opens an `AlertDialog` explaining the fog, exploring, importing GPX and what the chip numbers mean. It does not open by itself: this screen has no gesture that can't be guessed.

### Floating toolbar

A Material 3 `HorizontalFloatingToolbar` (Expressive, `@OptIn(ExperimentalMaterial3ExpressiveApi::class)`), centered at the bottom, with Explore as its floating action button.

| Slot | Icon | Action | Opens |
| --- | --- | --- | --- |
| FAB | `fiber_manual_record` / `stop` | Start / stop exploring | Permission flow on first start (unchanged) |
| 1 | `map` | Offline maps | Download screen |
| 2 | `upload_file` | Import GPX | System file picker (multiple files) |
| 3 | `settings` | Settings | Settings screen (Debug section only for now) |

- Every icon button is at least 56 dp. The default Material 3 icon buttons are 48 dp, so they get an explicit size.
- Each one has a `contentDescription` and a tooltip on long press (`TooltipBox` + `PlainTooltip`).
- The record/stop pair (● / ■) reads as "recording your route". Not exploring: `fiber_manual_record` tinted with the error color (red) on the regular FAB container, so it can't be mistaken for the blue, containerless user-position dot. Exploring: `stop` on the error container color, so the state can be seen at a glance.
- **Collapsing.** The toolbar collapses by itself when exploring starts: only the Explore FAB (now Stop) and a handle stay visible, so the map is as free as possible while walking.
  - The handle is a 56 dp icon button next to the FAB: `more_horiz` when collapsed (expands), `close` when expanded (collapses again). Same tooltip rules as the other buttons.
  - The handle exists only while exploring. When exploring stops, the toolbar expands and the handle goes away.
  - The expanded/collapsed choice is not stored: it follows the exploring state each time the screen opens.

### My location button

A `SmallFloatingActionButton` (56 dp touch target) at the bottom right, above the toolbar. It cycles through the camera modes MapLibre's location component already has:

| State | Icon | MapLibre `CameraMode` | Tap goes to |
| --- | --- | --- | --- |
| Free | `location_searching` | `NONE` | Follow |
| Follow (north up) | `my_location` | `TRACKING` | Compass |
| Compass (map rotates with the phone) | `explore` | `TRACKING_COMPASS` | Follow |

- **Follow** centers the map on the user at `USER_ZOOM` and keeps it centered as they move.
- Dragging the map leaves follow mode. MapLibre does this by itself (`OnCameraTrackingChangedListener.onCameraTrackingDismissed`), and the button goes back to Free.
- Leaving Compass resets the bearing to north.
- The screen opens in Free mode, centered on the user once, as today. The mode is not stored.
- Without location permission, a tap starts the same permission request as the first launch. With no fix yet, a Snackbar says "Waiting for your location…".
- Following moves the camera on every GPS fix, and each camera idle refreshes the fog for the viewport. That is allowed (viewport only, off the main thread), but it is a new recalculation trigger: add it to `docs/recalculations.md` when implementing it.

### Snackbar

One `SnackbarHost` above the toolbar. It replaces every `Toast` on this screen:

| Message | Action |
| --- | --- |
| GPX import result: imported / already imported / unreadable counts, in one line | — |
| "Exploring needs precise location." | "Settings" (opens the app's system settings) |
| "Exploring needs location switched on." | — |
| "Waiting for your location…" | — |

### Dialogs

- The background-location rationale stays a platform `AlertDialog` for now. It is part of the permission flow, not help (decision 0005).
- The help dialog (see above).

## States

Each state has a `@Preview` and a Roborazzi reference image. The map slot is a plain colored box.

| # | State | Differences from the default |
| --- | --- | --- |
| 1 | Default | Maps present, not exploring, toolbar expanded |
| 2 | Exploring, expanded | Toolbar opened with the handle: FAB shows `stop` in the error container color, handle shows `close` |
| 3 | Exploring, collapsed | Only the Stop FAB and the `more_horiz` handle |
| 4 | No maps | Chip shows `warning` + "No offline maps" |
| 5 | Snackbar | GPX import result shown above the toolbar |
| 6 | Help | Help dialog open |
| 7 | Dark | State 1 in the dark scheme |
| 8 | Following | My location button shows `my_location` |
| 9 | Compass | My location button shows `explore` |

### State 2 — Exploring, expanded

```
│   ╭─────────────────────────╮ ╭────╮ │
│   │  ✕   🗺    ⇪    ⚙       │ │ ■  │ │  handle (close) · FAB: stop, error container color
│   ╰─────────────────────────╯ ╰────╯ │
```

### State 3 — Exploring, collapsed

```
│                       ╭────╮ ╭────╮  │
│                       │ ⋯  │ │ ■  │  │  handle (more_horiz) · FAB: stop
│                       ╰────╯ ╰────╯  │
```

### State 4 — No maps

```
│ ┌──────────────────────────────┐(?)  │
│ │ ▦ 1 234  ·  ⚠ No offline maps│     │  tap → download screen
│ └──────────────────────────────┘     │
```

### State 5 — Snackbar

```
│ ┌──────────────────────────────────┐ │
│ │ Imported 3 412 points, 2 files.  │ │
│ └──────────────────────────────────┘ │
│      ╭───────────────────╮ ╭────╮    │
│      │  🗺    ⇪    ⚙     │ │ ●  │    │
```

## Strings

New or changed keys (English / Spanish). Removed: `fog_opacity` (moves to Settings), `status`, `no_maps`, `maps`, `import_gpx`, `start_exploring`, `stop_exploring` (replaced by the keys below).

| Key | English | Spanish |
| --- | --- | --- |
| `action_explore` | Start exploring | Empezar a explorar |
| `action_stop` | Stop exploring | Dejar de explorar |
| `action_maps` | Offline maps | Mapas sin conexión |
| `action_import_gpx` | Import GPX | Importar GPX |
| `action_settings` | Settings | Ajustes |
| `action_help` | Help | Ayuda |
| `action_show_toolbar` | Show actions | Mostrar acciones |
| `action_hide_toolbar` | Hide actions | Ocultar acciones |
| `action_follow` | Center on my location | Centrar en mi ubicación |
| `action_compass` | Rotate map with compass | Girar el mapa con la brújula |
| `action_north_up` | Keep north up | Mantener el norte arriba |
| `waiting_location` | Waiting for your location… | Esperando tu ubicación… |
| `chip_status` | %1$s revealed cells, %2$d offline maps | %1$s celdas reveladas, %2$d mapas sin conexión |
| `chip_no_maps` | No offline maps | Ningún mapa descargado |
| `help_main_title` | Hike Explorer | Hike Explorer |
| `help_main` | (text to write with the implementation) | |

The my location button's description names what the next tap does: `action_follow` in Free, `action_compass` in Follow, `action_north_up` in Compass. The `action_*` texts are tooltips and screen-reader labels, never visible labels, so they can be full sentences.

## Future entries

Not built now; listed so the toolbar layout leaves room for them.

| Entry | Planned for | Where |
| --- | --- | --- |
| Tracks list | `docs/PLAN.md` | Toolbar slot. Import GPX moves into that screen, so the toolbar keeps three icons |
| Exchange (NFC / Nearby) | Roadmap step 6 | Toolbar slot (`share` or `nfc`) |
| Highlight imported cells | Roadmap step 6 | Toggle icon button under the help button, top right |
| Google Maps Timeline import | Roadmap step 3 | Inside the tracks list screen, next to Import GPX |

With all of them the toolbar holds four icons plus the FAB: maps, tracks, exchange, settings.

## Exceptions

- **Toolbar handle.** Material's floating toolbar collapses on scroll, and a map has no scroll. It collapses when exploring starts instead, and a custom handle button reopens it. Considered and rejected: collapsing while the map is dragged (hides the actions too often), an always-present chevron (one more icon in a toolbar meant to be small), never collapsing (against the guidelines).

## Open questions

None.
