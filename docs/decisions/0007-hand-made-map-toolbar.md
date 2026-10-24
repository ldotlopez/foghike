# 0007. Hand-made floating toolbar and main screen redesign

- Status: Accepted
- Date: 2026-10-24

## Context
The redesign (`docs/ui/screens/main.md`) puts the actions in a floating, collapsible toolbar with Explore as its floating action button, as Material 3's `HorizontalFloatingToolbar` does. That component is only in material3 1.5, which is still beta and requires `compileSdk 37`. The project uses material3 1.4.0 (Compose BOM 2026.06.01) and compileSdk 36 (decision 0005).

## Decision
- `MapToolbar` in `ui/Theme.kt`: a `Surface` pill of `MapIconButton`s with the floating action button beside it. `AnimatedVisibility` collapses it to the FAB alone. An optional handle opens (`more_horiz`) and closes (`close`) it. Its signature mirrors the Material component so it can be swapped in later without touching the screens (`docs/PLAN.md`).
- Main screen rewritten to the spec (`ui/MainScreen.kt`):
  - Status chip (`AssistChip`); help button.
  - Toolbar with maps and import, and the record/stop FAB. It collapses while exploring.
  - My location button. `LocationMode` (Free/Follow/Compass) maps to MapLibre's `CameraMode` (`NONE`/`TRACKING`/`TRACKING_COMPASS`). `MainActivity` reads the mode back from `OnCameraTrackingChangedListener`, so dragging the map resets the button.
  - `SnackbarHost` replaces every `Toast` of the screen.
- The fog opacity slider is removed from the map; it moves to the Settings screen, built next. Until then the stored value is used.
- Previews per spec state, and `ui/MainScreenScreenshotTest.kt` with one Roborazzi image per state.

## Alternatives
- material3 1.5.0-beta: a beta library in a distributed APK, plus `compileSdk 37` and a BOM upgrade. Kept for later.
- Waiting for material3 1.5 stable before redesigning the main screen: it would block the redesign for an unknown time.
- `BottomAppBar`: stable, but full width and fixed, against the guidelines.

## Consequences
- About 40 lines of our own UI code to maintain, and possible small visual differences from the official component.
- When material3 1.5 is stable: follow the `docs/PLAN.md` item, then mark this decision superseded.
- The Settings icon is added to the toolbar together with the Settings screen.
