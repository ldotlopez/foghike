# Settings screen

User preferences and debugging controls. For now it holds only the Debug section with the fog opacity. Reveal radius, cell size and alias come with roadmap step 4, and the layout leaves room for them. Follows `docs/ui/guidelines.md`. It is the one screen without a map, so the map-screen rules (floating toolbar, chips, attribution) don't apply, and text labels are allowed ("Icons over text" excludes Settings).

Code: `ui/SettingsActivity.kt` (reads and writes preferences) and `ui/SettingsScreen.kt` (stateless layout). Opened from the `settings` icon of the main screen's toolbar; the system back button returns.

## Layout

```
┌──────────────────────────────────────┐
│ (status bar)                         │
│ ←  Settings                          │  TopAppBar: back arrow + title
│                                      │
│ Debug                                │  section header (primary color, labelLarge)
│ ┌──────────────────────────────────┐ │
│ │ Fog opacity                 70 % │ │  ListItem: headline + trailing value
│ │ ●━━━━━━━━━━━━━━━━━━━━━○───────── │ │  Slider 0–100, step 5
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
| Debug | Fog opacity | `Slider` 0–100 %, steps of 5, value shown as trailing text | `SharedPreferences` `ui` → `fogOpacity` (unchanged) | 70 % |
| About | Version | Read-only `ListItem` with `versionName (versionCode)` from `packageManager.getPackageInfo` (`BuildConfig` is off in this project) | — | — |

- Changes are saved as the slider moves, with no "Save" button. Android settings screens apply changes immediately.
- The main screen reads `fogOpacity` again when it comes back to the foreground (`onResume`) and sets it on the fog layer. It is only a layer property: nothing is recalculated. Update the trigger row in `docs/recalculations.md` and the "where" column in `docs/configurable-values.md` when implementing it.
- The Debug section is always visible: every user is a tester for now. See open question 2.
- The version row lets testers say which APK they run when reporting a problem.

### Planned (roadmap step 4)

Not built now. They go in a "Exploration" section above Debug:

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
| 2 | Opacity at limits | 0 % and 100 % (checks the trailing text width) |
| 3 | Dark | State 1 in the dark scheme |

## Strings

New keys. `fog_opacity` is kept (now the setting's headline).

| Key | English | Spanish |
| --- | --- | --- |
| `settings_title` | Settings | Ajustes |
| `section_debug` | Debug | Depuración |
| `section_about` | About | Acerca de |
| `version` | Version | Versión |
| `percent` | %1$d %% | %1$d %% |
| `action_back` | Back | Atrás |

## Exceptions

- No map, toolbar, chips or attribution, and text labels instead of icons: the guidelines exempt Settings.

## Open questions

1. **Building blocks.** Plain Material 3 `ListItem`s in a `Column` (above), or a Compose preference library (e.g. `me.zhanghai.compose.preference`)?
2. **Debug section visibility.** Always visible (above), or hidden until the version row is tapped seven times (the Android "developer options" pattern)?
3. **Seeing the fog while adjusting.** Settings covers the map, so the opacity can't be judged while moving the slider. Accept it (go back and look), or add a small preview (a sample image with the fog over it) next to the slider?
