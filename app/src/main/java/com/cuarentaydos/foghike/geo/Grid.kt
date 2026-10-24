package com.cuarentaydos.foghike.geo

import kotlin.math.PI
import kotlin.math.asin
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

// Pure Kotlin (no Android, no java.*) so it can move to a KMP shared module.

const val EARTH_RADIUS_M = 6_371_008.8
const val METERS_PER_DEG_LAT = PI * EARTH_RADIUS_M / 180.0

// shortcut: fixed in code, the Settings screen (roadmap step 4) will make them configurable.
const val CELL_SIZE_METERS = 25
const val REVEAL_RADIUS_METERS = 30.0

/** Each LOD level has cells LOD_FACTOR times larger than the previous one; level 0 is the base grid. */
const val LOD_FACTOR = 8
const val LOD_LEVELS = 4

internal fun rad(deg: Double) = deg * PI / 180.0

data class LatLon(val lat: Double, val lon: Double)

data class Box(val south: Double, val west: Double, val north: Double, val east: Double) {
    fun contains(other: Box) =
        other.south >= south && other.north <= north && other.west >= west && other.east <= east

    /** Grows the box by its own size on every side (3×3 viewports), clamped to the Web Mercator limits. */
    fun expanded(): Box {
        val h = north - south
        val w = east - west
        return Box(max(south - h, -85.0), max(west - w, -180.0), min(north + h, 85.0), min(east + w, 180.0))
    }
}

/**
 * A grid cell. Rows have a constant height of [size] meters; the column width depends only on the
 * latitude of the row's center, so the id alone is enough to know which rectangle a cell covers.
 */
data class Cell(val size: Int, val row: Int, val col: Int) {
    val id: String get() = "$size:$row:$col"

    fun bounds(): Box {
        val h = rowHeightDeg(size)
        val w = colWidthDeg(size, row)
        return Box(row * h, col * w, (row + 1) * h, (col + 1) * w)
    }

    fun center(): LatLon {
        val b = bounds()
        return LatLon((b.south + b.north) / 2, (b.west + b.east) / 2)
    }
}

fun rowHeightDeg(size: Int) = size / METERS_PER_DEG_LAT

fun colWidthDeg(size: Int, row: Int): Double {
    val centerLat = (row + 0.5) * rowHeightDeg(size)
    return size / (METERS_PER_DEG_LAT * cos(rad(centerLat)))
}

fun rowOf(lat: Double, size: Int) = floor(lat / rowHeightDeg(size)).toInt()

fun cellAt(p: LatLon, size: Int): Cell {
    val row = rowOf(p.lat, size)
    return Cell(size, row, floor(p.lon / colWidthDeg(size, row)).toInt())
}

fun haversineMeters(a: LatLon, b: LatLon): Double {
    val dLat = rad(b.lat - a.lat)
    val dLon = rad(b.lon - a.lon)
    val h = sin(dLat / 2).pow(2) + cos(rad(a.lat)) * cos(rad(b.lat)) * sin(dLon / 2).pow(2)
    return 2 * EARTH_RADIUS_M * asin(sqrt(min(1.0, h)))
}

/** Cells of [size] whose center lies within [radius] meters of [p]. */
fun cellsInRadius(p: LatLon, size: Int, radius: Double): List<Cell> {
    val center = cellAt(p, size)
    val dRows = ceil(radius / size).toInt() + 1
    val dLonDeg = radius / (METERS_PER_DEG_LAT * cos(rad(p.lat)))
    val result = mutableListOf<Cell>()
    for (row in center.row - dRows..center.row + dRows) {
        val w = colWidthDeg(size, row)
        for (col in floor((p.lon - dLonDeg) / w).toInt() - 1..floor((p.lon + dLonDeg) / w).toInt() + 1) {
            val cell = Cell(size, row, col)
            if (haversineMeters(p, cell.center()) <= radius) result += cell
        }
    }
    return result
}

fun lodSize(baseSize: Int, level: Int): Int {
    var size = baseSize
    repeat(level) { size *= LOD_FACTOR }
    return size
}

/** The coarse cells (LOD levels 1 and up) that contain the center of [cell]. */
fun coarseCells(cell: Cell): List<Cell> {
    val c = cell.center()
    return (1 until LOD_LEVELS).map { cellAt(c, lodSize(cell.size, it)) }
}

/** Smallest LOD level whose cells are at least [minPixels] wide on screen at [zoom]. */
fun lodLevelFor(baseSize: Int, zoom: Double, lat: Double, minPixels: Double = 4.0): Int {
    val metersPerPixel = 156_543.03392 * cos(rad(lat)) / 2.0.pow(zoom)
    return (0 until LOD_LEVELS).firstOrNull { lodSize(baseSize, it) / metersPerPixel >= minPixels }
        ?: (LOD_LEVELS - 1)
}

fun rowRange(box: Box, size: Int) = rowOf(box.south, size)..rowOf(box.north, size)

/** Columns of [size] cells touched by [box] in any of its rows. */
fun colRange(box: Box, size: Int): IntRange {
    var first = Int.MAX_VALUE
    var last = Int.MIN_VALUE
    for (row in rowRange(box, size)) {
        val w = colWidthDeg(size, row)
        first = min(first, floor(box.west / w).toInt())
        last = max(last, floor(box.east / w).toInt())
    }
    return first..last
}
