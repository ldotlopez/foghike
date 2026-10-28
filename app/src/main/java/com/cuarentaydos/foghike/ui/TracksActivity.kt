package com.cuarentaydos.foghike.ui

import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.lifecycleScope
import com.cuarentaydos.foghike.FogHikeApp
import com.cuarentaydos.foghike.R
import com.cuarentaydos.foghike.core.deleteTracks
import com.cuarentaydos.foghike.core.importTrack
import com.cuarentaydos.foghike.core.trackTiles
import com.cuarentaydos.foghike.data.Source
import com.cuarentaydos.foghike.gpx.MAX_GPX_NAME
import com.cuarentaydos.foghike.gpx.parseGpx
import com.cuarentaydos.foghike.tiles.DownloadWorker
import com.cuarentaydos.foghike.tracking.TrackingService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Tracks screen: lists recordings and imports, imports GPX files and deletes tracks. Layout in
 * [TracksScreen], spec in docs/ui/screens/tracks.md.
 */
class TracksActivity : ComponentActivity() {
    private val app get() = application as FogHikeApp
    private var busy by mutableStateOf(false)
    private val snackbar = SnackbarHostState()
    private var offeredTracks = emptyList<String>()
    private val trackMaps by lazy {
        TileOfferState(app, lifecycleScope, app.prefs, "trackDownload", {
            trackTiles(app.db, app.settings.grid, offeredTracks, it)
        }) { zooms ->
            DownloadWorker.enqueueTracks(this, offeredTracks, zooms)
            showMessage(getString(R.string.download_queued))
        }
    }

    private val pickGpx =
        registerForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
            if (uris.isNotEmpty()) importGpx(uris)
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            FogHikeTheme {
                // Room re-emits the list after every import or delete.
                val tracks by remember { app.db.trackDao().all() }.collectAsState(null)
                val activeTrackId by TrackingService.activeTrackId.collectAsState()
                TracksScreen(
                    tracks,
                    activeTrackId,
                    busy,
                    snackbar,
                    // Many file providers don't know the GPX type, so generic XML/binary types are accepted too.
                    onImport = {
                        pickGpx.launch(
                            arrayOf(
                                "application/gpx+xml",
                                "application/xml",
                                "text/xml",
                                "application/octet-stream"
                            )
                        )
                    },
                    onDelete = ::delete,
                    onBack = ::finish
                )
                trackMaps.offer?.let {
                    TileOfferSheet(
                        stringResource(R.string.download_track_maps),
                        it,
                        trackMaps::setZoom,
                        trackMaps::download,
                        trackMaps::dismiss
                    )
                }
            }
        }
    }

    /** Runs [block] with the progress bar on and the actions off. */
    private fun whileBusy(block: suspend () -> Unit) = lifecycleScope.launch {
        busy = true
        try {
            block()
        } finally {
            busy = false
        }
    }

    private fun showMessage(text: String) = lifecycleScope.launch { snackbar.showSnackbar(text) }

    private fun delete(trackIds: List<String>) = whileBusy {
        // Finishes even if the user leaves the screen; it is one transaction either way.
        withContext(NonCancellable) { deleteTracks(app.db, app.settings.grid, trackIds) }
        showMessage(
            resources.getQuantityString(R.plurals.tracks_deleted, trackIds.size, trackIds.size)
        )
    }

    private fun importGpx(uris: List<Uri>) = whileBusy {
        var points = 0
        var failed = 0
        var duplicates = 0
        val importedIds = mutableListOf<String>()
        for (uri in uris) {
            try {
                val gpx =
                    withContext(Dispatchers.IO) {
                        contentResolver.openInputStream(uri)?.use { parseGpx(it) }
                    }
                // A file without track points would leave an empty track in the list.
                if (gpx == null || gpx.points.isEmpty()) {
                    failed++
                    continue
                }
                val name = gpx.name ?: fileName(uri)
                val trackId =
                    importTrack(
                        app.db,
                        app.settings.grid,
                        "gpx-",
                        gpx.points,
                        Source.GPX_IMPORT,
                        name
                    )
                if (trackId != null) {
                    points += gpx.points.size
                    importedIds += trackId
                } else {
                    duplicates++
                }
            } catch (e: Exception) {
                Log.w("TracksActivity", "GPX import failed: $uri", e)
                failed++
            }
        }
        val imported = uris.size - failed - duplicates
        val message = listOfNotNull(
            getString(R.string.gpx_imported, points, imported).takeIf { imported > 0 },
            getString(R.string.gpx_duplicates, duplicates).takeIf { duplicates > 0 },
            getString(R.string.gpx_failed, failed).takeIf { failed > 0 }
        ).joinToString(" ")
        if (importedIds.isEmpty()) {
            showMessage(message)
            return@whileBusy
        }
        // Imported routes are often where no map was downloaded yet: offer their tiles (decision 0015).
        lifecycleScope.launch {
            val result = snackbar.showSnackbar(
                message,
                getString(R.string.action_download_maps),
                duration = SnackbarDuration.Long
            )
            if (result == SnackbarResult.ActionPerformed) {
                offeredTracks = importedIds.toList()
                trackMaps.open()
            }
        }
    }

    /** The picked file's name without ".gpx", as the provider reports it; null if it doesn't. */
    private fun fileName(uri: Uri): String? = contentResolver.query(
        uri,
        arrayOf(OpenableColumns.DISPLAY_NAME),
        null,
        null,
        null
    )?.use { c ->
        if (c.moveToFirst()) c.getString(0) else null
    }?.let {
        if (it.endsWith(".gpx", ignoreCase = true)) it.dropLast(4) else it
    }?.take(MAX_GPX_NAME)
}
