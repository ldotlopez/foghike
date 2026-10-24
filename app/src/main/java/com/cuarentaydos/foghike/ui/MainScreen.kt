package com.cuarentaydos.foghike.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.cuarentaydos.foghike.R
import org.maplibre.android.maps.MapView
import kotlin.math.roundToInt

/**
 * Main screen layout: the map fills the screen, with the status on top and the opacity slider,
 * actions and IGN attribution at the bottom. Holds no state: [MainActivity] passes values and actions.
 */
@Composable
fun MainScreen(
    mapView: MapView,
    cellCount: Int,
    mapCount: Int,
    tracking: Boolean,
    fogOpacity: Int,
    onFogOpacity: (Int) -> Unit,
    onTrack: () -> Unit,
    onMaps: () -> Unit,
    onImportGpx: () -> Unit,
) {
    Box(Modifier.fillMaxSize().background(MAP_BACKGROUND_COLOR)) {
        AndroidView(factory = { mapView }, modifier = Modifier.fillMaxSize())

        MapPanel(WindowInsetsSides.Top, Modifier.align(Alignment.TopCenter)) {
            Text(stringResource(R.string.status, cellCount, mapCount))
            if (mapCount == 0) Text(stringResource(R.string.no_maps))
        }

        MapPanel(WindowInsetsSides.Bottom, Modifier.align(Alignment.BottomCenter)) {
            val opacityLabel = stringResource(R.string.fog_opacity)
            Text(opacityLabel)
            Slider(
                value = fogOpacity / 100f,
                onValueChange = { onFogOpacity((it * 100).roundToInt()) },
                modifier = Modifier.semantics { contentDescription = opacityLabel },
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ActionButton(
                    stringResource(if (tracking) R.string.stop_exploring else R.string.start_exploring),
                    onTrack, Modifier.weight(1.4f), filled = true,
                )
                ActionButton(stringResource(R.string.maps), onMaps, Modifier.weight(1f))
                ActionButton(stringResource(R.string.import_gpx), onImportGpx, Modifier.weight(1f))
            }
            IgnAttribution()
        }
    }
}
