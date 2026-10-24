package com.cuarentaydos.foghike.core

import androidx.room.withTransaction
import com.cuarentaydos.foghike.data.AppDatabase
import com.cuarentaydos.foghike.data.RevealedCell
import com.cuarentaydos.foghike.data.Track
import com.cuarentaydos.foghike.data.TrackPoint
import com.cuarentaydos.foghike.geo.CELL_SIZE_METERS
import com.cuarentaydos.foghike.geo.Cell
import com.cuarentaydos.foghike.geo.LatLon
import com.cuarentaydos.foghike.geo.REVEAL_RADIUS_METERS
import com.cuarentaydos.foghike.geo.cellsInRadius
import com.cuarentaydos.foghike.geo.coarseCells
import com.cuarentaydos.foghike.geo.trackFingerprint

/** Inaccurate points are kept but ignored by everything the user sees. */
fun isAccurate(accuracy: Float?) = accuracy == null || accuracy <= REVEAL_RADIUS_METERS

/** [accuracy] is the GPS error radius in meters; null when unknown (e.g. GPX files). */
data class GpsPoint(val lat: Double, val lon: Double, val time: Long?, val accuracy: Float? = null)

/**
 * Stores raw points and reveals the cells around them, plus their coarse LOD cells with how many
 * revealed base cells each one holds. The first batch of a track also creates its [Track] row,
 * named [name] (null: the screen makes one up).
 * Shared by live tracking and every importer, so old hikes clear the map like new ones.
 * Inaccurate points are stored but never reveal anything: they would clear fog where the user wasn't.
 */
suspend fun recordPoints(
    db: AppDatabase, trackId: String, firstIndex: Int, points: List<GpsPoint>, source: String, name: String? = null,
) {
    // shortcut: System.currentTimeMillis and withTransaction are JVM/Android-only; at the iOS port use
    // kotlin.time.Clock and db.useWriterConnection { it.immediateTransaction { ... } }.
    val now = System.currentTimeMillis()
    db.withTransaction {
        db.trackDao().insertIgnoringExisting(Track(trackId, source, now, name, points.firstNotNullOfOrNull { it.time }))
        db.trackPointDao().insert(
            points.mapIndexed { i, p -> TrackPoint(trackId, firstIndex + i, p.lat, p.lon, p.time, p.accuracy, source) }
        )
        revealCells(db, points, source, now)
    }
}

/** Must run inside a transaction: the cells and their coarse counts change together. */
private suspend fun revealCells(db: AppDatabase, points: List<GpsPoint>, source: String, now: Long) {
    val cells = linkedSetOf<Cell>()
    for (p in points.filter { isAccurate(it.accuracy) }) {
        cells += cellsInRadius(LatLon(p.lat, p.lon), CELL_SIZE_METERS, REVEAL_RADIUS_METERS)
    }
    val cellDao = db.cellDao()
    val ids = cellDao.insertIgnoringExisting(cells.map { RevealedCell(it.id, it.size, it.row, it.col, now, source) })
    // Only base cells revealed for the first time add to their coarse cells' counts.
    val coarse = cells.filterIndexed { i, _ -> ids[i] != -1L }.flatMap(::coarseCells).groupingBy { it }.eachCount()
    cellDao.insertIgnoringExisting(coarse.keys.map { RevealedCell(it.id, it.size, it.row, it.col, now, source, 0) })
    coarse.forEach { (cell, n) -> cellDao.addToCount(cell.id, n) }
}

/**
 * Stores a whole route read from a file. Its id is [idPrefix] plus a fingerprint of its coordinates,
 * so a route already imported is skipped instead of duplicated: returns false in that case.
 */
suspend fun importTrack(db: AppDatabase, idPrefix: String, points: List<GpsPoint>, source: String, name: String? = null): Boolean {
    val trackId = idPrefix + trackFingerprint(points.map { LatLon(it.lat, it.lon) })
    if (db.trackPointDao().exists(trackId)) return false
    recordPoints(db, trackId, 0, points, source, name)
    return true
}

/**
 * Deletes tracks with their points and rebuilds every cell from the points left, own recordings
 * first so their source wins. Cells don't know which track revealed them, so this is the only way
 * to fog again what only the deleted tracks cleared. Cost grows with the total of stored points;
 * one transaction, so a crash halfway leaves the old cells.
 * shortcut: rebuilt cells get the current time as firstRevealedAt; keep the old one if anything shows it.
 */
suspend fun deleteTracks(db: AppDatabase, trackIds: List<String>) {
    val now = System.currentTimeMillis()
    db.withTransaction {
        db.trackDao().delete(trackIds)
        db.trackPointDao().delete(trackIds)
        db.cellDao().deleteAll()
        for (track in db.trackDao().ownFirst()) {
            val points = db.trackPointDao().ofTrack(track.trackId).map { GpsPoint(it.lat, it.lon, it.timestamp, it.accuracy) }
            revealCells(db, points, track.source, now)
        }
    }
}
