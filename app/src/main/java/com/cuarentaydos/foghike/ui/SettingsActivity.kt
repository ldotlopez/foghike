package com.cuarentaydos.foghike.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.addCallback
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.lifecycleScope
import androidx.work.WorkManager
import com.cuarentaydos.foghike.FogHikeApp
import com.cuarentaydos.foghike.R
import com.cuarentaydos.foghike.core.AppSettings
import com.cuarentaydos.foghike.core.exploredTiles
import com.cuarentaydos.foghike.core.keepsTile
import com.cuarentaydos.foghike.core.replacementFor
import com.cuarentaydos.foghike.geo.Tile
import com.cuarentaydos.foghike.tiles.DownloadWorker
import com.cuarentaydos.foghike.tiles.TILE_STORE_FILE
import com.cuarentaydos.foghike.tiles.averageTileBytes
import com.cuarentaydos.foghike.tiles.deleteTiles
import com.cuarentaydos.foghike.tiles.openTileStore
import com.cuarentaydos.foghike.tiles.storedTiles
import java.io.File
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Settings. Values are saved as they change, except the cell size and reveal radius: those wait
 * until the user leaves, so several changes rebuild the stored cells only once. The main screen
 * reads everything again when it comes back to the foreground. Also downloads the maps of the
 * explored cells and cleans up the rest (decision 0015).
 */
class SettingsActivity : ComponentActivity() {
    private val app get() = application as FogHikeApp
    private val prefs by lazy { getSharedPreferences("ui", MODE_PRIVATE) }
    private var fogOpacity by mutableIntStateOf(70)
    private var settings by mutableStateOf(AppSettings())
    private lateinit var cellsBuiltWith: AppSettings
    private var busy by mutableStateOf(false)
    private var mapsBytes by mutableLongStateOf(0L)
    private var cleanup by mutableStateOf<Cleanup?>(null)
    private var cleanupJob: Job? = null
    private val snackbar = SnackbarHostState()

    // Stored cells still use cellsBuiltWith until the user leaves; app.settings keeps that size meanwhile.
    private val exploredMaps by lazy {
        TileOfferState(app, lifecycleScope, prefs, "exploredDownload", {
            exploredTiles(app.db, app.settings.cellSize, it)
        }) { zooms ->
            DownloadWorker.enqueueExplored(this, zooms)
            showMessage(getString(R.string.download_queued))
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        fogOpacity = prefs.getInt("fogOpacity", 70)
        cellsBuiltWith = app.settings
        settings = cellsBuiltWith
        // BuildConfig is off in this project; the installed package knows its own version.
        val info = packageManager.getPackageInfo(packageName, 0)
        val version = "${info.versionName} (${info.longVersionCode})"
        // The system back gesture also goes through leave(), so pending changes are applied.
        onBackPressedDispatcher.addCallback(this) { leave() }

        setContent {
            FogHikeTheme {
                SettingsScreen(
                    fogOpacity, ::changeFogOpacity, settings, cellsBuiltWith, ::changeSettings, busy, version,
                    mapsBytes, snackbar,
                    onDownloadExplored = exploredMaps::open, onCleanup = ::openCleanup, onBack = ::leave
                )
                exploredMaps.offer?.let {
                    TileOfferSheet(
                        stringResource(R.string.download_explored),
                        it,
                        exploredMaps::setZoom,
                        exploredMaps::download,
                        exploredMaps::dismiss
                    )
                }
                cleanup?.let {
                    CleanupDialog(it, ::setKeepManual, ::runCleanup) {
                        cleanupJob?.cancel()
                        cleanup = null
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        mapsBytes = File(app.mapsDir, TILE_STORE_FILE).length() // a download may have finished meanwhile
    }

    private fun showMessage(text: String) = lifecycleScope.launch { snackbar.showSnackbar(text) }

    private fun changeFogOpacity(value: Int) {
        fogOpacity = value
        prefs.edit().putInt("fogOpacity", value).apply()
    }

    private fun changeSettings(s: AppSettings) {
        settings = s
        lifecycleScope.launch {
            app.saveSettings(s.copy(cellSize = cellsBuiltWith.cellSize, radiusFactor = cellsBuiltWith.radiusFactor))
        }
    }

    /** Opens the clean-up dialog, unless a download is writing to the tile store right now. */
    private fun openCleanup() = lifecycleScope.launch {
        val works = WorkManager.getInstance(
            this@SettingsActivity
        ).getWorkInfosForUniqueWorkFlow(DownloadWorker.WORK_NAME).first()
        if (works.any { !it.state.isFinished }) {
            showMessage(getString(R.string.cleanup_wait_download))
        } else {
            setKeepManual(true)
        }
    }

    private fun setKeepManual(keep: Boolean) {
        cleanup = Cleanup(keepManual = keep)
        cleanupJob?.cancel()
        cleanupJob = lifecycleScope.launch {
            val (tiles, bytes) = withContext(Dispatchers.IO) { cleanupPlan(keep) }
            cleanup = Cleanup(keep, tiles.size, bytes)
        }
    }

    /** The stored tiles a clean-up deletes, and their estimated size. */
    private suspend fun cleanupPlan(keepManual: Boolean): Pair<List<Tile>, Long> {
        val kept = if (keepManual) app.db.areaDao().all().filter { it.isManual } else emptyList()
        return openTileStore(app.mapsDir).use { store ->
            val stored = store.storedTiles()
            val explored = exploredTiles(app.db, app.settings.cellSize, stored.map { it.z }.distinct())
            val delete = stored.filter { !keepsTile(it, explored, kept) }
            delete to delete.size.toLong() * store.averageTileBytes()
        }
    }

    /**
     * Deletes the tiles outside the explored cells (and outside hand-drawn areas, if kept). Without
     * them, the hand-drawn areas' records are replaced by one that keeps their zoom range on the map.
     */
    private fun runCleanup() {
        val keepManual = cleanup?.keepManual ?: return
        cleanup = cleanup?.copy(running = true)
        lifecycleScope.launch {
            val before = File(app.mapsDir, TILE_STORE_FILE).length()
            // Finishes even if the screen goes away: the store must not be left half compacted.
            withContext(NonCancellable + Dispatchers.IO) {
                val (delete, _) = cleanupPlan(keepManual)
                openTileStore(app.mapsDir).use { it.deleteTiles(delete) }
                if (!keepManual) {
                    val manual = app.db.areaDao().all().filter { it.isManual }
                    replacementFor(manual, "kept-${UUID.randomUUID()}", System.currentTimeMillis())?.let {
                        app.db.areaDao().insert(it)
                    }
                    app.db.areaDao().delete(manual.map { it.id })
                }
            }
            mapsBytes = File(app.mapsDir, TILE_STORE_FILE).length()
            cleanup = null
            showMessage(
                getString(
                    R.string.cleanup_done,
                    android.text.format.Formatter.formatShortFileSize(
                        this@SettingsActivity,
                        before - mapsBytes
                    )
                )
            )
        }
    }

    /** Back: rebuilds the cells first if the cell size or reveal radius changed. */
    private fun leave() {
        if (busy) return
        if (settings.grid == cellsBuiltWith.grid) return finish()
        busy = true
        lifecycleScope.launch {
            // Finishes even if the screen goes away; if the process dies, the app reruns it at the next start.
            withContext(NonCancellable) { app.saveSettings(settings) }
            finish()
        }
    }
}
