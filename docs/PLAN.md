# Plan

## Fog

- **Animated fog (optional).** Setting to show real-looking moving fog, generated procedurally (e.g. scrolling/evolving noise), only while the screen is on and the map is visible; stop animating when the screen is off or the app is in the background. Default stays the current static texture to save battery.
- **Soft fog edges.** The fog still ends in hard cell-shaped edges; blur (feather) the border so it looks more natural.
- **100 % opacity is not fully opaque.** At 100 % the map still shows through the fog. Likely cause: `fogTexture` (`ui/FogLayer.kt`) gives each pixel an alpha of 205–255 ("thinner patches let a hint of the map through"), and the layer opacity only multiplies it. Make 100 % mean fully covered (e.g. scale the texture alpha towards 255 as the opacity approaches 100 %, or keep the texture opaque and let the opacity do the thinning); the Settings preview must match.
- **Less visible fog pattern.** At low zoom the 256 px texture visibly repeats as a grid of blobs; use a larger texture or broader noise (lower-frequency octaves).

## Tracks

- **Name a track when it ends.** When the user stops a recording, ask for a name (stored in `track.name`, decision 0010; today recordings show "Exploration HH:MM"). Prefill it with the town where the track started, when known; otherwise leave it empty or use the date.
  - Assume no network: the town must come from an offline source. The IGN maps are raster satellite imagery with no place names, so bundle a small gazetteer instead, e.g. Spain's ~8,100 municipalities with their coordinates (IGN Nomenclátor / INE), and take the nearest one to the first point. Generate it with a script in `tools/` like `spain.mbtiles`.

## UI

- **Red heading arrow for the user's position.** Replace MapLibre's default puck (blue dot with a heading arrow, `RenderMode.COMPASS` in `showUserLocation`, `ui/MainActivity.kt`) with a red compass-needle style arrow: a clearer cue of where the user faces. MapLibre's `LocationComponentOptions` takes custom drawables (e.g. `foregroundDrawable` / `bearingDrawable`), so no new layer should be needed. Keep it distinct from the red record button (decision 0007 picked red for record so it isn't confused with the blue dot) and readable over satellite imagery; update `docs/ui/screens/main.md` and re-check `docs/HUMAN_TEST.md` "Heading arrow".

- **Centering on the user must not change the zoom.** Today switching the location mode from Free to Follow/Compass (the my location button) also zooms to `userZoom` (`setLocationMode`, `ui/MainActivity.kt`). Changing the tracking mode should keep the current zoom: pass `null` as the zoom. `userZoom` (Settings → Advanced) is also used at startup (`centerOnUser`), so it stays unless that use goes too; if it ends up unused, remove it from `core/Settings.kt`, the Settings screen, strings and `docs/configurable-values.md`.

- **Material 3 floating toolbar (when material3 1.5 is stable).** The map toolbars are hand-made (`MapToolbar` in `ui/Theme.kt`, decision 0007) because `HorizontalFloatingToolbar` only exists from material3 1.5, which is still beta (1.5.0-beta02 in Oct 2026). Material3 1.5 requires `compileSdk 37`, AGP ≥ 9.1, and a Compose BOM of 2026.08 or later (2026.08+ already needs `compileSdk 37`, decision 0005).
  - Steps: install SDK 37 and set `compileSdk = 37` (targetSdk can stay); update the Compose BOM; replace the body of `MapToolbar` with `HorizontalFloatingToolbar(expanded, floatingActionButton = …)`, keeping its signature so the screens don't change; move the collapsed handle into the toolbar if the component supports it, or keep it outside; re-record the Roborazzi references and compare.
  - Then remove the "Toolbar" exception from `docs/ui/screens/main.md` and `download.md`, and mark decision 0007 superseded.
  - Same check for other Expressive components the specs could use then (e.g. `FloatingToolbarDefaults.VibrantFloatingActionButton` for Explore).

## Settings

- **Export / import data and settings (idea).** Let the user save the database (tracks, points, revealed cells) and the settings to a file and restore them, e.g. to move to another phone or keep a backup. An imported file is untrusted: limit its size and validate it as for exchange files.
- **Cycling profile (idea).** A set of settings tuned for riding a bike (e.g. GPS interval, reveal radius, trail), chosen when starting a track (walking or cycling).
- **Setting presets: "Battery saver" and "Old device".** Two switches that each set a group of the existing settings to fixed values; while a preset is on, the settings it controls are shown disabled in the UI. Pick which settings each preset covers (e.g. GPS interval, fog texture/animation, LOD detail). Document the behaviour and the affected settings in the user manual (`README.md`).

## Release

Goal: every release is published automatically in two places from the same signed APK: GitHub Releases (manual download; Obtainium users follow it on their own) and our own F-Droid repository on GitHub Pages. The official F-Droid catalog is out of scope (it rejects Play Services and needs a free license).

1. **First real release on GitHub (already built, never run).** Before tagging, decide the version scheme: today `versionCode` is bumped by hand and Android (and the F-Droid client) ignores an update whose `versionCode` is not higher. Proposal: `versionName` from the tag (`v0.2` → `0.2`) and `versionCode` from env vars set by the workflow, with the current values as the local default. Run the workflow once (tag or manual run) and install the APK on a phone.
2. **F-Droid repo key.** Run `fdroid init` once locally to create the repo keystore (separate from the APK signing key). Back it up next to the APK key; losing it forces every user to remove and re-add the repo. Store it as secrets: `FDROID_KEYSTORE_BASE64`, `FDROID_KEYSTORE_PASS`, `FDROID_KEY_PASS`. Note the repo fingerprint.
3. **Repo config in git, no secrets.** `fdroid/config.yml` with `repo_url` (`https://ldotlopez.github.io/foghike/repo`), `repo_name`, `repo_description`, `repo_icon`, `archive_older: 3` and passwords read with `{env: ...}`. App metadata in `fdroid/metadata/com.cuarentaydos.foghike.yml` (name Hike Explorer, license, source/issue URLs, `AntiFeatures: NonFreeDep` for Play Services) plus fastlane-style `en-US` and `es` texts and screenshots (reuse the Roborazzi ones).
4. **Pages job in `release.yml`.** A second job, `needs: release`, so it only runs after the APK is published. Rebuild the repo from scratch every time instead of keeping state in a `gh-pages` branch: `gh release download` the APKs of the last 3 releases into `fdroid/repo/`, install `fdroidserver` (apt), decode the keystore, `fdroid update`, then `actions/upload-pages-artifact` + `actions/deploy-pages` (permissions `pages: write`, `id-token: write`). Also triggerable by hand to republish without a new release. Size: ~54 MB per APK, well under the 1 GB Pages limit.
5. **One-time GitHub setup (person).** Settings → Pages → Source: "GitHub Actions".
6. **How users add it.** README section with the repo URL, fingerprint and the `https://ldotlopez.github.io/foghike/repo?fingerprint=…` link/QR, plus the GitHub Releases link for manual download.
7. **Docs when done.** New decision (F-Droid repo on Pages built from releases), update decision 0003 if the version scheme changes, the design document's "Distribución" paragraph, and `docs/HUMAN_TEST.md`: add the repo in the F-Droid app, install, then update to the next release.
