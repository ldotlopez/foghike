package com.cuarentaydos.foghike.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.cuarentaydos.foghike.R
import org.maplibre.android.maps.MapView

/**
 * Download screen layout: the map (where areas are drawn) fills the screen, with the help on top and
 * the zoom fields, actions, [message] and IGN attribution at the bottom.
 */
@Composable
fun DownloadScreen(
    mapView: MapView,
    message: String,
    onClear: () -> Unit,
    onDownload: (minZoom: String, maxZoom: String) -> Unit,
) {
    // rememberSaveable keeps what was typed when the phone rotates.
    var minZoom by rememberSaveable { mutableStateOf("13") }
    var maxZoom by rememberSaveable { mutableStateOf("16") }

    Box(Modifier.fillMaxSize().background(MAP_BACKGROUND_COLOR)) {
        AndroidView(factory = { mapView }, modifier = Modifier.fillMaxSize())

        MapPanel(WindowInsetsSides.Top, Modifier.align(Alignment.TopCenter)) {
            Text(stringResource(R.string.download_help))
        }

        MapPanel(WindowInsetsSides.Bottom, Modifier.align(Alignment.BottomCenter)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ZoomField(R.string.min_zoom, minZoom, { minZoom = it }, Modifier.weight(1f))
                ZoomField(R.string.max_zoom, maxZoom, { maxZoom = it }, Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ActionButton(stringResource(R.string.clear), onClear, Modifier.weight(1f))
                ActionButton(stringResource(R.string.download), { onDownload(minZoom, maxZoom) }, Modifier.weight(1f), filled = true)
            }
            if (message.isNotEmpty()) Text(message)
            IgnAttribution()
        }
    }
}

@Composable
private fun ZoomField(label: Int, value: String, onChange: (String) -> Unit, modifier: Modifier) {
    OutlinedTextField(
        value = value,
        // Zoom levels have at most two digits; the range is checked when downloading.
        onValueChange = { onChange(it.filter(Char::isDigit).take(2)) },
        label = { Text(stringResource(label)) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = modifier,
    )
}
