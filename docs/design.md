# Hike Explorer — Design document

Oct 23, 2026 · updated Oct 24, 2026 · @Luis

## Vision and concept

Hike Explorer (internal name FogHike) is an Android hiking app in which the map starts covered by an opaque fog that clears around the places the user walks, just like in strategy games.

The fog is revealed from the live GPS position or from routes already walked: the user can import GPX files (and, later, the JSON exported by the Google Maps Timeline from the phone) to reveal the map retroactively. The base maps are satellite views from Spain's Instituto Geográfico Nacional (IGN), downloaded for offline use.

## PoC scope

The PoC covers three end-to-end features: downloading offline maps, importing GPX routes and recording the user's movements live. The fog is shown over the downloaded maps, with adjustable opacity for debugging.

**Included**

- Offline map download: a general map of Spain, selection of one or more freehand-drawn areas and a button to download the IGN satellite view.
- Import of GPX routes to add them to the explored areas.
- Tracking mode: turns on the GPS and records movements; a single button toggles between starting and stopping the exploration.
- Downloaded maps and fog on the main screen: offline maps are seen under a fog whose opacity is set with a slider, meant for debugging and checking that the maps load.

**Out of the PoC**

- Google Maps JSON import.
- Settings screen with reprocessing.
- iOS version.

The exchange of explored areas between users over NFC is also designed but not implemented (see "Area exchange over NFC").

## Architecture and stack

The chosen stack is Kotlin Multiplatform with native MapLibre, prioritizing a working PoC quickly over architectural purity from day one.

- **Multiplatform:** today the code is Kotlin for Android, organized so it can move to Kotlin Multiplatform without a rewrite. The stack was chosen with a future iOS version in mind, comparing Kotlin Multiplatform, Flutter and React Native. Flutter and React Native were ruled out because of worse performance with custom overlays on the map.
- **Map:** MapLibre, which has native SDKs for Android and iOS.
- **UI:** Jetpack Compose with Material 3 and a fixed brand palette generated from the icon's green (`#3DDC84`), in light and dark. The design rules for each screen are in `docs/ui/`. The map remains MapLibre's native view, which Compose places under the panels with `AndroidView`; each screen is an Activity with no navigation library or ViewModel. When porting to iOS, we can move to Compose Multiplatform and share the screens, or write them in SwiftUI.
- **A single module:** today only the Android module `app` exists. The pure logic (`geo/` and `data/`) will be extracted into a KMP `shared` module when iOS needs to be ported, not before.
- **Persistence:** Room (2.7 or later, with KSP). Since 2.7 Room also works on iOS inside Kotlin Multiplatform with its bundled SQLite driver, so there is no need to switch to SQLDelight when porting: it is enough to write `data/` in a compatible way from now on (see "Structure and conventions").
- **Android versions:** minimum Android 12 (API 31), because some users use old phones as hiking GPS units; `targetSdk` and `compileSdk` on the latest stable version. See "Android 12 compatibility".
- **Location:** Play Services' Fused Location Provider, with coroutines for asynchronous work.
- **Base maps:** IGN satellite view in MBTiles format, for offline use.

## Reveal mechanics

Each GPS point reveals all the grid cells whose center falls within a configurable radius, not just one cell. Two parameters govern this: the cell size (`cellSizeMeters`) and the reveal radius (`revealRadiusMeters`). By default they are 25 m and 30 m, and they are changed in Settings > Advanced.

1. The point arrives as a position (latitude, longitude and an optional timestamp), coming from live GPS or an imported file.
2. It is quantized to its grid cell according to the cell size.
3. The nearby candidate cells are computed and filtered by real Haversine distance: only those whose center is within the radius remain.
4. The resulting cells are stored as revealed, ignoring those that already existed.

Each cell's identifier includes row, column and the cell size it was computed with. This way, if the user changes the size, the new identifiers do not collide with the old ones even if some old cell remains; the radius is not part of the identifier because it only decides which cells get revealed.

**Cell geometry.** Cell height is constant and its width depends on the latitude of its row, not on that of the GPS point. This way a cell's identifier is enough to know which rectangle it covers, which makes it possible to draw the fog from the stored identifiers.

**Reprocessing.** Both the radius and the cell size are configurable from the app, even though that means recalculating data already stored. When either one changes, all revealed cells are deleted and recalculated from the raw GPS points. That is why the points of each route are stored and not just the resulting cells.

Both values are chosen from a closed list, to bound the cost of the fog and avoid absurd configurations: a cell of 10, 25, 50 or 100 m, and a radius of 1.2×, 2× or 3× the cell (by default 25 m and 1.2×, i.e. 30 m). Reprocessing is launched only once, on leaving Settings, even if both values changed, and shows its progress; meanwhile, under each changed value a small line warns that it will be recalculated when the screen is closed. If the app dies midway, it is repeated at the next start. It also rebuilds the levels of detail.

