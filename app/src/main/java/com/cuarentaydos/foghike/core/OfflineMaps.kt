package com.cuarentaydos.foghike.core

import com.cuarentaydos.foghike.data.AppDatabase
import com.cuarentaydos.foghike.data.CELLS_PREFIX
import com.cuarentaydos.foghike.data.DownloadedArea
import com.cuarentaydos.foghike.geo.Box
import com.cuarentaydos.foghike.geo.Cell
import com.cuarentaydos.foghike.geo.RevealGrid
import com.cuarentaydos.foghike.geo.Tile
import com.cuarentaydos.foghike.geo.tileBounds
import com.cuarentaydos.foghike.geo.tileX
import com.cuarentaydos.foghike.geo.tileY
import com.cuarentaydos.foghike.geo.tilesCovering

// Offline maps from revealed cells (decision 0015): which tiles to download for the explored map or
// for imported tracks, and which ones a clean-up keeps. The Android side stores and fetches them.

/** Zoom range every kind of download offers the first time (decision 0019); each then remembers its own. */
val DOWNLOAD_ZOOMS = 13..18

/**
 * Tiles under every revealed base cell, at each of [zooms].
 * shortcut: reads every base cell; for very large histories (Timeline imports) use the coarse cells at low zooms.
 */
suspend fun exploredTiles(db: AppDatabase, cellSize: Int, zooms: Iterable<Int>): Set<Tile> {
    val cells = db.cellDao().ofSize(cellSize).map { Cell(cellSize, it.gridRow, it.gridCol) }
    return zooms.flatMapTo(HashSet()) { tilesCovering(cells, it) }
}

/** Tiles under the cells [trackIds] reveal with [grid], at each of [zooms]. */
suspend fun trackTiles(db: AppDatabase, grid: RevealGrid, trackIds: List<String>, zooms: Iterable<Int>): Set<Tile> {
    val cells = trackIds.flatMapTo(HashSet()) { id ->
        cellsRevealedBy(db.trackPointDao().ofTrack(id).map { GpsPoint(it.lat, it.lon, it.timestamp, it.accuracy) }, grid)
    }
    return zooms.flatMapTo(HashSet()) { tilesCovering(cells, it) }
}

/** Bounding box of [tiles], for the download's record. */
fun boundsOfTiles(tiles: Collection<Tile>): Box {
    val boxes = tiles.map(::tileBounds)
    return Box(boxes.minOf { it.south }, boxes.minOf { it.west }, boxes.maxOf { it.north }, boxes.maxOf { it.east })
}

/**
 * Whether a clean-up keeps [tile]: it is under a revealed cell ([explored], at every stored zoom), or
 * inside the bounding box and zoom range of one of [keptAreas] (hand-drawn areas the user keeps).
 */
fun keepsTile(tile: Tile, explored: Set<Tile>, keptAreas: List<DownloadedArea>): Boolean =
    tile in explored || keptAreas.any { a ->
        tile.z in a.minZoom..a.maxZoom &&
            tile.x in tileX(a.minLon, tile.z)..tileX(a.maxLon, tile.z) &&
            tile.y in tileY(a.maxLat, tile.z)..tileY(a.minLat, tile.z)
    }

/**
 * The record that replaces hand-drawn [areas] removed by a clean-up: their explored tiles stay, so
 * their zoom range must stay in the map style. Null if there are none.
 */
fun replacementFor(areas: List<DownloadedArea>, name: String, now: Long): DownloadedArea? {
    if (areas.isEmpty()) return null
    return DownloadedArea(
        name = CELLS_PREFIX + name,
        minLat = areas.minOf { it.minLat }, minLon = areas.minOf { it.minLon },
        maxLat = areas.maxOf { it.maxLat }, maxLon = areas.maxOf { it.maxLon },
        minZoom = areas.minOf { it.minZoom }, maxZoom = areas.maxOf { it.maxZoom },
        fileName = areas.first().fileName, downloadedAt = now,
    )
}
