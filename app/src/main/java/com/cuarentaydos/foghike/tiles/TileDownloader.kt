package com.cuarentaydos.foghike.tiles

import android.database.sqlite.SQLiteDatabase
import android.util.Log
import com.cuarentaydos.foghike.geo.Tile
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.ensureActive

/** IGN orthophoto WMTS (PNOA), Web Mercator matrix, so its tiles are plain XYZ tiles. */
const val IGN_TILE_URL =
    "https://www.ign.es/wmts/pnoa-ma?SERVICE=WMTS&REQUEST=GetTile&VERSION=1.0.0" +
        "&LAYER=OI.OrthoimageCoverage&STYLE=default&TILEMATRIXSET=GoogleMapsCompatible" +
        "&TILEMATRIX={z}&TILEROW={y}&TILECOL={x}&FORMAT=image/jpeg"
const val IGN_ATTRIBUTION = "PNOA cedido por © Instituto Geográfico Nacional de España"
const val IGN_MAX_ZOOM = 19

/** Guard against a huge rectangle hammering the free IGN service and filling the phone. */
const val MAX_TILES_PER_DOWNLOAD = 20_000

/**
 * Downloads [tiles] into the tile [store], skipping the ones it already has, so a download cut short
 * resumes where it stopped. Sequential and without retries: a failed tile is skipped and logged.
 */
suspend fun downloadTiles(
    store: SQLiteDatabase,
    tiles: Collection<Tile>,
    onProgress: suspend (done: Int, total: Int) -> Unit
) {
    val missing = tiles - store.storedTiles()
    missing.sortedWith(compareBy({ it.z }, { it.x }, { it.y })).forEachIndexed { i, tile ->
        coroutineContext.ensureActive()
        fetch(tile)?.let { store.putTile(tile, it) }
        onProgress(i + 1, missing.size)
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
            if (conn.responseCode == 200) {
                conn.inputStream.use { it.readBytes() }
            } else {
                null.also { Log.w("TileDownloader", "HTTP ${conn.responseCode} for $tile") }
            }
        } finally {
            conn.disconnect()
        }
    } catch (e: IOException) {
        Log.w("TileDownloader", "Failed $tile", e)
        null
    }
}
