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
    fun aCellInsideATileCoversOnlyThatTile() {
        val cell = cellAt(LatLon(40.4168, -3.7038), 25)
        val c = cell.center()
        assertEquals(
            setOf(Tile(10, tileX(c.lon, 10), tileY(c.lat, 10))),
            tilesCovering(listOf(cell), 10)
        )
    }

    @Test
    fun aCellOnATileEdgeCoversBothSides() {
        val z = 19
        val edgeLon = 245_000.0 / (1 shl z) * 360 - 180 // west edge of column 245000
        val cell = cellAt(LatLon(40.4, edgeLon), 25)
        val xs = tilesCovering(listOf(cell), z).map { it.x }.toSet()
        assertEquals(setOf(244_999, 245_000), xs)
    }

    @Test
    fun aCorridorNeedsFewerTilesThanItsBox() {
        // 2 km walk north, three cells wide: at zoom 17 its tiles are a subset of the bounding box's.
        val cells = (0..80).flatMap { i ->
            (-1..1).map { j ->
                cellAt(
                    LatLon(
                        40.0 + i * 0.000225,
                        -3.7 + j * 0.0003
                    ),
                    25
                )
            }
        }
        val covering = tilesCovering(cells, 17)
        val box =
            Box(
                cells.minOf {
                    it.bounds().south
                },
                cells.minOf {
                    it.bounds().west
                },
                cells.maxOf { it.bounds().north },
                cells.maxOf { it.bounds().east }
            )
        val boxTiles = tilesFor(box, 17, 17).toSet()
        assert(boxTiles.containsAll(covering))
        assert(covering.isNotEmpty() && covering.size <= boxTiles.size)
    }

    @Test
    fun countMatchesEnumeration() {
        val box = Box(40.0, -4.0, 40.2, -3.7)
        assertEquals(tilesFor(box, 10, 14).count().toLong(), tileCount(box, 10, 14))
    }
}
