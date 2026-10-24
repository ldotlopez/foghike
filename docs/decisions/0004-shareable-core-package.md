# 0004. Shareable logic in `core/`, screens as platform glue

- Status: Accepted
- Date: 2026-10-24

## Context
An iOS version is planned (Kotlin Multiplatform, native MapLibre on each side) but not started. `geo/` and `data/` were already platform-free, but part of the app logic lived inside Android screens: the fog computation (query, rectangles, density, coarser-level fallback) in `ui/FogLayer.kt`, the trail segments in `ui/TrailLayer.kt`, and the GPX duplicate check in `ui/MainActivity.kt`. `Exploration.kt` sat in the root package next to the Android-only `FogHikeApp`. Porting would have meant rewriting that logic, and a future Compose Multiplatform UI could not reuse it.

## Decision
A `core/` package holds app logic that is neither pure geometry nor platform code, and moves to the KMP `shared` module with `geo/` and `data/`:

- `core/Exploration.kt` — `recordPoints` (moved from the root) and `importTrack` (fingerprint id, skip duplicates), used by tracking and importers.
- `core/FogCells.kt` — `fogPieces`, `FogMode`, `FOG_MODE`, `MAX_FOG_RECTS` (moved from `ui/FogLayer.kt`).
- `core/Trail.kt` — `trailSegments` (moved from `ui/TrailLayer.kt`).

Screens, services and workers only do platform glue: permissions, file access, MapLibre drawing, showing results. `FogLayer` and `TrailLayer` now only convert results to GeoJSON.

`core/` uses no Android, MapLibre or `java.*`, with two known exceptions marked in `recordPoints`, to swap at the port: `System.currentTimeMillis` (→ `kotlin.time.Clock`) and Room's Android-only `withTransaction` (→ `useWriterConnection { immediateTransaction }`). They are not changed now because that transaction guards stored GPS points and the change can't be verified without a device.

## Alternatives
- Create the KMP `shared` module now: more Gradle setup and slower builds for no Android benefit; still postponed until the port.
- Repository interfaces or expect/actual now: the DAOs already act as the interface; nothing to abstract until there is a second platform.

## Consequences
- Porting reduces to moving three packages and rewriting the glue (screens, tracking service, tile server, GPX reader).
- Moving the UI to Compose (and later Compose Multiplatform) only touches `ui/`.
- New logic must go to `core/`, `geo/` or `data/`, not into an Activity or service.
