package com.cuarentaydos.foghike.geo

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.tan

/** A Web Mercator (XYZ, "Google") tile. */
data class Tile(val z: Int, val x: Int, val y: Int)

fun tileX(lon: Double, z: Int): Int {
    val n = 1 shl z
    return floor((lon + 180.0) / 360.0 * n).toInt().coerceIn(0, n - 1)
}

fun tileY(lat: Double, z: Int): Int {
    val n = 1 shl z
    val r = rad(lat)
    return floor((1.0 - ln(tan(r) + 1.0 / cos(r)) / PI) / 2.0 * n).toInt().coerceIn(0, n - 1)
}

fun tilesFor(box: Box, minZoom: Int, maxZoom: Int): Sequence<Tile> = sequence {
    for (z in minZoom..maxZoom) {
        for (x in tileX(box.west, z)..tileX(box.east, z)) {
            for (y in tileY(box.north, z)..tileY(box.south, z)) yield(Tile(z, x, y))
        }
    }
}

/**
 * Tiles at zoom [z] that overlap any of [cells]: each cell's rectangle touches one tile, or up to
 * 2×2 when it straddles a tile edge. Revealed corridors are narrow, so at high zooms this is far
 * fewer tiles than the bounding box of the same cells.
 */
fun tilesCovering(cells: Iterable<Cell>, z: Int): Set<Tile> {
    val tiles = HashSet<Tile>()
    for (cell in cells) {
        val b = cell.bounds()
        for (x in tileX(b.west, z)..tileX(b.east, z)) {
            for (y in tileY(b.north, z)..tileY(b.south, z)) tiles += Tile(z, x, y)
        }
    }
    return tiles
}

fun tileCount(box: Box, minZoom: Int, maxZoom: Int): Long = (minZoom..maxZoom).sumOf { z ->
    (tileX(box.east, z) - tileX(box.west, z) + 1).toLong() * (tileY(box.south, z) - tileY(box.north, z) + 1)
}
