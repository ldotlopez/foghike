package com.cuarentaydos.foghike

import android.app.Application
import android.content.SharedPreferences
import android.util.Log
import androidx.room.Room
import com.cuarentaydos.foghike.core.AppSettings
import com.cuarentaydos.foghike.core.CELL_SIZES
import com.cuarentaydos.foghike.core.FOG_DETAILS
import com.cuarentaydos.foghike.core.FOG_EDGES
import com.cuarentaydos.foghike.core.FogMode
import com.cuarentaydos.foghike.core.GPS_INTERVALS
import com.cuarentaydos.foghike.core.LocationMode
import com.cuarentaydos.foghike.core.RADIUS_FACTORS
import com.cuarentaydos.foghike.core.TRAIL_COLORS
import com.cuarentaydos.foghike.core.TRAIL_MINUTES
import com.cuarentaydos.foghike.core.TRAIL_WIDTHS
import com.cuarentaydos.foghike.core.USER_ZOOMS
import com.cuarentaydos.foghike.core.rebuildCells
import com.cuarentaydos.foghike.data.AppDatabase
import com.cuarentaydos.foghike.data.MIGRATION_2_3
import com.cuarentaydos.foghike.tiles.BASE_MAP_NAME
import com.cuarentaydos.foghike.tiles.TILE_STORE_FILE
import com.cuarentaydos.foghike.tiles.TileServer
import com.cuarentaydos.foghike.tiles.mergeFrom
import com.cuarentaydos.foghike.tiles.openTileStore
import java.io.File
import java.io.FileNotFoundException
import kotlin.concurrent.thread
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Set while stored cells don't match the saved cell size or reveal radius yet. */
private const val CELLS_STALE = "cellsStale"

/**
 * Process-wide objects. Android creates this class before any screen or service
 * (it is registered with android:name in the manifest).
 */
class FogHikeApp : Application() {
    lateinit var db: AppDatabase
    lateinit var tileServer: TileServer
    val mapsDir: File get() = File(filesDir, "maps")
    // The "ui" preferences file: the user's settings plus a few internal flags.
    val prefs: SharedPreferences by lazy { getSharedPreferences("ui", MODE_PRIVATE) }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** Read on every use, so screens and the tracking service see changes; SharedPreferences caches them in memory. */
    val settings: AppSettings get() = prefs.readSettings()

    override fun onCreate() {
        super.onCreate()
        // The only Android-specific piece of the database; becomes expect/actual when porting to KMP.
        db = Room.databaseBuilder(this, AppDatabase::class.java, "foghike.db")
            .addMigrations(MIGRATION_2_3)
            // shortcut: wipes data on a schema change without a migration; drop it before handing out the first APK.
            .fallbackToDestructiveMigration(dropAllTables = true)
            .build()
        mapsDir.mkdirs()
        tileServer = TileServer(mapsDir).also { it.start() }
        thread(name = "base-map-copy") { copyBaseMap() }
        scope.launch { mergeLegacyMaps() }
        if (prefs.getBoolean(CELLS_STALE, false)) scope.launch { rebuildStaleCells() }
    }

    /**
     * Before decision 0015 every area had its own .mbtiles file; their tiles move into the shared store
     * once. Each file is deleted only after its tiles are in, so a merge cut short resumes next start.
     */
    private suspend fun mergeLegacyMaps() {
        val legacy = db.areaDao().all().filter { it.fileName != TILE_STORE_FILE }
        if (legacy.isEmpty()) return
        openTileStore(mapsDir).use { store ->
            for (area in legacy) {
                val file = File(mapsDir, area.fileName)
                if (file.exists()) store.mergeFrom(file)
                db.areaDao().setFileName(area.id, TILE_STORE_FILE)
                file.delete()
            }
        }
    }

    /**
     * Stores [s]. If the cell size or reveal radius changed, rebuilds every cell: the new values are
     * written first, flagged, so tracking already stores new points with them and a rebuild cut short
     * by the process dying runs again at the next start.
     */
    suspend fun saveSettings(s: AppSettings) {
        val rebuild = s.grid != settings.grid
        val edit = prefs.edit().putSettings(s)
        if (!rebuild) return edit.apply()
        edit.putBoolean(CELLS_STALE, true).commit()
        rebuildStaleCells()
    }

    private suspend fun rebuildStaleCells() {
        rebuildCells(db, settings.grid)
        prefs.edit().remove(CELLS_STALE).apply()
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

// Values outside the closed lists (e.g. from an older version) fall back to the default.
private fun SharedPreferences.readSettings(): AppSettings {
    val d = AppSettings()
    fun int(key: String, options: List<Int>, default: Int) = getInt(key, default).takeIf { it in options } ?: default
    return AppSettings(
        startMode = LocationMode.entries.firstOrNull { it.name == getString("startMode", null) } ?: d.startMode,
        batterySaver = getBoolean("batterySaver", d.batterySaver),
        cellSize = int("cellSize", CELL_SIZES, d.cellSize),
        // A string, so 1.2 reads back exactly (a Float would not match the list).
        radiusFactor = RADIUS_FACTORS.firstOrNull { it.toString() == getString("radiusFactor", null) } ?: d.radiusFactor,
        fogMode = FogMode.entries.firstOrNull { it.name == getString("fogMode", null) } ?: d.fogMode,
        slowPhone = getBoolean("slowPhone", d.slowPhone),
        fogDetail = int("fogDetail", FOG_DETAILS, d.fogDetail),
        fogEdge = int("fogEdge", FOG_EDGES, d.fogEdge),
        gpsIntervalSeconds = int("gpsInterval", GPS_INTERVALS, d.gpsIntervalSeconds),
        userZoom = int("userZoom", USER_ZOOMS, d.userZoom),
        showTrail = getBoolean("showTrail", d.showTrail),
        trailMinutes = int("trailMinutes", TRAIL_MINUTES, d.trailMinutes),
        trailColor = getString("trailColor", null)?.takeIf { it in TRAIL_COLORS } ?: d.trailColor,
        trailWidth = int("trailWidth", TRAIL_WIDTHS, d.trailWidth),
    )
}

private fun SharedPreferences.Editor.putSettings(s: AppSettings) = this
    .putString("startMode", s.startMode.name)
    .putBoolean("batterySaver", s.batterySaver)
    .putInt("cellSize", s.cellSize)
    .putString("radiusFactor", s.radiusFactor.toString())
    .putString("fogMode", s.fogMode.name)
    .putBoolean("slowPhone", s.slowPhone)
    .putInt("fogDetail", s.fogDetail)
    .putInt("fogEdge", s.fogEdge)
    .putInt("gpsInterval", s.gpsIntervalSeconds)
    .putInt("userZoom", s.userZoom)
    .putBoolean("showTrail", s.showTrail)
    .putInt("trailMinutes", s.trailMinutes)
    .putString("trailColor", s.trailColor)
    .putInt("trailWidth", s.trailWidth)
