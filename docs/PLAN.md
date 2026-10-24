# Plan

## Fog

- **Animated fog (optional).** Setting to show real-looking moving fog, generated procedurally (e.g. scrolling/evolving noise), only while the screen is on and the map is visible; stop animating when the screen is off or the app is in the background. Default stays the current static texture to save battery.
- **Soft fog edges.** The fog still ends in hard cell-shaped edges; blur (feather) the border so it looks more natural.
- **Less visible fog pattern.** At low zoom the 256 px texture visibly repeats as a grid of blobs; use a larger texture or broader noise (lower-frequency octaves).

## Tracks

- **Name a track when it ends.** When the user stops a recording, ask for a name (stored in `track.name`, decision 0010; today recordings show "Exploration HH:MM"). Prefill it with the town where the track started, when known; otherwise leave it empty or use the date.
  - Assume no network: the town must come from an offline source. The IGN maps are raster satellite imagery with no place names, so bundle a small gazetteer instead, e.g. Spain's ~8,100 municipalities with their coordinates (IGN Nomenclátor / INE), and take the nearest one to the first point. Generate it with a script in `tools/` like `spain.mbtiles`.

## UI

- **Reorder the map toolbar: location button first, handle last.** Inside the toolbar container (`MapToolbar`, `ui/Theme.kt`) the order is today: handle (… / ✕), Maps, Tracks, Settings, location mode button (`pinned`). Move the location mode button to the first position and the handle to the last, so the … button (collapsed) and the ✕ button (expanded, returns to collapsed) both sit on the right, in the same place. Check the expand/shrink animation direction still looks right, update `docs/ui/screens/main.md` (and `download.md` if it uses the same toolbar) and re-record the Roborazzi references.

- **Material 3 floating toolbar (when material3 1.5 is stable).** The map toolbars are hand-made (`MapToolbar` in `ui/Theme.kt`, decision 0007) because `HorizontalFloatingToolbar` only exists from material3 1.5, which is still beta (1.5.0-beta02 in Oct 2026). Material3 1.5 requires `compileSdk 37`, AGP ≥ 9.1, and a Compose BOM of 2026.08 or later (2026.08+ already needs `compileSdk 37`, decision 0005).
  - Steps: install SDK 37 and set `compileSdk = 37` (targetSdk can stay); update the Compose BOM; replace the body of `MapToolbar` with `HorizontalFloatingToolbar(expanded, floatingActionButton = …)`, keeping its signature so the screens don't change; move the collapsed handle into the toolbar if the component supports it, or keep it outside; re-record the Roborazzi references and compare.
  - Then remove the "Toolbar" exception from `docs/ui/screens/main.md` and `download.md`, and mark decision 0007 superseded.
  - Same check for other Expressive components the specs could use then (e.g. `FloatingToolbarDefaults.VibrantFloatingActionButton` for Explore).

## Settings

- **Export / import data and settings (idea).** Let the user save the database (tracks, points, revealed cells) and the settings to a file and restore them, e.g. to move to another phone or keep a backup. An imported file is untrusted: limit its size and validate it as for exchange files.
- **Cycling profile (idea).** A set of settings tuned for riding a bike (e.g. GPS interval, reveal radius, trail), chosen when starting a track (walking or cycling).
- **Setting presets: "Battery saver" and "Old device".** Two switches that each set a group of the existing settings to fixed values; while a preset is on, the settings it controls are shown disabled in the UI. Pick which settings each preset covers (e.g. GPS interval, fog texture/animation, LOD detail). Document the behaviour and the affected settings in the user manual (`README.md`).

## Release

- **Signed release builds in GitHub Actions (idea).** Store the signing keystore and its passwords as GitHub secrets and build the signed release APK in the workflow that runs when a tag is pushed; the workflow must be able to run on its own (also manually) without local files. That APK is the release version, not debug.
  - Debug builds on the test phone: Android never installs an unsigned APK, but debug builds don't need the release key — Gradle signs them with the local debug keystore (`~/.android/debug.keystore`), so `./gradlew installDebug` keeps working.
  - To keep release and debug installed side by side, add `applicationIdSuffix = ".debug"` (and e.g. a different app name) to the debug build type; otherwise both share the application id and installing one over the other fails with a signature mismatch.
