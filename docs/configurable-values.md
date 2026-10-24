# Configurable values

Inventory of the values and behaviors the user can already change, or that depend on a constant in the code. Input for designing the preferences panel. The "Setting?" column is a first proposal: **Yes** (clear candidate), **Maybe** (useful for testing or advanced users), **No** (technical, legal or fixed by design). "Reprocess" says whether changing it forces rebuilding the stored cells (see `recalculations.md`). When adding or changing a constant that affects behavior, update this file. State as of 2026-10-24.

## Already configurable by the user

| Value | Where | Default | Persisted | Notes |
| --- | --- | --- | --- | --- |
| Fog opacity | Slider on the main screen; `SharedPreferences` `ui` → `fogOpacity` | 70 % | Yes, across sessions | Scales all fog, including `DENSITY`'s partial fog. Recalculates nothing |
| Download min zoom | Field on the download screen (`activity_download.xml`) | 13 | No, per download | Validated between 0 and `IGN_MAX_ZOOM` |
| Download max zoom | Field on the download screen | 16 | No, per download | Must be ≥ min zoom |
| Areas to download | Drawn freehand | — | In `downloaded_area` | — |

`ui` also stores `askedStartup` and `askedBackground`: internal flags so permission requests aren't repeated. Not preferences.

## Designed for Settings (`AppSettings` table, not implemented)

| Value | Planned options | Default | Reprocess | Current constant |
| --- | --- | --- | --- | --- |
| Cell size | 10, 25, 50 or 100 m | 25 m | Yes | `CELL_SIZE_METERS` (`geo/Grid.kt`) |
| Reveal radius | 1.2×, 2× or 3× the cell | 1.2× (30 m) | Yes | `REVEAL_RADIUS_METERS` (`geo/Grid.kt`); also the GPS accuracy threshold of `isAccurate` |
| Trail duration | Minutes (list to be defined) | 60 min | No | `TRAIL_DURATION_MS` (`ui/TrailLayer.kt`); the design doc calls it `trailMinutes` |
| User alias | Text | — | No | Asked on the first NFC send |

## Code constants

### Fog and reveal

| Constant | File | Value | Effect | Reprocess | Setting? |
| --- | --- | --- | --- | --- | --- |
| `FOG_MODE` | `ui/FogLayer.kt` | `DENSITY` | How coarse cells are drawn: `ANY_CELL` (clear if anything inside is revealed) or `DENSITY` (partial fog by explored share). See [0001](decisions/0001-low-zoom-fog-density.md) | No | Maybe: only if more than one mode survives testing |
| `fogDensity` (formula) | `geo/Fog.kt` | Logarithmic | Fog left on a coarse cell given `revealedCount` | No | No (maybe a "contrast" knob) |
| `minPixels` of `lodLevelFor` | `geo/Grid.kt` (parameter default) | 4 px | Minimum on-screen cell size before switching to a coarser level. Fewer pixels = more faithful trace when zoomed out, more rectangles | No | Maybe ("fog detail") |
| `LOD_FACTOR` | `geo/Grid.kt` | 8 | Each detail level is 8× the previous one | Yes (coarse cells) | No |
| `LOD_LEVELS` | `geo/Grid.kt` | 4 | Levels: 25 m, 200 m, 1.6 km, 12.8 km | Yes (coarse cells) | No |
| `MAX_FOG_RECTS` | `ui/FogLayer.kt` | 5,000 | Above it, a coarser level is used. Protects old phones | No | Maybe ("slow phone" mode) |
| `FOG_TEXTURE_SIZE` | `ui/FogLayer.kt` | 256 px | Noise texture size; repetition shows at low zoom (`PLAN.md`) | No | No |
| Texture colors and alpha | `fogTexture` in `ui/FogLayer.kt` (inline numbers) | Blue-grey to white, alpha 205–255 | Fog look | No | Maybe (fog theme, with the animated fog in `PLAN.md`) |
| Computation margin | `Box.expanded()` in `geo/Grid.kt` | One screen per side (3×3) | How far the map can move without recomputing fog | No | No |

### Trail and position

