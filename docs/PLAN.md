# Plan

## Fog

- **Animated fog (optional).** Setting to show real-looking moving fog, generated procedurally (e.g. scrolling/evolving noise), only while the screen is on and the map is visible; stop animating when the screen is off or the app is in the background. Default stays the current static texture to save battery.
- **Soft fog edges.** The fog still ends in hard cell-shaped edges; blur (feather) the border so it looks more natural.
- **100 % opacity is not fully opaque.** At 100 % the map still shows through the fog. Likely cause: `fogTexture` (`ui/FogLayer.kt`) gives each pixel an alpha of 205–255 ("thinner patches let a hint of the map through"), and the layer opacity only multiplies it. Make 100 % mean fully covered (e.g. scale the texture alpha towards 255 as the opacity approaches 100 %, or keep the texture opaque and let the opacity do the thinning); the Settings preview must match.
- **Less visible fog pattern.** At low zoom the 256 px texture visibly repeats as a grid of blobs; use a larger texture or broader noise (lower-frequency octaves).

## Tracks

- **Name a track when it ends.** When the user stops a recording, ask for a name (stored in `track.name`, decision 0010; today recordings show "Exploration HH:MM"). Prefill it with the town where the track started, when known; otherwise leave it empty or use the date.
  - Assume no network: the town must come from an offline source. The IGN maps are raster satellite imagery with no place names, so bundle a small gazetteer instead, e.g. Spain's ~8,100 municipalities with their coordinates (IGN Nomenclátor / INE), and take the nearest one to the first point. Generate it with a script in `tools/` like `spain.mbtiles`.

## Settings

- **Configurable values in Settings.** Show in the Settings screen the values listed in `docs/configurable-values.md` that make sense as preferences. Before implementing, go through them with the author **one by one**: for each, whether it is exposed in the main sections, under an "Advanced" subsection, or not at all. Values that force reprocessing follow "One recalculation when leaving Settings" below.

- **One recalculation when leaving Settings.** When several changed preferences each need a recalculation, run it once when the user leaves the Settings screen instead of after each change. Under every changed preference that needs it, show a small warning line (smaller font) such as "Will be recalculated when you close this screen".

## UI

- **Red heading arrow for the user's position.** Replace MapLibre's default puck (blue dot with a heading arrow, `RenderMode.COMPASS` in `showUserLocation`, `ui/MainActivity.kt`) with a red compass-needle style arrow: a clearer cue of where the user faces. MapLibre's `LocationComponentOptions` takes custom drawables (e.g. `foregroundDrawable` / `bearingDrawable`), so no new layer should be needed. Keep it distinct from the red record button (decision 0007 picked red for record so it isn't confused with the blue dot) and readable over satellite imagery; update `docs/ui/screens/main.md` and re-check `docs/HUMAN_TEST.md` "Heading arrow".

- **Material 3 floating toolbar (when material3 1.5 is stable).** The map toolbars are hand-made (`MapToolbar` in `ui/Theme.kt`, decision 0007) because `HorizontalFloatingToolbar` only exists from material3 1.5, which is still beta (1.5.0-beta02 in Oct 2026). Material3 1.5 requires `compileSdk 37`, AGP ≥ 9.1, and a Compose BOM of 2026.08 or later (2026.08+ already needs `compileSdk 37`, decision 0005).
  - Steps: install SDK 37 and set `compileSdk = 37` (targetSdk can stay); update the Compose BOM; replace the body of `MapToolbar` with `HorizontalFloatingToolbar(expanded, floatingActionButton = …)`, keeping its signature so the screens don't change; move the collapsed handle into the toolbar if the component supports it, or keep it outside; re-record the Roborazzi references and compare.
  - Then remove the "Toolbar" exception from `docs/ui/screens/main.md` and `download.md`, and mark decision 0007 superseded.
  - Same check for other Expressive components the specs could use then (e.g. `FloatingToolbarDefaults.VibrantFloatingActionButton` for Explore).
