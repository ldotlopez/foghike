package com.cuarentaydos.foghike

import android.app.Application
import android.util.Log
import androidx.room.Room
import com.cuarentaydos.foghike.data.AppDatabase
import com.cuarentaydos.foghike.tiles.BASE_MAP_NAME
import com.cuarentaydos.foghike.tiles.TileServer
import java.io.File
import java.io.FileNotFoundException
import kotlin.concurrent.thread

/**
 * Process-wide objects. Android creates this class before any screen or service
 * (it is registered with android:name in the manifest).
 */
class FogHikeApp : Application() {
    lateinit var db: AppDatabase
    lateinit var tileServer: TileServer
    val mapsDir: File get() = File(filesDir, "maps")

    override fun onCreate() {
        super.onCreate()
        // The only Android-specific piece of the database; becomes expect/actual when porting to KMP.
        db = Room.databaseBuilder(this, AppDatabase::class.java, "foghike.db")
            // shortcut: wipes data on schema change, switch to real migrations before handing out the first APK.
            .fallbackToDestructiveMigration(dropAllTables = true)
            .build()
        mapsDir.mkdirs()
        tileServer = TileServer(mapsDir).also { it.start() }
        thread(name = "base-map-copy") { copyBaseMap() }
    }

    /** SQLite needs a real file, so the bundled base map is copied out of the APK assets once. */
    private fun copyBaseMap() {
        val target = File(mapsDir, "$BASE_MAP_NAME.mbtiles")
        if (target.exists()) return
        val tmp = File(mapsDir, "$BASE_MAP_NAME.tmp")
        try {
            assets.open("$BASE_MAP_NAME.mbtiles").use { input -> tmp.outputStream().use { input.copyTo(it) } }
            tmp.renameTo(target)
        } catch (e: FileNotFoundException) {
            Log.w("FogHike", "Base map asset missing; run tools/build_spain_mbtiles.py", e)
        }
    }
}
