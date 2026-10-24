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
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.cuarentaydos.foghike.R

/** The my location button's state; [MainActivity] maps it to MapLibre's camera modes. */
enum class LocationMode { FREE, FOLLOW, COMPASS }

/**
 * Main screen layout (docs/ui/screens/main.md): the map fills the screen, with the status chip and
 * help on top and the my location button, toolbar and IGN attribution at the bottom. Holds no app
 * state: [MainActivity] passes values and actions.
 */
@Composable
fun MainScreen(
    map: @Composable () -> Unit,
    cellCount: Int,
    mapCount: Int,
    tracking: Boolean,
    locationMode: LocationMode,
    snackbar: SnackbarHostState,
    onTrack: () -> Unit,
    onMaps: () -> Unit,
    onImportGpx: () -> Unit,
    onSettings: () -> Unit,
    onLocation: () -> Unit,
) {
    // Collapsed while exploring, so the map is as free as possible; reset whenever exploring starts or stops.
    var expanded by rememberSaveable(tracking) { mutableStateOf(!tracking) }

    Box(Modifier.fillMaxSize().background(MAP_BACKGROUND_COLOR)) {
        map()

        Row(
            Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)).padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            StatusChip(cellCount, mapCount, onMaps)
            Spacer(Modifier.weight(1f))
            HelpButton(stringResource(R.string.app_name), stringResource(R.string.help_main))
        }

        Column(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal))
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SnackbarHost(snackbar)
            LocationButton(locationMode, onLocation, Modifier.align(Alignment.End))
            MapToolbar(
                expanded = expanded,
                fab = { ExploreButton(tracking, onTrack) },
                modifier = Modifier.align(Alignment.CenterHorizontally),
                onExpandedChange = if (tracking) ({ expanded = it }) else null,
            ) {
                MapIconButton(R.drawable.ic_map, stringResource(R.string.action_maps), onMaps)
                MapIconButton(R.drawable.ic_upload_file, stringResource(R.string.action_import_gpx), onImportGpx)
                MapIconButton(R.drawable.ic_settings, stringResource(R.string.action_settings), onSettings)
            }
            // A tight translucent background keeps the attribution readable over bright imagery.
            Surface(color = MaterialTheme.colorScheme.surface.copy(alpha = 0.6f), shape = MaterialTheme.shapes.extraSmall) {
                Box(Modifier.padding(horizontal = 4.dp)) { IgnAttribution() }
            }
        }
    }
}

/** Revealed cells and offline maps; with no maps it warns instead and opens the download screen. */
@Composable
private fun StatusChip(cellCount: Int, mapCount: Int, onMaps: () -> Unit) {
    val cells = "%,d".format(cellCount)
    val description = if (mapCount == 0) "${stringResource(R.string.chip_cells, cells)}, ${stringResource(R.string.chip_no_maps)}"
    else stringResource(R.string.chip_status, cells, mapCount)
    AssistChip(
        onClick = { if (mapCount == 0) onMaps() },
        label = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ChipIcon(R.drawable.ic_grid_view)
                Text(cells)
                Text("  ·  ")
                if (mapCount == 0) {
                    ChipIcon(R.drawable.ic_warning, MaterialTheme.colorScheme.error)
                    Text(stringResource(R.string.chip_no_maps))
                } else {
                    ChipIcon(R.drawable.ic_map)
                    Text("$mapCount")
                }
            }
        },
        colors = AssistChipDefaults.assistChipColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        // Read as one sentence; only tappable for screen readers when it does something.
        modifier = Modifier.clearAndSetSemantics {
            contentDescription = description
            if (mapCount == 0) onClick { onMaps(); true }
        },
    )
}

@Composable
private fun ChipIcon(icon: Int, tint: Color = MaterialTheme.colorScheme.onSurfaceVariant) {
    Icon(painterResource(icon), contentDescription = null, tint = tint, modifier = Modifier.size(18.dp))
    Spacer(Modifier.width(4.dp))
}

/** Record / stop: red record dot when idle (not to be mistaken for the blue position dot), stop on red while exploring. */
@Composable
private fun ExploreButton(tracking: Boolean, onTrack: () -> Unit) {
    val label = stringResource(if (tracking) R.string.action_stop else R.string.action_explore)
    val colors = MaterialTheme.colorScheme
    WithTooltip(label) {
        FloatingActionButton(
            onTrack,
            containerColor = if (tracking) colors.errorContainer else colors.primaryContainer,
            contentColor = if (tracking) colors.onErrorContainer else colors.error,
        ) {
            Icon(painterResource(if (tracking) R.drawable.ic_stop else R.drawable.ic_fiber_manual_record), contentDescription = label)
        }
    }
}

/** Cycles Free → Follow → Compass → Follow; its label says what the next tap does. */
@Composable
private fun LocationButton(mode: LocationMode, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val (icon, label) = when (mode) {
        LocationMode.FREE -> R.drawable.ic_location_searching to R.string.action_follow
        LocationMode.FOLLOW -> R.drawable.ic_my_location to R.string.action_compass
        LocationMode.COMPASS -> R.drawable.ic_explore to R.string.action_north_up
    }
    val text = stringResource(label)
    Box(modifier) {
        WithTooltip(text) {
            FloatingActionButton(onClick, containerColor = MaterialTheme.colorScheme.surfaceContainer) {
                Icon(painterResource(icon), contentDescription = text)
            }
        }
    }
}

// Previews: one per state in docs/ui/screens/main.md. ui/MainScreenScreenshotTest.kt renders them.

@Composable
private fun MapPlaceholder() = Box(Modifier.fillMaxSize().background(Color(0xFF5B6B4E)))

@Composable
internal fun MainPreview(
    tracking: Boolean = false,
    mapCount: Int = 3,
    locationMode: LocationMode = LocationMode.FREE,
    snackbarText: String? = null,
) = FogHikeTheme {
    val snackbar = remember { SnackbarHostState() }
    if (snackbarText != null) LaunchedEffect(Unit) { snackbar.showSnackbar(snackbarText) }
    MainScreen({ MapPlaceholder() }, 1234, mapCount, tracking, locationMode, snackbar, {}, {}, {}, {}, {})
}

@Preview(showSystemUi = true) @Composable internal fun MainDefault() = MainPreview()
@Preview(showSystemUi = true) @Composable internal fun MainExploringCollapsed() = MainPreview(tracking = true)
@Preview(showSystemUi = true) @Composable internal fun MainNoMaps() = MainPreview(mapCount = 0)
@Preview(showSystemUi = true) @Composable internal fun MainSnackbar() = MainPreview(snackbarText = "Imported 3,412 points from 2 files.")
@Preview(showSystemUi = true, uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES) @Composable internal fun MainDark() = MainPreview()
@Preview(showSystemUi = true) @Composable internal fun MainFollowing() = MainPreview(locationMode = LocationMode.FOLLOW)
@Preview(showSystemUi = true) @Composable internal fun MainCompass() = MainPreview(locationMode = LocationMode.COMPASS)
