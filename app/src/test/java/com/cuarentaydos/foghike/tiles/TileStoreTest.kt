package com.cuarentaydos.foghike.tiles

import com.cuarentaydos.foghike.geo.Tile
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import java.io.File

/** On Robolectric's real SQLite: the store is a plain MBTiles file. */
@RunWith(RobolectricTestRunner::class)
class TileStoreTest {
    private val dir = File(RuntimeEnvironment.getApplication().filesDir, "maps").apply { deleteRecursively(); mkdirs() }

    @Test
    fun storesXyzTilesAndDeletesThem() {
        openTileStore(dir).use { store ->
            store.putTile(Tile(15, 16000, 12000), ByteArray(100))
            store.putTile(Tile(16, 32000, 24000), ByteArray(300))
            store.putTile(Tile(15, 16000, 12000), ByteArray(100)) // same tile again: still one row
            assertEquals(setOf(Tile(15, 16000, 12000), Tile(16, 32000, 24000)), store.storedTiles())
            assertEquals(200, store.averageTileBytes())
            store.deleteTiles(listOf(Tile(16, 32000, 24000)))
            assertEquals(setOf(Tile(15, 16000, 12000)), store.storedTiles())
        }
        // Rows are TMS (bottom-up), as MBTiles readers expect.
        openTileStore(dir).use { store ->
            store.rawQuery("SELECT tile_row FROM tiles", null).use { c ->
                c.moveToFirst()
                assertEquals((1 shl 15) - 1 - 12000, c.getInt(0))
            }
        }
    }

    @Test
    fun emptyStoreEstimatesTheDefaultSize() = openTileStore(dir).use { assertEquals(DEFAULT_TILE_BYTES, it.averageTileBytes()) }

    @Test
    fun mergesAnOldAreaFileKeepingExistingTiles() {
        val legacyDir = File(dir, "legacy").apply { mkdirs() }
        openTileStore(legacyDir).use {
            it.putTile(Tile(14, 1, 1), ByteArray(10))
            it.putTile(Tile(14, 2, 2), ByteArray(10))
        }
        openTileStore(dir).use { store ->
            store.putTile(Tile(14, 1, 1), ByteArray(50))
            store.mergeFrom(File(legacyDir, TILE_STORE_FILE))
            assertEquals(setOf(Tile(14, 1, 1), Tile(14, 2, 2)), store.storedTiles())
            assertEquals(30, store.averageTileBytes()) // the stored 50-byte tile won over the old 10-byte one
        }
    }
}
