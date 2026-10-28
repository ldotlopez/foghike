package com.cuarentaydos.foghike.geo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FogTest {
    private val box = Box(40.0, -4.0, 40.01, -3.99)

    private fun Box.area() = (north - south) * (east - west)
    private fun Box.has(p: LatLon) = p.lat > south && p.lat < north && p.lon > west && p.lon < east

    @Test
    fun nothingRevealedIsOneRectangle() {
        assertEquals(listOf(box), fogRects(box, 25, emptyList()))
    }

    @Test
    fun revealedCellIsAHoleAndAreaAddsUp() {
        val cell = cellAt(LatLon(40.005, -3.995), 25)
        val rects = fogRects(box, 25, listOf(cell))
        rects.forEach { assertFalse(it.has(cell.center())) }
        assertEquals(box.area(), rects.sumOf { it.area() } + cell.bounds().area(), 1e-12)
        // empty rows below + 2 sides of the hole + empty rows above
        assertEquals(4, rects.size)
    }

    @Test
    fun adjacentRevealedCellsMergeIntoOneGap() {
        val a = cellAt(LatLon(40.005, -3.995), 25)
        val b = Cell(25, a.row, a.col + 1)
        assertEquals(4, fogRects(box, 25, listOf(a, b)).size)
    }

    @Test
    fun revealedCellsOutsideTheBoxDoNotBreakIt() {
        val far = cellAt(LatLon(40.005, 10.0), 25)
        val rects = fogRects(box, 25, listOf(far))
        assertEquals(box.area(), rects.sumOf { it.area() }, 1e-12)
    }

    @Test
    fun densityFollowsHowMuchOfTheCellIsExplored() {
        assertEquals(0f, fogDensity(1, 0)) // base cells are either clear or fogged
        assertEquals(0f, fogDensity(64 * 64, 2))
        assertTrue(fogDensity(1, 3) > 0.9f) // a single base cell barely thins a 12.8 km cell
        // One straight hike across a 12.8 km cell (~1,200 base cells) is clearly visible but not clear.
        assertTrue(fogDensity(1_200, 3) in 0.3f..0.6f)
        assertTrue(fogDensity(100, 2) > fogDensity(1_000, 2))
    }

    private fun List<Pair<Box, Float>>.densityAt(p: LatLon) = filter {
        it.first.has(p)
    }.map { it.second }

    @Test
    fun softFogWithNothingRevealedIsOneRectangle() {
        assertEquals(listOf(box to 1f), softFogRects(box, 25, emptyList(), 3))
    }

    @Test
    fun softFogFadesAcrossTheEdgeWithoutOverlaps() {
        val c = cellAt(LatLon(40.005, -3.995), 25)
        val block = (-2..2).flatMap { dr -> (-2..2).map { dc -> Cell(25, c.row + dr, c.col + dc) } }
        val pieces = softFogRects(box, 25, block, 3)
        assertTrue(pieces.all { it.second > 0f && it.second <= 1f })
        // Pieces never overlap: their area is the box minus what is left fully clear.
        val clearArea = box.area() - pieces.sumOf { it.first.area() }
        assertTrue(clearArea > 0 && clearArea < block.sumOf { it.bounds().area() })
        // Clear in the middle, partial fog on both sides of the edge, full fog far away.
        assertEquals(emptyList<Float>(), pieces.densityAt(c.center()))
        val edgeIn = Cell(25, c.row, c.col + 2).bounds()
        val edgeOut = Cell(25, c.row, c.col + 3).bounds()
        val y = (edgeIn.south + edgeIn.north) / 2
        val inside = pieces.densityAt(
            LatLon(y, edgeIn.east - (edgeIn.east - edgeIn.west) / 6)
        ).single()
        val outside = pieces.densityAt(
            LatLon(y, edgeOut.west + (edgeOut.east - edgeOut.west) / 6)
        ).single()
        assertTrue(inside in 0.3f..0.5f)
        assertTrue(outside in 0.5f..0.7f)
        assertEquals(listOf(1f), pieces.densityAt(LatLon(40.0001, -3.9999)))
    }
}
