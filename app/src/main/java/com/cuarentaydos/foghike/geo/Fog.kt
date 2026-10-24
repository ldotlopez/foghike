package com.cuarentaydos.foghike.geo

import kotlin.math.max
import kotlin.math.min

/**
 * Rectangles covering [box] except the [revealed] cells (all of the same [size]).
 *
 * Each row costs one rectangle per gap between runs of revealed cells; consecutive rows with
 * nothing revealed are merged into a single rectangle.
 */
fun fogRects(box: Box, size: Int, revealed: Collection<Cell>): List<Box> {
    val h = rowHeightDeg(size)
    val colsByRow = revealed.groupBy({ it.row }, { it.col })
    val rects = mutableListOf<Box>()
    var emptySince: Double? = null // south edge of the pending block of empty rows

    for (row in rowRange(box, size)) {
        val south = max(box.south, row * h)
        val north = min(box.north, (row + 1) * h)
        val cols = colsByRow[row]?.sorted()
        if (cols == null) {
            if (emptySince == null) emptySince = south
            continue
        }
        emptySince?.let { rects += Box(it, box.west, south, box.east) }
        emptySince = null

        val w = colWidthDeg(size, row)
        var west = box.west
        var i = 0
        while (i < cols.size) {
            var j = i
            while (j + 1 < cols.size && cols[j + 1] <= cols[j] + 1) j++
            val gapEnd = min(cols[i] * w, box.east)
            if (gapEnd > west) rects += Box(south, west, north, gapEnd)
            west = max(west, (cols[j] + 1) * w)
            i = j + 1
        }
        if (west < box.east) rects += Box(south, west, north, box.east)
    }
    emptySince?.let { rects += Box(it, box.west, box.north, box.east) }
    return rects
}
