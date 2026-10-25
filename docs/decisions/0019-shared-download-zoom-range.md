# 0019. One default download zoom range, 13–18

- Status: Accepted
- Date: 2026-10-25

## Context
Each kind of download offered its own zoom range the first time: 13–16 for areas drawn by hand, 13–17 for explored cells and imported routes (`CELL_DOWNLOAD_ZOOMS`, decision 0015). Past the max zoom the map only shows enlarged, blurry tiles, and zoom 16–17 is not enough detail on a hike. The bundled Spain base map (zoom 5–9, `tools/build_spain_mbtiles.py`) is not a download and is out of scope.

Estimated tiles at latitude 40° (the evaluation behind the choice; 25 KB per tile, the store's default estimate):

| Download | 13–16 | 13–17 | 13–18 | 13–18 size |
|---|---|---|---|---|
| Area 3×3 km | 75 | 271 | 1,000 | 25 MB |
| Area 5×5 km | 211 | 740 | 2,676 | 67 MB |
| Area 10×10 km | 668 | 2,604 | 10,173 | 254 MB |
| Route 15 km (60 m corridor) | 92 | 199 | 441 | 11 MB |
| Route 50 km | 298 | 651 | 1,458 | 36 MB |

Each extra zoom is about 4× the tiles for an area and about 2× for a corridor. At 13–18 a hand-drawn area reaches `MAX_TILES_PER_DOWNLOAD` (20,000) at about 14×14 km, and an explored-area download at roughly 500–700 km of walked corridors. Zoom 19 would be another 4× for areas (a 10×10 km area at about 40,000 tiles, over the limit).

## Decision
- `DOWNLOAD_ZOOMS = 13..18` in `core/OfflineMaps.kt` (renamed from `CELL_DOWNLOAD_ZOOMS`) is the first-time range of all three downloads: the download screen (`ui/DownloadActivity.kt`) and both `TileOfferSheet`s (`ui/OfflineMaps.kt`).
- Each kind still remembers the last range used under its own keys. Users who already downloaded keep their stored range.
- `MAX_TILES_PER_DOWNLOAD` and the size estimate stay as they are: the sheets already show the tile count against the limit, and the user can lower the max zoom.

## Alternatives
- 13–19 (`IGN_MAX_ZOOM`, the earlier `PLAN.md` item): 4× again; ordinary hand-drawn areas would reach the limit.
- One remembered range shared by all downloads: not asked for; corridors and areas cost very differently.

## Consequences
- First downloads are about 4× larger than before for areas (15× for hand-drawn ones, which defaulted to 13–16) and about 2× for routes.
- Large hand-drawn areas now reach the tile limit more often; the user lowers the max zoom or draws smaller areas.
