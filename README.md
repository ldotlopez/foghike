# Hike Explorer

An Android hiking app where the map starts covered by fog that clears around the places you have
walked, like in a strategy game. Fog is revealed from live GPS tracking or from imported GPX routes.
Base maps are IGN (Spain) satellite imagery, downloaded for offline use.

FogHike is the internal name (package `com.cuarentaydos.foghike`, class and file names).

## Features

- Offline maps: a bundled overview of Spain, plus hand-drawn areas downloaded as MBTiles.
- GPX import (several files at once) to reveal routes walked in the past.
- Tracking mode: a foreground service records your position; one button starts and stops it.
- Fog drawn per viewport with levels of detail; adjustable opacity for debugging.
- English and Spanish UI.

## Requirements

- Android 12 (API 31) or newer, ARM phone.
- JDK 21 to build.

## Build

```sh
./gradlew assembleDebug   # build
./gradlew test            # JVM unit tests
./gradlew installDebug    # install on a connected device
```

The bundled overview map `app/src/main/assets/spain.mbtiles` (zoom 5–9) is already in the repository.
To regenerate it, run `python3 tools/build_spain_mbtiles.py` from the repository root.

## Release

Pushing a tag builds a signed ARM release APK and publishes it as a GitHub release
(`.github/workflows/release.yml`). The signing key never lives in the repository; it is passed
through the `SIGNING_STORE_FILE`, `SIGNING_STORE_PASSWORD`, `SIGNING_KEY_ALIAS` and
`SIGNING_KEY_PASSWORD` environment variables. Every release must use the same key.

## Documentation

- [`FogHike — Documento de diseño.md`](FogHike%20—%20Documento%20de%20diseño.md) — design document (Spanish), source of truth for product decisions.
- [`docs/`](docs/) — technical documentation: recalculation triggers, configurable values,
  decision records, planned work and manual device tests.

## Attribution

Satellite imagery: PNOA cedido por © Instituto Geográfico Nacional de España.
