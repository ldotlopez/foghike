# Settings screen

User preferences and debugging controls: a Map section, an Advanced section and About (decision 0011). The alias comes with the exchange. Follows `docs/ui/guidelines.md`. It is the one screen without a map, so the map-screen rules (floating toolbar, chips, attribution) don't apply, and text labels are allowed ("Icons over text" excludes Settings).

Code: `ui/SettingsActivity.kt` (reads and writes preferences) and `ui/SettingsScreen.kt` (stateless layout). Opened from the `settings` icon of the main screen's toolbar; the system back button returns.

## Layout

```
┌──────────────────────────────────────┐
│ (status bar)                         │
│ ←  Settings                          │  TopAppBar: back arrow + title
│                                      │
│ Map                                  │  section header (primary color, labelLarge)
│   Open the map in                    │  choice row: opens a single-choice dialog
│   Free: the map stays still          │
│   Battery saver                 (○ ) │  switch row; the whole row toggles
│   GPS every 15 s and trail …         │
│                                      │
│ Advanced                             │
│ ┌──────────────────────────────────┐ │
│ │ Fog opacity                 70 % │ │  ListItem: headline row with the value at the end
│ │ ●━━━━━━━━━━━━━━━━━━━━━○───────── │ │  Slider 0–100, step 5
│ │ ┌──────────────────────────────┐ │ │
│ │ │ ░░▒▒ IGN sample + fog ▒▒░░   │ │ │  fog preview card, updates live
│ │ └──────────────────────────────┘ │ │
│ │ PNOA cedido por © IGN de España  │ │  required: the preview shows IGN imagery
│ └──────────────────────────────────┘ │
│   Cell size                          │
│   50 m                               │
│   Will be recalculated when you …    │  note (bodySmall, tertiary) while changed
│   Reveal radius · Fog when zoomed out│
│   Old device (switch) · Fog detail   │  fog detail disabled while Old device is on
│   GPS interval · Zoom when opening   │  GPS interval disabled while Battery saver is on
│   Recent trail (switch)              │  when on: duration, color (swatch), width
│                                      │
│ About                                │
│   Version                    0.1 (1) │  ListItem: versionName (versionCode)
│                                      │
│ (navigation bar)                     │
└──────────────────────────────────────┘
```

- `Scaffold` + `TopAppBar` with a back arrow (`arrow_back`, `contentDescription` "Back"). The arrow and the system back button both leave through `SettingsActivity.leave()`, which first runs the pending recalculation, if any.
- Content is a scrolling `Column` of sections, each a header followed by Material 3 `ListItem`s. No preference library: Material 3 for Compose has none, and `androidx.preference` uses XML views and Fragments.
- Two row types besides the opacity slider: **choice** (`ChoiceItem`: headline, current value below, tap opens a single-choice `AlertDialog` with radio rows and Cancel) and **switch** (`SwitchItem`: headline, one-line explanation, `Switch` at the end; the whole row toggles). The trail color shows a color dot in the row and in each option.
- No help button `(?)`: each switch explains itself in its supporting text, and the recalculation note says what will happen.

## Settings

| Section | Setting | Control | Storage | Default |
| --- | --- | --- | --- | --- |
| Map | Open the map in | Choice: Free / Follow, north up / Follow, rotating with the compass | `startMode` | Free |
| Map | Battery saver | Switch (preset). GPS every 15 s, trail redrawn every 30 s. Shows GPS interval disabled at 15 s with "Set by Battery saver" | `batterySaver` | Off |
| Advanced | Fog opacity | `Slider` 0–100 %, steps of 5, value at the end of the headline row (a trailing slot would center it on the whole item, preview included) | `fogOpacity` (unchanged) | 70 % |
| Advanced | Cell size | Choice: 10 / 25 / 50 / 100 m. Recalculation note while changed | `cellSize` | 25 m |
| Advanced | Reveal radius | Choice: 1.2× / 2× / 3× the cell, with the meters for the current cell size. Recalculation note while changed | `radiusFactor` | 1.2× |
| Advanced | Fog when zoomed out | Choice: thinner the more explored (`DENSITY`) / clear if anything inside is explored (`ANY_CELL`) | `fogMode` | `DENSITY` |
| Advanced | Old device | Switch (preset). Fog detail Low (8 px), at most 1,500 fog pieces, trail redrawn every 30 s. Shows fog detail disabled at Low with "Set by Old device" | `slowPhone` | Off |
| Advanced | Fog detail when zoomed out | Choice: High / Medium / Low (2 / 4 / 8 px). Disabled, showing Low, while Old device is on | `fogDetail` | Medium |
| Advanced | GPS interval | Choice: 2 / 5 / 10 / 30 s. Disabled, showing 15 s, while Battery saver is on | `gpsInterval` | 5 s |
| Advanced | Zoom when opening the map | Choice: 12–17. Only at startup; the my location button keeps the current zoom | `userZoom` | 14 |
| Advanced | Recent trail | Switch. When on, shows the three rows below | `showTrail` | On |
| Advanced | Trail duration | Choice: 15 / 30 / 60 / 120 min | `trailMinutes` | 60 min |
| Advanced | Trail color | Choice with color dots: orange, yellow, pink, cyan | `trailColor` | Orange |
| Advanced | Trail width | Choice: 2 / 4 / 6 px | `trailWidth` | 4 px |
| About | Version | Read-only `ListItem` with `versionName (versionCode)` from `packageManager.getPackageInfo` (`BuildConfig` is off in this project) | — | — |

