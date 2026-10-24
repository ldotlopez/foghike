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
│                                      │
│      ╭───────────────────╮ ╭────╮    │
│      │  🗺    ⇪    ⚙     │ │ 🥾 │    │  floating toolbar + Explore FAB
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
| FAB | `hiking` / `stop` | Start / stop exploring | Permission flow on first start (unchanged) |
| 1 | `map` | Offline maps | Download screen |
| 2 | `upload_file` | Import GPX | System file picker (multiple files) |
| 3 | `settings` | Settings | Settings screen (Debug section only for now) |

- Every icon button is at least 56 dp. The default Material 3 icon buttons are 48 dp, so they get an explicit size.
- Each one has a `contentDescription` and a tooltip on long press (`TooltipBox` + `PlainTooltip`).
- While exploring, the FAB uses the `stop` icon and the error container color so the state can be seen at a glance.
- Collapsing: see open question 1.

### Snackbar

One `SnackbarHost` above the toolbar. It replaces every `Toast` on this screen:

| Message | Action |
| --- | --- |
| GPX import result: imported / already imported / unreadable counts, in one line | — |
| "Exploring needs precise location." | "Settings" (opens the app's system settings) |
| "Exploring needs location switched on." | — |

### Dialogs

- The background-location rationale stays a platform `AlertDialog` for now. It is part of the permission flow, not help (decision 0005).
- The help dialog (see above).

## States

Each state has a `@Preview` and a Roborazzi reference image. The map slot is a plain colored box.

| # | State | Differences from the default |
| --- | --- | --- |
| 1 | Default | Maps present, not exploring, toolbar expanded |
| 2 | Exploring | FAB shows `stop` in the error container color |
| 3 | Collapsed | Toolbar collapsed (see open question 1) |
| 4 | No maps | Chip shows `warning` + "No offline maps" |
| 5 | Snackbar | GPX import result shown above the toolbar |
| 6 | Help | Help dialog open |
| 7 | Dark | State 1 in the dark scheme |

### State 2 — Exploring

```
│      ╭───────────────────╮ ╭────╮    │
│      │  🗺    ⇪    ⚙     │ │ ■  │    │  FAB: stop, error container color
│      ╰───────────────────╯ ╰────╯    │
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
│      │  🗺    ⇪    ⚙     │ │ 🥾 │    │
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
| `chip_status` | %1$s revealed cells, %2$d offline maps | %1$s celdas reveladas, %2$d mapas sin conexión |
| `chip_no_maps` | No offline maps | Ningún mapa descargado |
| `help_main_title` | Hike Explorer | Hike Explorer |
| `help_main` | (text to write with the implementation) | |

The `action_*` texts are tooltips and screen-reader labels, never visible labels, so they can be full sentences.

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

None yet.

## Open questions

1. **When does the toolbar collapse?** Material's floating toolbar is meant to collapse on scroll, and a map has no scroll. Options:
   - a) Collapse while the map is being dragged and expand on camera idle (automatic, no extra control).
   - b) Collapse automatically once exploring starts, expand with a tap on a small handle.
   - c) An explicit chevron button at the toolbar's edge.

   Recommendation: a). It frees the map exactly while the user is looking at it, and needs no extra button.
2. **"My location" button.** There is no way to re-center on the user after moving the map (only at launch). This is the usual map pattern (`my_location`, bottom right, above the toolbar). Add it now?
3. **FAB icon.** `hiking` for start reads as "the hiking app" more than "start". The alternative is `play_arrow` / `stop`, clearer as an action but generic.