| Constant | File | Value | Effect | Setting? |
| --- | --- | --- | --- | --- |
| `TRAIL_DURATION_MS` | `ui/TrailLayer.kt` | 1 h | How long the trail stays before fully fading | Yes (already planned) |
| `TRAIL_REFRESH_MS` | `ui/TrailLayer.kt` | 10 s | How often the trail is redrawn with the screen on | No |
| `TRAIL_COLOR` | `ui/TrailLayer.kt` | `#ff7a1a` (orange) | Trail color | Maybe |
| Trail width | `ui/TrailLayer.kt` (`lineWidth(4f)`) | 4 px | — | Maybe, with the color |
| Show trail | — (always on) | Yes | — | Maybe (on/off) |
| Position mode | `showUserLocation` in `ui/MainActivity.kt` (`RenderMode.COMPASS`, `CameraMode.NONE`) | Compass, camera doesn't follow | Arrow towards where the phone points | Maybe (follow user / rotate map) |

### GPS tracking

| Constant | File | Value | Effect | Setting? |
| --- | --- | --- | --- | --- |
| `INTERVAL_MS` | `tracking/TrackingService.kt` | 5 s | How often the service requests a location. Shorter = finer trail, more battery | Maybe ("battery saver") |
| GPS priority | `TrackingService.kt` (`PRIORITY_HIGH_ACCURACY`) | High accuracy | Without it points exceed the accuracy radius and reveal nothing | No |
| Interval when checking location settings | `ensureLocationOn` in `ui/MainActivity.kt` (inline `5_000`) | 5 s | Only used to ask the system to turn location on; should reuse `INTERVAL_MS` | No |
| Accuracy threshold | `isAccurate` in `Exploration.kt` | = reveal radius | Less accurate points are stored but reveal nothing | Not on its own (follows the radius) |

### Map and camera

| Constant | File | Value | Effect | Setting? |
| --- | --- | --- | --- | --- |
| `USER_ZOOM` | `ui/MainActivity.kt` | 14 | Zoom when centering on the user at startup | Maybe |
| `BASE_MAP_MIN_ZOOM` | `ui/MapStyle.kt` | 5 | Minimum zoom; there is no base map below | No |
| `BASE_MAP_MAX_ZOOM` | `ui/MapStyle.kt` | 9 | Highest zoom of the bundled base map; overzoomed above. Must match `tools/build_spain_mbtiles.py` | No |
| `SPAIN_BOUNDS` | `ui/MapStyle.kt` | Mainland and Balearic Islands | Framing with no areas or location | No |
| `BACKGROUND_COLOR` | `ui/MapStyle.kt` | `#101418` | Color below the maps | No |
| Framing padding | `frameArea` in `ui/MainActivity.kt` (inline `40`) | 40 px | Border when framing an area | No |

### Map downloads

| Constant | File | Value | Effect | Setting? |
| --- | --- | --- | --- | --- |
| Default zooms | `activity_download.xml` | 13–16 | Initial field values | Maybe (remember the last ones used) |
| `IGN_MAX_ZOOM` | `tiles/TileDownloader.kt` | 19 | Highest zoom IGN serves | No |
| `MAX_TILES_PER_DOWNLOAD` | `tiles/TileDownloader.kt` | 20,000 | Tile limit per download | Maybe (advanced; protects storage and the IGN server) |
| `AREA_PICK_ZOOM` | `ui/DownloadActivity.kt` | 11 | Zoom when opening the download screen | No |
| `MIN_STROKE_STEP_PX` | `ui/DownloadActivity.kt` | 12 px | Minimum distance between points when drawing an area | No |
| Drawing colors | `ui/DownloadActivity.kt` | Yellow `#ffcc00`, 3 px | Look of drawn areas | No |
| Online IGN on the download screen | `mapStyleJson(..., onlineIgn = true)` | Only there | Preview imagery before downloading | No |

### Fixed by design or obligation (not preferences)

| Constant | File | Reason |
| --- | --- | --- |
| `IGN_TILE_URL`, `IGN_ATTRIBUTION` | `tiles/TileDownloader.kt` | Imagery source and mandatory attribution |
| `EARTH_RADIUS_M`, `METERS_PER_DEG_LAT` | `geo/Grid.kt` | Physical constants |
| Fingerprint rounding (1e-6°) | `geo/Fingerprint.kt` | Must match on every phone to detect duplicates in the exchange |
| `BASE_MAP_NAME`, notification and channel ids | `tiles/`, `tracking/` | Internal identifiers |
| Sources (`Source.LIVE`, `GPX_IMPORT`, `GMAPS_IMPORT`) | `data/Entities.kt` | Values stored in the database |
| `MIN_ZOOM`, `MAX_ZOOM`, `AREAS` | `tools/build_spain_mbtiles.py` | Build the base map bundled in the APK |

## Still to define

- Exchange limits (maximum file size, coordinate validation): no constants yet; the design doc requires them.
- Highlight of imported cells (toggleable): designed, not implemented.
