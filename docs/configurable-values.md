# Configurable values

Inventory of the values and behaviors the user can already change, or that depend on a constant in the code. Input for designing the preferences panel. The "Setting?" column is a first proposal: **Yes** (clear candidate), **Maybe** (useful for testing or advanced users), **No** (technical, legal or fixed by design). "Reprocess" says whether changing it forces rebuilding the stored cells (see `recalculations.md`). When adding or changing a constant that affects behavior, update this file. State as of 2026-10-24.

## Already configurable by the user

Settings values live in `AppSettings` (`core/Settings.kt`: defaults, closed option lists and the overrides of the two grouping switches) and are stored in `SharedPreferences` `ui` by `FogHikeApp` (decision 0011). Stored values outside the lists fall back to the default. Placement agreed with the author on 2026-10-24.

| Value | Where | Options | Default | Reprocess | Notes |
| --- | --- | --- | --- | --- | --- |
| Open the map in | Settings → Map; `startMode` | Free, Follow, Compass | Free | No | Applied after the first style load if location is available. The my location button still cycles; a long press picks a mode |
| Battery saver | Settings → Map; `batterySaver` | On/off | Off | No | Preset: GPS every 15 s and trail refresh 30 s. While on, the GPS interval row shows 15 s, disabled ("Set by Battery saver"); the user's own value is kept for when it is turned off |
| Fog opacity | Settings → Advanced slider (0–100 %, steps of 5); `fogOpacity` | — | 70 % | No | Scales all fog, including `DENSITY`'s partial fog |
| Cell size | Settings → Advanced; `cellSize` | 10, 25, 50, 100 m | 25 m | Yes, when leaving Settings | Part of `RevealGrid` (`geo/Grid.kt`) |
| Reveal radius | Settings → Advanced; `radiusFactor` | 1.2×, 2×, 3× the cell | 1.2× (30 m) | Yes, when leaving Settings | Also the GPS accuracy threshold of `isAccurate` |
| Fog mode | Settings → Advanced; `fogMode` | `DENSITY` (thinner the more explored), `ANY_CELL` | `DENSITY` | No | See [0001](decisions/0001-low-zoom-fog-density.md) |
| Old device | Settings → Advanced; `slowPhone` | On/off | Off | No | Preset: fog detail 8 px (Low), at most 1,500 fog pieces, trail refresh 30 s. While on, the fog detail row shows Low, disabled ("Set by Old device"); the user's own value is kept |
| Fog detail | Settings → Advanced; `fogDetail` | High 2 px, Medium 4 px, Low 8 px | 4 px | No | `minPixels` of `lodLevelFor`: minimum on-screen cell size before a coarser level |
| GPS interval | Settings → Advanced; `gpsInterval` | 2, 5, 10, 30 s | 5 s | No | Read when exploring starts |
| Zoom when opening the map | Settings → Advanced; `userZoom` | 12–17 | 14 | No | Only at startup, when centering on the user. The my location button keeps the current zoom |
| Recent trail | Settings → Advanced; `showTrail` | On/off | On | No | When on, shows the three rows below |
| Trail duration | Settings → Advanced; `trailMinutes` | 15, 30, 60, 120 min | 60 min | No | The design doc's `trailMinutes` |
| Trail color | Settings → Advanced; `trailColor` | Orange `#ff7a1a`, yellow `#ffd400`, pink `#ff40c0`, cyan `#00e5ff` | Orange | No | No red: that is the record button |
| Trail width | Settings → Advanced; `trailWidth` | 2, 4, 6 px | 4 px | No | — |
| Download zoom range | Range slider in the download screen's zoom sheet; remembered in `downloadMinZoom` / `downloadMaxZoom` | Between 0 and `IGN_MAX_ZOOM` | Last used (13–16 the first time) | No | — |
| Areas to download | Drawn freehand | — | — | — | In `downloaded_area` |

`ui` also stores `askedStartup`, `askedBackground`, `helpSeenDownload` and `cellsStale` (cells not yet rebuilt for the saved cell size and radius): internal flags, not preferences.

Not exposed, decided on 2026-10-24: the tile limit per download (`MAX_TILES_PER_DOWNLOAD`, below), because it protects the phone's storage and the IGN server. Still to come: the user alias, asked on the first NFC send.

## Code constants

### Fog and reveal

