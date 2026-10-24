# UI guidelines

General rules for every Hike Explorer screen. Read before designing or changing any screen; each screen spec in `docs/ui/screens/` assumes them. When a screen needs an exception, the spec states it and why.

## Principles

- **Common patterns first.** The author is new to Android: by default use the most common, officially recommended pattern. Use Material 3 components; build a custom component only when Material 3 has no equivalent, and note why in the screen spec.
- **The map is the center.** Every screen is built around the map, except Settings. Actions and access to other screens live in floating, collapsible toolbars over the map.
- **No text that takes useful space.** Help and explanations never take permanent space on screen: a `(?)` icon opens a modal with the text.
- **Icons over text.** Outside Settings, actions are icon buttons (`IconButton`, `FloatingActionButton`), not text buttons. Each one shows its `contentDescription` as a tooltip on long press (Material 3 `TooltipBox` + `PlainTooltip`), so its meaning can always be checked. Text stays where an icon would be ambiguous or the pattern expects it: dialog buttons ("Cancel", "Download"), Snackbar actions, form fields.

## Screens and navigation

- One Activity per screen, each with a stateless `*Screen` composable (decision 0005). The system back button returns to the previous screen.
- Secondary screens (tracks list, downloaded maps, exchange confirmation) open **full screen**. Exceptions are decided per screen in its spec.
- Portrait only (`android:screenOrientation="portrait"` on every Activity).

## Map screens

- **Floating toolbars at the bottom**, within thumb reach for one-handed use while walking. They are collapsible.
- **Explore (start/stop) lives inside the toolbar** and collapses with it.
- Floating elements block map gestures only on their own surface.
- **Touch targets: at least 56 dp** (above Material's 48 dp minimum, for outdoor use while walking).
- **Status as a compact chip** over the map (revealed cells, offline maps). The "no maps" notice goes in the chip or a Snackbar, never a full-width panel.
- **IGN attribution** ("PNOA cedido por © Instituto Geográfico Nacional de España") is mandatory wherever IGN imagery is shown: one line of small text in a bottom corner of the map. It can never move into a modal.

## Help

- Help text is shown in a modal (`AlertDialog`) opened from a `(?)` icon button (Material Symbols `help`), **always at the top right** of the screen.
- When a screen relies on a gesture that can't be guessed (e.g. circling the area to download), its help modal opens **automatically the first time** the screen is entered; after that, only from `(?)`. Remember "seen" per screen in preferences.
- Not help, and therefore not in the modal:
  - The IGN attribution (see above).
  - Permission rationale dialogs: part of the Android permission flow.
  - Validation errors: next to their field (`OutlinedTextField` `supportingText` / `isError`).

## Messages

- Transient messages (import results, download finished, errors) use a Material 3 **`Snackbar`**, with at most one action ("Retry", "View"). No `Toast`.

## Look

- **Fixed brand palette**, not dynamic color: generated with Material Theme Builder from the launcher icon green (`#3DDC84`) as seed, light and dark schemes. Map layer colors (fog, trail, download areas, imported-cells highlight) are fixed brand colors defined next to the theme and must keep contrast with the panels and toolbars.
- Icons: [Material Symbols](https://fonts.google.com/icons) (Apache 2.0; the same Google set that MUI wraps for React), Outlined style, one style for the whole app. Import only the icons used, as vector drawables in `res/drawable/` (Android Studio → New → Vector Asset, or the "Android" download on the Symbols site), and draw them with `painterResource`. Delete the `android:tint="?attr/colorControlNormal"` line from downloaded files: it needs AppCompat, which the app doesn't use, and Compose's `Icon` tints anyway. Don't add the `material-icons-extended` library: it is no longer maintained and Google recommends Material Symbols instead. Every icon button has a `contentDescription` (string resource, translated).
- All UI text in `strings.xml` (English) and `values-es` (Spanish), as before.

## Debug controls

- Debugging controls (e.g. fog opacity) live in a **Debug section of Settings**, never on the map. Their values are stored and applied as today.

## Verifying a screen

- Screen composables take the map as a slot (`map: @Composable () -> Unit`); the Activity passes `{ AndroidView(factory = { mapView }) }`, previews and tests pass a placeholder.
- Every state listed in a screen spec has a `@Preview`.
- Screenshot tests with **Roborazzi** render those states on the JVM (`./gradlew verifyRoborazziDebug`); reference images live in the repo and are compared with the mockups in `docs/ui/screens/`.
- What can only be checked on a device (gestures over the map, sunlight readability, edge-to-edge insets) goes to `docs/HUMAN_TEST.md`.
