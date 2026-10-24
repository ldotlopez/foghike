# Settings screen

User preferences and debugging controls. For now it holds only the Advanced section with the fog opacity. Reveal radius, cell size and alias come with roadmap step 4, and the layout leaves room for them. Follows `docs/ui/guidelines.md`. It is the one screen without a map, so the map-screen rules (floating toolbar, chips, attribution) don't apply, and text labels are allowed ("Icons over text" excludes Settings).

Code: `ui/SettingsActivity.kt` (reads and writes preferences) and `ui/SettingsScreen.kt` (stateless layout). Opened from the `settings` icon of the main screen's toolbar; the system back button returns.

## Layout

```
┌──────────────────────────────────────┐
│ (status bar)                         │
│ ←  Settings                          │  TopAppBar: back arrow + title
│                                      │
│ Advanced                             │  section header (primary color, labelLarge)
│ ┌──────────────────────────────────┐ │
│ │ Fog opacity                 70 % │ │  ListItem: headline row with the value at the end
│ │ ●━━━━━━━━━━━━━━━━━━━━━○───────── │ │  Slider 0–100, step 5
│ │ ┌──────────────────────────────┐ │ │
│ │ │ ░░▒▒ IGN sample + fog ▒▒░░   │ │ │  fog preview card, updates live
│ │ └──────────────────────────────┘ │ │
│ │ PNOA cedido por © IGN de España  │ │  required: the preview shows IGN imagery
│ └──────────────────────────────────┘ │
│                                      │
│ About                                │
│   Version                    0.1 (1) │  ListItem: versionName (versionCode)
│                                      │
│ (navigation bar)                     │
└──────────────────────────────────────┘
```

- `Scaffold` + `TopAppBar` with a back arrow (`arrow_back`, `contentDescription` "Back"). The arrow calls `finish()`, the same as the system back button.
- Content is a scrolling `Column` of sections, each a header followed by Material 3 `ListItem`s. No preference library: Material 3 for Compose has none, and `androidx.preference` uses XML views and Fragments.
- The help button `(?)` is only added when a setting needs an explanation (step 4: radius and cell size trigger reprocessing). It then goes in the `TopAppBar` actions, top right.

## Settings

| Section | Setting | Control | Storage | Default |
| --- | --- | --- | --- | --- |
| Advanced | Fog opacity | `Slider` 0–100 %, steps of 5, value at the end of the headline row (a trailing slot would center it on the whole item, preview included) | `SharedPreferences` `ui` → `fogOpacity` (unchanged) | 70 % |
| About | Version | Read-only `ListItem` with `versionName (versionCode)` from `packageManager.getPackageInfo` (`BuildConfig` is off in this project) | — | — |

- Changes are saved as the slider moves, with no "Save" button. Android settings screens apply changes immediately.
- The main screen reads `fogOpacity` again when it comes back to the foreground (`onResume`) and sets it on the fog layer. It is only a layer property: nothing is recalculated. Update the trigger row in `docs/recalculations.md` and the "where" column in `docs/configurable-values.md` when implementing it.
- The Advanced section is always visible: every user is a tester for now (< 100 testers installing the APK by hand). Hide it when the app reaches regular users.
- The version row lets testers say which APK they run when reporting a problem.

### Fog preview

Settings covers the map, so a small card under the opacity slider shows the effect while moving it.

- **Background:** one IGN orthophoto tile, bundled as `res/drawable-nodpi/fog_preview_sample.jpg` (19 KB): zoom 16, column 32029, row 24621, pine wood with clearings near Cercedilla (Sierra de Guadarrama), chosen for its contrast. Fetched once from `IGN_TILE_URL`.
- **Fog:** the same texture the map uses, `fogTexture(FOG_TEXTURE_SIZE)` from `FogLayer.kt`, turned into an `ImageBitmap` once and drawn over the background with alpha = opacity. It reuses the code, so the preview matches the map.
- Shown at full density (as unexplored fog), not the partial density of low zoom levels.
- The IGN attribution goes under the card (`IgnAttribution()`), as on every screen that shows IGN imagery.
- Decorative: `contentDescription = null`, since the slider's value already tells screen readers the opacity.

### Planned (roadmap step 4)

Not built now. They go in a "Exploration" section above Advanced:

| Setting | Control |
| --- | --- |
| Reveal radius | `ListItem` that opens a single-choice `AlertDialog` (closed list) |
| Cell size | Same |
| Alias | `ListItem` that opens a dialog with an `OutlinedTextField` |

Saving radius or cell size asks for confirmation and starts reprocessing (design document, "Ajustes").

## States

Each state has a `@Preview` and a Roborazzi reference image.

| # | State | Differences |
| --- | --- | --- |
| 1 | Default | Opacity 70 % |
| 2 | Opacity at limits | 0 % and 100 % (checks the trailing text width and the preview's two ends) |
| 3 | Dark | State 1 in the dark scheme |

## Strings

New keys. `fog_opacity` is kept (now the setting's headline), and the title reuses `action_settings`.

| Key | English | Spanish |
| --- | --- | --- |
| `section_advanced` | Advanced | Avanzado |
| `section_about` | About | Acerca de |
| `version` | Version | Versión |
| `percent` | %1$d %% | %1$d %% |
| `action_back` | Back | Atrás |

## Exceptions

- No map, toolbar or chips, and text labels instead of icons: the guidelines exempt Settings. The IGN attribution does appear, under the fog preview.

## Open questions

None.
