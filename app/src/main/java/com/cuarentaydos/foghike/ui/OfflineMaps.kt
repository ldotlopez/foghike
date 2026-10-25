package com.cuarentaydos.foghike.ui

import android.content.SharedPreferences
import android.text.format.Formatter
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import com.cuarentaydos.foghike.FogHikeApp
import com.cuarentaydos.foghike.R
import com.cuarentaydos.foghike.core.DOWNLOAD_ZOOMS
import com.cuarentaydos.foghike.geo.Tile
import com.cuarentaydos.foghike.tiles.IGN_MAX_ZOOM
import com.cuarentaydos.foghike.tiles.MAX_TILES_PER_DOWNLOAD
import com.cuarentaydos.foghike.tiles.averageTileBytes
import com.cuarentaydos.foghike.tiles.openTileStore
import com.cuarentaydos.foghike.tiles.storedTiles
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

// Pieces shared by every screen that downloads or cleans up offline maps (decision 0015).

/** "Zoom 13 – 16" and a range slider with one stop per level, 0 to [IGN_MAX_ZOOM]. */
@Composable
fun ZoomRangeSlider(zoom: IntRange, onZoom: (IntRange) -> Unit) {
    Text(stringResource(R.string.zoom_range, zoom.first, zoom.last), style = MaterialTheme.typography.titleMedium)
    val label = stringResource(R.string.action_zoom_range)
    RangeSlider(
        value = zoom.first.toFloat()..zoom.last.toFloat(),
        onValueChange = { onZoom(it.start.roundToInt()..it.endInclusive.roundToInt()) },
        valueRange = 0f..IGN_MAX_ZOOM.toFloat(),
        steps = IGN_MAX_ZOOM - 1, // one stop per zoom level
        modifier = Modifier.clearAndSetSemantics { contentDescription = label },
    )
}

/** "4,210 / 20,000 tiles"; error colors over the limit, so the problem shows before tapping Download. */
@Composable
fun TilesChip(tiles: Int?) {
    val over = tiles != null && tiles > MAX_TILES_PER_DOWNLOAD
    val colors = MaterialTheme.colorScheme
    AssistChip(
        onClick = {},
        label = { Text(stringResource(R.string.chip_tiles, tiles?.let { "%,d".format(it) } ?: "…", "%,d".format(MAX_TILES_PER_DOWNLOAD))) },
        leadingIcon = {
            Icon(painterResource(if (over) R.drawable.ic_warning else R.drawable.ic_grid_view), null, Modifier.size(18.dp))
        },
        colors = AssistChipDefaults.assistChipColors(
            containerColor = if (over) colors.errorContainer else colors.surfaceContainer,
            labelColor = if (over) colors.onErrorContainer else colors.onSurface,
            leadingIconContentColor = if (over) colors.onErrorContainer else colors.onSurfaceVariant,
        ),
    )
}

/** A size as the system shows it ("12 MB"), in the user's locale. */
@Composable
fun fileSize(bytes: Long): String = Formatter.formatShortFileSize(LocalContext.current, bytes)

/** An offer to download tiles: the chosen [zoom] range, the tiles not stored yet and their estimated size (null while counting). */
data class TileOffer(val zoom: IntRange, val tiles: Int? = null, val bytes: Long? = null)

/** Bottom sheet for [offer]: zoom range, new tiles against the limit, estimated size and Download. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TileOfferSheet(title: String, offer: TileOffer, onZoom: (IntRange) -> Unit, onDownload: () -> Unit, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.padding(horizontal = 24.dp).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleLarge)
            ZoomRangeSlider(offer.zoom, onZoom)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TilesChip(offer.tiles)
                offer.bytes?.let { Text(stringResource(R.string.tiles_size, fileSize(it)), style = MaterialTheme.typography.bodyMedium) }
            }
            val tiles = offer.tiles
            Button(onDownload, Modifier.fillMaxWidth(), enabled = tiles != null && tiles in 1..MAX_TILES_PER_DOWNLOAD) {
                Text(stringResource(R.string.download))
            }
        }
    }
}

/**
 * Activity-side state of a [TileOfferSheet]: counts the tiles [tilesFor] a range that the store lacks,
 * off the main thread (the newest range wins), and hands the range to [enqueue]. The range is
 * remembered under [prefsKey] so each kind of download keeps its own.
 */
class TileOfferState(
    private val app: FogHikeApp,
    private val scope: CoroutineScope,
    private val prefs: SharedPreferences,
    private val prefsKey: String,
    private val tilesFor: suspend (IntRange) -> Set<Tile>,
    private val enqueue: (IntRange) -> Unit,
) {
    var offer by mutableStateOf<TileOffer?>(null)
        private set
    private var countJob: Job? = null

    fun open() {
        val min = prefs.getInt("${prefsKey}MinZoom", DOWNLOAD_ZOOMS.first).coerceIn(0, IGN_MAX_ZOOM)
        setZoom(min..prefs.getInt("${prefsKey}MaxZoom", DOWNLOAD_ZOOMS.last).coerceIn(min, IGN_MAX_ZOOM))
    }

    fun setZoom(range: IntRange) {
        prefs.edit().putInt("${prefsKey}MinZoom", range.first).putInt("${prefsKey}MaxZoom", range.last).apply()
        offer = TileOffer(range)
        countJob?.cancel()
        countJob = scope.launch {
            val (missing, bytes) = withContext(Dispatchers.IO) {
                val wanted = tilesFor(range)
                openTileStore(app.mapsDir).use { store ->
                    val missing = (wanted - store.storedTiles()).size
                    missing to missing.toLong() * store.averageTileBytes()
                }
            }
            offer = TileOffer(range, missing, bytes)
        }
    }

    fun download() {
        val o = offer ?: return
        enqueue(o.zoom)
        dismiss()
    }

    fun dismiss() {
        countJob?.cancel()
        offer = null
    }
}

/** A clean-up being confirmed: the tiles it deletes and their size (null while counting); [running] while deleting. */
data class Cleanup(val keepManual: Boolean = true, val tiles: Int? = null, val bytes: Long? = null, val running: Boolean = false)

/** Confirms a [cleanup]: what it deletes, the option to keep hand-drawn areas, and the tiles and space it frees. */
@Composable
fun CleanupDialog(cleanup: Cleanup, onKeepManual: (Boolean) -> Unit, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    val (keepManual, tiles, bytes, running) = cleanup
    AlertDialog(
        onDismissRequest = { if (!running) onDismiss() },
        title = { Text(stringResource(R.string.cleanup_maps)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.cleanup_text))
                Row(
                    Modifier.fillMaxWidth().height(MIN_TOUCH_TARGET)
                        .toggleable(keepManual, enabled = !running, role = Role.Checkbox, onValueChange = onKeepManual),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(keepManual, onCheckedChange = null, enabled = !running)
                    Text(stringResource(R.string.cleanup_keep_manual), Modifier.padding(start = 12.dp))
                }
                if (running) LinearProgressIndicator(Modifier.fillMaxWidth())
                else Text(
                    if (tiles == null || bytes == null) "…" else stringResource(R.string.cleanup_count, "%,d".format(tiles), fileSize(bytes)),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        },
        confirmButton = { TextButton(onConfirm, enabled = !running && tiles != null && tiles > 0) { Text(stringResource(R.string.delete)) } },
        dismissButton = { TextButton(onDismiss, enabled = !running) { Text(stringResource(R.string.cancel)) } },
    )
}
