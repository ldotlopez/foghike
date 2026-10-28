package com.cuarentaydos.foghike.geo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PolygonTest {
    private val square =
        listOf(LatLon(0.0, 0.0), LatLon(0.0, 10.0), LatLon(10.0, 10.0), LatLon(10.0, 0.0))

    @Test
    fun pointInPolygon() {
        assertTrue(pointInPolygon(LatLon(5.0, 5.0), square))
        assertFalse(pointInPolygon(LatLon(15.0, 5.0), square))
        // L shape: the notch is outside
        val l =
            listOf(
                LatLon(0.0, 0.0),
                LatLon(0.0, 10.0),
                LatLon(5.0, 10.0),
                LatLon(5.0, 5.0),
                LatLon(10.0, 5.0),
                LatLon(10.0, 0.0)
            )
        assertFalse(pointInPolygon(LatLon(8.0, 8.0), l))
        assertTrue(pointInPolygon(LatLon(8.0, 2.0), l))
    }

    @Test
    fun boxPolygonIntersection() {
        assertTrue(Box(4.0, 4.0, 6.0, 6.0).intersects(square)) // box inside polygon
        assertTrue(Box(-1.0, -1.0, 11.0, 11.0).intersects(square)) // polygon inside box
        assertFalse(Box(20.0, 20.0, 30.0, 30.0).intersects(square))
        // Thin diagonal strip crossing the box with no vertex inside and no box corner inside it
        val strip =
            listOf(LatLon(-5.0, -4.9), LatLon(15.0, 15.1), LatLon(15.0, 15.2), LatLon(-5.0, -4.8))
        assertTrue(Box(4.0, 4.0, 6.0, 6.0).intersects(strip))
        // Same bounding boxes overlap but the L's notch misses the box
        val l =
            listOf(
                LatLon(0.0, 0.0),
                LatLon(0.0, 10.0),
                LatLon(5.0, 10.0),
                LatLon(5.0, 5.0),
                LatLon(10.0, 5.0),
                LatLon(10.0, 0.0)
            )
        assertFalse(Box(7.0, 7.0, 9.0, 9.0).intersects(l))
    }

    @Test
    fun tileBoundsMatchTileMath() {
        val t = Tile(10, tileX(-3.7038, 10), tileY(40.4168, 10))
        val b = tileBounds(t)
        assertEquals(t.x, tileX((b.west + b.east) / 2, 10))
        assertEquals(t.y, tileY((b.south + b.north) / 2, 10))
    }

    @Test
    fun polygonTilesAreSubsetOfItsBoundingBox() {
        val triangle = listOf(LatLon(40.0, -4.0), LatLon(40.0, -3.0), LatLon(41.0, -4.0))
        val inPolygon = tilesFor(triangle, 10, 10).toSet()
        val inBox = tilesFor(boundsOf(triangle), 10, 10).toSet()
        assertTrue(inBox.containsAll(inPolygon))
        assertTrue(inPolygon.size < inBox.size) // the far corner of the box is skipped
        assertTrue(inPolygon.size > inBox.size / 3)
    }
}
