package com.cuarentaydos.foghike.core

import androidx.room.withTransaction
import com.cuarentaydos.foghike.data.AppDatabase
import com.cuarentaydos.foghike.data.RevealedCell
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
 * revealed base cells each one holds.
 * Shared by live tracking and every importer, so old hikes clear the map like new ones.
 * Inaccurate points are stored but never reveal anything: they would clear fog where the user wasn't.
 */
suspend fun recordPoints(db: AppDatabase, trackId: String, firstIndex: Int, points: List<GpsPoint>, source: String) {
    val cells = linkedSetOf<Cell>()
    for (p in points.filter { isAccurate(it.accuracy) }) {
        cells += cellsInRadius(LatLon(p.lat, p.lon), CELL_SIZE_METERS, REVEAL_RADIUS_METERS)
    }
    // shortcut: System.currentTimeMillis and withTransaction are JVM/Android-only; at the iOS port use
    // kotlin.time.Clock and db.useWriterConnection { it.immediateTransaction { ... } }.
    val now = System.currentTimeMillis()
    db.withTransaction {
        db.trackPointDao().insert(
            points.mapIndexed { i, p -> TrackPoint(trackId, firstIndex + i, p.lat, p.lon, p.time, p.accuracy, source) }
        )
        val cellDao = db.cellDao()
        val ids = cellDao.insertIgnoringExisting(cells.map { RevealedCell(it.id, it.size, it.row, it.col, now, source) })
        // Only base cells revealed for the first time add to their coarse cells' counts.
        val coarse = cells.filterIndexed { i, _ -> ids[i] != -1L }.flatMap(::coarseCells).groupingBy { it }.eachCount()
        cellDao.insertIgnoringExisting(coarse.keys.map { RevealedCell(it.id, it.size, it.row, it.col, now, source, 0) })
        coarse.forEach { (cell, n) -> cellDao.addToCount(cell.id, n) }
    }
}

/**
 * Stores a whole route read from a file. Its id is [idPrefix] plus a fingerprint of its coordinates,
 * so a route already imported is skipped instead of duplicated: returns false in that case.
 */
suspend fun importTrack(db: AppDatabase, idPrefix: String, points: List<GpsPoint>, source: String): Boolean {
    val trackId = idPrefix + trackFingerprint(points.map { LatLon(it.lat, it.lon) })
    if (db.trackPointDao().exists(trackId)) return false
    recordPoints(db, trackId, 0, points, source)
    return true
}
