# 0008. Settings screen with Debug section and fog preview

- Status: Accepted
- Date: 2026-10-24

## Context
The guidelines take debugging controls off the map. The fog opacity slider needed a home before the full Settings screen of roadmap step 4 (reveal radius, cell size, alias, reprocessing). Settings covers the map, so the opacity can't be judged while moving the slider.

## Decision
- `ui/SettingsActivity.kt` and `ui/SettingsScreen.kt` (spec `docs/ui/screens/settings.md`). It is opened from the gear icon of the main toolbar.
- Layout: `Scaffold` + `TopAppBar` with back, then sections of Material 3 `ListItem`s. No preference library: Material 3 for Compose has none, and `androidx.preference` uses XML views and Fragments.
- Values stay in `SharedPreferences` `ui` (same `fogOpacity` key as before), saved as the slider moves. `MainActivity` applies the opacity again in `onResume`.
- Debug section always visible while every user is a tester.
- Fog preview card: a bundled IGN tile (`res/drawable-nodpi/fog_preview_sample.jpg`) under the map's own texture (`fogTexture`, `FOG_TEXTURE_SIZE` now visible to the package), repeated at its pixel size like MapLibre's fill pattern, with alpha = opacity. The IGN attribution goes under it.
- About section with the version, read from `packageManager.getPackageInfo` because `BuildConfig` is off.

## Alternatives
- A Compose preference library: less code per row, but a third-party dependency and another storage model for 4–5 settings.
- Debug hidden behind seven taps on the version: extra state and explaining it to every tester.
- Settings as a bottom sheet over the map (the fog visible behind): against the full-screen rule, and too small for step 4.
- No preview: go back to the map to judge each change.

## Consequences
- Roadmap step 4 adds an "Exploration" section above Debug with the same pieces (`ListItem` + dialog).
- The APK grows by the 19 KB sample tile.
- Hide the Debug section when the app reaches regular users.
