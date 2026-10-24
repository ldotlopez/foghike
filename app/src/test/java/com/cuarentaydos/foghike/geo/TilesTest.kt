package com.cuarentaydos.foghike.geo

import org.junit.Assert.assertEquals
import org.junit.Test

class TilesTest {
    @Test
    fun knownTiles() {
        assertEquals(0, tileX(-180.0, 3))
        assertEquals(7, tileX(180.0, 3)) // clamped to the last column
        assertEquals(Tile(1, 1, 1), Tile(1, tileX(0.0, 1), tileY(-0.001, 1)))
        assertEquals(512, tileY(-0.0001, 10))
        assertEquals(501, tileX(-3.7038, 10)) // Madrid
    }

    @Test
    fun countMatchesEnumeration() {
        val box = Box(40.0, -4.0, 40.2, -3.7)
        assertEquals(tilesFor(box, 10, 14).count().toLong(), tileCount(box, 10, 14))
    }
}
