# FogHike (brand: Hike Explorer)

Users see the app as **Hike Explorer** (`app_name`). FogHike stays the internal name: package, application id, class names, file names, the design document and `docs/`. Don't rename those; new user-visible text says Hike Explorer.

Android hiking app: the map starts covered by fog that clears around places the user has walked (strategy-game style). Fog is revealed from live GPS or imported routes (GPX; later the Google Maps Timeline on-device export). Base maps are IGN (Spain) satellite imagery stored offline as MBTiles.

The design document is the source of truth: `FogHike — Documento de diseño.md` (Spanish). Read it before any non-trivial change, and update it when a decision changes. Its "Hoja de ruta y decisiones" section lists closed decisions — don't re-open them without asking.

## Documentation

- `docs/` holds technical documentation, in English. Keep it current in the same commit as the code change:
  - `docs/recalculations.md` — every interaction that triggers a recalculation (reprocessing stored data, storing new points, redrawing). Update it when adding or changing a trigger.
  - `docs/configurable-values.md` — every user setting and every constant or inline value that changes behavior; input for the preferences panel. Update it when adding, changing or removing one.
- `docs/PLAN.md` — ideas and planned work not yet in the roadmap; move an item out when it is implemented or becomes a decision.
- `docs/HUMAN_TEST.md` — hands-on checks that need a person and a device (walking, gestures, other phones). Add an item when a change can't be verified with adb or JVM tests.
- `docs/decisions/` — one numbered file per implemented decision (`NNNN-short-title.md`, template in `docs/decisions/README.md`). Add one whenever a design choice lands in code; never renumber or delete, mark replaced ones as superseded. Also reflect the decision in the design document.
- The design document stays in Spanish and remains the source of truth for product decisions; `docs/` explains how the code implements them.

## Stack

- Kotlin, single Android module `app`, application id `com.cuarentaydos.foghike`.
- MapLibre Native (Android) for the map; local HTTP server on 127.0.0.1 serves tiles from `.mbtiles` files (cleartext allowed only for that address).
- Room ≥ 2.7 with KSP; Fused Location Provider (Play Services); coroutines/Flow.
- `minSdk = 31` (Android 12) — old phones are used as hiking GPS units. `targetSdk`/`compileSdk` = latest stable.
- Distribution: signed APK installed by hand (< 100 testers). Every release must use the same signing key; never commit it.

## Layout

Code lives in `app/src/main/java/com/cuarentaydos/foghike/`:

- `geo/` — pure Kotlin: grid quantization, Haversine reveal, cell geometry, fog rectangles, LOD, tile math. No Android, no libraries.
- `data/` — Room entities, DAOs, database only. LOD cells share `revealed_cell` (the id encodes the cell size).
- `core/` — app logic used by screens and services, no Android: `Exploration.kt` (`recordPoints`: the single path from GPS points to stored points and revealed cells, used by tracking and every importer; `importTrack` for whole routes), `FogCells.kt` (fog pieces for an area), `Trail.kt` (recent trail segments).
- Root — `FogHikeApp` (builds the database and tile server; the one Android-specific DB file).
- `tiles/` — local tile server and IGN download to MBTiles. `gpx/` — GPX reader. `tracking/` — foreground tracking service. `ui/` — the two screens, map style JSON, fog layer.
- `tools/build_spain_mbtiles.py` — generates the bundled `app/src/main/assets/spain.mbtiles` (zoom 5–9).

`geo/`, `data/` and `core/` will move to a KMP `shared` module when iOS is ported. Activities, services and workers are platform glue (permissions, files, MapLibre drawing, showing results); any computation or decision iOS would also need goes in `core/`, `geo/` or `data/`, without Android, MapLibre or `java.*` (see decision 0004 for the two known exceptions). Do not create that module, or repository interfaces, before then.

## Rules

- All code, comments, identifiers and commit messages in English, even when the conversation is in Spanish.
- UI strings in `res/values/strings.xml` (English) with Spanish in `res/values-es/strings.xml`. No hardcoded UI text.
- Keep complexity at the level of a well-structured Python CLI: no elaborate architecture, DI frameworks or extra layers. The author is new to mobile development — prefer plain, readable code and explain Android-specific concepts briefly when introducing them.
- KMP-ready `data/`: DAOs use `suspend` and `Flow`; no `LiveData`, `Cursor`, `Context` or `java.*` (timestamps are `Long` epoch millis). Database construction lives in one Android file.
- Cell IDs encode row, column and cell size; cell geometry depends only on the row's latitude. Don't change the ID scheme without updating reprocessing and the design doc.
- Fog is computed only for the visible viewport plus margin, at the LOD matching the zoom, off the main thread, on camera idle or data change — never for the whole country, never per frame.
- Handle permissions per API level (31/33/34+): precise location, background location, battery-optimization exemption, `POST_NOTIFICATIONS`, `FOREGROUND_SERVICE_LOCATION`, Bluetooth/Nearby. See "Compatibilidad con Android 12" in the design doc.
- Exploring must never lose GPS points: every batch is written as it arrives, and the tracking service is `START_STICKY`. Don't buffer points in memory or make the service non-sticky.
- Files received from other users (exchange) are untrusted: limit size and validate coordinates before importing.
- Show the IGN attribution ("PNOA cedido por © Instituto Geográfico Nacional de España") wherever IGN imagery is displayed.
- Room uses destructive migration until the first APK is handed to testers; after that every schema change needs a real migration.

## Build and test

- `./gradlew assembleDebug` — build.
- `./gradlew test` — JVM unit tests (grid and fog geometry live here; add tests for new `geo/` logic).
- `./gradlew installDebug` — install on a connected device.

The project has never been built on a device yet; expect to adjust Gradle/Kotlin/library versions on first sync.