| Constant | File | Value | Effect | Reprocess | Setting? |
| --- | --- | --- | --- | --- | --- |
| `fogDensity` (formula) | `geo/Fog.kt` | Logarithmic | Fog left on a coarse cell given `revealedCount` | No | No (maybe a "contrast" knob) |
| `LOD_FACTOR` | `geo/Grid.kt` | 8 | Each detail level is 8× the previous one | Yes (coarse cells) | No |
| `LOD_LEVELS` | `geo/Grid.kt` | 4 | Levels: 25 m, 200 m, 1.6 km, 12.8 km | Yes (coarse cells) | No |
| `FOG_TEXTURE_SIZE` | `ui/FogLayer.kt` | 256 px | Noise texture size; repetition shows at low zoom (`PLAN.md`) | No | No |
| Texture colors and alpha | `fogTexture` in `ui/FogLayer.kt` (inline numbers) | Blue-grey to white, opaque (the fog opacity setting does the thinning) | Fog look | No | Maybe (fog theme, with the animated fog in `PLAN.md`) |
| Computation margin | `Box.expanded()` in `geo/Grid.kt` | One screen per side (3×3) | How far the map can move without recomputing fog | No | No |

### Trail and position

| Constant | File | Value | Effect | Setting? |
| --- | --- | --- | --- | --- |
| `trailRefreshMs` | `core/Settings.kt` | 10 s, 30 s with Old device or Battery saver | How often the trail is redrawn with the screen on | Only through the two presets |
| Position marker | `showUserLocation` in `ui/MainActivity.kt` (`RenderMode.COMPASS`) | Red compass needle (`location_needle`) towards where the phone points, white pivot (`location_pivot`, gray without a recent fix) | — | No |

### GPS tracking

| Constant | File | Value | Effect | Setting? |
| --- | --- | --- | --- | --- |
| GPS priority | `TrackingService.kt` (`PRIORITY_HIGH_ACCURACY`) | High accuracy | Without it points exceed the accuracy radius and reveal nothing | No |
| Interval when checking location settings | `ensureLocationOn` in `ui/MainActivity.kt` (inline `5_000`) | 5 s | Only used to ask the system to turn location on; could reuse the GPS interval setting | No |
| Accuracy threshold | `isAccurate` in `core/Exploration.kt` | = reveal radius | Less accurate points are stored but reveal nothing | Not on its own (follows the radius) |

### Map and camera

| Constant | File | Value | Effect | Setting? |
| --- | --- | --- | --- | --- |
| `CAMERA_TRANSITION_MS` | `ui/MainActivity.kt` | 750 ms | Camera animation when the my location button changes mode | No |
| `BASE_MAP_MIN_ZOOM` | `ui/MapStyle.kt` | 5 | Minimum zoom; there is no base map below | No |
| `BASE_MAP_MAX_ZOOM` | `ui/MapStyle.kt` | 9 | Highest zoom of the bundled base map; overzoomed above. Must match `tools/build_spain_mbtiles.py` | No |
| `SPAIN_BOUNDS` | `ui/MapStyle.kt` | Mainland and Balearic Islands | Framing with no areas or location | No |
| `BACKGROUND_COLOR` | `ui/MapStyle.kt` | `#101418` | Color below the maps | No |
| Framing padding | `frameArea` in `ui/MainActivity.kt` (inline `40`) | 40 px | Border when framing an area | No |

### Map downloads

| Constant | File | Value | Effect | Setting? |
| --- | --- | --- | --- | --- |
| `IGN_MAX_ZOOM` | `tiles/TileDownloader.kt` | 19 | Highest zoom IGN serves | No |
| `MAX_TILES_PER_DOWNLOAD` | `tiles/TileDownloader.kt` | 20,000 | Tile limit per download | No (decided 2026-10-24: protects storage and the IGN server) |
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
| `MAX_GPX_NAME` | `gpx/GpxParser.kt` | 100 characters: cap on track names taken from GPX files or file names (untrusted input) |
| `MIN_ZOOM`, `MAX_ZOOM`, `AREAS` | `tools/build_spain_mbtiles.py` | Build the base map bundled in the APK |

## Still to define

- Exchange limits (maximum file size, coordinate validation): no constants yet; the design doc requires them.
- Highlight of imported cells (toggleable): designed, not implemented.
