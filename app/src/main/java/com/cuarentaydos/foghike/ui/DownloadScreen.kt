package com.cuarentaydos.foghike.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.cuarentaydos.foghike.R
import com.cuarentaydos.foghike.tiles.IGN_MAX_ZOOM
import com.cuarentaydos.foghike.tiles.MAX_TILES_PER_DOWNLOAD
import kotlin.math.roundToInt

/** Background download state: [percent] of the running area, or null while waiting for a connection. */
data class DownloadProgress(val areasLeft: Int, val percent: Int?)

/**
 * Download screen layout (docs/ui/screens/download.md): the map (where areas are drawn) fills the
 * screen, with the selection and progress chips and help on top, and the toolbar (recenter included) and
 * IGN attribution at the bottom. Holds no app state: [DownloadActivity] passes values and actions.
 * [tiles] is the tile count of the drawn areas at [zoom], null while counting or with no areas.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadScreen(
    map: @Composable () -> Unit,
    areaCount: Int,
    tiles: Int?,
    zoom: IntRange,
    zoomSheetOpen: Boolean,
    progress: DownloadProgress?,
    snackbar: SnackbarHostState,
    helpFirst: Boolean,
    onHelpClosed: () -> Unit,
    onZoom: (IntRange) -> Unit,
    onZoomSheet: (Boolean) -> Unit,
    onUndo: () -> Unit,
    onClear: () -> Unit,
    onDownload: () -> Unit,
    onRecenter: () -> Unit,
) {
    Box(Modifier.fillMaxSize().background(MAP_BACKGROUND_COLOR)) {
        map()

        Row(
            Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)).padding(8.dp),
            verticalAlignment = Alignment.Top,
        ) {
            // Takes only the room the help button leaves, so a long translation wraps instead of overlapping it.
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (areaCount > 0) TilesChip(tiles)
                if (progress != null) ProgressChip(progress)
            }
            Spacer(Modifier.width(8.dp))
            HelpButton(stringResource(R.string.help_download_title), stringResource(R.string.help_download), openFirst = helpFirst, onClosed = onHelpClosed)
        }

        Column(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal))
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SnackbarHost(snackbar)
            // Never collapses: nobody explores from here, and these are the controls used while drawing.
            MapToolbar(
                expanded = true,
                fab = {
                    val label = stringResource(R.string.action_download)
                    WithTooltip(label) {
                        FloatingActionButton(onDownload) { Icon(painterResource(R.drawable.ic_download), contentDescription = label) }
                    }
                },
                modifier = Modifier.align(Alignment.CenterHorizontally),
                pinned = { MapIconButton(R.drawable.ic_my_location, stringResource(R.string.action_recenter), onRecenter) },
            ) {
                MapIconButton(R.drawable.ic_undo, stringResource(R.string.action_undo_area), onUndo, enabled = areaCount > 0)
                MapIconButton(R.drawable.ic_delete, stringResource(R.string.action_clear_areas), onClear, enabled = areaCount > 0)
                MapIconButton(R.drawable.ic_tune, stringResource(R.string.action_zoom_range), { onZoomSheet(true) })
            }
            MapAttribution()
        }
    }

    if (zoomSheetOpen) {
        ModalBottomSheet(onDismissRequest = { onZoomSheet(false) }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
            Column(Modifier.padding(horizontal = 24.dp).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.zoom_range, zoom.first, zoom.last), style = MaterialTheme.typography.titleMedium)
                val label = stringResource(R.string.action_zoom_range)
                RangeSlider(
                    value = zoom.first.toFloat()..zoom.last.toFloat(),
                    onValueChange = { onZoom(it.start.roundToInt()..it.endInclusive.roundToInt()) },
                    valueRange = 0f..IGN_MAX_ZOOM.toFloat(),
                    steps = IGN_MAX_ZOOM - 1, // one stop per zoom level
                    modifier = Modifier.clearAndSetSemantics { contentDescription = label },
                )
                if (areaCount > 0) TilesChip(tiles)
            }
        }
    }
}

/** "4,210 / 20,000 tiles"; error colors over the limit, so the problem shows before tapping Download. */
@Composable
private fun TilesChip(tiles: Int?) {
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

/** Areas left and the running area's percentage, or a waiting notice without a connection. */
@Composable
private fun ProgressChip(progress: DownloadProgress) {
    val text = if (progress.percent != null) pluralStringResource(R.plurals.chip_progress, progress.areasLeft, progress.areasLeft, progress.percent)
    else pluralStringResource(R.plurals.chip_waiting, progress.areasLeft, progress.areasLeft)
    Box {
        AssistChip(
            onClick = {},
            label = { Text(text) },
            leadingIcon = {
                Icon(painterResource(if (progress.percent != null) R.drawable.ic_download else R.drawable.ic_cloud_off), null, Modifier.size(18.dp))
            },
            colors = AssistChipDefaults.assistChipColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        )
        if (progress.percent != null) {
            // Sized to the chip (matchParentSize), in the gap between the chip and the bottom of its 48 dp touch area.
            Box(Modifier.matchParentSize()) {
                LinearProgressIndicator(
                    progress = { progress.percent / 100f },
                    modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(start = 8.dp, end = 8.dp, bottom = 2.dp),
                )
            }
        }
    }
}

// Previews: one per state in docs/ui/screens/download.md that the JVM can render (the drawn stroke
// and the downloaded areas are MapLibre layers). ui/DownloadScreenScreenshotTest.kt renders them.

@Composable
private fun MapPlaceholder() = Box(Modifier.fillMaxSize().background(Color(0xFF5B6B4E)))

@Composable
internal fun DownloadPreview(
    areaCount: Int = 0,
    tiles: Int? = null,
    zoomSheetOpen: Boolean = false,
    progress: DownloadProgress? = null,
    snackbarText: String? = null,
    snackbarAction: String? = null,
    helpFirst: Boolean = false,
) = FogHikeTheme {
    val snackbar = remember { SnackbarHostState() }
    if (snackbarText != null) LaunchedEffect(Unit) { snackbar.showSnackbar(snackbarText, snackbarAction) }
    DownloadScreen({ MapPlaceholder() }, areaCount, tiles, 13..16, zoomSheetOpen, progress, snackbar, helpFirst, {}, {}, {}, {}, {}, {}, {})
}

@Preview(showSystemUi = true) @Composable internal fun DownloadEmpty() = DownloadPreview()
@Preview(showSystemUi = true) @Composable internal fun DownloadAreas() = DownloadPreview(areaCount = 2, tiles = 4210)
@Preview(showSystemUi = true) @Composable internal fun DownloadTooMany() = DownloadPreview(areaCount = 3, tiles = 25310)
@Preview(showSystemUi = true) @Composable internal fun DownloadZoomSheet() = DownloadPreview(areaCount = 2, tiles = 4210, zoomSheetOpen = true)
@Preview(showSystemUi = true) @Composable internal fun DownloadRunning() = DownloadPreview(progress = DownloadProgress(2, 37))
@Preview(showSystemUi = true) @Composable internal fun DownloadWaiting() = DownloadPreview(progress = DownloadProgress(2, null))
@Preview(showSystemUi = true) @Composable internal fun DownloadFinished() = DownloadPreview(snackbarText = "Download finished.", snackbarAction = "View")
@Preview(showSystemUi = true) @Composable internal fun DownloadHelp() = DownloadPreview(helpFirst = true)
@Preview(showSystemUi = true, uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES) @Composable internal fun DownloadDark() = DownloadPreview(areaCount = 2, tiles = 4210)
