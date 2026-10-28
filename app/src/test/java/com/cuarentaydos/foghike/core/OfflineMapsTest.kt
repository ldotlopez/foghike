package com.cuarentaydos.foghike.core

import androidx.room.Room
import com.cuarentaydos.foghike.data.AREA_PREFIX
import com.cuarentaydos.foghike.data.AppDatabase
import com.cuarentaydos.foghike.data.DownloadedArea
import com.cuarentaydos.foghike.data.Source
import com.cuarentaydos.foghike.geo.RevealGrid
import com.cuarentaydos.foghike.geo.Tile
import com.cuarentaydos.foghike.geo.tileX
import com.cuarentaydos.foghike.geo.tileY
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

/** On Robolectric's real SQLite, so the cell queries run as on the phone. */
@RunWith(RobolectricTestRunner::class)
class OfflineMapsTest {
    private val db = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), AppDatabase::class.java).build()
    private val walk = (0..40).map { GpsPoint(40.0 + it * 0.0003, -3.7, null) }
    private val elsewhere = (0..40).map { GpsPoint(42.0, -1.0 + it * 0.0003, null) }

    @Test
    fun trackTilesAreTheTilesOfItsCells() = runBlocking {
        val id = importTrack(db, RevealGrid(), "gpx-", walk, Source.GPX_IMPORT)!!
        importTrack(db, RevealGrid(), "gpx-", elsewhere, Source.GPX_IMPORT)
        val tiles = trackTiles(db, RevealGrid(), listOf(id), 13..17)
        // The first walk only: every tile is near 40 N, none near the other track.
        assertTrue(tiles.isNotEmpty())
        assertTrue(tiles.all { it.y in tileY(40.02, it.z)..tileY(39.99, it.z) })
        assertEquals((13..17).toSet(), tiles.map { it.z }.toSet())
    }

    @Test
    fun exploredTilesCoverEveryTrack() = runBlocking {
        val a = importTrack(db, RevealGrid(), "gpx-", walk, Source.GPX_IMPORT)!!
        val b = importTrack(db, RevealGrid(), "gpx-", elsewhere, Source.GPX_IMPORT)!!
        val explored = exploredTiles(db, 25, 13..17)
        assertEquals(trackTiles(db, RevealGrid(), listOf(a, b), 13..17), explored)
    }

    @Test
    fun cleanupKeepsExploredTilesAndKeptAreas() {
        val explored = setOf(Tile(15, 100, 200))
        val area = DownloadedArea(
            name = AREA_PREFIX + "x", minLat = 40.0, minLon = -3.8, maxLat = 40.1, maxLon = -3.7,
            minZoom = 13, maxZoom = 16, fileName = "tiles.mbtiles", downloadedAt = 0
        )
        val inArea = Tile(14, tileX(-3.75, 14), tileY(40.05, 14))
        assertTrue(keepsTile(Tile(15, 100, 200), explored, emptyList()))
        assertTrue(keepsTile(inArea, explored, listOf(area)))
        assertFalse(keepsTile(inArea, explored, emptyList()))
        assertFalse(keepsTile(inArea.copy(z = 17), explored, listOf(area))) // above the area's zoom range
    }

    @Test
    fun replacementKeepsTheZoomRangeOfRemovedAreas() {
        fun area(min: Int, max: Int, lat: Double) = DownloadedArea(
            name = AREA_PREFIX + lat, minLat = lat, minLon = -3.8, maxLat = lat + 0.1, maxLon = -3.7,
            minZoom = min, maxZoom = max, fileName = "tiles.mbtiles", downloadedAt = 0
        )
        val r = replacementFor(listOf(area(13, 16, 40.0), area(12, 18, 41.0)), "kept", 5)!!
        assertEquals(12..18, r.minZoom..r.maxZoom)
        assertEquals(40.0, r.minLat, 0.0)
        assertEquals(41.1, r.maxLat, 1e-9)
        assertFalse(r.isManual)
        assertNull(replacementFor(emptyList(), "kept", 5))
    }
}