**Levels of detail (LOD).** To draw the fog at low zoom without thousands of tiny rectangles, in addition to the fine grid, coarser grids are maintained, each 8 times larger than the previous one: with the default cell, 200 m, 1.6 km and 12.8 km. They use the same geometry scheme (a cell of `cellSizeMeters × 8ⁿ`), so their identifier is also enough to know which rectangle they cover. A coarse cell is revealed if it contains the center of any revealed fine cell, and it stores in `revealedCount` how many it contains (fine ones store 1); how it is drawn according to that number is explained in "Fog at low zoom". They are computed when each new fine cell is stored (a few more inserts and updates; only new fine cells add to the counter) and are stored with their origin in the same `revealed_cell` table: since the identifier already includes the size, no other table is needed, and the highlighting of imported cells will also work at low zoom.

## Data model

The local database stores four implemented tables; another is designed but does not exist yet.

| Table | Key | Fields | Purpose | Status |
| --- | --- | --- | --- | --- |
| `revealed_cell` | `cellId` (`size:row:column`) | `cellSize`, `gridRow`, `gridCol`, `firstRevealedAt`, `source` (`live`, `gpx_import`, `gmaps_import`), `revealedCount`; index by size, row and column | Revealed cells, from the base grid and the levels of detail, and their origin | Implemented |
| `track_point` | `trackId` + `sequenceIndex` | `lat`, `lon`, `timestamp` (optional), `accuracy` (meters, optional), `source` | Raw GPS points, needed for reprocessing (the origin lets us process the user's own first). A GPX's `trackId` is its coordinate fingerprint | Implemented |
| `downloaded_area` | autogenerated `id` | name (prefix `area-` if drawn by hand, `cells-` if it came from revealed cells), bounds (min and max lat/lon), min and max zoom, `.mbtiles` file name (the single store), download date | Record of finished downloads: boxes on the download screen and map sources by max zoom | Implemented |
| `AppSettings` | — | `revealRadiusMeters`, `cellSizeMeters`, `trailMinutes`, `userAlias` | User-configurable settings; the alias is sent with shared routes | Replaced: settings are stored in `SharedPreferences` (decision 0011); the alias, when the exchange arrives |
| `track` | `trackId` | `source`, `createdAt` (start of recording or moment of import), `name` (optional), `startedAt` (time of the first point, optional). With the exchange: `fingerprint` and `senderAlias` (only `nfc_import`) | Describe each route to list and delete it; with the exchange, avoid importing it twice and know who it came from | Implemented (version 3), without the exchange columns |

While nobody but the author uses the app, Room uses destructive migration: if the schema changes, the local data is deleted. From the first version handed out to testers, every schema change carries a real migration, so as not to delete the testers' routes.

With the NFC exchange, the `source` field of `revealed_cell` will admit a fourth value, `nfc_import`, for cells derived from received routes. The `track` table will grow to store the identifier, the origin, a fingerprint of each route so the same one is not imported twice, and the alias of whoever sent it (see "Area exchange over NFC").

## Offline maps

The user chooses on a general map of Spain which zones to take offline, or directly downloads what they have explored (see "Maps of the explored area"). All tiles, with the IGN satellite view, go into a single shared MBTiles file (decision 0015).

1. From the main screen the map download opens, centered on the user's position at zoom 11 (or on all of Spain if there is no location), over the general map included in the app.
2. The user circles each zone they want to download with one finger, and each stroke closes as a polygon. Several can be drawn. With two fingers the map is moved and zoomed: as soon as the second finger touches down, the stroke in progress is discarded and the gesture goes to the map. There is no button to switch modes; in exchange, one finger does not pan the map and double tap does not zoom it.
3. The user sets the zoom range (13 to 16 by default) and taps the download button.
4. For each polygon, only the tiles that touch it are computed (corner inside, vertex inside or crossing edge), not all those of its bounding rectangle, in the standard Web Mercator scheme, and they are requested from the IGN's orthophoto WMTS service (layer `OI.OrthoimageCoverage`, matrix `GoogleMapsCompatible`, JPEG, up to zoom 19).
5. The tiles are written to the single store `tiles.mbtiles` (standard MBTiles, each tile only once) and, when finished, the download is recorded in the database.

The download runs in WorkManager (Android's task scheduler), not in the screen: it continues even if the user leaves, waits for a connection and restarts if Android kills the process. Downloads go one at a time, in order, with a progress notification that has a Cancel button (it also cancels those waiting behind). Tiles already in the store are skipped, so an interrupted download continues where it left off; the area only appears on the map when it finishes. The download screen shows the progress on returning to it.

### Maps of the explored area

With opaque fog, the imagery outside the revealed cells is not visible, so the tiles that touch those cells are enough. There are also few of them: the revealed corridor is about 60 m wide, and at high zoom the number of tiles grows with the length of the route, not its area (about 400 tiles for 15 km between zooms 13 and 17).

- **When importing routes**, the result notice offers "Download map" for the newly imported routes.
- **In Settings > Offline maps**, "Download maps of the explored area" downloads the tiles of all revealed cells.
- **In Settings > Offline maps**, "Delete maps outside the explored area" deletes the tiles that touch no revealed cell. By default it keeps the hand-drawn downloaded areas (checkbox "Keep hand-drawn downloaded areas"), shows how much space it frees and asks for confirmation; it cannot be undone. It does not run while a download is in progress.
- Downloads use a sheet with the zoom range (13–18 the first time, the same for every type of download; each then remembers its own), the new tiles against the limit of 20,000 and the estimated size.
- Downloads made from cells are not drawn as boxes on the download screen.
- Areas downloaded earlier, each in its own file, are merged into the single store at startup.

Design notes:

- **General map of Spain.** The app includes an `.mbtiles` with the IGN satellite view of all of Spain at zoom 5–9 (4.8 MB, 551 tiles). It is generated once with `tools/build_spain_mbtiles.py` (Python, same WMTS source and same format as the app's download) and goes in the APK's assets; on first launch it is copied to the app's storage, because SQLite needs a file path, and it is served by the same local server as the other maps. It is used as the background on both screens; on the main one it sits under the fog like any other map (see "Main map and fog").
- To choose areas precisely, the download screen overlays the IGN online layer at zooms above 9. Offline you can see the general map, but not download.
- The download is sequential and without retries: a tile that fails is skipped and logged.
- Using the service is free, but it requires showing the attribution "PNOA cedido por © Instituto Geográfico Nacional de España" wherever the imagery is displayed.

## Main map and fog

The main screen shows the maps under a fog that covers the entire screen except the revealed cells, whether it is Spain, the sea or the Pyrenees, and a slider adjusts its opacity to check that the maps load.

**Layer composition**, bottom to top:

| # | Layer | Content | Cost |
| --- | --- | --- | --- |
| 1 | Background | Dark color | None |
| 2 | General map | MBTiles of Spain, zoom 5–9; above that, zoom 9 is scaled up | Tiles |
| 3 | Downloaded areas | Single store `tiles.mbtiles` (drawn areas and maps of the explored area); one source per max zoom so each download scales up above its last level | Tiles |
| 4 | Fog | The whole visible window minus the revealed cells, with the slider's opacity | Computed only for what is visible |
| 5 | Highlight | Color filter over the imported cells, toggleable | Same as the fog |
| 6 | Trail | Orange line where the user has passed in the last hour, fading with age | A few hundred segments; redrawn every 10 s with the screen on |
| 7 | Position | Red arrow toward where the phone is facing, from MapLibre's location component with custom icons | GPS and compass only with the screen on |

- **Downloaded maps.** Each downloaded area is an image layer over the general map. The project's MapLibre version does not read MBTiles directly, so the app starts a small local HTTP server, reachable only from the phone itself (127.0.0.1), that serves the tiles from the `.mbtiles` files. Unencrypted HTTP is allowed only for that address. MapLibre is told it is always connected, because otherwise it stops requesting tiles, local ones included, when the phone has no network. It stays this way in the PoC: it works, and for a test app with fewer than 100 users migrating to PMTiles is not worth it (it would require updating MapLibre and changing the download format). It will be reconsidered only if the server causes problems on real devices.
- **Fog.** It is computed only for the visible window plus a margin of one screen on each side, never for all of Spain, so its cost depends on the screen size and not on how much has been explored. The unrevealed cells of each row are grouped into runs, and consecutive rows with nothing revealed are merged into a single rectangle; each row costs a few rectangles, not a polygon per cell.
- **Level of detail by zoom.** The finest level whose cells measure at least 4 px on screen is used; with the 25 m cell at Spain's latitudes this gives the fine one at zoom 15 or more, the ×8 one between 12 and 14, the ×64 one between 9 and 11 and the ×512 one at 8 or less (see "Levels of detail" in "Reveal mechanics"). With cells of between 4 and 30 px and 3×3 screens, at most about 1,800 rows result, and since routes are narrow, a few thousand rectangles at most (limit: 5,000). If the rectangle limit is still exceeded, it first drops the soft edges and then moves to the next coarser level instead of not drawing.
- **Fog at low zoom.** See the next section. The mode is chosen in Settings > Advanced ("Fog when zoomed out").
- **When it is recalculated.** When the camera stops (`onCameraIdle`) if it has left the computed margin or the level of detail has changed, and when the cells change. While the user drags, nothing is computed: MapLibre already has the rectangles on the GPU. The computation runs off the main thread. With the screen off the fog is not computed; the tracking service only stores points and cells.
- **Appearance.** The fog is static to save battery: a fractal noise texture (256 px, from bluish gray to white, with somewhat finer areas) generated only once when the style loads and repeated by MapLibre as a fill pattern (`fill-pattern`), so drawing it costs no more than a flat color. The edges are soft (decision 0017): MapLibre cannot blur part of the map, so at the fine level the cells on both sides of each fog edge are split into small pieces whose opacity ramps from clear, one cell inside, to full fog, one cell out ("Soft fog edges" in Settings > Advanced: Off, Low or High smoothness; Old device turns it off). The animated fog is in `docs/PLAN.md`.
- **Recent trail.** The stored points of the last hour are drawn (any origin with a time), joined per route, with opacity 1 at the latest point dropping to 0 at one hour. Imprecise points are ignored, as in the reveal. The duration (one hour by default), the color, the thickness and whether it is shown are chosen in Settings > Advanced. It is queried every 10 s (30 s with "Old device" or "Battery saver") while the screen is on, so it picks up the new points and fades the old ones.
- **Position and orientation.** MapLibre's location component in compass mode, with custom icons (decisions 0012, 0018): a red arrow with a white border pointing where the phone points, rotating on a white pivot, without moving the camera. It is red to stand out better than the blue dot over satellite imagery; it is not confused with the red record button because it is an arrow on the map and not a circle on a button. It follows the screen's lifecycle, so with the screen off it uses no GPS or compass; recording points is still done by the service. The pivot appears gray when there is no recent position.
- **Adjustable opacity.** A slider from 0% to 100% (70% by default) controls the fog: at 0% the maps are fully visible and at 100% they are covered. The value is remembered between sessions.
- **Updating.** The fog is recalculated when a GPS point is recorded, when routes are imported (GPX, Google Maps or exchange) and when reprocessing. The maps are reloaded on returning from the download screen and the camera frames the most recent area, never below its minimum zoom so that it is visible.
- **Moving the map while following.** In follow or compass mode, dragging or zooming to look around only pauses following: about 5 s after the last gesture the map re-centers on the user in the same mode, keeping the zoom (decision 0017).
- **Initial position.** When the app opens, the map centers on the user at the zoom chosen in Settings (14 by default) and in the mode chosen there (free, follow or compass): first on the last known position, which is instant, and then on a new one. The location permission is requested once on first launch; without it, the map frames the most recent area or Spain.
- **Minimum zoom 5.** Below that there is no general map to draw, so zooming out further is not allowed.
- **Visible status.** The top text shows the revealed cells (own and total, when there are imported ones) and the offline maps loaded, and warns if there are no downloaded maps.

### Fog at low zoom

When zooming out, the fog switches to coarse cells, and with the rule "revealed if it contains any fine cell" a 60 m wide route clears strips of 200 m, 1.6 km or 12.8 km. On screen the excess is at most one cell (≤ 30 px), but it shows unexplored terrain. The coarse grids do not line up column by column with the fine ones (the width depends on each row's latitude), so the counter is computed from the center of each fine cell and not with a SQL `GROUP BY`.

A straight route crossing a coarse cell covers, approximately: ~30% of a 200 m one (19 of 64 fine cells), ~4% of a 1.6 km one (150 of 4,096) and ~0.5% of a 12.8 km one (1,200 of 262,144). That is why a single percentage threshold leaves the view of all of Spain under fog.

Strategies, to be tried on the device. Those that depend on `revealedCount` are swapped with `FogMode` without touching the data:

| Strategy | Idea | Cost | Status |
| --- | --- | --- | --- |
| `ANY_CELL` | Coarse cell cleared if it contains any revealed fine one | — | Implemented (original behavior) |
| `DENSITY` | Coarse cell revealed with partial fog: `1 − ln(1+n)/ln(1+capacity)`; logarithmic scale so a route in a 12.8 km cell is visible (~0.45) | Counter + one property per element and `fill-opacity` by expression | **Implemented, active** |
| Threshold | Cleared only if `revealedCount ≥ X` (X per level; a single percentage X leaves Spain under fog) | One more filter on the same counter | Pending |
| Threshold + density | Above the threshold, cleared; below it, partial fog | Combine the previous two | Pending |
| Fewer pixels per cell | Lower the 4 px minimum of `lodLevelFor` to 1–2 px: the fine level holds for one more zoom range and the excess is halved or less | One constant; more rectangles, so the 5,000 limit will trigger a level earlier | Pending; independent of the mode |
| Stroke at low zoom | Coarse fog uncleared and a simplified line of the routes (minimum thickness 2 px) on top | New line layer and simplification per zoom | Pending; stops being "fog that clears" |
| Cascading threshold | Cleared if at least k of its children from the previous level are | Count children | Discarded: the columns do not line up and the result depends on whether the route goes straight or diagonally |

Coarse cells stored before version 2 of the database start with `revealedCount = 1` (automatic migration), so in `DENSITY` they look almost covered until reprocessing (step 4 of the roadmap) or until they are walked through again.

## Route import

Importing a route consists of passing its points through the same reveal calculation used by live tracking, so past walks clear the map just like new ones.

**GPX (implemented).** The user picks one or more files from the system picker. The track points (latitude, longitude and time) are read from it and everything else is ignored: routes, waypoints and extensions. The points are stored as raw points so they can be reprocessed, and the revealed cells are marked with the origin `gpx_import`. The route's identifier is `gpx-` plus a fingerprint of its coordinate sequence (without times, rounded to 1e-6°, the same idea as the exchange): if it already exists, the file is skipped and the notice counts it as already imported. A file with no track points counts as unreadable. The route is named after the `<name>` of its track (or of `<metadata>`) and, if it has none, after the file; it is imported from the routes screen.

**Deleting routes (implemented).** From the routes screen one or several are deleted, always after confirming. Since cells do not know which route revealed them, the route and its points are deleted and all cells are rebuilt from the remaining points, the user's own first (decision 0010). The route being recorded cannot be deleted.

**Google Maps JSON (pending).** The reference format is the one the phone exports (Settings > Location > Timeline > Export), because the Timeline is already stored on the device and Takeout does not include it for most users. The old Takeout format (`Records.json`, Semantic Location History) is not supported. Before writing the parser a real sample must be obtained to fix the fields: the expectation is to read the points of `timelinePath` inside `semanticSegments`. The parser will feed the same reveal calculation and mark its cells with the origin `gmaps_import`.

## Tracking mode

Tracking only turns on when the user asks for it: a single button starts the exploration and, while it is active, offers to stop it.

- On start, the following are chained, skipping those already resolved: precise location permission (and notifications on Android 13+); an explanation and the request for "Allow all the time", only the first time; the battery optimization exemption, at every start while missing; and the check that system location is on, with the Play Services dialog to turn it on. That Play Services check also fails in airplane mode or with Google Location Accuracy off; if the system location switch is on, exploring starts anyway, since GPS alone is enough. If precise location or system location is missing, it does not start and explains why.
- Then a new session is opened and the GPS is turned on with high accuracy and one update every 5 seconds.
- Recording runs in a foreground service of location type, with a persistent notification for the duration of the exploration, so it keeps recording with the screen off or the app in the background.
- **Not losing data.** Each batch of positions is saved to the database as soon as it arrives. If Android kills the service, it restarts it on its own (`START_STICKY`) and the exploration continues in a new session; tested on the S10e with a forced crash: restart in 1 s and a gap of one point. For that restart from the background to be allowed and have GPS, the battery exemption and "Allow all the time" are needed; without them the service stops cleanly instead of failing. Stopping from the button is final.
- Precise location is required: since Android 12 the user can grant only approximate location, which is useless with 25 m cells. If they grant it, the app explains this and does not start the exploration.
- Each received position is stored as a raw point of the session, with its accuracy, and reveals the cells of its radius with the origin `live`. A position whose accuracy is worse than the reveal radius is stored (it exists and may be useful later), but is ignored for everything the user sees: it reveals no cells, neither now nor when reprocessing.
- **Location turned off mid-exploration.** The service stays active and the notification changes to "Location off: turn it on to keep exploring"; when it is turned back on it keeps recording in the same session.
- The main screen shows a counter of revealed cells that updates with each point.
- On stopping, location updates are halted and the session is closed.

## Area exchange over NFC

Two users can merge their explored areas by bringing their phones together, and the direction of the exchange is chosen at the moment of contact. This feature is designed but not implemented.

**Requirements**

- Users integrate the areas explored by other users via NFC.
- The direction is set when making contact: one way, the other, or bidirectional.
- Imported areas are marked in the database, so that either only the truly explored ones or all of them can be shown.
- What is transferred is a set of GPX files, not cells, which removes the problem of different cell sizes.

**Design**

**Direction.** Whoever starts the contact chooses the direction and the other user confirms it on their screen before anything is transferred.

| Initiator's choice | Who sends | Result |
| --- | --- | --- |
| I send | The initiator | The other user receives my areas |
| I receive | The other user | I receive the other's areas |
| Exchange | Both | Each receives the other's areas |

**What is shared.** A set of GPX files with the sender's own routes (origins `live`, `gpx_import` and `gmaps_import`), not the cells. Each receiver recomputes the cells with their own cell size and radius, so the difference between configurations stops being a problem. Routes received from third parties are not forwarded, so as not to spread other people's trips.

**Route selection.** The sender chooses what to send with two options: "All" (default) or "Select", which opens the list of their own routes to tick one by one.

**Marking.** Received routes are stored with origin `nfc_import` in the `Track` table, with an identifier, a content fingerprint and the sender's alias. Everything selected is sent and the receiver discards the GPX files whose fingerprint it already has; there is no prior fingerprint negotiation. The fingerprint is computed over the coordinate sequence, without timestamps, so that it matches even if the times were stripped. If the same route arrives from two senders, the first one's alias is kept. The cells derived from them are also marked `nfc_import`. If a cell has two origins, the user's own prevails: it is enough to reprocess the own routes first and then the imported ones, ignoring duplicates. Outside reprocessing, however, a new own route (live tracking or GPX or Google Maps import) that passes through an already imported cell must turn it into an explored one, and that requires changing the current insertion, which ignores duplicates.

**View.** The map always shows all areas, own and imported, without having to go to the settings. A button on the main screen turns on or off a subtle highlight of the imported cells: a second "fog" layer only over the `nfc_import` cells, which lets the map show through but distinguishes them from those explored by the user. In the PoC that layer is a color filter: semi-transparent rectangles of a different tone (for example, bluish at 20–30%), drawn with the same per-row runs as the fog, so the cost is almost nil. A blur or desaturation would look better, but MapLibre does not allow applying them to a zone of the map: they would require a layer with its own shader or a processed copy of the tiles, and are left as a later improvement. The button's state is remembered between sessions, like the opacity. The counter shows both figures: own cells and total. This meets the requirement to distinguish "only the truly explored or all" without two separate views.

**Reprocessing.** Since received routes are stored as raw points, reprocessing treats them like the user's own: the cells are deleted and recalculated from the points, the own first and then the imported. It is no longer necessary to keep `nfc_import` cells separately.

**Transfer channel.** NFC serves for contact, identification, agreeing on the direction and consent. A set of GPX files is too bulky for NFC, so the files travel over Play Services' Nearby Connections (Bluetooth and Wi-Fi), which fits because Play Services is already a project dependency. Android Beam, the old NFC exchange API, is deprecated and removed in recent Android versions, so the NFC contact uses card emulation (HCE) on one phone and reading on the other. Nearby Connections needs the Bluetooth permissions `BLUETOOTH_SCAN`, `BLUETOOTH_ADVERTISE` and `BLUETOOTH_CONNECT` (Android 12 or later) and `NEARBY_WIFI_DEVICES` on Android 13 or later; on Android 12 it also asks for precise location, which the app already has. Some old phones have no NFC: on them the exchange is not available.

**Privacy.** A route reveals more than some cells: exact trips and times. That is why the exchange requires explicit confirmation on both phones, the sender chooses which routes to send and timestamps are removed before sending them. The sender's alias (`userAlias`) travels with the routes, and the receiver stores it; the sender sees their alias on the confirmation screen before sending. If the alias is empty, that screen asks for it before the first send; afterwards it is changed in Settings. Once delivered, a route cannot be withdrawn from the receiver's phone.

**Validation at the receiver.** Received files are untrusted data: their size is limited and the coordinates are checked to be valid before importing them.

**Pending risk**

- On iOS, phone-to-phone NFC is very limited and Nearby Connections has no stable SDK. The exchange is designed for Android only; before promising it across platforms, both pieces must be verified.

## Screens and flows

The PoC has four screens of its own and uses the system file picker to import routes.

| Screen | What it shows | Actions |
| --- | --- | --- |
| Main | Downloaded maps under the fog, label with revealed cells and maps, position and trail | Start or stop the exploration (the bar collapses); open the map download; open the routes; center and follow the position, with compass (long press chooses the mode); help `(?)`. Details in `docs/ui/screens/main.md` |
| Map download | Map centered on the user with the areas already downloaded, tile count against the limit, download progress and help `(?)` (opens by itself the first time) | Draw freehand areas / move the map; undo the last area or remove them all; choose the zoom range; recenter; download. Details in `docs/ui/screens/download.md` |
| Routes | Recordings and imports, from most recent to oldest: name (imported ones with the route's date) and, in smaller type, date added and origin | Import GPX (the notice offers to download the map of the imported routes); delete one (trash can or swipe left) or several (multi-select), always after confirming. Details in `docs/ui/screens/tracks.md` |
| File picker | System picker | Choose one or more GPX files to import |
| Settings | Map: mode when opening the map and "Battery saver". Advanced: fog opacity with preview, cell size, reveal radius, fog mode, "Old device", fog detail, soft fog edges, GPS interval, zoom when opening the map and trail (whether it is shown; duration, color and thickness). Offline maps: download those of the explored area and delete the rest, with the space used. Version. Pending: alias | Changes are saved immediately, except cell and radius, which are applied on leaving with a single reprocessing. The two presets ("Battery saver" and "Old device") fix several values; while they are active, those settings appear disabled with the fixed value and "Set by …" (decision 0014). Details in `docs/ui/screens/settings.md` |

The NFC exchange would add a contact and confirmation screen (direction, "All / Select" and own alias) and a button on the main screen to highlight the imported cells (see "Area exchange over NFC").

## Structure and conventions

The project is a single Android module with application id `com.cuarentaydos.foghike`, organized into packages by responsibility:

| Package | Contents | Moves to `shared`? |
| --- | --- | --- |
| `geo/` | Grid, reveal, levels of detail, fog rectangles, tile computation and download polygons | Yes |
| `data/` | Room entities, DAOs and database | Yes |
| `core/` | App logic used by screens and services: storing points and revealing cells (`Exploration.kt`, common to tracking and import), importing a route without duplicating it, computing the fog of an area (`FogCells.kt`) and the trail segments (`Trail.kt`) | Yes |
| root | `FogHikeApp` (creates the database and the tile server) | No |
| `tiles/` | Local tile server and area download to MBTiles | No |
| `gpx/` | GPX reader | No |
| `tracking/` | Tracking foreground service | No |
| `ui/` | The two screens, the map style and the drawing of the fog and trail in MapLibre | No |

Outside the app, `tools/build_spain_mbtiles.py` generates the general map of Spain.

Development conventions:

- All code is in English (names, comments and commits), even when the conversation is in Spanish.
- UI text goes in `strings.xml` in English and is translated to Spanish in `values-es`; Android chooses according to the phone's language.
- Complexity is kept at the level of the author's well-structured Python scripts and CLIs: no elaborate architectures or unnecessary layers, because they have no prior experience in mobile development.
- The pure logic (`geo/`) depends on neither Android nor any library, so it can be extracted into a shared module when porting to iOS.
- `data/` contains only Room entities, DAOs and the database, written so that Room KMP compiles them for iOS too: DAOs with `suspend` and `Flow` (no `LiveData`, `Cursor` or `Context`), dates as `Long` in milliseconds (no `java.time` or other `java.*` APIs). Creating the database, which needs a `Context` and a path, lives in a single Android file; when porting it becomes an `expect/actual`.
- `core/` gathers the logic that is not pure geometry but does not depend on the platform either: it uses `geo/`, `data/` and coroutines, without Android, MapLibre or `java.*` APIs. Today it has two exceptions noted in the code, which are changed when porting: `withTransaction` (Android only) and `System.currentTimeMillis`.
- Screens, services and workers are glue with the platform: they request permissions, read files, draw in MapLibre and show results. Any computation or decision that iOS would also need goes to `core/`, `geo/` or `data/`. This way, when porting, only that glue is rewritten.
- The tile server and the MBTiles reading are Android code and are not shared: on iOS the map would be fed in another way, for example with PMTiles.
- The `shared` module and repository interfaces are not created until the iOS version exists: the DAOs already act as the interface.

**Distribution.** The app is handed out to testers (fewer than 100) as a signed APK, installed by hand. All versions must be signed with the same key, kept outside the repository and backed up: if it is lost, testers will have to uninstall, and would lose their data. Every tag pushed to GitHub generates a release with the signed APK, for ARM only; it can also be launched by hand from GitHub Actions giving the tag, which is created on publishing; the key goes in the repository secrets. The same APKs are published in an own F-Droid repository on GitHub Pages (`https://ldotlopez.github.io/foghike/repo`), with the last three versions, to install and update from the F-Droid app; a single Spanish web page at the Pages root (`https://ldotlopez.github.io/foghike/`) explains how to install F-Droid, add the repo or use Obtainium, and links to donations; the official F-Droid catalog is out because it rejects Google Play Services. Phones need Google Play Services, on which location and Nearby Connections depend.

## Android 12 compatibility

The app works from Android 12 (API 31, `minSdk = 31`), because some users carry an old phone as a hiking GPS. Nothing in the design needs a later version; what changes by version are permissions:

| Piece | Android 12 | Android 13 | Android 14 or later |
| --- | --- | --- | --- |
| Service notification | No permission | `POST_NOTIFICATIONS` | `POST_NOTIFICATIONS` |
| Location service | `foregroundServiceType="location"` | Same | Plus `FOREGROUND_SERVICE_LOCATION` |
| Background location | `ACCESS_BACKGROUND_LOCATION` on a separate settings screen | Same | Same |
| Battery exemption | `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` (system dialog) | Same | Same |
| Nearby Connections | Bluetooth and precise location | Bluetooth and `NEARBY_WIFI_DEVICES` | Same as 13 |
| File picker | System picker (no permissions) | Same | Same |

- MapLibre, Room, Play Services and HCE support much earlier versions, so they do not limit.
- Old phones have less memory and slower GPUs: the fog by visible window and levels of detail is designed with them in mind too. It must be tested on at least one real device with Android 12.
- Lowering the minimum, for example to Android 8 (API 26), would be possible, but would require Bluetooth permission branches from before 12; it is not done while nobody needs it.

## Current status and limitations

The PoC code was rewritten from scratch on Oct 24, 2026 following this document: Gradle 9.6 with AGP 9.4 (Kotlin built into AGP), KSP 2.3.6, Room 2.7.2, MapLibre 11.13, Compose BOM 2026.06.01 (the latest that compiles with `compileSdk 36`), `minSdk 31` and `compileSdk 36`. It includes the three PoC features, the layered fog with levels of detail, the foreground service, multi-select of GPX files and the texts in English and Spanish. It has not yet been compiled or tested on a device, and the JVM tests (`geo/` and GPX reader) are written but have not been run; adjusting versions on the first sync is to be expected.

**Mismatches with what was requested**

- None. The general map file `spain.mbtiles` is generated with `tools/build_spain_mbtiles.py` before building.

**Known limitations**

- Maps are only visible between the minimum and maximum downloaded zoom: below it the area appears empty (the general map is seen) and above it the last level is scaled up.
- The grid configuration (25 m cell, 30 m radius) is fixed in the code.
- The tile download is sequential and without retries.
- Since version 3 of the database, schema changes carry a migration (2→3 creates the `track` table); destructive migration only covers versions without one.

## Roadmap and decisions

The next logical step is to generate the general map and test the app on a device. The rest follows the project's original order, except the preparation for handing out the app (step 5), which is new.

1. Generate `spain.mbtiles` with `tools/build_spain_mbtiles.py` and check its size.
2. First sync and build, pass the JVM tests and test the fog, offline maps, import and tracking on a device with Android 12.
3. Get a sample of the phone's Timeline export and write its parser.
4. Done (decision 0011): settings screen with list values and reprocessing on leaving, which also recalculates `revealedCount`; the low-zoom fog mode is an advanced setting. The alias is missing, which arrives with the exchange (step 6).
5. Before handing out the first APK: definitive signing key and move to real Room migrations.
6. Implement the exchange over NFC and Nearby Connections with GPX (fingerprint and alias columns in the `track` table, "All / Select" selection and toggleable highlight of imported cells).
7. Add retries to the download.
8. Port to iOS: move `geo/`, `data/` and `core/` to a KMP `shared` module, with Room KMP, and turn the database creation into `expect/actual`.

**Closed decisions** (Oct 24, 2026)

- Google Maps format: the phone's Timeline export; old Takeout is not supported.
- General map of Spain: an MBTiles at zoom 5–9 included in the APK and served by the local server.
- Local tile server: kept. MapLibre reads local PMTiles since 11.7.0 ([documentation](https://maplibre.org/maplibre-native/android/examples/data/PMTiles/)), but with fewer than 100 users the migration is not worth it.
- Map store: a single `tiles.mbtiles` for all downloads, with each tile once; downloads from revealed cells (on import and from Settings) and clean-up of the unexplored, keeping hand-drawn areas by default (decision 0015).
- Exchange: NFC for contact and Nearby Connections for data; "All / Select" selection; no forwarding of third-party routes; the sender's alias is stored, asked for on the first send; the receiver discards duplicates by fingerprint.
- View: always all areas, with a subtle, toggleable highlight of the imported cells.
- Settings: radius and cell from closed lists; reprocessing only once on leaving Settings, with a notice under each changed value (replaces "on save, after confirming").
- Interface in English with Spanish translation (`values-es`).
- Tracking with a foreground service within the PoC.
- Destructive migration only until the first version is handed out; real migrations afterwards.
- Distribution: directly signed APK, always with the same key, from GitHub Releases and an own F-Droid repository.
- Layered fog: covers the whole screen; computed only for the visible window with margin and at the level of detail that corresponds to the zoom.
- Persistence: Room KMP (≥ 2.7) instead of migrating to SQLDelight; `data/` is written compatible from now on.
- Interface: Jetpack Compose with Material 3 and a fixed brand palette (no dynamic color); the map remains a native view inside `AndroidView`. Portrait only. Floating bars with icons on the map screens, help in a `(?)` and reference screenshots with Roborazzi. Compose Multiplatform or SwiftUI is decided when porting to iOS.
- Shareable logic: what iOS would also need lives in `core/`, `geo/` and `data/`, without Android; screens and services are only glue with the platform. No `shared` module or interfaces until porting.
- Minimum version: Android 12 (API 31).
- Brand: the app is called "Hike Explorer" for the user; FogHike remains the internal name (package, `applicationId`, classes, files and technical documentation).
