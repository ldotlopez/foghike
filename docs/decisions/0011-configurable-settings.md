# 0011. Configurable values in Settings, one recalculation on leaving

- Status: Accepted
- Date: 2026-10-24

## Context
Roadmap step 4 and `docs/PLAN.md` ("Configurable values in Settings", "One recalculation when leaving Settings"): expose the values of `docs/configurable-values.md` that make sense as preferences. The author went through them one by one and chose, for each, the Map (main) section, Advanced, or not shown. Cell size and reveal radius force rebuilding every stored cell; the design doc had them applied with a "Save" button and a confirmation.

## Decision
- **Placement** (agreed with the author): Map — open the map in Free / Follow / Compass, battery saver. Advanced — fog opacity (unchanged), cell size, reveal radius, fog mode, slow phone, fog detail, GPS interval, zoom when centering, recent trail on/off with duration, color and width. Not shown — tile limit per download (protects storage and the IGN server). The download screen remembers the last zoom range instead of a setting.
- **Grouping switches**: slow phone sets fog detail 8 px, at most 1,500 fog pieces and trail refresh 30 s; battery saver sets GPS every 15 s and trail refresh 30 s. While a switch is on, the row it overrides is hidden; turning it off shows the user's own value again.
- **Code**: `AppSettings` (`core/Settings.kt`) holds the values, their closed lists and the effective values after the overrides; plain Kotlin, unit-tested. `RevealGrid` (`geo/Grid.kt`) replaces `CELL_SIZE_METERS` and `REVEAL_RADIUS_METERS` and is passed to `recordPoints`, `importTrack`, `deleteTracks` and the new `rebuildCells`. `FOG_MODE`, `MAX_FOG_RECTS`, `TRAIL_DURATION_MS`, `TRAIL_REFRESH_MS`, `TRAIL_COLOR`, `INTERVAL_MS` and `USER_ZOOM` are gone.
- **Storage**: `SharedPreferences` `ui`, read and written only by `FogHikeApp` (`settings`, `saveSettings`), as `fogOpacity` already was. Not the designed `AppSettings` Room table.
- **One recalculation**: cell size and radius are held in the Settings screen until the user leaves; each changed one shows "Will be recalculated when you close this screen". Leaving saves them with a `cellsStale` flag, rebuilds once and clears the flag; `FogHikeApp` reruns a rebuild it finds flagged at startup (process died halfway). Saving before rebuilding means tracking points that arrive meanwhile already use the new grid and are included in the rebuild.
- **My location button**: a long press opens a menu to pick any of the three modes. It replaces the button's tooltip.

## Alternatives
- A Room `AppSettings` table: settings are read synchronously by the service and screens and need no queries; a table would add a DAO, a migration and async reads for no gain. When porting to iOS, `AppSettings` is already shared; only the storage file is per platform.
- Save button with confirmation (old design): replaced by the author's "recalculate once on leaving", which also avoids several rebuilds.
- Rebuild in the background after leaving: the main screen would show stale fog with no explanation; the progress stays in Settings.

## Consequences
- A GPS interval change applies from the next exploration; cell size and radius apply to the running exploration's next batch.
- If Android destroys Settings while it is in the background, unsaved cell size and radius changes are lost (other settings are already saved).
- Rebuild time on a real device is still unmeasured (`docs/HUMAN_TEST.md`).
