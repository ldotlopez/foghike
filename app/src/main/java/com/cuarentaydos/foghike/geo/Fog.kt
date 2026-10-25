package com.cuarentaydos.foghike.geo

import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

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

/**
 * Fog left over a revealed cell of LOD [level] holding [revealedCount] revealed base cells: 0 when
 * all of it is explored, close to 1 when barely touched. Log scale, so a single hike across a 12.8 km
 * cell (~0.5 % of it) still shows as a clear patch.
 */
fun fogDensity(revealedCount: Int, level: Int): Float {
    val perSide = lodSize(1, level).toDouble()
    val explored = ln(1.0 + revealedCount) / ln(1.0 + perSide * perSide)
    return (1.0 - explored).coerceIn(0.0, 1.0).toFloat()
}

/**
 * Like [fogRects] (every piece with density 1), but the fog fades across each edge of the
 * [revealed] area instead of stopping at hard cell-shaped steps. The cells on both sides of an edge
 * are split into [steps]×[steps] pieces whose density (0 clear, 1 full fog) follows the distance to
 * the other side: 0.5 on the edge, 0 one cell inside, 1 one cell out. Distances are in cell units
 * and treat neighbouring rows as aligned (their widths differ by a negligible fraction).
 */
fun softFogRects(box: Box, size: Int, revealed: Collection<Cell>, steps: Int): List<Pair<Box, Float>> {
    val clear = revealed.toHashSet()
    fun neighbours(c: Cell) = (-1..1).flatMap { dr -> (-1..1).map { dc -> Cell(size, c.row + dr, c.col + dc) } } - c
    val edge = HashSet<Cell>() // cells on either side of an edge
    for (c in clear) {
        val fog = neighbours(c).filter { it !in clear }
        if (fog.isNotEmpty()) { edge += c; edge += fog }
    }
    val pieces = fogRects(box, size, clear + edge).mapTo(mutableListOf()) { it to 1f }

    for (c in edge) {
        val inside = c in clear
        val others = neighbours(c).filter { (it in clear) != inside }
        val b = c.bounds()
        val h = (b.north - b.south) / steps
        val w = (b.east - b.west) / steps
        fun density(i: Int, j: Int): Float {
            val y = c.row + (i + 0.5) / steps
            val x = c.col + (j + 0.5) / steps
            val d = others.minOf { o -> hypot(max(0.0, abs(x - o.col - 0.5) - 0.5), max(0.0, abs(y - o.row - 0.5) - 0.5)) }
            val ramp = if (inside) (1 - d) / 2 else (1 + d) / 2
            // Rounded to 1/(2·steps) so neighbouring pieces with almost the same density merge.
            return (ramp.coerceIn(0.0, 1.0) * 2 * steps).roundToInt() / (2f * steps)
        }
        for (i in 0 until steps) {
            var j = 0
            while (j < steps) {
                val dens = density(i, j)
                var k = j + 1
                while (k < steps && density(i, k) == dens) k++
                val piece = Box(b.south + i * h, b.west + j * w, b.south + (i + 1) * h, b.west + k * w).clippedTo(box)
                if (dens > 0f && piece != null) pieces += piece to dens
                j = k
            }
        }
    }
    return pieces
}

private fun Box.clippedTo(other: Box): Box? {
    val b = Box(max(south, other.south), max(west, other.west), min(north, other.north), min(east, other.east))
    return b.takeIf { it.south < it.north && it.west < it.east }
}
