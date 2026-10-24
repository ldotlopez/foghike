package com.cuarentaydos.foghike.tiles

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import android.util.Log
import com.cuarentaydos.foghike.geo.Box
import com.cuarentaydos.foghike.geo.Tile
import kotlinx.coroutines.ensureActive
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import kotlin.coroutines.coroutineContext

/** IGN orthophoto WMTS (PNOA), Web Mercator matrix, so its tiles are plain XYZ tiles. */
const val IGN_TILE_URL = "https://www.ign.es/wmts/pnoa-ma?SERVICE=WMTS&REQUEST=GetTile&VERSION=1.0.0" +
    "&LAYER=OI.OrthoimageCoverage&STYLE=default&TILEMATRIXSET=GoogleMapsCompatible" +
    "&TILEMATRIX={z}&TILEROW={y}&TILECOL={x}&FORMAT=image/jpeg"
const val IGN_ATTRIBUTION = "PNOA cedido por © Instituto Geográfico Nacional de España"
const val IGN_MAX_ZOOM = 19

/** Guard against a huge rectangle hammering the free IGN service and filling the phone. */
const val MAX_TILES_PER_DOWNLOAD = 20_000

/**
 * Downloads [tiles] into a standard MBTiles [file]; [box] and the zooms only go into its metadata.
 * Sequential and without retries: a failed tile is skipped and logged.
 */
suspend fun downloadArea(
    file: File, tiles: List<Tile>, box: Box, minZoom: Int, maxZoom: Int, onProgress: (done: Int, total: Int) -> Unit,
) {
    SQLiteDatabase.openOrCreateDatabase(file, null).use { db ->
        db.execSQL("CREATE TABLE IF NOT EXISTS metadata (name TEXT, value TEXT)")
        db.execSQL("CREATE TABLE IF NOT EXISTS tiles (zoom_level INTEGER, tile_column INTEGER, tile_row INTEGER, tile_data BLOB)")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS tile_index ON tiles (zoom_level, tile_column, tile_row)")
        mapOf(
            "name" to file.nameWithoutExtension,
            "format" to "jpg",
            "type" to "baselayer",
            "bounds" to "${box.west},${box.south},${box.east},${box.north}",
            "minzoom" to minZoom.toString(),
            "maxzoom" to maxZoom.toString(),
            "attribution" to IGN_ATTRIBUTION,
        ).forEach { (name, value) -> db.execSQL("INSERT INTO metadata VALUES (?, ?)", arrayOf<Any>(name, value)) }

        tiles.forEachIndexed { i, tile ->
            coroutineContext.ensureActive()
            fetch(tile)?.let { bytes ->
                db.insertWithOnConflict("tiles", null, ContentValues().apply {
                    put("zoom_level", tile.z)
                    put("tile_column", tile.x)
                    put("tile_row", (1 shl tile.z) - 1 - tile.y) // TMS rows
                    put("tile_data", bytes)
                }, SQLiteDatabase.CONFLICT_REPLACE)
            }
            onProgress(i + 1, tiles.size)
        }
    }
}

private fun fetch(tile: Tile): ByteArray? {
    val url = IGN_TILE_URL.replace("{z}", tile.z.toString())
        .replace("{x}", tile.x.toString())
        .replace("{y}", tile.y.toString())
    return try {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = 10_000
        conn.readTimeout = 20_000
        try {
            if (conn.responseCode == 200) conn.inputStream.use { it.readBytes() }
            else null.also { Log.w("TileDownloader", "HTTP ${conn.responseCode} for $tile") }
        } finally {
            conn.disconnect()
        }
    } catch (e: IOException) {
        Log.w("TileDownloader", "Failed $tile", e)
        null
    }
}
