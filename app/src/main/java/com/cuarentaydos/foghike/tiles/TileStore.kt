package com.cuarentaydos.foghike.tiles

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import com.cuarentaydos.foghike.geo.Tile
import java.io.File

/**
 * Name (without .mbtiles) of the one file that holds every downloaded tile (decision 0015). Each tile
 * is stored once, so overlapping downloads share it, a download skips what is already there and a
 * clean-up is one delete.
 */
const val TILE_STORE_NAME = "tiles"
const val TILE_STORE_FILE = "$TILE_STORE_NAME.mbtiles"

/** Size estimate per tile while the store is still empty (IGN orthophoto JPEGs; measured ones replace it). */
const val DEFAULT_TILE_BYTES = 25_000

/** Opens the store in [dir], creating a standard MBTiles schema the first time. Close it after use. */
fun openTileStore(dir: File): SQLiteDatabase {
    val db = SQLiteDatabase.openOrCreateDatabase(File(dir, TILE_STORE_FILE), null)
    db.execSQL("CREATE TABLE IF NOT EXISTS metadata (name TEXT, value TEXT)")
    db.execSQL("CREATE TABLE IF NOT EXISTS tiles (zoom_level INTEGER, tile_column INTEGER, tile_row INTEGER, tile_data BLOB)")
    db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS tile_index ON tiles (zoom_level, tile_column, tile_row)")
    val empty = db.rawQuery("SELECT COUNT(*) FROM metadata", null).use { it.moveToFirst() && it.getInt(0) == 0 }
    if (empty) mapOf("name" to TILE_STORE_NAME, "format" to "jpg", "type" to "baselayer", "attribution" to IGN_ATTRIBUTION)
        .forEach { (name, value) -> db.execSQL("INSERT INTO metadata VALUES (?, ?)", arrayOf<Any>(name, value)) }
    return db
}

// MBTiles stores rows bottom-up (TMS); the app uses XYZ rows everywhere else.
private fun tmsRow(z: Int, y: Int) = (1 shl z) - 1 - y

fun SQLiteDatabase.storedTiles(): Set<Tile> =
    rawQuery("SELECT zoom_level, tile_column, tile_row FROM tiles", null).use { c ->
        HashSet<Tile>(c.count * 2).apply {
            while (c.moveToNext()) add(Tile(c.getInt(0), c.getInt(1), tmsRow(c.getInt(0), c.getInt(2))))
        }
    }

/** Average stored tile size in bytes, or [DEFAULT_TILE_BYTES] while empty. */
fun SQLiteDatabase.averageTileBytes(): Int =
    rawQuery("SELECT AVG(LENGTH(tile_data)) FROM tiles", null).use { c ->
        if (c.moveToFirst() && !c.isNull(0)) c.getDouble(0).toInt() else DEFAULT_TILE_BYTES
    }

fun SQLiteDatabase.putTile(tile: Tile, bytes: ByteArray) {
    insertWithOnConflict("tiles", null, ContentValues().apply {
        put("zoom_level", tile.z)
        put("tile_column", tile.x)
        put("tile_row", tmsRow(tile.z, tile.y))
        put("tile_data", bytes)
    }, SQLiteDatabase.CONFLICT_REPLACE)
}

/** Deletes [tiles] in one transaction, then compacts the file so the space goes back to the phone. */
fun SQLiteDatabase.deleteTiles(tiles: Collection<Tile>) {
    beginTransaction()
    try {
        for (t in tiles) {
            execSQL("DELETE FROM tiles WHERE zoom_level = ? AND tile_column = ? AND tile_row = ?", arrayOf(t.z, t.x, tmsRow(t.z, t.y)))
        }
        setTransactionSuccessful()
    } finally {
        endTransaction()
    }
    execSQL("VACUUM")
}

/** Copies the tiles of an older one-file-per-area download into the store; tiles already there win. */
fun SQLiteDatabase.mergeFrom(file: File) {
    execSQL("ATTACH DATABASE ? AS legacy", arrayOf(file.path))
    try {
        execSQL("INSERT OR IGNORE INTO tiles SELECT zoom_level, tile_column, tile_row, tile_data FROM legacy.tiles")
    } finally {
        execSQL("DETACH DATABASE legacy")
    }
}
