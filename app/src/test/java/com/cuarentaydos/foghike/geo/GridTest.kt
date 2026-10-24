package com.cuarentaydos.foghike.geo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GridTest {
    private val madrid = LatLon(40.4168, -3.7038)

    private fun Box.has(p: LatLon) = p.lat >= south && p.lat < north && p.lon >= west && p.lon < east

    @Test
    fun cellContainsItsPoint() {
        for (size in listOf(10, 25, 50, 100, 12_800)) {
            assertTrue(cellAt(madrid, size).bounds().has(madrid))
        }
    }

    @Test
    fun cellIdIsEnoughToRebuildGeometry() {
        val cell = cellAt(madrid, 25)
        val again = cellAt(cell.center(), 25)
        assertEquals(cell.id, again.id)
        assertEquals(cell.bounds(), Cell(25, cell.row, cell.col).bounds())
    }

    @Test
    fun cellIsRoughlySquareInMeters() {
        val b = cellAt(madrid, 25).bounds()
        val height = haversineMeters(LatLon(b.south, b.west), LatLon(b.north, b.west))
        val width = haversineMeters(LatLon(b.south, b.west), LatLon(b.south, b.east))
        assertEquals(25.0, height, 0.01)
        assertEquals(25.0, width, 0.1)
    }

    @Test
    fun haversineOneDegreeOfLatitude() {
        assertEquals(111_195.0, haversineMeters(LatLon(40.0, 0.0), LatLon(41.0, 0.0)), 5.0)
    }

    @Test
    fun revealKeepsOnlyCentersInsideRadius() {
        val cells = cellsInRadius(madrid, 25, 30.0)
        assertTrue(cells.all { haversineMeters(madrid, it.center()) <= 30.0 })
        // π·30² / 25² ≈ 4.5 cells on average
        assertTrue(cells.size in 2..9)
        // Nothing just outside the candidate window was missed: a wider search finds the same set.
        val wider = cellsInRadius(madrid, 25, 60.0).filter { haversineMeters(madrid, it.center()) <= 30.0 }
        assertEquals(wider.toSet(), cells.toSet())
    }

    @Test
    fun coarseCellsContainTheFineCellCenter() {
        val fine = cellAt(madrid, 25)
        val coarse = coarseCells(fine)
        assertEquals(listOf(200, 1_600, 12_800), coarse.map { it.size })
        assertTrue(coarse.all { it.bounds().has(fine.center()) })
    }

    @Test
    fun lodLevelFollowsZoom() {
        assertEquals(0, lodLevelFor(25, 16.0, 40.0))
        assertEquals(0, lodLevelFor(25, 15.0, 40.0))
        assertEquals(1, lodLevelFor(25, 13.0, 40.0))
        assertEquals(2, lodLevelFor(25, 10.0, 40.0))
        assertEquals(3, lodLevelFor(25, 6.0, 40.0))
        assertEquals(3, lodLevelFor(25, 2.0, 40.0))
    }

    @Test
    fun expandedBoxIsThreeTimesWiderAndClamped() {
        val b = Box(40.0, -4.0, 41.0, -3.0).expanded()
        assertEquals(Box(39.0, -5.0, 42.0, -2.0), b)
        assertEquals(Box(-85.0, -180.0, 85.0, 180.0), Box(-80.0, -170.0, 80.0, 170.0).expanded())
    }

    @Test
    fun colRangeCoversEveryRow() {
        val box = Box(40.0, -4.0, 40.01, -3.99)
        val cols = colRange(box, 25)
        for (row in rowRange(box, 25)) {
            val w = colWidthDeg(25, row)
            assertTrue(kotlin.math.floor(box.west / w).toInt() >= cols.first)
            assertTrue(kotlin.math.floor(box.east / w).toInt() <= cols.last)
        }
    }
}
