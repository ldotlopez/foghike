# 0006. Fixed brand palette, map slot and screenshot tests

- Status: Accepted
- Date: 2026-10-24

## Context
The UI is being redesigned following `docs/ui/guidelines.md` and the screen specs in `docs/ui/screens/`. The guidelines ask for a fixed palette, icon-only map toolbars, a `(?)` help button, portrait only, and screen states checked with `@Preview` and screenshot tests. Decision 0005 used dynamic color: the app took its colors from the phone's wallpaper, so it looked different on every phone and the map layer colors (fog, trail, areas) couldn't be chosen to contrast with the panels. This decision is the common base. The screens are rewritten on top of it, one per change.

## Decision
- **Fixed palette** in `ui/Color.kt`: light and dark `ColorScheme`s generated from the launcher icon green `#3DDC84` with material-color-utilities (Material Theme Builder's algorithm, 2021 spec, Tonal Spot scheme). `FogHikeTheme` picks one from the phone's light/dark setting. The map layer colors (`BACKGROUND_COLOR`, `TRAIL_COLOR`, `AREA_COLOR`) moved there too, as fixed brand colors.
- **Map slot:** `MainScreen` and `DownloadScreen` take `map: @Composable () -> Unit` instead of a `MapView`. Activities pass `{ AndroidView(factory = { mapView }) }`; previews and tests pass a placeholder.
- **Shared components** in `ui/Theme.kt`: `MIN_TOUCH_TARGET` (56 dp), `WithTooltip` (long-press tooltip), `MapIconButton` (icon-only, 56 dp, label as description and tooltip) and `HelpButton` (`(?)` plus its `AlertDialog`, optionally opened by itself the first time). `MapPanel` and `ActionButton` stay until each screen is rewritten.
- **Icons:** Material Symbols (Outlined) copied as vector drawables into `res/drawable/ic_*.xml`. The `android:tint="?attr/colorControlNormal"` line in the downloaded files is removed (it needs AppCompat, which the app doesn't use; Compose's `Icon` tints anyway).
- **Portrait only:** `android:screenOrientation="portrait"` on every activity.
- **Screenshot tests:** Roborazzi 1.77.0 on Robolectric 4.17. Reference images are in `app/src/test/screenshots/`. `./gradlew recordRoborazziDebug` writes them and `verifyRoborazziDebug` compares against them. `ui/ThemeScreenshotTest.kt` covers the shared components. Robolectric on SDK 36 needs `--add-exports=java.base/jdk.internal.access=ALL-UNNAMED` on the test JVM (set in `app/build.gradle.kts`).

## Alternatives
- Keep dynamic color: rejected by the guidelines (inconsistent look between phones, no control over contrast with the map layers).
- Material Theme Builder web export: the same algorithm. The command-line library gives the same values without a manual export.
- `material-icons-extended`: no longer maintained; Google recommends Material Symbols as vector drawables.
- Paparazzi for screenshots: it renders with layoutlib instead of Robolectric. Roborazzi was chosen in the guidelines and also runs Compose UI tests.

## Consequences
- Supersedes the dynamic-color part of 0005; the rest of 0005 (Compose, one Activity per screen, stateless layouts) stands.
- Every UI change must re-record and check the reference images (`recordRoborazziDebug`, then look at the diff).
- On Android 16+ large screens (≥ 600 dp), the system ignores the portrait lock. Phones are not affected.
- The JVM unit test task now also runs the Robolectric screenshot tests, which download the Android SDK jar on first run.
