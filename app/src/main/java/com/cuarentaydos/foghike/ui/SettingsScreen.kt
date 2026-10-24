package com.cuarentaydos.foghike.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.cuarentaydos.foghike.R
import kotlin.math.roundToInt

/**
 * Settings layout (docs/ui/screens/settings.md): a top bar with back, then sections of list rows.
 * Holds no app state: [SettingsActivity] reads and stores the values.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(fogOpacity: Int, onFogOpacity: (Int) -> Unit, version: String, onBack: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.action_settings)) },
                navigationIcon = {
                    val back = stringResource(R.string.action_back)
                    WithTooltip(back) {
                        IconButton(onBack) { Icon(painterResource(R.drawable.ic_arrow_back), contentDescription = back) }
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).verticalScroll(rememberScrollState())) {
            SectionHeader(R.string.section_debug)
            val opacityLabel = stringResource(R.string.fog_opacity)
            ListItem(
                // The value goes on the headline row: a trailing slot would center it on the whole item.
                headlineContent = {
                    Row {
                        Text(opacityLabel, Modifier.weight(1f))
                        Text(stringResource(R.string.percent, fogOpacity))
                    }
                },
                supportingContent = {
                    Column {
                        Slider(
                            value = fogOpacity / 100f,
                            onValueChange = { onFogOpacity((it * 20).roundToInt() * 5) },
                            steps = 19, // 5 % steps: 0, 5, … 100
                            modifier = Modifier.semantics { contentDescription = opacityLabel },
                        )
                        FogPreview(fogOpacity)
                        IgnAttribution()
                    }
                },
            )
            SectionHeader(R.string.section_about)
            ListItem(headlineContent = { Text(stringResource(R.string.version)) }, trailingContent = { Text(version) })
        }
    }
}

@Composable
private fun SectionHeader(text: Int) = Text(
    stringResource(text),
    style = MaterialTheme.typography.labelLarge,
    color = MaterialTheme.colorScheme.primary,
    modifier = Modifier.padding(start = 16.dp, top = 24.dp, bottom = 4.dp),
)

/**
 * Sample IGN imagery under the same fog texture the map uses, at [opacity] %, so the slider can be
 * judged without the map. Decorative: the slider already tells screen readers the value.
 */
@Composable
private fun FogPreview(opacity: Int) {
    val fog = remember {
        val size = FOG_TEXTURE_SIZE
        val bitmap = Bitmap.createBitmap(fogTexture(size), size, size, Bitmap.Config.ARGB_8888).asImageBitmap()
        // Repeated at its pixel size, as MapLibre repeats it as a fill pattern.
        ShaderBrush(ImageShader(bitmap, TileMode.Repeated, TileMode.Repeated))
    }
    Card(Modifier.fillMaxWidth().padding(vertical = 8.dp).height(120.dp)) {
        Box {
            Image(painterResource(R.drawable.fog_preview_sample), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            Box(Modifier.fillMaxSize().background(fog, alpha = opacity / 100f))
        }
    }
}

// Previews: one per state in docs/ui/screens/settings.md. ui/SettingsScreenScreenshotTest.kt renders them.

@Composable
internal fun SettingsPreview(opacity: Int = 70) = FogHikeTheme {
    var value by remember { mutableIntStateOf(opacity) }
    SettingsScreen(value, { value = it }, "0.1 (1)", {})
}

@Preview(showSystemUi = true) @Composable internal fun SettingsDefault() = SettingsPreview()
@Preview(showSystemUi = true) @Composable internal fun SettingsOpacityMin() = SettingsPreview(0)
@Preview(showSystemUi = true) @Composable internal fun SettingsOpacityMax() = SettingsPreview(100)
@Preview(showSystemUi = true, uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES) @Composable internal fun SettingsDark() = SettingsPreview()
