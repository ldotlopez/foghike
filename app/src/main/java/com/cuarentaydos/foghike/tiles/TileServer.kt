package com.cuarentaydos.foghike.tiles

import android.database.sqlite.SQLiteDatabase
import android.util.Log
import java.io.File
import java.io.IOException
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import kotlin.concurrent.thread

/** Name (without .mbtiles) of the Spain overview map bundled in the APK assets. */
const val BASE_MAP_NAME = "spain"

/**
 * Minimal HTTP server on 127.0.0.1 that serves tiles from the .mbtiles files in [dir], because this
 * MapLibre version cannot read MBTiles directly. URLs look like /{name}/{z}/{x}/{y}.
 */
class TileServer(private val dir: File) {
    private val socket = ServerSocket(0, 50, InetAddress.getByName("127.0.0.1"))
    private val pool = Executors.newFixedThreadPool(4)
    private val databases = ConcurrentHashMap<String, SQLiteDatabase>()

    fun start() {
        thread(isDaemon = true, name = "tile-server") {
            while (true) {
                val client = socket.accept()
                pool.execute { handle(client) }
            }
        }
    }

    fun urlTemplate(name: String) = "http://127.0.0.1:${socket.localPort}/$name/{z}/{x}/{y}"

    private fun handle(client: Socket) = client.use {
        val tile = try {
            val match = it.getInputStream().bufferedReader().readLine()?.let(REQUEST::matchEntire)
            match?.destructured?.let { (name, z, x, y) -> read(name, z.toInt(), x.toInt(), y.toInt()) }
        } catch (e: Exception) {
            Log.w("TileServer", "Bad request", e)
            null
        }
        try {
            val out = it.getOutputStream()
            if (tile == null) {
                out.write("HTTP/1.1 404 Not Found\r\nContent-Length: 0\r\nConnection: close\r\n\r\n".toByteArray())
            } else {
                out.write("HTTP/1.1 200 OK\r\nContent-Type: image/jpeg\r\nContent-Length: ${tile.size}\r\nConnection: close\r\n\r\n".toByteArray())
                out.write(tile)
            }
            out.flush()
        } catch (_: IOException) {
            // MapLibre drops requests for tiles it no longer needs (e.g. while zooming); the client is gone.
        }
    }

    private fun read(name: String, z: Int, x: Int, y: Int): ByteArray? {
        if (z > 22) return null
        val file = File(dir, "$name.mbtiles")
        if (!file.exists()) return null
        val db = databases.getOrPut(name) {
            SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READONLY)
        }
        val tmsY = (1 shl z) - 1 - y // MBTiles stores rows bottom-up (TMS)
        return db.rawQuery(
            "SELECT tile_data FROM tiles WHERE zoom_level = ? AND tile_column = ? AND tile_row = ?",
            arrayOf(z.toString(), x.toString(), tmsY.toString()),
        ).use { c -> if (c.moveToFirst()) c.getBlob(0) else null }
    }

    private companion object {
        // Strict name pattern: other apps can reach 127.0.0.1 too, so no path traversal into app files.
        val REQUEST = Regex("""GET /([a-z0-9-]+)/(\d{1,2})/(\d{1,7})/(\d{1,7}) HTTP/1\.[01]""")
    }
}
