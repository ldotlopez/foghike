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
}
