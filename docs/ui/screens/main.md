# Main screen

The map with the fog, the user's position and the recent trail. From here the user starts and stops exploring and opens every other screen. Follows `docs/ui/guidelines.md`; exceptions are listed under "Exceptions".

Code: `ui/MainActivity.kt` (state, permissions, map) and `ui/MainScreen.kt` (stateless layout).

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
│                                      │
│                                      │
│   ╭────╮ ╭───────────────────────╮   │
│   │ ●  │ │  ◎    🗺    ⫝    ⚙    │   │  Explore FAB + toolbar (◎ = my location)
│   ╰────╯ ╰───────────────────────╯   │
│ PNOA cedido por © IGN de España      │  IGN attribution (bottom left, one line)
│ (navigation bar)                     │
└──────────────────────────────────────┘
```

- The map fills the whole screen, edge to edge, behind the system bars.
- Floating elements pad themselves from the system bars and block map gestures only on their own surface.
- The attribution is one line of `labelSmall` text that shrinks on narrow phones, on a tight translucent background so it stays readable over bright imagery. It sits above the navigation bar, below the toolbar.

## Components

### Status chip

A Material 3 `AssistChip` at the top left, below the status bar.

| Content | Icon | Meaning |
| --- | --- | --- |
| Revealed cells count | `grid_view` | `cellCount`, grouped by thousands (locale-aware) |
| Offline maps count | `map` | Number of downloaded areas |

- Tapping the chip does nothing while there are maps. Its `contentDescription` reads both values in full ("1,234 revealed cells, 3 offline maps").
- **No maps:** the chip shows `warning` + "No offline maps" instead of the map count. Tapping it opens the download screen.
- **Loading:** the chip is hidden until the downloaded areas have been read (`mapCount` null), so it never flashes "No offline maps" on launch.

### Help button

`IconButton` with `help` at the top right. It opens an `AlertDialog` explaining the fog, exploring, importing GPX and what the chip numbers mean. It does not open by itself: this screen has no gesture that can't be guessed.

### Floating toolbar

`MapToolbar` (`ui/Theme.kt`), centered at the bottom: the Explore floating action button on the left, then a pill of icon buttons starting with the my location button. Hand-made with stable components because Material's `HorizontalFloatingToolbar` needs material3 1.5, not stable yet (see Exceptions and `docs/PLAN.md`).

| Slot | Icon | Action | Opens |
| --- | --- | --- | --- |
| FAB | `fiber_manual_record` / `stop` | Start / stop exploring | Permission flow on first start (unchanged) |
| 1 (pinned) | see "My location button" | Center / follow / compass | — |
| 2 | `map` | Offline maps | Download screen |
| 3 | `route` | My tracks | Tracks screen (list, import GPX, delete; `docs/ui/screens/tracks.md`) |
| 4 | `settings` | Settings | Settings screen (Advanced section only for now) |

- Spacing is tight (4 dp between FAB and pill, 8 dp screen margin) so the FAB, the handle and four 56 dp icons fit a 360 dp wide phone.
- Every icon button is at least 56 dp. The default Material 3 icon buttons are 48 dp, so they get an explicit size.
- Each one has a `contentDescription` and a tooltip on long press (`TooltipBox` + `PlainTooltip`), except my location, whose long press opens its mode menu.
- The record/stop pair (● / ■) reads as "recording your route". Not exploring: `fiber_manual_record` tinted with the error color (red) on the regular FAB container, so it stands apart from the user-position arrow (a red arrow on the map, not a filled circle on a button). Exploring: `stop` on the error container color, so the state can be seen at a glance.
- **Collapsing.** The toolbar collapses by itself when exploring starts: only the Explore FAB (now Stop), the handle and the my location button stay visible, so the map is as free as possible while walking. My location stays because re-centering is what a walker needs after dragging the map.
  - The handle is the pill's last button, at its right end in both states: `more_horiz` when collapsed (expands), `close` when expanded (collapses again), so opening and closing are tapped in the same place. The other buttons unfold out of it. Same tooltip rules as the other buttons.
  - The handle exists only while exploring. When exploring stops, the toolbar expands and the handle goes away.
  - The expanded/collapsed choice is not stored: it follows the exploring state each time the screen opens.

### User position

MapLibre's location component in `RenderMode.COMPASS`, with custom icons (decisions 0012, 0018): a one-way navigation arrow (`location_arrow`, red towards where the phone faces, white border and dark outline for satellite imagery and red or brown terrain) rotating around a small white pivot (`location_pivot`). The pivot turns gray when there is no recent fix. The accuracy circle stays MapLibre's default.

### My location button

The first button of the toolbar pill (a `MapIconButton`, pinned: visible also when the toolbar is collapsed). It cycles through the camera modes MapLibre's location component already has:

| State | Icon | MapLibre `CameraMode` | Tap goes to |
| --- | --- | --- | --- |
| Free | `location_searching` | `NONE` | Follow |
| Follow (north up) | `my_location` | `TRACKING` | Compass |
| Compass (map rotates with the phone) | `explore` | `TRACKING_COMPASS` | Free |

- **Follow** centers the map on the user, keeping the current zoom, and keeps it centered as they move. Changing mode never zooms; only opening the map uses the "Zoom when opening the map" setting.
- Moving the map (drag, zoom, rotate) in Follow or Compass only pauses following (decision 0017). MapLibre drops tracking by itself (`OnCameraTrackingChangedListener.onCameraTrackingDismissed`); 5 s after the camera last stops (`RESUME_FOLLOWING_MS`, restarted by every new gesture) the previous mode comes back at the zoom the user left, re-centering (and re-applying the compass bearing in Compass; Follow stays north up). The button keeps showing the previous mode during the pause; picking a mode with it cancels the pause. Reloading the maps (back from a download) frames the new area and does go back to Free.
- Leaving Compass resets the bearing to north.
- MapLibre's own compass (shown only while the map is rotated; tapping it turns north up) sits at the top left, below the status chip: `compassGravity` TOP|START, margins 8 dp from the left and 72 dp below the status bar (8 dp padding + 56 dp chip row + 8 dp). It stays there when the chip is hidden while loading.
- The screen opens in Free mode, centered on the user once, as today. The mode is not stored.
- Without location permission, a tap starts the same permission request as the first launch. With no fix yet, a Snackbar says "Waiting for your location…".
- **Long press** opens a `DropdownMenu` to pick any of the three modes directly (icon, name, radio button on the current one); the same permission and fix checks apply to Follow and Compass. Because the long press is taken, this button has no tooltip: the menu names the modes. Screen readers get the long-press action as "Choose location mode". The mode the map opens in is a setting (`docs/ui/screens/settings.md`).
- Following moves the camera on every GPS fix, and each camera idle refreshes the fog for the viewport. That is allowed (viewport only, off the main thread), but it is a new recalculation trigger: add it to `docs/recalculations.md` when implementing it.

### Snackbar

One `SnackbarHost` above the toolbar. It replaces every `Toast` on this screen:

| Message | Action |
| --- | --- |
| "Exploring needs precise location." | "Settings" (opens the app's system settings) |
| "Exploring needs location switched on." | — |
| "Waiting for your location…" | — |

### Dialogs

- The background-location rationale stays a platform `AlertDialog` for now. It is part of the permission flow, not help (decision 0005).
- The help dialog (see above).

## States

Each state has a Roborazzi reference image (`ui/MainScreenScreenshotTest.kt`) and, except 2 and 6, a `@Preview` in `MainScreen.kt`. States 2 and 6 are reached by tapping (the handle, the help button), so the test taps instead of adding preview-only parameters. The map slot is a plain colored box.

| # | State | Differences from the default |
| --- | --- | --- |
| 1 | Default | Maps present, not exploring, toolbar expanded |
| 2 | Exploring, expanded | Toolbar opened with the handle: FAB shows `stop` in the error container color, handle shows `close` |
| 3 | Exploring, collapsed | Only the Stop FAB, the `more_horiz` handle and my location |
| 4 | No maps | Chip shows `warning` + "No offline maps" |
| 5 | Snackbar | "Waiting for your location…" shown above the toolbar |
| 6 | Help | Help dialog open |
| 7 | Dark | State 1 in the dark scheme |
| 8 | Following | My location button shows `my_location` |
| 9 | Compass | My location button shows `explore` |
| 10 | Loading | No status chip yet (downloaded areas not read) |

### State 2 — Exploring, expanded

```
│ ╭────╮ ╭───────────────────────────╮ │
│ │ ■  │ │  ◎    🗺    ⫝    ⚙    ✕   │ │  FAB: stop, error container color · handle (close)
│ ╰────╯ ╰───────────────────────────╯ │
```

### State 3 — Exploring, collapsed

```
│          ╭────╮ ╭──────────╮         │
│          │ ■  │ │  ◎    ⋯  │         │  FAB: stop · my location · handle (more_horiz)
│          ╰────╯ ╰──────────╯         │
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
│ │ Waiting for your location…       │ │
│ └──────────────────────────────────┘ │
│   ╭────╮ ╭───────────────────────╮   │
│   │ ●  │ │  🗺    ⫝    ⚙    ◎    │   │
```

## Strings

New or changed keys (English / Spanish). Removed: `fog_opacity` (moves to Settings), `status`, `no_maps`, `maps`, `import_gpx`, `start_exploring`, `stop_exploring` (replaced by the keys below).

| Key | English | Spanish |
| --- | --- | --- |
| `action_explore` | Start exploring | Empezar a explorar |
| `action_stop` | Stop exploring | Dejar de explorar |
| `action_maps` | Offline maps | Mapas sin conexión |
| `action_tracks` | My tracks | Mis rutas |
| `action_settings` | Settings | Ajustes |
| `action_help` | Help | Ayuda |
| `action_show_toolbar` | Show actions | Mostrar acciones |
| `action_hide_toolbar` | Hide actions | Ocultar acciones |
| `action_follow` | Center on my location | Centrar en mi ubicación |
| `action_compass` | Rotate map with compass | Girar el mapa con la brújula |
| `action_north_up` | Stop following, north up | Dejar de seguir, con el norte arriba |
| `waiting_location` | Waiting for your location… | Esperando tu ubicación… |
| `chip_status` | %1$s revealed cells, %2$d offline maps | %1$s celdas reveladas, %2$d mapas sin conexión |
| `chip_no_maps` | No offline maps | Ningún mapa descargado |
| `chip_cells` | %1$s revealed cells | %1$s celdas reveladas |
| `help_main_title` | Hike Explorer | Hike Explorer |
| `help_main` | (text to write with the implementation) | |

The my location button's description names what the next tap does: `action_follow` in Free, `action_compass` in Follow, `action_north_up` in Compass. The `action_*` texts are tooltips and screen-reader labels, never visible labels, so they can be full sentences.

## Future entries

Not built now; listed so the toolbar layout leaves room for them.

| Entry | Planned for | Where |
| --- | --- | --- |
| Exchange (NFC / Nearby) | Roadmap step 6 | Toolbar slot (`share` or `nfc`) |
| Highlight imported cells | Roadmap step 6 | Toggle icon button under the help button, top right |
| Google Maps Timeline import | Roadmap step 3 | Inside the tracks list screen, next to Import GPX |

The tracks list is built (slot 2, decision 0010). With the rest the pill would hold maps, tracks, exchange, settings and my location, plus the handle: too wide for a 360 dp phone at 56 dp each. Before adding the fourth entry, decide what moves out (e.g. settings into an overflow menu, or exchange into the tracks screen).

## Exceptions

- **Hand-made toolbar.** Material 3 stable (1.4) has no floating toolbar; `MapToolbar` imitates it (decision 0007) until material3 1.5 is stable (`docs/PLAN.md`).
- **Toolbar handle.** Material's floating toolbar collapses on scroll, and a map has no scroll. It collapses when exploring starts instead, and a custom handle button reopens it. Considered and rejected: collapsing while the map is dragged (hides the actions too often), an always-present chevron (one more icon in a toolbar meant to be small), never collapsing (against the guidelines).

## Open questions

None.
