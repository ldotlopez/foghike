# 0010. Track table and tracks screen with delete by rebuilding

- Status: Accepted
- Date: 2026-10-24

## Context
Recordings and imports could not be listed or undone: `track_point` had a `trackId` but no row describing the track (name, import date), and `revealed_cell` doesn't know which track revealed a cell. Testing imports and the future exchange needs a way to remove a track and get its fog back.

## Decision
- `track` table (`data/Entities.kt`): `trackId`, `source`, `createdAt` (recording start or import time), `name` (null: the screen makes one up), `startedAt` (first timed point). Database version 3 with a real migration (`MIGRATION_2_3` in `data/AppDatabase.kt`) that creates a row per existing `trackId`; recordings take `createdAt` from their id, older imports get the migration time. The design doc's `fingerprint` and `senderAlias` columns wait for the exchange (the GPX fingerprint is already in the id).
- `recordPoints` creates the track row with the first batch (insert-or-ignore), so live tracking and every importer get one without extra calls.
- `deleteTracks` (`core/Exploration.kt`): delete the rows and points, wipe `revealed_cell` and rebuild it from the points left, own recordings first so their source wins. One transaction. This is the reprocessing roadmap step 4 will reuse for Settings.
- GPX name: the track's `<name>`, else `<metadata><name>`, else the file name; capped at `MAX_GPX_NAME`. A file with no track points is now unreadable instead of an empty track.
- Screen: `ui/TracksActivity.kt` + `ui/TracksScreen.kt` (spec `docs/ui/screens/tracks.md`), opened from a new `route` icon that replaces Import GPX in the main toolbar; import moves into this screen. List sorted by date added; imports show the route's date as a name suffix. Delete from a trash icon, swipe left or multiple selection (long press or the select icon), always confirmed. The track being recorded can't be deleted (`TrackingService.activeTrackId`).

## Alternatives
- A cell↔track link table: instant deletes, but several rows per point and a more complex insert. Kept for later if rebuilding is too slow on a real device.
- Sorting by the route's own date: an old route imported today would be hard to find to undo the import.
- Naming recordings when they stop: planned separately (`docs/PLAN.md`); until then "Exploration HH:MM".

## Consequences
- Delete cost grows with the total of stored points; not measured on a device yet (`docs/HUMAN_TEST.md`).
- Rebuilt cells get the current time as `firstRevealedAt`.
- While a delete runs, the tracking service's writes wait for the transaction.
- From version 3 on, schema changes keep data through migrations; the destructive fallback only covers versions without one.