- All keys are in `SharedPreferences` `ui`; the values and their lists are `AppSettings` in `core/Settings.kt`.
- Changes are saved at once, with no "Save" button, as Android settings screens do. **Except cell size and reveal radius:** both force rebuilding every stored cell, so they are held until the user leaves. Meanwhile a small note (`bodySmall`, tertiary color) under each changed one says "Will be recalculated when you close this screen". Setting a value back to the stored one removes its note. On leaving, the content is replaced by a progress bar and "Recalculating the explored area…" and back is disabled until the single rebuild ends (`docs/recalculations.md`).
- The main screen reads every setting again when it comes back to the foreground (`onStart`); the fog opacity is set on the fog layer in `onResume`.
- The Advanced section is always visible: every user is a tester for now (< 100 testers installing the APK by hand). Hide it when the app reaches regular users.
- The version row lets testers say which APK they run when reporting a problem.

### Fog preview

Settings covers the map, so a small card under the opacity slider shows the effect while moving it.

- **Background:** one IGN orthophoto tile, bundled as `res/drawable-nodpi/fog_preview_sample.jpg` (19 KB): zoom 16, column 32029, row 24621, pine wood with clearings near Cercedilla (Sierra de Guadarrama), chosen for its contrast. Fetched once from `IGN_TILE_URL`.
- **Fog:** the same texture the map uses, `fogTexture(FOG_TEXTURE_SIZE)` from `FogLayer.kt`, turned into an `ImageBitmap` once and drawn over the background with alpha = opacity. It reuses the code, so the preview matches the map.
- Shown at full density (as unexplored fog), not the partial density of low zoom levels.
- The IGN attribution goes under the card (`IgnAttribution()`), as on every screen that shows IGN imagery.
- Decorative: `contentDescription = null`, since the slider's value already tells screen readers the opacity.

### Planned

The alias, with the exchange: a `ListItem` in the Map section that opens a dialog with an `OutlinedTextField`.

## States

Each state has a `@Preview` and a Roborazzi reference image.

| # | State | Differences |
| --- | --- | --- |
| 1 | Default | Opacity 70 % |
| 2 | Opacity at limits | 0 % and 100 % (checks the trailing text width and the preview's two ends) |
| 3 | Dark | State 1 in the dark scheme |
| 4 | All rows | Defaults in a tall window: every row visible, trail sub-rows included |
| 5 | Pending and grouped | Cell size 50 m and radius 2× (two recalculation notes), Old device and Battery saver on (fog detail and GPS interval disabled, showing Low and 15 s with "Set by …"), trail off (its sub-rows hidden) |
| 6 | Recalculating | Progress bar and message instead of the content |

## Strings

New keys. `fog_opacity` is kept (now the setting's headline), and the title reuses `action_settings`.

| Key | English | Spanish |
| --- | --- | --- |
| `section_advanced` | Advanced | Avanzado |
| `section_about` | About | Acerca de |
| `version` | Version | Versión |
| `percent` | %1$d %% | %1$d %% |
| `action_back` | Back | Atrás |

Decision 0011 adds `section_map`, `start_mode`, `location_mode_*` (shared with the my location menu), `battery_saver(_detail)`, `recalculate_on_close`, `recalculating`, `cell_size`, `reveal_radius`, `radius_option`, `fog_mode(_density|_any_cell)`, `slow_phone(_detail)`, `fog_detail`, `detail_high|medium|low`, `gps_interval`, `user_zoom`, `show_trail(_detail)`, `trail_duration`, `trail_color`, `color_*`, `trail_width` and the unit formats `meters`, `seconds`, `minutes`, `pixels`; see `res/values*/strings.xml`.

Decision 0014 renames the `slow_phone` text to "Old device" (key kept) and adds `set_by_preset` ("Set by %1$s" / "Fijado por %1$s"), the note on rows a preset sets.

## Exceptions

- No map, toolbar or chips, and text labels instead of icons: the guidelines exempt Settings. The IGN attribution does appear, under the fog preview.

## Open questions

None.
