package com.cuarentaydos.foghike.geo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
}
