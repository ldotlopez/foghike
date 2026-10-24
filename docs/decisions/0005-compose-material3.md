# 0005. Jetpack Compose + Material 3 for the UI

- Status: Accepted; dynamic color superseded by 0006 (fixed brand palette)
- Date: 2026-10-24

## Context
The two screens were XML layouts with `findViewById` and the device-default theme, so the app looked different on every manufacturer. Settings, reprocessing progress and the exchange flow are still to come, and an iOS port is planned. Writing them in XML would mean migrating later anyway, and XML can't be shared with iOS.

## Decision
- Both screens use Jetpack Compose with Material 3 and dynamic color (always available on Android 12+, our minSdk). Theme, translucent map panels, the auto-sizing `ActionButton` and the IGN attribution live in `ui/Theme.kt`.
- Each screen stays an Activity calling `setContent`. The layout is a stateless composable (`ui/MainScreen.kt`, `ui/DownloadScreen.kt`) that receives values and callbacks. The Activity keeps the state in Compose `mutableStateOf` fields, plus the permissions, file pickers and map logic. There is no Navigation, ViewModel or DI library.
- The MapLibre `MapView` is still created in the Activity and driven by its lifecycle methods as before; Compose only shows it with `AndroidView`. The touch listener for drawing download areas is unchanged.
- The app draws edge to edge (`enableEdgeToEdge`); panels pad themselves away from system bars and the keyboard.
- Compose BOM 2026.06.01 (Compose 1.11): 2026.08.00 and later need `compileSdk 37`, which isn't installed yet. The Compose compiler plugin version must match the Kotlin built into AGP (2.2.10).
- The background-location explanation dialog is still a platform `AlertDialog`; it can move to Compose when Settings is built.

## Alternatives
- Material 3 theme on XML views: same look with no rewrite, but every new screen would still be XML and nothing could be shared with iOS.
- Community MapLibre Compose wrappers: an extra dependency, and the lifecycle and touch handling we already have work.
- Compose Multiplatform now: needs the KMP `shared` module, postponed until the iOS port (see 0004).

## Consequences
- New screens (Settings first) are written in Compose.
- At the iOS port, screen layouts can move to Compose Multiplatform (the map would go in `UIKitView`) or be redone in SwiftUI. UI strings would then move from `strings.xml` to Compose resources.
- The APK grows by the Compose libraries.
- Gestures over `AndroidView` and the edge-to-edge panels need checking on a device (`docs/HUMAN_TEST.md`).
